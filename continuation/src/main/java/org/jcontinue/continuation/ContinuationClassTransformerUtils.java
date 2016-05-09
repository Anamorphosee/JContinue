package org.jcontinue.continuation;

import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import org.jcontinue.analyzer.AnalyzeMethodResult;
import org.jcontinue.analyzer.AnalyzerUtils;
import org.jcontinue.analyzer.BaseFrame;
import org.jcontinue.analyzer.Frame;
import org.jcontinue.analyzer.FrameItem;
import org.jcontinue.analyzer.ObjectFrameItemClassNameSupplier;
import org.jcontinue.analyzer.UninitializedObjectFrameItem;
import org.jcontinue.base.AsmUtils;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ContinuationClassTransformerUtils {

    public static int getLocalsNumber(MethodNode method, AnalyzeMethodResult analyzeMethodResult) {
        int result = 0;
        if (!AsmUtils.isStatic(method)) {
            result += AsmUtils.OBJECT_ASM_TYPE.getSize();
        }
        for (Type argumentType : Type.getArgumentTypes(method.desc)) {
            result += argumentType.getSize();
        }
        for (Iterator<AbstractInsnNode> i = method.instructions.iterator(); i.hasNext();) {
            AbstractInsnNode instruction = i.next();
            if (analyzeMethodResult.isInstructionReachable(instruction)) {
                if (instruction.getType() == AbstractInsnNode.VAR_INSN) {
                    VarInsnNode varInstruction = (VarInsnNode) instruction;
                    int localIndex = varInstruction.var;
                    if (varInstruction.getOpcode() == Opcodes.LLOAD || varInstruction.getOpcode() == Opcodes.DLOAD ||
                            varInstruction.getOpcode() == Opcodes.LSTORE ||
                            varInstruction.getOpcode() == Opcodes.DSTORE) {
                        localIndex++;
                    }
                    result = Math.max(result, localIndex + 1);
                }
                if (instruction.getType() == AbstractInsnNode.IINC_INSN) {
                    IincInsnNode incInstruction = (IincInsnNode) instruction;
                    result = Math.max(result, incInstruction.var + 1);
                }
            }
        }
        return result;
    }

    public static boolean containsStoreIn0Local(MethodNode method, AnalyzeMethodResult analyzeMethodResult) {
        for (Iterator<AbstractInsnNode> i = method.instructions.iterator(); i.hasNext();) {
            AbstractInsnNode instruction = i.next();
            if (analyzeMethodResult.isInstructionReachable(instruction) &&
                    instruction.getType() == AbstractInsnNode.VAR_INSN) {
                VarInsnNode varInstruction = (VarInsnNode) instruction;
                if (varInstruction.var == 0 && (varInstruction.getOpcode() == Opcodes.ISTORE ||
                        varInstruction.getType() == Opcodes.LSTORE || varInstruction.getType() == Opcodes.FSTORE ||
                        varInstruction.getType() == Opcodes.DSTORE || varInstruction.getType() == Opcodes.ASTORE)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static Frame removeFrameInvocationParameters(Frame frameWithInvocationParameters,
            AbstractInsnNode invocationInstruction) {
        String invocationMethodDescription;
        boolean isStatic;
        if (invocationInstruction instanceof MethodInsnNode) {
            MethodInsnNode methodInstruction = (MethodInsnNode) invocationInstruction;
            invocationMethodDescription = methodInstruction.desc;
            isStatic = methodInstruction.getOpcode() == Opcodes.INVOKESTATIC;
        } else if (invocationInstruction instanceof InvokeDynamicInsnNode) {
            InvokeDynamicInsnNode invokeDynamicInstruction = (InvokeDynamicInsnNode) invocationInstruction;
            invocationMethodDescription = invokeDynamicInstruction.desc;
            isStatic = true;
        } else {
            throw new IllegalArgumentException("invalid invocationInstruction: " + invocationInstruction);
        }
        int invocationArgumentsSize = 0;
        for (Type argumentType : Type.getArgumentTypes(invocationMethodDescription)) {
            invocationArgumentsSize += argumentType.getSize();
        }
        if (!isStatic) {
            invocationArgumentsSize++;
        }
        if (invocationArgumentsSize == 0) {
            return frameWithInvocationParameters;
        } else {
            BaseFrame result = new BaseFrame(frameWithInvocationParameters);
            for (int i = 0; i < invocationArgumentsSize; i++) {
                result.getStack().remove(result.getStack().size() - 1);
            }
            return result;
        }
    }

    public static TypeInsnNode getPreviousNewInstruction(Frame frame,
            Map<? super AbstractInsnNode, ? extends Frame> frames) {
        Set<UninitializedObjectFrameItem> frameUninitializedObjectItems = getUninitializedObjectFrameItemSet(frame);
        if (frameUninitializedObjectItems.isEmpty()) {
            return null;
        }
        for (UninitializedObjectFrameItem item : frameUninitializedObjectItems) {
            Frame newInstructionFrame = frames.get(item.getNewInstruction());
            Frame afterNewInstructionFrame = getFrameAfterNewInstruction(newInstructionFrame, item.getNewInstruction());
            Set<UninitializedObjectFrameItem> afterNewInstructionUninitializedObjectsFrameItems =
                    getUninitializedObjectFrameItemSet(afterNewInstructionFrame);
            if (afterNewInstructionUninitializedObjectsFrameItems.containsAll(frameUninitializedObjectItems)) {
                return item.getNewInstruction();
            }
        }
        throw new IllegalStateException("previous new instruction not found");
    }

    public static BaseFrame getFrameAfterNewInstruction(Frame frameBeforeNewInstruction, TypeInsnNode newInstruction) {
        BaseFrame result = new BaseFrame(frameBeforeNewInstruction);
        result.getStack().add(new UninitializedObjectFrameItem(newInstruction));
        return result;
    }

    public static InsnList getTransformFrameInstructions(Frame currentFrame, Frame targetFrame, int auxiliaryLocal) {
        InsnList result = getJavacTransformFrameInstructions(currentFrame, targetFrame);
        if (result != null) {
            return result;
        }
        throw new UnsupportedOperationException("transforming arbitrary frames is not supported yet");
    }

    public static InsnList getLoadSavedFrameInstructions(Frame currentFrame, PointcutFrameStructure pointcutStructure,
            int auxiliaryLocal) {
        int auxiliaryLocalCounter = auxiliaryLocal;
        Map<FrameItem, Integer> uninitializedObjectsLocalIndexes = new HashMap<>();
        InsnList result = new InsnList();
        for (FrameItem frameItem : Lists.reverse(currentFrame.getStack())) {
            if (frameItem.isStartingWord()) {
                if (AnalyzerUtils.isUninitializedReference(frameItem) &&
                        !uninitializedObjectsLocalIndexes.containsKey(frameItem)) {
                    result.add(AsmUtils.getStoreReferenceInstruction(auxiliaryLocalCounter));
                    uninitializedObjectsLocalIndexes.put(frameItem, auxiliaryLocalCounter);
                    auxiliaryLocalCounter++;
                } else if (frameItem.getWordsNumber() == 1) {
                    result.add(AsmUtils.getPop1WordInstruction());
                } else {
                    result.add(AsmUtils.getPop2WordInstruction());
                }
            }
        }
        for (int i = 0; i < currentFrame.getLocals().size(); i++) {
            FrameItem frameItem = currentFrame.getLocals().get(i);
            if (AnalyzerUtils.isUninitializedReference(frameItem) &&
                    !uninitializedObjectsLocalIndexes.containsKey(frameItem)) {
                result.add(AsmUtils.getLoadReferenceInstruction(i));
                result.add(AsmUtils.getStoreReferenceInstruction(auxiliaryLocalCounter));
                uninitializedObjectsLocalIndexes.put(frameItem, auxiliaryLocalCounter);
                auxiliaryLocalCounter++;
            }
        }

        // get and store saved context
        result.add(AsmUtils.getInvocationInstruction(getSavedFrameContextMethod));
        int savedFrameContextLocalIndex = -1;
        Type savedFrameContextClassType = null;
        if (!pointcutStructure.getSortedAsmTypes().isEmpty()) {
            savedFrameContextClassType = pointcutStructure.getSavedFrameContextClassType();
            result.add(AsmUtils.getCheckcastInstruction(savedFrameContextClassType));
            savedFrameContextLocalIndex = auxiliaryLocalCounter;
            result.add(AsmUtils.getStoreReferenceInstruction(savedFrameContextLocalIndex));
            auxiliaryLocalCounter++;
        } else {
            result.add(AsmUtils.getPop1WordInstruction());
        }

        // fill locals
        for (Map.Entry<Integer, PointcutFrameStructureItem> pointcutFrameItemEntry : pointcutStructure.getLocals().entrySet()) {
            int localIndex = pointcutFrameItemEntry.getKey();
            PointcutFrameStructureItem pointcutFrameItem = pointcutFrameItemEntry.getValue();
            FrameItem frameItem = pointcutFrameItem.getFrameItem();
            if (frameItem.equals(FrameItem.NULL)) {
                result.add(AsmUtils.getPushNullInstruction());
                result.add(AsmUtils.getStoreReferenceInstruction(localIndex));
            } else if (AnalyzerUtils.isUninitializedReference(frameItem)) {
                if (!uninitializedObjectsLocalIndexes.containsKey(frameItem)) {
                    throw new IllegalArgumentException("pointcutStructure contains " + frameItem + ". currentFrame " +
                            currentFrame);
                }
                int uninitializedObjectLocalIndex = uninitializedObjectsLocalIndexes.get(frameItem);
                result.add(AsmUtils.getLoadReferenceInstruction(uninitializedObjectLocalIndex));
                result.add(AsmUtils.getStoreReferenceInstruction(localIndex));
            } else {
                String fieldName = pointcutFrameItem.getFieldName();
                Type fieldType = pointcutFrameItem.getAsmType();
                result.add(AsmUtils.getLoadReferenceInstruction(savedFrameContextLocalIndex));
                result.add(AsmUtils.getGetFieldInstruction(savedFrameContextClassType, fieldName, fieldType, false));
                result.add(AsmUtils.getStoreInstruction(fieldType, localIndex));
            }
        }

        // fill stack
        for (PointcutFrameStructureItem pointcutFrameItem : pointcutStructure.getStack()) {
            FrameItem frameItem = pointcutFrameItem.getFrameItem();
            if (frameItem.equals(FrameItem.TOP) || frameItem.equals(FrameItem.NULL)) {
                result.add(AsmUtils.getPushNullInstruction());
            } else if (AnalyzerUtils.isUninitializedReference(frameItem)) {
                if (!uninitializedObjectsLocalIndexes.containsKey(frameItem)) {
                    throw new IllegalArgumentException("pointcutStructure contains " + frameItem + ". currentFrame " +
                            currentFrame);
                }
                int localIndex = uninitializedObjectsLocalIndexes.get(frameItem);
                result.add(AsmUtils.getLoadReferenceInstruction(localIndex));
            } else {
                String fieldName = pointcutFrameItem.getFieldName();
                Type fieldType = pointcutFrameItem.getAsmType();
                result.add(AsmUtils.getLoadReferenceInstruction(savedFrameContextLocalIndex));
                result.add(AsmUtils.getGetFieldInstruction(savedFrameContextClassType, fieldName, fieldType, false));
            }
        }

        // load invocation object
        if (pointcutStructure.getInvocationObjectItem() != null) {
            String fieldName = pointcutStructure.getInvocationObjectItem().getFieldName();
            Type fieldType = pointcutStructure.getInvocationObjectItem().getAsmType();
            result.add(AsmUtils.getLoadReferenceInstruction(savedFrameContextLocalIndex));
            result.add(AsmUtils.getGetFieldInstruction(savedFrameContextClassType, fieldName, fieldType, false));
        }

        if (!pointcutStructure.getSortedAsmTypes().isEmpty()) {
            result.add(AsmUtils.getPushAndStoreIntInstructions(0, savedFrameContextLocalIndex));
        }

        // fill arguments
        for (Type argumentType : pointcutStructure.getInvocationArgumentTypes()) {
            result.add(AsmUtils.getPushAnyValueInstruction(argumentType));
        }

        return result;
    }

    public static PointcutFrameStructure getPointcutFrameStructure(Frame invocationFrame,
            AbstractInsnNode invocationInstruction, boolean methodStatic,
            ObjectFrameItemClassNameSupplier classNameSupplier) {
        String asmMethodDescriptor = getAsmMethodDescriptor(invocationInstruction);
        boolean invocationStatic = isInvocationStatic(invocationInstruction);
        boolean reflectionMethodInvocation = isReflectionMethodInvocation(invocationInstruction);
        int argumentLength = 0;
        PointcutFrameStructure result = new PointcutFrameStructure();
        if (reflectionMethodInvocation) {
            result.getInvocationArgumentTypes().add(reflectMethodAsmType);
        }
        for (Type argumentType : Type.getArgumentTypes(asmMethodDescriptor)) {
            argumentLength += argumentType.getSize();
            result.getInvocationArgumentTypes().add(argumentType);
        }
        if (!invocationStatic) {
            argumentLength++;
        }
        List<PointcutFrameStructureItem> structureItems = new ArrayList<>();
        int index = 0;
        for (FrameItem frameItem : invocationFrame.getLocals()) {
            if (!isStorable(frameItem)) {
                if (!frameItem.equals(FrameItem.TOP)) {
                    PointcutFrameStructureItem structureItem = new PointcutFrameStructureItem();
                    structureItem.setFrameItem(FrameItem.NULL);
                    result.getLocals().put(index, structureItem);
                }
            } else if (!frameItem.equals(FrameItem.TOP) && frameItem.isStartingWord()) {
                Type amsType = AnalyzerUtils.getAsmType(frameItem, classNameSupplier);
                PointcutFrameStructureItem structureItem = new PointcutFrameStructureItem();
                structureItem.setFrameItem(frameItem);
                structureItem.setAsmType(amsType);
                structureItems.add(structureItem);
                result.getLocals().put(index, structureItem);
            }
            index++;
        }
        int invocationObjectStackIndex = invocationFrame.getStack().size() - argumentLength;
        List<? extends FrameItem> stackWithoutArguments =
                invocationFrame.getStack().subList(0, invocationObjectStackIndex);
        for (FrameItem frameItem : stackWithoutArguments) {
            if (!isStorable(frameItem)) {
                PointcutFrameStructureItem structureItem = new PointcutFrameStructureItem();
                structureItem.setFrameItem(frameItem);
                result.getStack().add(structureItem);
            } else if (frameItem.isStartingWord()) {
                Type asmType = AnalyzerUtils.getAsmType(frameItem, classNameSupplier);
                PointcutFrameStructureItem structureItem = new PointcutFrameStructureItem();
                structureItem.setFrameItem(frameItem);
                structureItem.setAsmType(asmType);
                structureItems.add(structureItem);
                result.getStack().add(structureItem);
            }
        }
        if (!invocationStatic && !reflectionMethodInvocation) {
            FrameItem frameItem = invocationFrame.getStack().get(invocationObjectStackIndex);
            if (!AnalyzerUtils.isInitializedReference(frameItem)) {
                throw new IllegalStateException("frameItem cannot be " + frameItem);
            }
            Type asmType = AnalyzerUtils.getAsmType(frameItem, classNameSupplier);
            PointcutFrameStructureItem structureItem = new PointcutFrameStructureItem();
            structureItem.setAsmType(asmType);
            structureItem.setFrameItem(frameItem);
            structureItems.add(structureItem);
            result.setInvocationObjectItem(structureItem);
        }
        Collections.sort(structureItems, pointcutFrameStructureItemTypeComparator);
        for (PointcutFrameStructureItem structureItem : structureItems) {
            result.getSortedAsmTypes().add(structureItem.getAsmType());
        }
        result.setSavedFrameContextClassName(getSavedContextClassName(result.getSortedAsmTypes()));
        for (int i = 0; i < structureItems.size(); i++) {
            PointcutFrameStructureItem structureItem = structureItems.get(i);
            structureItem.setFieldName(getSavedContextFieldName(i));
        }
        result.setMethodStatic(methodStatic);
        result.setInvocationReturnType(Type.getReturnType(asmMethodDescriptor));
        result.setReflectionMethodInvocation(reflectionMethodInvocation);
        return result;
    }

    public static InsnList getSaveFrameInstructions(PointcutFrameStructure pointcutStructure, int pointcutNumber,
            int auxiliaryLocal) {
        InsnList result = new InsnList();

        // construct saved frame context
        int savedFrameContextLocalIndex = auxiliaryLocal++;
        result.add(AsmUtils.getConstructNewObjectInstrcutions(pointcutStructure.getSavedFrameContextClassType()));
        result.add(AsmUtils.getStoreReferenceInstruction(savedFrameContextLocalIndex));

        // save stack items
        for (PointcutFrameStructureItem pointcutFrameItem : Lists.reverse(pointcutStructure.getStack())) {
            FrameItem frameItem = pointcutFrameItem.getFrameItem();
            if (frameItem.equals(FrameItem.TOP) || frameItem.equals(FrameItem.NULL) ||
                    AnalyzerUtils.isUninitializedReference(frameItem)) {
                result.add(AsmUtils.getPop1WordInstruction());
            } else {
                result.add(AsmUtils.getLoadReferenceInstruction(savedFrameContextLocalIndex));
                result.add(AsmUtils.getSwapInstructions(pointcutFrameItem.getAsmType(),
                        pointcutStructure.getSavedFrameContextClassType()));
                result.add(AsmUtils.getSetFieldInstruction(pointcutStructure.getSavedFrameContextClassType(),
                        pointcutFrameItem.getFieldName(), pointcutFrameItem.getAsmType(), false));
            }
        }

        // save local items
        for (Map.Entry<Integer, PointcutFrameStructureItem> localEntry : pointcutStructure.getLocals().entrySet()) {
            PointcutFrameStructureItem pointcutFrameItem = localEntry.getValue();
            FrameItem frameItem = pointcutFrameItem.getFrameItem();
            if (!frameItem.equals(FrameItem.NULL) && !AnalyzerUtils.isUninitializedReference(frameItem)) {
                int localIndex = localEntry.getKey();
                result.add(AsmUtils.getLoadReferenceInstruction(savedFrameContextLocalIndex));
                result.add(AsmUtils.getLoadInstruction(pointcutFrameItem.getAsmType(), localIndex));
                result.add(AsmUtils.getSetFieldInstruction(pointcutStructure.getSavedFrameContextClassType(),
                        pointcutFrameItem.getFieldName(), pointcutFrameItem.getAsmType(), false));
            }
        }

        // store pointcut number
        result.add(AsmUtils.getLoadReferenceInstruction(savedFrameContextLocalIndex));
        result.add(AsmUtils.getPushIntInstruction(pointcutNumber));
        result.add(AsmUtils.getSetFieldInstruction(pointcutNumberSavedFrameContextField));

        // add saved frame context
        result.add(AsmUtils.getLoadReferenceInstruction(savedFrameContextLocalIndex));
        if (pointcutStructure.getInvocationObjectItem() != null) {
            result.add(AsmUtils.getPushStringInstruction(pointcutStructure.getInvocationObjectItem().getFieldName()));
            result.add(getLoadThisInstruction(pointcutStructure));
            result.add(AsmUtils.getInvocationInstruction(storeFrameNonStaticInvocationMethod));
        } else {
            result.add(getLoadThisInstruction(pointcutStructure));
            result.add(AsmUtils.getInvocationInstruction(storeFrameStaticInvocationMethod));
        }

        return result;
    }

    public static boolean isInvocationInstruction(AbstractInsnNode instruction) {
        return instruction.getType() == AbstractInsnNode.METHOD_INSN ||
                instruction.getType() == AbstractInsnNode.INVOKE_DYNAMIC_INSN;
    }

    public static boolean isInvocationConstructor(MethodInsnNode instruction) {
        return instruction.name.equals("<init>") || instruction.name.equals("<clinit>");
    }

    public static InsnList getGetAndStorePointcutNumberInstructions(int pointcutLocalIndex) {
        InsnList result = new InsnList();
        result.add(AsmUtils.getInvocationInstruction(startingMethod));
        result.add(AsmUtils.getStoreIntInstruction(pointcutLocalIndex));
        return result;
    }

    public static List<TypeInsnNode> getNewInstructionChain(AbstractInsnNode lastNode,
            Map<? super AbstractInsnNode, ? extends Frame> frames) {
        List<TypeInsnNode> result = new LinkedList<>();
        Frame frame = frames.get(lastNode);
        TypeInsnNode newInstruction = getPreviousNewInstruction(frame, frames);
        while (newInstruction != null) {
            result.add(0, newInstruction);
            frame = frames.get(newInstruction);
            newInstruction = getPreviousNewInstruction(frame, frames);
        }
        return result;
    }

    public static byte[] getSavedFrameContextClassBody(List<? extends Type> sortedTypes) {
        String className = getSavedContextClassName(sortedTypes);
        String asmClassInternalName = className.replace('.', '/');
        String asmSuperclassInternalName = __SavedFrameContext.class.getName().replace('.', '/');

        ClassWriter classWriter = new ClassWriter(0);
        classWriter.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, asmClassInternalName, null,
                asmSuperclassInternalName, null);

        int fieldIndexCounter = 0;
        for (Type type : sortedTypes) {
            FieldVisitor fieldVisitor = classWriter.visitField(Opcodes.ACC_PUBLIC,
                    getSavedContextFieldName(fieldIndexCounter), type.getDescriptor(), null, null);
            fieldVisitor.visitEnd();
            fieldIndexCounter++;
        }

        MethodVisitor constructorVisitor = classWriter.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructorVisitor.visitCode();
        constructorVisitor.visitVarInsn(Opcodes.ALOAD, 0);
        constructorVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, asmSuperclassInternalName, "<init>", "()V", false);
        constructorVisitor.visitInsn(Opcodes.RETURN);
        constructorVisitor.visitMaxs(1, 1);
        constructorVisitor.visitEnd();

        classWriter.visitEnd();

        return classWriter.toByteArray();
    }


    /// private methods

    private static final Method getSavedFrameContextMethod;
    private static final Field pointcutNumberSavedFrameContextField;
    private static final Method storeFrameNonStaticInvocationMethod;
    private static final Method storeFrameStaticInvocationMethod;
    private static final Method startingMethod;

    static {
        try {
            getSavedFrameContextMethod = Continuation.class.getMethod("__getSavedFrameContext");
            pointcutNumberSavedFrameContextField = __SavedFrameContext.class.getField("pointcut");
            storeFrameNonStaticInvocationMethod = Continuation.class.getMethod("__addSavedFrameContext",
                    __SavedFrameContext.class, String.class, Object.class);
            storeFrameStaticInvocationMethod = Continuation.class.getMethod("__addSavedFrameContext",
                    __SavedFrameContext.class, Object.class);
            startingMethod = Continuation.class.getMethod("__startingMethod");
        } catch (NoSuchMethodException | NoSuchFieldException e) {
            throw Throwables.propagate(e);
        }

    }

    private static AbstractInsnNode getLoadThisInstruction(PointcutFrameStructure pointcutStructure) {
        if (pointcutStructure.isMethodStatic()) {
            return AsmUtils.getPushNullInstruction();
        } else {
            return AsmUtils.getLoadReferenceInstruction(0);
        }
    }

    private static String getSavedContextFieldName(int fieldIndex) {
        String result = "_" + fieldIndex;
        return result;
    }

    private static String getSavedContextClassName(List<? extends Type> sortedTypes) {
        if (sortedTypes.isEmpty()) {
            return __SavedFrameContext.class.getName();
        } else {
            Hasher hasher = Hashing.sha256().newHasher();
            for (Type type : sortedTypes) {
                hasher.putString(type.getDescriptor(), utf8Charset);
            }
            String hash = hasher.hash().toString();
            String result = __SavedFrameContext.class.getName() + "$" + hash;
            return result;
        }
    }

    private static Charset utf8Charset = Charset.forName("UTF-8");

    private static String getAsmMethodDescriptor(AbstractInsnNode invocationInstruction) {
        if (invocationInstruction instanceof MethodInsnNode) {
            MethodInsnNode methodInstruction = (MethodInsnNode) invocationInstruction;
            return methodInstruction.desc;
        } else if (invocationInstruction instanceof InvokeDynamicInsnNode) {
            InvokeDynamicInsnNode invokeDynamicInstruction = (InvokeDynamicInsnNode) invocationInstruction;
            return invokeDynamicInstruction.desc;
        } else {
            throw new IllegalArgumentException("invalid invocationInstruction: " + invocationInstruction);
        }
    }

    private static boolean isInvocationStatic(AbstractInsnNode invocationInstruction) {
        if (invocationInstruction instanceof MethodInsnNode) {
            MethodInsnNode methodInstruction = (MethodInsnNode) invocationInstruction;
            return methodInstruction.getOpcode() == Opcodes.INVOKESTATIC;
        } else if (invocationInstruction instanceof InvokeDynamicInsnNode) {
            return true;
        } else {
            throw new IllegalArgumentException("invalid invocationInstruction: " + invocationInstruction);
        }
    }

    private static final Comparator<PointcutFrameStructureItem> pointcutFrameStructureItemTypeComparator =
        (item1, item2) -> {
            String descriptor1 = item1.getAsmType().getDescriptor();
            String descriptor2 = item2.getAsmType().getDescriptor();
            return descriptor1.compareTo(descriptor2);
        };

    private static Set<UninitializedObjectFrameItem> getUninitializedObjectFrameItemSet(Frame frame) {
        Set<UninitializedObjectFrameItem> result = new HashSet<>();
        for (FrameItem item : frame.getLocals()) {
            if (item instanceof UninitializedObjectFrameItem) {
                result.add((UninitializedObjectFrameItem) item);
            }
        }
        for (FrameItem item : frame.getStack()) {
            if (item instanceof UninitializedObjectFrameItem) {
                result.add((UninitializedObjectFrameItem) item);
            }
        }
        return result;
    }

    private static InsnList getJavacTransformFrameInstructions(Frame currentFrame, Frame targetFrame) {
        int currentLocalsNumber = currentFrame.getLocals().size();
        if (currentLocalsNumber > targetFrame.getLocals().size()) {
            return null;
        }
        List<? extends FrameItem> targetLocalsPrefix = targetFrame.getLocals().subList(0, currentLocalsNumber);
        if (!Iterables.elementsEqual(currentFrame.getLocals(), targetLocalsPrefix)) {
            return null;
        }
        List<? extends FrameItem> targetLocalsSuffix =
                targetFrame.getLocals().subList(currentLocalsNumber, targetFrame.getLocals().size());
        if (containsUninitializedReferenceItem(targetFrame.getLocals())) {
            return null;
        }
        int currentStackNumber = currentFrame.getStack().size();
        if (currentStackNumber > targetFrame.getStack().size()) {
            return null;
        }
        List<? extends FrameItem> targetStackPrefix = targetFrame.getStack().subList(0, currentStackNumber);
        if (!Iterables.elementsEqual(currentFrame.getStack(), targetStackPrefix)) {
            return null;
        }
        List<? extends FrameItem> targetStackSuffix = targetFrame.getStack().subList(currentStackNumber,
                targetFrame.getStack().size());
        boolean duplicateStack;
        if (currentStackNumber > 0 && !targetStackSuffix.isEmpty() &&
                currentFrame.getStack().get(currentStackNumber - 1).equals(targetStackSuffix.get(0))) {
            duplicateStack = true;
            targetStackSuffix = targetStackSuffix.subList(1, targetStackSuffix.size());
        } else {
            duplicateStack = false;
        }
        if (containsUninitializedReferenceItem(targetStackSuffix)) {
            return null;
        }
        InsnList result = new InsnList();
        // append locals
        int localIndexCounter = currentLocalsNumber;
        for (FrameItem item : targetLocalsSuffix) {
            if (item.isStartingWord() && !item.equals(FrameItem.TOP)) {
                if (item.equals(FrameItem.INT)) {
                    result.add(AsmUtils.getPushAndStoreIntInstructions(0, localIndexCounter));
                } else if (item.equals(FrameItem.FLOAT)) {
                    result.add(AsmUtils.getPushAndStoreFloatInstructions(0, localIndexCounter));
                } else if (item.equals(FrameItem.LONG_0)) {
                    result.add(AsmUtils.getPushAndStoreLongInstructions(0, localIndexCounter));
                } else if (item.equals(FrameItem.DOUBLE_0)) {
                    result.add(AsmUtils.getPushAndStoreDoubleInstructions(0, localIndexCounter));
                } else if (AnalyzerUtils.isInitializedReference(item)) {
                    result.add(AsmUtils.getPushAndStoreNullInstructions(localIndexCounter));
                } else {
                    throw new IllegalStateException("invalid item: " + item);
                }
            }
            localIndexCounter++;
        }
        // append stack
        if (duplicateStack) {
            result.add(AsmUtils.getDupInstruction());
        }
        for (FrameItem item : targetStackSuffix) {
            if (item.isStartingWord() && !item.equals(FrameItem.TOP)) {
                if (item.equals(FrameItem.INT)) {
                    result.add(AsmUtils.getPushIntInstruction(0));
                } else if (item.equals(FrameItem.FLOAT)) {
                    result.add(AsmUtils.getPushFloatInstruction(0));
                } else if (item.equals(FrameItem.LONG_0)) {
                    result.add(AsmUtils.getPushLongInstruction(0));
                } else if (item.equals(FrameItem.DOUBLE_0)) {
                    result.add(AsmUtils.getPushDoubleInstruction(0));
                } else if (AnalyzerUtils.isInitializedReference(item)) {
                    result.add(AsmUtils.getPushNullInstruction());
                } else {
                    throw new IllegalStateException("invalid item: " + item);
                }
            }
        }
        return result;
    }

    private static boolean containsUninitializedReferenceItem(Iterable<? extends FrameItem> frameItems) {
        for (FrameItem item : frameItems) {
            if (AnalyzerUtils.isUninitializedReference(item)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isStorable(FrameItem frameItem) {
        return !frameItem.equals(FrameItem.TOP) && !frameItem.equals(FrameItem.NULL) &&
                !AnalyzerUtils.isUninitializedReference(frameItem);
    }

    private static final String reflectMethodClassInternalName;
    private static final String reflectMethodClassInvokeMethodName;
    private static final String reflectMethodClassInvokeMethodDescriptor;
    private static Type reflectMethodAsmType;

    static {
        reflectMethodClassInternalName = Type.getInternalName(Method.class);
        reflectMethodClassInvokeMethodName = "invoke";
        try {
            reflectMethodClassInvokeMethodDescriptor =
                    Type.getMethodDescriptor(Method.class.getMethod(reflectMethodClassInvokeMethodName, Object.class,
                            Object[].class));
        } catch (NoSuchMethodException e) {
            throw Throwables.propagate(e);
        }
        reflectMethodAsmType = Type.getType(Method.class);
    }

    private static boolean isReflectionMethodInvocation(AbstractInsnNode invocationInstruction) {
        if (invocationInstruction.getType() != AbstractInsnNode.METHOD_INSN) {
            return false;
        }
        MethodInsnNode methodInvocationInstruction = (MethodInsnNode) invocationInstruction;
        if (methodInvocationInstruction.getOpcode() != Opcodes.INVOKEVIRTUAL) {
            return false;
        }
        if (!methodInvocationInstruction.owner.equals(reflectMethodClassInternalName)) {
            return false;
        }
        if (!methodInvocationInstruction.name.equals(reflectMethodClassInvokeMethodName)) {
            return false;
        }
        if (!methodInvocationInstruction.desc.equals(reflectMethodClassInvokeMethodDescriptor)) {
            return false;
        }
        return true;
    }

}
