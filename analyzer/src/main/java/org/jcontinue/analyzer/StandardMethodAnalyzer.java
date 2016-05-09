package org.jcontinue.analyzer;

import org.jcontinue.base.AsmUtils;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Created by mylenium on 13.09.15.
 */
public class StandardMethodAnalyzer implements MethodAnalyzer {

    private final ObjectFrameItemFactory objectFactory;

    public StandardMethodAnalyzer(ObjectFrameItemFactory objectFactory) {
        Objects.requireNonNull(objectFactory);
        this.objectFactory = objectFactory;
    }

    @Override
    public AnalyzeMethodResult analyzeMethod(String ownerClassName, MethodNode method) {
        if (method.instructions == null || method.instructions.size() == 0) {
            throw new IllegalArgumentException("method does not contain instructions (it is abstract or native)");
        }
        Internal internal = new Internal();
        internal.init(ownerClassName, method);
        internal.processAnalyze();
        return internal.getAnalyzeMethodResult();
    }

    private class Internal {
        private final Map<AbstractInsnNode, List<TryCatchBlockNode>> tryCatchBlocks = new HashMap<>();
        private final Map<AbstractInsnNode, Frame> frames = new HashMap<>();
        private final Set<AbstractInsnNode> instructionQueue = new LinkedHashSet<>();
        private int localsNumber, stackSize;

        private void updateFrame(AbstractInsnNode instruction, Frame frame) {
            localsNumber = Math.max(localsNumber, frame.getLocals().size());
            stackSize = Math.max(stackSize, frame.getStack().size());
            if (frames.containsKey(instruction)) {
                Frame currentFrame = frames.get(instruction);
                Frame mergedFrame = MethodAnalyzerUtils.mergeFrames(currentFrame, frame, objectFactory);
                if (!MethodAnalyzerUtils.isEquals(currentFrame, mergedFrame)) {
                    frames.put(instruction, mergedFrame);
                    instructionQueue.add(instruction);
                }
            } else {
                frames.put(instruction, frame);
                instructionQueue.add(instruction);
            }
        }

        private void init(String ownerClassName, MethodNode method) {
            fillTryCatchBlocks(method.tryCatchBlocks);
            BaseFrame initialFrame = MethodAnalyzerUtils.getInitialFrame(ownerClassName, method, objectFactory);
            AbstractInsnNode initialInstruction = AsmUtils.getCodeInstruction(method.instructions.getFirst());
            if (initialInstruction == null) {
                throw new AnalyzeMethodException("initial instruction not found");
            }
            localsNumber = 0;
            stackSize = 0;
            updateFrame(initialInstruction, initialFrame);
        }

        private void fillTryCatchBlocks(List<TryCatchBlockNode> tryCatchBlockList) {
            for (TryCatchBlockNode tryCatchBlock : tryCatchBlockList) {
                for (AbstractInsnNode instruction = tryCatchBlock.start; instruction != tryCatchBlock.end;
                     instruction = instruction.getNext()) {
                    if (AsmUtils.isCodeInstruction(instruction)) {
                        List<TryCatchBlockNode> instructionTryCatchBlocks = tryCatchBlocks.get(instruction);
                        if (instructionTryCatchBlocks == null) {
                            instructionTryCatchBlocks = new LinkedList<>();
                            tryCatchBlocks.put(instruction, instructionTryCatchBlocks);
                        }
                        instructionTryCatchBlocks.add(tryCatchBlock);
                    }
                }
            }
        }

        private void processAnalyze() {
            while (!instructionQueue.isEmpty()) {
                AbstractInsnNode instruction = popInstructionQueue();
                Frame currentFrame = frames.get(instruction);
                updateExceptionHandlerFrames(instruction, currentFrame);
                MethodAnalyzerUtils.PerformInstructionResult performResult =
                        MethodAnalyzerUtils.performInstruction(currentFrame, instruction, objectFactory);
                if (!MethodAnalyzerUtils.isLocalsEquals(currentFrame, performResult.getFrame())) {
                    updateExceptionHandlerFrames(instruction, performResult.getFrame());
                }
                for (AbstractInsnNode nextInstruction : performResult.getPossibleNextInstructions()) {
                    if (nextInstruction == null) {
                        throw new AnalyzeMethodException("invalid instruction label");
                    }
                    updateFrame(nextInstruction, performResult.getFrame());
                }
            }
        }

        private void updateExceptionHandlerFrames(AbstractInsnNode instruction, Frame frame) {
            List<TryCatchBlockNode> tryCatchBlocks = this.tryCatchBlocks.get(instruction);
            if (tryCatchBlocks != null) {
                for (TryCatchBlockNode tryCatchBlock : tryCatchBlocks) {
                    Frame exceptionFrame = MethodAnalyzerUtils.getExceptionFrame(frame, tryCatchBlock, objectFactory);
                    AbstractInsnNode handlerInstruction = AsmUtils.getCodeInstruction(tryCatchBlock.handler);
                    if (handlerInstruction == null) {
                        throw new AnalyzeMethodException("invalid exception handler instruction");
                    }
                    updateFrame(handlerInstruction, exceptionFrame);
                }
            }
        }

        private AbstractInsnNode popInstructionQueue() {
            Iterator<AbstractInsnNode> iterator = instructionQueue.iterator();
            AbstractInsnNode result = iterator.next();
            iterator.remove();
            return result;
        }

        private BaseAnalyzeMethodResult getAnalyzeMethodResult() {
            BaseAnalyzeMethodResult result = new BaseAnalyzeMethodResult(frames);
            result.setLocalsNumber(localsNumber);
            result.setStackSize(stackSize);
            return result;
        }
    }
}
