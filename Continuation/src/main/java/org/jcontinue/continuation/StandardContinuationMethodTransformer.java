package org.jcontinue.continuation;

import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.jcontinue.analyzer.AnalyzeMethodResult;
import org.jcontinue.analyzer.Frame;
import org.jcontinue.analyzer.MethodAnalyzer;
import org.jcontinue.analyzer.ObjectFrameItemClassNameSupplier;
import org.jcontinue.base.AsmUtils;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class StandardContinuationMethodTransformer implements ContinuationMethodTransformer {

    private final ContinuationMethodTransformerRegistry registry;
    private final MethodAnalyzer methodAnalyzer;
    private final ObjectFrameItemClassNameSupplier classNameSupplier;
    private final Map<String, byte[]> auxiliaryClasses = new ConcurrentHashMap<>();

    public StandardContinuationMethodTransformer(ContinuationMethodTransformerRegistry registry,
            MethodAnalyzer methodAnalyzer, ObjectFrameItemClassNameSupplier classNameSupplier) {
        this.registry = registry;
        this.methodAnalyzer = methodAnalyzer;
        this.classNameSupplier = classNameSupplier;
    }

    @Override
    public void transformMethod(String className, MethodNode method) {
        if (method.name.equals("<init>") || method.name.equals("<clinit>")) {
            throw new UnsupportedOperationException("transforming constructors is not supported");
        }
        AnalyzeMethodResult analyzeResult = methodAnalyzer.analyzeMethod(className, method);
        if (!AsmUtils.isStatic(method) &&
                ContinuationClassTransformerUtils.containsStoreIn0Local(method, analyzeResult)) {
            throw new UnsupportedOperationException("transforming method which overrides this is not supported");
        }

        //find pointcuts
        List<AbstractInsnNode> pointcutInvocationInstructions = new ArrayList<>();
        for (Iterator<AbstractInsnNode> i = method.instructions.iterator(); i.hasNext(); ) {
            AbstractInsnNode instruction = i.next();
            if (ContinuationClassTransformerUtils.isInvocationInstruction(instruction) &&
                    analyzeResult.isInstructionReachable(instruction) &&
                    registry.doTransformInvokeInstruction(className, method, instruction)) {
                if (instruction instanceof MethodInsnNode &&
                        ContinuationClassTransformerUtils.isInvocationConstructor((MethodInsnNode) instruction)) {
                    throw new UnsupportedOperationException("transforming constructor invocation is not supported");
                }
                pointcutInvocationInstructions.add(instruction);
            }
        }
        if (pointcutInvocationInstructions.isEmpty()) {
            return;
        }

        boolean methodStatic = AsmUtils.isStatic(method);
        int pointcutLocalIndex = ContinuationClassTransformerUtils.getLocalsNumber(method, analyzeResult);
        AbstractInsnNode startMethodInstruction = AsmUtils.getCodeInstruction(method.instructions.getFirst());
        InsnList storePointcutNumberInstructions =
                ContinuationClassTransformerUtils.getGetAndStorePointcutNumberInstructions(pointcutLocalIndex);
        AbstractInsnNode firstPointcutDistributionInstruction = storePointcutNumberInstructions.getLast();
        Map<? super AbstractInsnNode, ? extends Frame> frames = analyzeResult.getFrames();
        int auxiliaryLocalIndex = pointcutLocalIndex + 1;

        Set<AbstractInsnNode> auxiliaryInstructions = Sets.newHashSet(storePointcutNumberInstructions.iterator());
        method.instructions.insertBefore(startMethodInstruction, storePointcutNumberInstructions);

        Map<AbstractInsnNode, Map<Integer, InsnList>> actions = new HashMap<>();

        int pointcutNumberCounter = 1;
        for (AbstractInsnNode pointcutInvocation : pointcutInvocationInstructions) {

            List<AbstractInsnNode> pointcutDistributions = new ArrayList<>();
            pointcutDistributions.add(firstPointcutDistributionInstruction);
            pointcutDistributions.addAll(ContinuationClassTransformerUtils.getNewInstructionChain(pointcutInvocation,
                    frames));

            for (int i = 0; i < pointcutDistributions.size() - 1; i++) {
                AbstractInsnNode distribution = pointcutDistributions.get(i);
                AbstractInsnNode nextDistribution = pointcutDistributions.get(i + 1);

                Frame distributionFrame = frames.get(AsmUtils.getCodeInstruction(distribution.getNext()));
                Frame nextDistributionFrame = frames.get(nextDistribution);

                InsnList distributionPointcutInstructions = getActionInstructions(actions, distribution,
                        pointcutNumberCounter);

                distributionPointcutInstructions.add(
                        ContinuationClassTransformerUtils.getTransformFrameInstructions(distributionFrame,
                        nextDistributionFrame, auxiliaryLocalIndex)
                );

                distributionPointcutInstructions.add(AsmUtils.getGotoInstruction(nextDistribution, method.instructions));
            }

            Frame invocationFrame = frames.get(pointcutInvocation);
            PointcutFrameStructure pointcutStructure =
                    ContinuationClassTransformerUtils.getPointcutFrameStructure(invocationFrame,
                    pointcutInvocation, methodStatic, classNameSupplier);

            AbstractInsnNode lastDistribution = pointcutDistributions.get(pointcutDistributions.size() - 1);
            Frame lastDistributionFrame = frames.get(AsmUtils.getCodeInstruction(lastDistribution.getNext()));

            InsnList lastDistributionPointcutInstructions = getActionInstructions(actions, lastDistribution,
                    pointcutNumberCounter);

            lastDistributionPointcutInstructions.add(
                    ContinuationClassTransformerUtils.getLoadSavedFrameInstructions(lastDistributionFrame,
                    pointcutStructure, auxiliaryLocalIndex)
            );
            lastDistributionPointcutInstructions.add(AsmUtils.getPushAndStoreIntInstructions(0, pointcutLocalIndex));
            lastDistributionPointcutInstructions.add(AsmUtils.getGotoInstruction(pointcutInvocation,
                    method.instructions));

            AbstractInsnNode afterInvocationInstruction = AsmUtils.getCodeInstruction(pointcutInvocation.getNext());
            InsnList saveInstructions = new InsnList();
            saveInstructions.add(AsmUtils.getInvocationInstruction(finishedMethod));
            saveInstructions.add(AsmUtils.getGotoIfZeroInstruction(afterInvocationInstruction, method.instructions));

            // pop return value
            if (!pointcutStructure.getInvocationReturnType().equals(Type.VOID_TYPE)) {
                saveInstructions.add(AsmUtils.getPopInstruction(pointcutStructure.getInvocationReturnType()));
            }

            saveInstructions.add(ContinuationClassTransformerUtils.getSaveFrameInstructions(pointcutStructure,
                    pointcutNumberCounter, auxiliaryLocalIndex));
            saveInstructions.add(AsmUtils.getReturnAnyValueInstructions(Type.getReturnType(method.desc)));

            auxiliaryInstructions.addAll(Lists.newArrayList(saveInstructions.iterator()));
            method.instructions.insert(pointcutInvocation, saveInstructions);

            String savedContextClassName = pointcutStructure.getSavedFrameContextClassName();
            if (!auxiliaryClasses.containsKey(savedContextClassName)) {
                byte[] auxiliaryClassBody = ContinuationClassTransformerUtils.getSavedFrameContextClassBody(
                        pointcutStructure.getSortedAsmTypes());
                auxiliaryClasses.put(pointcutStructure.getSavedFrameContextClassName(), auxiliaryClassBody);
            }

            pointcutNumberCounter++;
        }

        for (Map.Entry<AbstractInsnNode, Map<Integer, InsnList>> actionEntry : actions.entrySet()) {
            AbstractInsnNode instruction = actionEntry.getKey();
            Map<Integer, InsnList> cases = actionEntry.getValue();

            LabelNode defaultLabel = AsmUtils.getLabelOnInstruction(instruction.getNext(), method.instructions);
            Map<Integer, LabelNode> caseLabels = new HashMap<>();
            for (Integer caseKey : cases.keySet()) {
                caseLabels.put(caseKey, new LabelNode());
            }
            InsnList switchInstructions = new InsnList();
            switchInstructions.add(AsmUtils.getLoadIntInstruction(pointcutLocalIndex));
            switchInstructions.add(AsmUtils.getSwitchInstruction(caseLabels, defaultLabel));
            for (Map.Entry<Integer, InsnList> caseEntry : cases.entrySet()) {
                Integer caseKey = caseEntry.getKey();
                InsnList caseInstructions = caseEntry.getValue();
                switchInstructions.add(caseLabels.get(caseKey));
                switchInstructions.add(caseInstructions);
            }
            auxiliaryInstructions.addAll(Lists.newArrayList(switchInstructions.iterator()));
            method.instructions.insert(instruction, switchInstructions);
        }

        // remove auxiliary instruction from try-catch blocks
        List<TryCatchBlockNode> tryCatchBlocks = new ArrayList<>();
        for (TryCatchBlockNode tryCatchBlock : (List<TryCatchBlockNode>) method.tryCatchBlocks) {
            AbstractInsnNode segmentStart = null;
            for (AbstractInsnNode instruction  = tryCatchBlock.start; !instruction.equals(tryCatchBlock.end);
                 instruction = instruction.getNext()) {
                if (AsmUtils.isCodeInstruction(instruction)) {
                    if (auxiliaryInstructions.contains(instruction)) {
                        if (segmentStart != null) {
                            tryCatchBlocks.add(getTryCatchBlock(tryCatchBlock, segmentStart, instruction,
                                    method.instructions));
                            segmentStart = null;
                        }
                    } else {
                        if (segmentStart == null) {
                            segmentStart = instruction;
                        }
                    }
                }
            }
            if (segmentStart != null) {
                tryCatchBlocks.add(getTryCatchBlock(tryCatchBlock, segmentStart, tryCatchBlock.end, method.instructions));
            }
        }
        method.tryCatchBlocks = tryCatchBlocks;

    }

    @Override
    public Map<String, byte[]> getAuxiliaryClasses() {
        return auxiliaryClasses;
    }

    // private methods

    private static TryCatchBlockNode getTryCatchBlock(TryCatchBlockNode original, AbstractInsnNode start,
            AbstractInsnNode end, InsnList instructions) {
        LabelNode startLabel = AsmUtils.getLabelOnInstruction(start, instructions);
        LabelNode endLabel = AsmUtils.getLabelOnInstruction(end, instructions);
        TryCatchBlockNode result = new TryCatchBlockNode(startLabel, endLabel, original.handler, original.type);
        result.invisibleTypeAnnotations = original.invisibleTypeAnnotations;
        result.visibleTypeAnnotations = original.visibleTypeAnnotations;
        return result;
    }

    private static final Method finishedMethod;

    static {
        try {
            finishedMethod = Continuation.class.getMethod("__finishedMethod");
        } catch (NoSuchMethodException e) {
            throw Throwables.propagate(e);
        }
    }

    private InsnList getActionInstructions(Map<AbstractInsnNode, Map<Integer, InsnList>> actions,
            AbstractInsnNode instruction, int pointcutNumber) {
        Map<Integer, InsnList> instructionActions;
        if (actions.containsKey(instruction)) {
            instructionActions = actions.get(instruction);
        } else {
            instructionActions = new HashMap<>();
            actions.put(instruction, instructionActions);
        }
        InsnList result;
        if (instructionActions.containsKey(pointcutNumber)) {
            result = instructionActions.get(pointcutNumber);
        } else {
            result = new InsnList();
            instructionActions.put(pointcutNumber, result);
        }
        return result;
    }


}
