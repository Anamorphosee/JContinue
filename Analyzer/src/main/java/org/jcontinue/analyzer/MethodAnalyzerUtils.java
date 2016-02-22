package org.jcontinue.analyzer;

import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import org.apache.commons.lang3.ArrayUtils;
import org.jcontinue.base.AsmUtils;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class MethodAnalyzerUtils {

    public static class PerformInstructionResult {
        private Frame frame;
        private Set<? extends AbstractInsnNode> possibleNextInstructions;

        public Frame getFrame() {
            return frame;
        }

        public void setFrame(Frame frame) {
            this.frame = frame;
        }

        public Set<? extends AbstractInsnNode> getPossibleNextInstructions() {
            return possibleNextInstructions;
        }

        public void setPossibleNextInstructions(Set<? extends AbstractInsnNode> possibleNextInstructions) {
            this.possibleNextInstructions = possibleNextInstructions;
        }
    }

    public static FrameItem mergeFrameItems(FrameItem item1, FrameItem item2, ObjectFrameItemFactory objectFactory) {
        Objects.requireNonNull(item1);
        Objects.requireNonNull(item2);
        Objects.requireNonNull(objectFactory);
        if (item1.equals(item2)) {
            return item1;
        }
        if (item1 instanceof ObjectFrameItem && item2 instanceof ObjectFrameItem) {
            return ((ObjectFrameItem) item1).getCommonSuperClass((ObjectFrameItem) item2);
        }
        if (item1 instanceof PrimitiveArrayFrameItem && item2 instanceof PrimitiveArrayFrameItem) {
            PrimitiveType primitiveType1 = ((PrimitiveArrayFrameItem) item1).getPrimitiveType();
            PrimitiveType primitiveType2 = ((PrimitiveArrayFrameItem) item2).getPrimitiveType();
            if (primitiveType1.equals(primitiveType2)) {
                return item1;
            }
            return objectFactory.getObjectFrameItem(Object.class.getName());
        }
        if (item1 instanceof ReferenceArrayFrameItem && item2 instanceof ReferenceArrayFrameItem) {
            NotNullInitializedReferenceFrameItem itemElement1 = ((ReferenceArrayFrameItem) item1).getElementType();
            NotNullInitializedReferenceFrameItem itemElement2 = ((ReferenceArrayFrameItem) item2).getElementType();
            FrameItem commonElementItem = mergeFrameItems(itemElement1, itemElement2, objectFactory);
            return new ReferenceArrayFrameItem((NotNullInitializedReferenceFrameItem) commonElementItem);
        }
        if (item1 instanceof NotNullInitializedReferenceFrameItem && item2.equals(FrameItem.NULL)) {
            return item1;
        }
        if (item2 instanceof NotNullInitializedReferenceFrameItem && item1.equals(FrameItem.NULL)) {
            return item2;
        }
        if (item1 instanceof NotNullInitializedReferenceFrameItem && item2 instanceof NotNullInitializedReferenceFrameItem) {
            return objectFactory.getObjectFrameItem(Object.class.getName());
        }
        return FrameItem.TOP;
    }

    public static BaseFrame mergeFrames(Frame frame1, Frame frame2, ObjectFrameItemFactory objectFactory) {
        Objects.requireNonNull(frame1);
        Objects.requireNonNull(frame2);
        Objects.requireNonNull(objectFactory);
        BaseFrame result = new BaseFrame();
        int localsNumber = Math.min(frame1.getLocals().size(), frame2.getLocals().size());
        result.getLocals().ensureCapacity(localsNumber);
        for (int i = 0; i < localsNumber; ++i) {
            FrameItem item1 = frame1.getLocals().get(i);
            FrameItem item2 = frame2.getLocals().get(i);
            FrameItem mergedItem = mergeFrameItems(item1, item2, objectFactory);
            result.getLocals().add(mergedItem);
        }
        while (!result.getLocals().isEmpty() && result.getLocals().get(result.getLocals().size() - 1).equals(FrameItem.TOP)) {
            result.getLocals().remove(result.getLocals().size() - 1);
        }
        if (frame1.getStack().size() != frame2.getStack().size()) {
            throw new AnalyzeMethodException(
                    "frames have different stack sizes" +
                            "frame1: " + frame1 + "\n" +
                            "frame2: " + frame2 + "\n"
            );
        }
        int stackSize = frame1.getStack().size();
        result.getStack().ensureCapacity(stackSize);
        for (int i = 0; i < stackSize; ++i) {
            FrameItem item1 = frame1.getStack().get(i);
            FrameItem item2 = frame2.getStack().get(i);
            FrameItem mergedItem = mergeFrameItems(item1, item2, objectFactory);
            result.getStack().add(mergedItem);
        }
        if (frame1.isThisInitialized() != frame2.isThisInitialized()) {
            throw new AnalyzeMethodException("different thisInitialized values");
        }
        result.setThisInitialized(frame1.isThisInitialized());
        return result;
    }

    public static List<FrameItem> getFrameItems(Type asmType, ObjectFrameItemFactory objectFactory) {
        Objects.requireNonNull(asmType);
        Objects.requireNonNull(objectFactory);
        switch (asmType.getSort()) {
            case Type.BOOLEAN:
            case Type.BYTE:
            case Type.CHAR:
            case Type.SHORT:
            case Type.INT:
                return Collections.singletonList(FrameItem.INT);
            case Type.FLOAT:
                return Collections.singletonList(FrameItem.FLOAT);
            case Type.LONG:
                return FrameItem.LONG;
            case Type.DOUBLE:
                return FrameItem.DOUBLE;
            case Type.ARRAY:
                int dimensionsNumber = asmType.getDimensions();
                Type elementAsmType = asmType.getElementType();
                if (elementAsmType.getSort() == Type.OBJECT) {
                    ObjectFrameItem elementType = objectFactory.getObjectFrameItem(elementAsmType.getClassName());
                    return Collections.singletonList(new ReferenceArrayFrameItem(elementType, dimensionsNumber));
                } else {
                    PrimitiveType primitiveType = PrimitiveType.valueOf(elementAsmType);
                    PrimitiveArrayFrameItem primitiveArrayType = new PrimitiveArrayFrameItem(primitiveType);
                    if (dimensionsNumber == 1) {
                        return Collections.singletonList(primitiveArrayType);
                    } else {
                        ReferenceArrayFrameItem refArrayType =
                                new ReferenceArrayFrameItem(primitiveArrayType, dimensionsNumber - 1);
                        return Collections.singletonList(refArrayType);
                    }
                }
            case Type.OBJECT:
                return Collections.singletonList(objectFactory.getObjectFrameItem(asmType.getClassName()));
            default:
                throw new IllegalArgumentException("invalid asmType: " + asmType);
        }
    }

    public static BaseFrame getInitialFrame(String ownerClassName, MethodNode method, ObjectFrameItemFactory objectFactory) {
        BaseFrame result = new BaseFrame();
        if (!AsmUtils.isStatic(method)) {
            ReferenceFrameItem thisItem;
            if (method.name.equals("<init>") && !ownerClassName.equals(Object.class.getName())) {
                thisItem = new UninitializedThisFrameItem(ownerClassName);
                result.setThisInitialized(false);
            } else {
                thisItem = objectFactory.getObjectFrameItem(ownerClassName);
            }
            result.getLocals().add(thisItem);
        }
        for (Type argumentAsmType : Type.getArgumentTypes(method.desc)) {
            result.getLocals().addAll(MethodAnalyzerUtils.getFrameItems(argumentAsmType, objectFactory));
        }
        return result;
    }

    public static boolean isLocalsEquals(Frame frame1, Frame frame2) {
        return Iterables.elementsEqual(frame1.getLocals(), frame2.getLocals());
    }

    public static boolean isStackEquals(Frame frame1, Frame frame2) {
        return Iterables.elementsEqual(frame1.getStack(), frame2.getStack());
    }

    public static boolean isEquals(Frame frame1, Frame frame2) {
        return isLocalsEquals(frame1, frame2) && isStackEquals(frame1, frame2) &&
                frame1.isThisInitialized() == frame2.isThisInitialized();
    }

    public static BaseFrame getExceptionFrame(Frame currentFrame, TryCatchBlockNode tryCatchBlock,
            ObjectFrameItemFactory objectFactory) {
        ObjectFrameItem exceptionFrameItem;
        if (tryCatchBlock.type != null) {
            exceptionFrameItem = objectFactory.getObjectFrameItem(Type.getObjectType(tryCatchBlock.type).getClassName());
        } else {
            exceptionFrameItem = objectFactory.getObjectFrameItem(Throwable.class.getName());
        }
        BaseFrame result = new BaseFrame(currentFrame);
        result.getStack().clear();
        result.getStack().add(exceptionFrameItem);
        return result;
    }

    // performing instructions

    private static FrameItem popPrimitiveArrayOrNull(Frame frame, PrimitiveType ... types) {
        if (frame.getStack().isEmpty()) {
            throw new AnalyzeMethodException("trying to pop empty stack");
        }
        int lastIndex = frame.getStack().size() - 1;
        FrameItem lastItem = frame.getStack().get(lastIndex);
        boolean correctItem = lastItem.equals(FrameItem.NULL);
        if (!correctItem && lastItem instanceof PrimitiveArrayFrameItem) {
            PrimitiveType lastItemPrimitiveType = ((PrimitiveArrayFrameItem) lastItem).getPrimitiveType();
            correctItem = ArrayUtils.contains(types, lastItemPrimitiveType);
        }
        if (!correctItem) {
            throw new AnalyzeMethodException("invalid last stack item: " + lastItem);
        }
        frame.getStack().remove(lastIndex);
        return lastItem;
    }

    private static FrameItem popInt(Frame frame) {
        return checkLastStackItem(frame, FrameItem.INT, true);
    }

    public static FrameItem popFloat(Frame frame) {
        return checkLastStackItem(frame, FrameItem.FLOAT, true);
    }

    private static List<FrameItem> popLong(Frame frame) {
        return checkLastStackItems(frame, FrameItem.LONG, true);
    }

    private static List<FrameItem> popDouble(Frame frame) {
        return checkLastStackItems(frame, FrameItem.DOUBLE, true);
    }

    private static ReferenceFrameItem popReference(Frame frame, boolean initialized) {
        return checkLastStackItemIsReference(frame, initialized, true);
    }

    private static ReferenceFrameItem checkLastStackItemIsReference(Frame frame, boolean initialized, boolean remove) {
        if (frame.getStack().isEmpty()) {
            throw new AnalyzeMethodException("trying to pop empty stack. expected reference, initialized = " + initialized);
        }
        int lastIndex = frame.getStack().size() - 1;
        FrameItem lastItem = frame.getStack().get(lastIndex);
        boolean correct;
        if (initialized) {
            correct = lastItem instanceof InitializedReferenceFrameItem;
        } else {
            correct = lastItem instanceof ReferenceFrameItem;
        }
        if (!correct) {
            throw new AnalyzeMethodException("invalid last stack item " + lastItem + ". expected reference, initialized = " + initialized);
        }
        if (remove) {
            frame.getStack().remove(lastIndex);
        }
        return (ReferenceFrameItem) lastItem;
    }

    private static FrameItem checkLastStackItem(Frame frame, FrameItem item, boolean remove) {
        if (frame.getStack().isEmpty()) {
            throw new AnalyzeMethodException("trying to pop empty stack. expected " + item);
        }
        int lastIndex = frame.getStack().size() - 1;
        FrameItem lastItem = frame.getStack().get(lastIndex);
        if (!(lastItem.equals(item))) {
            throw new AnalyzeMethodException("invalid stack last item: " + lastItem + ". expected " + item);
        }
        if (remove) {
            frame.getStack().remove(lastIndex);
        }
        return lastItem;
    }

    private static List<FrameItem> checkLastStackItems(Frame frame, List<? extends FrameItem> items, boolean remove) {
        int wordsNumber = items.size();
        if (frame.getStack().size() < wordsNumber) {
            throw new AnalyzeMethodException("trying to pop " + items + " from stack " + frame.getStack());
        }
        List<FrameItem> result = new ArrayList<>(wordsNumber);
        for (int i = 0; i < wordsNumber; ++i) {
            result.add(frame.getStack().get(frame.getStack().size() - wordsNumber + i));
        }
        for (int i = 0; i < wordsNumber; ++i) {
            if (!result.get(i).equals(items.get(i))) {
                throw new AnalyzeMethodException("invalid stack elements " + result + ". expected " + items);
            }
        }
        if (remove) {
            for (int i = 0; i < wordsNumber; i++) {
                frame.getStack().remove(frame.getStack().size() - 1);
            }
        }
        return result;
    }

    private static FrameItem getReferenceArrayElementType(FrameItem refArrayOrNull) {
        if (refArrayOrNull.equals(FrameItem.NULL)) {
            return FrameItem.NULL;
        }
        if (refArrayOrNull instanceof ReferenceArrayFrameItem) {
            return ((ReferenceArrayFrameItem) refArrayOrNull).getElementType();
        }
        throw new IllegalArgumentException("invalid refArrayOrNull: " + refArrayOrNull);
    }

    private static FrameItem popReferenceArrayOrNull(Frame frame) {
        if (frame.getStack().isEmpty()) {
            throw new AnalyzeMethodException("trying to pop empty stack");
        }
        int lastIndex = frame.getStack().size() - 1;
        FrameItem lastItem = frame.getStack().get(lastIndex);
        boolean correctItem = lastItem.equals(FrameItem.NULL) || lastItem instanceof ReferenceArrayFrameItem;
        if (!correctItem) {
            throw new AnalyzeMethodException("invalid last stack item: " + lastItem);
        }
        frame.getStack().remove(lastIndex);
        return lastItem;
    }

    private static FrameItem pop1WordItem(Frame frame) {
        if (frame.getStack().isEmpty()) {
            throw new AnalyzeMethodException("trying to pop empty stack. expected 1-word item");
        }
        int lastIndex = frame.getStack().size() - 1;
        FrameItem lastItem = frame.getStack().get(lastIndex);
        if (lastItem.getWordsNumber() != 1) {
            throw new AnalyzeMethodException("invalid last stack item: " + lastItem + ". excepted 1-word item");
        }
        frame.getStack().remove(lastIndex);
        return lastItem;
    }

    private static List<FrameItem> pop2WordItemOr2_1WordItems(Frame frame) {
        if (frame.getStack().size() < 2) {
            throw new AnalyzeMethodException("invalid stack " + frame.getStack() + ". expected 2-word item or 2 1-word items");
        }
        List<FrameItem> result = new ArrayList<>(2);
        int lastIndex = frame.getStack().size() - 1;
        result.add(frame.getStack().get(lastIndex - 1));
        result.add(frame.getStack().get(lastIndex));
        boolean correct = result.get(0).getWordsNumber() == 2 ||
                (result.get(0).getWordsNumber() == 1 && result.get(1).getWordsNumber() == 1);
        if (!correct) {
            throw new AnalyzeMethodException("invalid stack last items " + result + ". expected 2-word item or 2 1-word items");
        }
        frame.getStack().remove(lastIndex);
        frame.getStack().remove(lastIndex - 1);
        return result;
    }

    private static FrameItem popArrayOrNull(Frame frame) {
        if (frame.getStack().isEmpty()) {
            throw new AnalyzeMethodException("trying to pop empty stack. expected array");
        }
        int lastIndex = frame.getStack().size() - 1;
        FrameItem lastItem = frame.getStack().get(lastIndex);
        boolean correct = lastItem.equals(FrameItem.NULL) || lastItem instanceof ReferenceArrayFrameItem
                || lastItem instanceof PrimitiveArrayFrameItem;
        if (!correct) {
            throw new AnalyzeMethodException("invalid last stack item " + lastItem + ". expected array");
        }
        frame.getStack().remove(lastIndex);
        return lastItem;
    }

    private static FrameItem checkLastStackItemIsNullOrInstanceOf(Frame frame, ObjectFrameItem type, boolean remove) {
        if (frame.getStack().isEmpty()) {
            throw new AnalyzeMethodException("trying to pop empty stack. expected " + type);
        }
        int lastIndex = frame.getStack().size() - 1;
        FrameItem lastItem = frame.getStack().get(lastIndex);
        boolean correct = lastItem.equals(FrameItem.NULL) || (lastItem instanceof ObjectFrameItem
                && ((ObjectFrameItem) lastItem).isSubClass(type));
        if (!correct) {
            throw new AnalyzeMethodException("invalid last stack item " + lastItem + ". expected " + type);
        }
        if (remove) {
            frame.getStack().remove(lastIndex);
        }
        return lastItem;
    }

    public static PerformInstructionResult performInstruction(Frame frame, AbstractInsnNode instruction,
            ObjectFrameItemFactory objectFactory) {
        switch (instruction.getType()) {
            case AbstractInsnNode.INSN:
                return performInstruction(frame, (InsnNode) instruction, objectFactory);
            case AbstractInsnNode.INT_INSN:
                return performInstruction(frame, (IntInsnNode) instruction);
            case AbstractInsnNode.VAR_INSN:
                return performInstruction(frame, (VarInsnNode) instruction);
            case AbstractInsnNode.TYPE_INSN:
                return performInstruction(frame, (TypeInsnNode) instruction, objectFactory);
            case AbstractInsnNode.FIELD_INSN:
                return performInstruction(frame, (FieldInsnNode) instruction, objectFactory);
            case AbstractInsnNode.METHOD_INSN:
                return performInstruction(frame, (MethodInsnNode) instruction, objectFactory);
            case AbstractInsnNode.INVOKE_DYNAMIC_INSN:
                return performInstruction(frame, (InvokeDynamicInsnNode) instruction, objectFactory);
            case AbstractInsnNode.JUMP_INSN:
                return performInstruction(frame, (JumpInsnNode) instruction);
            case AbstractInsnNode.LDC_INSN:
                return performInstruction(frame, (LdcInsnNode) instruction, objectFactory);
            case AbstractInsnNode.IINC_INSN:
                return performInstruction(frame, (IincInsnNode) instruction);
            case AbstractInsnNode.TABLESWITCH_INSN:
                return performInstruction(frame, (TableSwitchInsnNode) instruction);
            case AbstractInsnNode.LOOKUPSWITCH_INSN:
                return performInstruction(frame, (LookupSwitchInsnNode) instruction);
            case AbstractInsnNode.MULTIANEWARRAY_INSN:
                return performInstruction(frame, (MultiANewArrayInsnNode) instruction, objectFactory);
            default:
                throw new IllegalArgumentException("invalid instruction: " + instruction);
        }
    }

    // private methods

    private static PerformInstructionResult performInstruction(Frame frame, MultiANewArrayInsnNode instruction,
            ObjectFrameItemFactory objectFactory) {
        BaseFrame nextFrame = new BaseFrame(frame);
        for (int i = 0; i < instruction.dims; i++) {
            popInt(nextFrame);
        }
        PerformInstructionResult result = new PerformInstructionResult();
        result.setFrame(nextFrame);
        nextFrame.getStack().addAll(getFrameItems(Type.getType(instruction.desc), objectFactory));
        result.setPossibleNextInstructions(Collections.singleton(getNextInstruction(instruction)));
        return result;
    }

    private static PerformInstructionResult performInstruction(Frame frame, LookupSwitchInsnNode instruction) {
        BaseFrame nextFrame = new BaseFrame(frame);
        popInt(nextFrame);
        Set<AbstractInsnNode> nextInstructions = new HashSet<>();
        for (LabelNode label : (List<LabelNode>) instruction.labels) {
            nextInstructions.add(getNextInstruction(label));
        }
        nextInstructions.add(getNextInstruction(instruction.dflt));
        PerformInstructionResult result = new PerformInstructionResult();
        result.setFrame(nextFrame);
        result.setPossibleNextInstructions(nextInstructions);
        return result;
    }

    private static PerformInstructionResult performInstruction(Frame frame, TableSwitchInsnNode instruction) {
        BaseFrame nextFrame = new BaseFrame(frame);
        popInt(nextFrame);
        Set<AbstractInsnNode> nextInstructions = new HashSet<>();
        for (LabelNode label : (List<LabelNode>) instruction.labels) {
            nextInstructions.add(getNextInstruction(label));
        }
        nextInstructions.add(getNextInstruction(instruction.dflt));
        PerformInstructionResult result = new PerformInstructionResult();
        result.setFrame(nextFrame);
        result.setPossibleNextInstructions(nextInstructions);
        return result;
    }


    private static PerformInstructionResult performInstruction(Frame frame, IincInsnNode instruction) {
        FrameItem item;
        if (frame.getLocals().size() <= instruction.var) {
            item = FrameItem.TOP;
        } else {
            item = frame.getLocals().get(instruction.var);
        }
        if (!item.equals(FrameItem.INT)) {
            throw new AnalyzeMethodException("invalid local #" + instruction.var + ": " + item + ". expected INT");
        }
        PerformInstructionResult result = new PerformInstructionResult();
        result.setFrame(frame);
        result.setPossibleNextInstructions(Collections.singleton(getNextInstruction(instruction)));
        return result;
    }

    private static PerformInstructionResult performInstruction(Frame frame, LdcInsnNode instruction,
            ObjectFrameItemFactory objectFactory) {
        BaseFrame nextFrame = new BaseFrame(frame);
        if (instruction.cst instanceof Integer) {
            nextFrame.getStack().add(FrameItem.INT);
        } else if (instruction.cst instanceof Float) {
            nextFrame.getStack().add(FrameItem.FLOAT);
        } else if (instruction.cst instanceof Long) {
            nextFrame.getStack().addAll(FrameItem.LONG);
        } else if (instruction.cst instanceof Double) {
            nextFrame.getStack().addAll(FrameItem.DOUBLE);
        } else if (instruction.cst instanceof String) {
            nextFrame.getStack().add(objectFactory.getObjectFrameItem(String.class.getName()));
        } else if (instruction.cst instanceof Type) {
            Type asmType = (Type) instruction.cst;
            if (asmType.getSort() == Type.OBJECT || asmType.getSort() == Type.ARRAY) {
                nextFrame.getStack().add(objectFactory.getObjectFrameItem(Class.class.getName()));
            } else if (asmType.getSort() == Type.METHOD) {
                nextFrame.getStack().add(objectFactory.getObjectFrameItem(MethodType.class.getName()));
            } else {
                throw new IllegalArgumentException("invalid amsType.sort " + asmType.getSort());
            }
        } else if (instruction.cst instanceof Handle) {
            nextFrame.getStack().add(objectFactory.getObjectFrameItem(MethodHandle.class.getName()));
        } else {
            throw new IllegalArgumentException("invalid LDC constant " + instruction.cst);
        }
        PerformInstructionResult result = new PerformInstructionResult();
        result.setFrame(nextFrame);
        result.setPossibleNextInstructions(Collections.singleton(getNextInstruction(instruction)));
        return result;
    }

    private static PerformInstructionResult performInstruction(Frame frame, JumpInsnNode instruction) {
        AbstractInsnNode labelInstruction = getNextInstruction(instruction.label);
        boolean goToNextInstruction = true;
        BaseFrame nextFrame = null;
        switch (instruction.getOpcode()) {
            case Opcodes.IFEQ:
            case Opcodes.IFNE:
            case Opcodes.IFLT:
            case Opcodes.IFGE:
            case Opcodes.IFGT:
            case Opcodes.IFLE:
                nextFrame = new BaseFrame(frame);
                popInt(nextFrame);
                break;
            case Opcodes.IF_ICMPEQ:
            case Opcodes.IF_ICMPNE:
            case Opcodes.IF_ICMPLT:
            case Opcodes.IF_ICMPGE:
            case Opcodes.IF_ICMPGT:
            case Opcodes.IF_ICMPLE:
                nextFrame = new BaseFrame(frame);
                popInt(nextFrame);
                popInt(nextFrame);
                break;
            case Opcodes.IF_ACMPEQ:
            case Opcodes.IF_ACMPNE:
                nextFrame = new BaseFrame(frame);
                popReference(nextFrame, false);
                popReference(nextFrame, false);
                break;
            case Opcodes.GOTO:
                goToNextInstruction = false;
                break;
            case Opcodes.JSR:
                // TODO
                throw new UnsupportedOperationException("jsr instruction analyzing is not supported yet");
            case Opcodes.IFNULL:
            case Opcodes.IFNONNULL:
                nextFrame = new BaseFrame(frame);
                popReference(nextFrame, false);
                break;
            default:
                throw new IllegalArgumentException("invalid opcode " + instruction.getOpcode());
        }
        PerformInstructionResult result = new PerformInstructionResult();
        if (nextFrame != null) {
            result.setFrame(nextFrame);
        } else {
            result.setFrame(frame);
        }
        if (goToNextInstruction) {
            AbstractInsnNode nextInstruction = getNextInstruction(instruction);
            result.setPossibleNextInstructions(Sets.newHashSet(nextInstruction, labelInstruction));
        } else {
            result.setPossibleNextInstructions(Collections.singleton(labelInstruction));
        }
        return result;
    }

    private static PerformInstructionResult performInstruction(Frame frame, InvokeDynamicInsnNode instruction,
            ObjectFrameItemFactory objectFactory) {
        BaseFrame nextFrame = new BaseFrame(frame);
        popMethodArguments(nextFrame, instruction.desc, objectFactory);
        pushMethodReturn(nextFrame, instruction.desc, objectFactory);
        PerformInstructionResult result = new PerformInstructionResult();
        result.setFrame(nextFrame);
        result.setPossibleNextInstructions(Collections.singleton(getNextInstruction(instruction)));
        return result;
    }

    private static PerformInstructionResult performInstruction(Frame frame, MethodInsnNode instruction,
            ObjectFrameItemFactory objectFactory) {
        BaseFrame nextFrame = new BaseFrame(frame);
        popMethodArguments(nextFrame, instruction.desc, objectFactory);
        if (instruction.getOpcode() == Opcodes.INVOKESPECIAL && instruction.name.equals("<init>")) {
            ReferenceFrameItem invokedItem = popReference(nextFrame, false);
            if (invokedItem instanceof UninitializedObjectFrameItem) {
                UninitializedObjectFrameItem uninitializedInvokedItem = (UninitializedObjectFrameItem) invokedItem;
                if (!uninitializedInvokedItem.getAsmInternalName().equals(instruction.owner)) {
                    throw new AnalyzeMethodException("invalid last stack item " + invokedItem + ". expected uninitialized "
                            + instruction.owner);
                }
            } else if (invokedItem instanceof UninitializedThisFrameItem) {
                if (nextFrame.isThisInitialized()) {
                    throw new AnalyzeMethodException("called constructor for already initialized this");
                }
                nextFrame.setThisInitialized(true);
            } else {
                throw new AnalyzeMethodException("invalid last stack item " + invokedItem + ". expected uninitialized "
                        + instruction.owner);
            }
            initObject(nextFrame, invokedItem, objectFactory);
        } else if (instruction.getOpcode() == Opcodes.INVOKEVIRTUAL || instruction.getOpcode() == Opcodes.INVOKESPECIAL
                || instruction.getOpcode() == Opcodes.INVOKEINTERFACE) {
            InitializedReferenceFrameItem invokedItem = (InitializedReferenceFrameItem) popReference(nextFrame, true);
            InitializedReferenceFrameItem ownerItem = getFrameItemFromAsmInternalName(instruction.owner, objectFactory);
            if (!isSubType(ownerItem, invokedItem, objectFactory)) {
                throw new AnalyzeMethodException("invalid stack item " + invokedItem + ". expected " + ownerItem);
            }
        } else if (instruction.getOpcode() != Opcodes.INVOKESTATIC) {
            throw new IllegalArgumentException("invalid opcode " + instruction.getOpcode());
        }
        pushMethodReturn(nextFrame, instruction.desc, objectFactory);
        PerformInstructionResult result = new PerformInstructionResult();
        result.setFrame(nextFrame);
        result.setPossibleNextInstructions(Collections.singleton(getNextInstruction(instruction)));
        return result;
    }

    private static void popMethodArguments(BaseFrame frame, String methodDesc, ObjectFrameItemFactory objectFactory) {
        Type[] argumentTypes = Type.getArgumentTypes(methodDesc);
        for (int i = argumentTypes.length - 1; i >= 0; i--) {
            Type argumentType = argumentTypes[i];
            checkLastStackItems(frame, argumentType, objectFactory, true);
        }
    }

    private static void pushMethodReturn(BaseFrame frame, String methodDesc, ObjectFrameItemFactory objectFactory) {
        Type returnAsmType = Type.getReturnType(methodDesc);
        if (!returnAsmType.equals(Type.VOID_TYPE)) {
            frame.getStack().addAll(getFrameItems(returnAsmType, objectFactory));
        }
    }

    private static void initObject(BaseFrame frame, ReferenceFrameItem uninitializedItem,
            ObjectFrameItemFactory objectFactory) {
        String className;
        if (uninitializedItem instanceof UninitializedObjectFrameItem) {
            className = ((UninitializedObjectFrameItem) uninitializedItem).getClassName();
        } else if (uninitializedItem instanceof UninitializedThisFrameItem) {
            className = ((UninitializedThisFrameItem) uninitializedItem).getClassName();
        } else {
            throw new AnalyzeMethodException("invalid frame item " + uninitializedItem + ". expected uninitialized object");
        }
        ObjectFrameItem initializedItem = objectFactory.getObjectFrameItem(className);
        for (int i = 0; i < frame.getLocals().size(); i++) {
            if (frame.getLocals().get(i).equals(uninitializedItem)) {
                frame.getLocals().set(i, initializedItem);
            }
        }
        for (int i = 0; i < frame.getStack().size(); i++) {
            if (frame.getStack().get(i).equals(uninitializedItem)) {
                frame.getStack().set(i, initializedItem);
            }
        }
    }

    private static PerformInstructionResult performInstruction(Frame frame, FieldInsnNode instruction,
            ObjectFrameItemFactory objectFactory) {
        Type asmType = Type.getType(instruction.desc);
        BaseFrame nextFrame = new BaseFrame(frame);
        switch (instruction.getOpcode()) {
            case Opcodes.GETSTATIC:
                nextFrame.getStack().addAll(getFrameItems(asmType, objectFactory));
                break;
            case Opcodes.PUTSTATIC:
                checkLastStackItems(nextFrame, asmType, objectFactory, true);
                break;
            case Opcodes.GETFIELD:
                checkLastStackItems(nextFrame, Type.getObjectType(instruction.owner), objectFactory, true);
                nextFrame.getStack().addAll(getFrameItems(asmType, objectFactory));
                break;
            case Opcodes.PUTFIELD:
                checkLastStackItems(nextFrame, asmType, objectFactory, true);
                checkLastStackItems(nextFrame, Type.getObjectType(instruction.owner), objectFactory, true);
                break;
            default:
                throw new IllegalArgumentException("invalid opcode " + instruction.getOpcode());
        }
        PerformInstructionResult result = new PerformInstructionResult();
        result.setFrame(nextFrame);
        result.setPossibleNextInstructions(Collections.singleton(getNextInstruction(instruction)));
        return result;
    }

    private static List<FrameItem> checkLastStackItems(Frame frame, Type asmType,
            ObjectFrameItemFactory objectFactory, boolean remove) {
        List<? extends FrameItem> asmTypeItems = getFrameItems(asmType, objectFactory);
        if (asmTypeItems.get(0) instanceof InitializedReferenceFrameItem) {
            InitializedReferenceFrameItem asmTypeItem = (InitializedReferenceFrameItem) asmTypeItems.get(0);
            if (frame.getStack().isEmpty()) {
                throw new AnalyzeMethodException("trying to pop empty stack. expected " + asmTypeItem);
            }
            int lastStackItemIndex = frame.getStack().size() - 1;
            FrameItem lastStackItem = frame.getStack().get(lastStackItemIndex);
            if (!(lastStackItem instanceof InitializedReferenceFrameItem)) {
                throw new AnalyzeMethodException("invalid last stack item " + lastStackItem + ". expected " + asmTypeItem);
            }
            if (isSubType(asmTypeItem, (InitializedReferenceFrameItem) lastStackItem, objectFactory)) {
                List<FrameItem> result = Collections.singletonList(lastStackItem);
                if (remove) {
                    frame.getStack().remove(lastStackItemIndex);
                }
                return result;
            } else {
                throw new AnalyzeMethodException("invalid last stack item " + lastStackItem + ". expected " + asmTypeItem);
            }
        } else {
            return checkLastStackItems(frame, asmTypeItems, remove);
        }
    }

    private static boolean isSubType(InitializedReferenceFrameItem superType, InitializedReferenceFrameItem subType,
            ObjectFrameItemFactory objectFactory) {
        if (subType.equals(FrameItem.NULL)) {
            return true;
        }
        if (superType.equals(FrameItem.NULL)) {
            return false;
        }
        if (superType instanceof ObjectFrameItem) {
            ObjectFrameItem objectItem = objectFactory.getObjectFrameItem(Object.class.getName());
            if (objectItem.isSubClass((ObjectFrameItem) superType)) {
                return true;
            }
        }
        if (superType instanceof PrimitiveArrayFrameItem && subType instanceof PrimitiveArrayFrameItem) {
            PrimitiveType ptSuperType = ((PrimitiveArrayFrameItem) superType).getPrimitiveType();
            PrimitiveType ptSubType = ((PrimitiveArrayFrameItem) subType).getPrimitiveType();
            return ptSuperType.equals(ptSubType);
        }
        if (superType instanceof ObjectFrameItem && subType instanceof ObjectFrameItem) {
            ObjectFrameItem ofiSuperType = (ObjectFrameItem) superType;
            ObjectFrameItem ofiSubType = (ObjectFrameItem) subType;
            return ofiSubType.isSubClass(ofiSuperType);
        }
        if (superType instanceof ReferenceArrayFrameItem && subType instanceof ReferenceArrayFrameItem) {
            return isSubType(((ReferenceArrayFrameItem) superType).getElementType(),
                    ((ReferenceArrayFrameItem) subType).getElementType(), objectFactory);
        }
        return false;
    }

    private static PerformInstructionResult performInstruction(Frame frame, TypeInsnNode instruction,
            ObjectFrameItemFactory objectFactory) {
        BaseFrame nextFrame = new BaseFrame(frame);
        switch (instruction.getOpcode()) {
            case Opcodes.NEW:
                nextFrame.getStack().add(new UninitializedObjectFrameItem(instruction));
                break;
            case Opcodes.ANEWARRAY:
                popInt(nextFrame);
                NotNullInitializedReferenceFrameItem frameItem =
                        getFrameItemFromAsmInternalName(instruction.desc, objectFactory);
                nextFrame.getStack().add(new ReferenceArrayFrameItem(frameItem));
                break;
            case Opcodes.CHECKCAST:
                popReference(nextFrame, true);
                frameItem = getFrameItemFromAsmInternalName(instruction.desc, objectFactory);
                nextFrame.getStack().add(frameItem);
                break;
            case Opcodes.INSTANCEOF:
                popReference(nextFrame, true);
                nextFrame.getStack().add(FrameItem.INT);
                break;
            default:
                throw new IllegalArgumentException("invalid opcode " + instruction.getOpcode());
        }
        PerformInstructionResult result = new PerformInstructionResult();
        result.setFrame(nextFrame);
        result.setPossibleNextInstructions(Collections.singleton(getNextInstruction(instruction)));
        return result;
    }

    private static NotNullInitializedReferenceFrameItem getFrameItemFromAsmInternalName(String asmInternalName,
            ObjectFrameItemFactory objectFactory) {
        Type asmType = Type.getObjectType(asmInternalName);
        return (NotNullInitializedReferenceFrameItem) getFrameItems(asmType, objectFactory).get(0);
    }

    private static PerformInstructionResult performInstruction(Frame frame, VarInsnNode instruction) {
        BaseFrame nextFrame = new BaseFrame(frame);
        switch (instruction.getOpcode()) {
            case Opcodes.ILOAD:
                checkLocalIsInt(nextFrame, instruction.var);
                nextFrame.getStack().add(FrameItem.INT);
                break;
            case Opcodes.LLOAD:
                checkLocalIsLong(nextFrame, instruction.var);
                nextFrame.getStack().addAll(FrameItem.LONG);
                break;
            case Opcodes.FLOAD:
                checkLocalIsFloat(nextFrame, instruction.var);
                nextFrame.getStack().add(FrameItem.FLOAT);
                break;
            case Opcodes.DLOAD:
                checkLocalIsDouble(nextFrame, instruction.var);
                nextFrame.getStack().addAll(FrameItem.DOUBLE);
                break;
            case Opcodes.ALOAD:
                FrameItem item = checkLocalIsReference(nextFrame, instruction.var, false);
                nextFrame.getStack().add(item);
                break;
            case Opcodes.ISTORE:
                popInt(nextFrame);
                setLocalInt(nextFrame, instruction.var);
                break;
            case Opcodes.LSTORE:
                popLong(nextFrame);
                setLocalLong(nextFrame, instruction.var);
                break;
            case Opcodes.FSTORE:
                popFloat(nextFrame);
                setLocalFloat(nextFrame, instruction.var);
                break;
            case Opcodes.DSTORE:
                popDouble(nextFrame);
                setLocalDouble(nextFrame, instruction.var);
                break;
            case Opcodes.ASTORE:
                item = popReference(nextFrame, false);
                setLocal(nextFrame, instruction.var, item);
                break;
            case Opcodes.RET:
                // TODO
                throw new UnsupportedOperationException("ret instruction analyzing is not supported yet");
            default:
                throw new IllegalArgumentException("invalid opcode " + instruction.getOpcode());
        }
        PerformInstructionResult result = new PerformInstructionResult();
        result.setFrame(nextFrame);
        result.setPossibleNextInstructions(Collections.singleton(getNextInstruction(instruction)));
        return result;
    }

    private static void setLocalInt(BaseFrame frame, int index) {
        setLocal(frame, index, FrameItem.INT);
    }

    private static void setLocalLong(BaseFrame frame, int index) {
        setLocals(frame, index, FrameItem.LONG);
    }

    private static void setLocalFloat(BaseFrame frame, int index) {
        setLocal(frame, index, FrameItem.FLOAT);
    }

    private static void setLocalDouble(BaseFrame frame, int index) {
        setLocals(frame, index, FrameItem.DOUBLE);
    }

    private static void setLocals(BaseFrame frame, int index, List<? extends FrameItem> items) {
        for (FrameItem item : items) {
            setLocal(frame, index, item);
            index++;
        }
    }

    private static void setLocal(BaseFrame frame, int index, FrameItem item) {
        if (item.equals(FrameItem.TOP)) {
            if (frame.getLocals().size() > index) {
                eraseLocal(frame, index);
            }
        } else {
            frame.getLocals().ensureCapacity(index + 1);
            while (frame.getLocals().size() <= index) {
                frame.getLocals().add(FrameItem.TOP);
            }
            eraseLocal(frame, index);
            frame.getLocals().set(index, item);
        }
        normalizeLocals(frame);
    }

    private static void eraseLocal(BaseFrame frame, int index) {
        FrameItem item = frame.getLocals().get(index);
        for (int wordIndex = 0; wordIndex < item.getWordsNumber(); wordIndex++) {
            frame.getLocals().set(index - item.getWordIndex() + wordIndex, FrameItem.TOP);
        }
    }

    public static void normalizeLocals(BaseFrame frame) {
        while (!frame.getLocals().isEmpty() && frame.getLocals().get(frame.getLocals().size() - 1).equals(FrameItem.TOP)) {
            frame.getLocals().remove(frame.getLocals().size() - 1);
        }
    }

    private static void checkLocalIsInt(Frame frame, int index) {
        checkLocal(frame, index, FrameItem.INT);
    }

    private static void checkLocalIsLong(Frame frame, int index) {
        checkLocal(frame, index, FrameItem.LONG);
    }

    public static void checkLocalIsFloat(Frame frame, int index) {
        checkLocal(frame, index, FrameItem.FLOAT);
    }

    public static void checkLocalIsDouble(Frame frame, int index) {
        checkLocal(frame, index, FrameItem.DOUBLE);
    }

    public static FrameItem checkLocalIsReference(Frame frame, int index, boolean initialized) {
        FrameItem item = getLocal(frame, index);
        if (initialized) {
            if (!(item instanceof InitializedReferenceFrameItem)) {
                throw new AnalyzeMethodException("local " + index + " is " + item + ". expected initialized reference");
            }
        } else {
            if (!(item instanceof ReferenceFrameItem)) {
                throw new AnalyzeMethodException("local " + index + " is " + item + ". expected reference");
            }
        }
        return item;
    }

    private static FrameItem getLocal(Frame frame, int index) {
        if (frame.getLocals().size() <= index) {
            return FrameItem.TOP;
        } else {
            return frame.getLocals().get(index);
        }
    }

    private static void checkLocal(Frame frame, int index, FrameItem item) {
        FrameItem localItem = getLocal(frame, index);
        if (!localItem.equals(item)) {
            throw new AnalyzeMethodException("local " + index + " is " + localItem + ". expected " + item);
        }
    }

    private static void checkLocal(Frame frame, int index, List<? extends FrameItem> items) {
        for (FrameItem item : items) {
            checkLocal(frame, index, item);
            index++;
        }
    }

    private static PerformInstructionResult performInstruction(Frame frame, IntInsnNode instruction) {
        BaseFrame nextFrame = new BaseFrame(frame);
        switch (instruction.getOpcode()) {
            case Opcodes.BIPUSH:
            case Opcodes.SIPUSH:
                nextFrame.getStack().add(FrameItem.INT);
                break;
            case Opcodes.NEWARRAY:
                PrimitiveType primitiveType = PrimitiveType.valueOfNewArrayInstructionOperand(instruction.operand);
                popInt(nextFrame);
                nextFrame.getStack().add(new PrimitiveArrayFrameItem(primitiveType));
                break;
            default:
                throw new IllegalArgumentException("invalid opcode " + instruction.getOpcode());
        }
        PerformInstructionResult result = new PerformInstructionResult();
        result.setFrame(nextFrame);
        result.setPossibleNextInstructions(Collections.singleton(getNextInstruction(instruction)));
        return result;
    }

    private static PerformInstructionResult performInstruction(Frame frame, InsnNode instruction,
            ObjectFrameItemFactory objectFactory) {
        BaseFrame nextFrame = null;
        boolean gotoNextInstruction = true;
        switch (instruction.getOpcode()) {
            case Opcodes.NOP:
                break;
            case Opcodes.ACONST_NULL:
                nextFrame = new BaseFrame(frame);
                nextFrame.getStack().add(FrameItem.NULL);
                break;
            case Opcodes.ICONST_M1:
            case Opcodes.ICONST_0:
            case Opcodes.ICONST_1:
            case Opcodes.ICONST_2:
            case Opcodes.ICONST_3:
            case Opcodes.ICONST_4:
            case Opcodes.ICONST_5:
                nextFrame = new BaseFrame(frame);
                nextFrame.getStack().add(FrameItem.INT);
                break;
            case Opcodes.LCONST_0:
            case Opcodes.LCONST_1:
                nextFrame = new BaseFrame(frame);
                nextFrame.getStack().addAll(FrameItem.LONG);
                break;
            case Opcodes.FCONST_0:
            case Opcodes.FCONST_1:
            case Opcodes.FCONST_2:
                nextFrame = new BaseFrame(frame);
                nextFrame.getStack().add(FrameItem.FLOAT);
                break;
            case Opcodes.DCONST_0:
            case Opcodes.DCONST_1:
                nextFrame = new BaseFrame(frame);
                nextFrame.getStack().addAll(FrameItem.DOUBLE);
                break;
            case Opcodes.IALOAD:
                nextFrame = new BaseFrame(frame);
                popInt(nextFrame);
                popPrimitiveArrayOrNull(nextFrame, PrimitiveType.INT);
                nextFrame.getStack().add(FrameItem.INT);
                break;
            case Opcodes.LALOAD:
                nextFrame = new BaseFrame(frame);
                popInt(nextFrame);
                popPrimitiveArrayOrNull(nextFrame, PrimitiveType.LONG);
                nextFrame.getStack().addAll(FrameItem.LONG);
                break;
            case Opcodes.FALOAD:
                nextFrame = new BaseFrame(frame);
                popInt(nextFrame);
                popPrimitiveArrayOrNull(nextFrame, PrimitiveType.FLOAT);
                nextFrame.getStack().add(FrameItem.FLOAT);
                break;
            case Opcodes.DALOAD:
                nextFrame = new BaseFrame(frame);
                popInt(nextFrame);
                popPrimitiveArrayOrNull(nextFrame, PrimitiveType.DOUBLE);
                nextFrame.getStack().addAll(FrameItem.DOUBLE);
                break;
            case Opcodes.AALOAD:
                nextFrame = new BaseFrame(frame);
                popInt(nextFrame);
                FrameItem refArrayOrNull = popReferenceArrayOrNull(nextFrame);
                nextFrame.getStack().add(getReferenceArrayElementType(refArrayOrNull));
                break;
            case Opcodes.BALOAD:
                nextFrame = new BaseFrame(frame);
                popInt(nextFrame);
                popPrimitiveArrayOrNull(nextFrame, PrimitiveType.BOOLEAN, PrimitiveType.BYTE);
                nextFrame.getStack().add(FrameItem.INT);
                break;
            case Opcodes.CALOAD:
                nextFrame = new BaseFrame(frame);
                popInt(nextFrame);
                popPrimitiveArrayOrNull(nextFrame, PrimitiveType.CHAR);
                nextFrame.getStack().add(FrameItem.INT);
                break;
            case Opcodes.SALOAD:
                nextFrame = new BaseFrame(frame);
                popInt(nextFrame);
                popPrimitiveArrayOrNull(nextFrame, PrimitiveType.SHORT);
                nextFrame.getStack().add(FrameItem.INT);
                break;
            case Opcodes.IASTORE:
                nextFrame = new BaseFrame(frame);
                popInt(nextFrame);
                popInt(nextFrame);
                popPrimitiveArrayOrNull(nextFrame, PrimitiveType.INT);
                break;
            case Opcodes.LASTORE:
                nextFrame = new BaseFrame(frame);
                popLong(nextFrame);
                popInt(nextFrame);
                popPrimitiveArrayOrNull(nextFrame, PrimitiveType.LONG);
                break;
            case Opcodes.FASTORE:
                nextFrame = new BaseFrame(frame);
                popFloat(nextFrame);
                popInt(nextFrame);
                popPrimitiveArrayOrNull(nextFrame, PrimitiveType.FLOAT);
                break;
            case Opcodes.DASTORE:
                nextFrame = new BaseFrame(frame);
                popDouble(nextFrame);
                popInt(nextFrame);
                popPrimitiveArrayOrNull(nextFrame, PrimitiveType.DOUBLE);
                break;
            case Opcodes.AASTORE:
                nextFrame = new BaseFrame(frame);
                popReference(nextFrame, true);
                popInt(nextFrame);
                popReferenceArrayOrNull(nextFrame);
                break;
            case Opcodes.BASTORE:
                nextFrame = new BaseFrame(frame);
                popInt(nextFrame);
                popInt(nextFrame);
                popPrimitiveArrayOrNull(nextFrame, PrimitiveType.BOOLEAN, PrimitiveType.BYTE);
                break;
            case Opcodes.CASTORE:
                nextFrame = new BaseFrame(frame);
                popInt(nextFrame);
                popInt(nextFrame);
                popPrimitiveArrayOrNull(nextFrame, PrimitiveType.CHAR);
                break;
            case Opcodes.SASTORE:
                nextFrame = new BaseFrame(frame);
                popInt(nextFrame);
                popInt(nextFrame);
                popPrimitiveArrayOrNull(nextFrame, PrimitiveType.SHORT);
                break;
            case Opcodes.POP:
                nextFrame = new BaseFrame(frame);
                pop1WordItem(nextFrame);
                break;
            case Opcodes.POP2:
                nextFrame = new BaseFrame(frame);
                pop2WordItemOr2_1WordItems(nextFrame);
                break;
            case Opcodes.DUP:
                nextFrame = new BaseFrame(frame);
                FrameItem lastItem = pop1WordItem(nextFrame);
                nextFrame.getStack().add(lastItem);
                nextFrame.getStack().add(lastItem);
                break;
            case Opcodes.DUP_X1:
                nextFrame = new BaseFrame(frame);
                lastItem = pop1WordItem(nextFrame);
                FrameItem last2Item = pop1WordItem(nextFrame);
                nextFrame.getStack().add(lastItem);
                nextFrame.getStack().add(last2Item);
                nextFrame.getStack().add(lastItem);
                break;
            case Opcodes.DUP_X2:
                nextFrame = new BaseFrame(frame);
                lastItem = pop1WordItem(nextFrame);
                List<FrameItem> lastItems = pop2WordItemOr2_1WordItems(nextFrame);
                nextFrame.getStack().add(lastItem);
                nextFrame.getStack().addAll(lastItems);
                nextFrame.getStack().add(lastItem);
                break;
            case Opcodes.DUP2:
                nextFrame = new BaseFrame(frame);
                lastItems = pop2WordItemOr2_1WordItems(nextFrame);
                nextFrame.getStack().addAll(lastItems);
                nextFrame.getStack().addAll(lastItems);
                break;
            case Opcodes.DUP2_X1:
                nextFrame = new BaseFrame(frame);
                lastItems = pop2WordItemOr2_1WordItems(nextFrame);
                lastItem = pop1WordItem(nextFrame);
                nextFrame.getStack().addAll(lastItems);
                nextFrame.getStack().add(lastItem);
                nextFrame.getStack().addAll(lastItems);
                break;
            case Opcodes.DUP2_X2:
                nextFrame = new BaseFrame(frame);
                lastItems = pop2WordItemOr2_1WordItems(nextFrame);
                List<FrameItem> last2Items = pop2WordItemOr2_1WordItems(nextFrame);
                nextFrame.getStack().addAll(lastItems);
                nextFrame.getStack().addAll(last2Items);
                nextFrame.getStack().addAll(lastItems);
                break;
            case Opcodes.SWAP:
                nextFrame = new BaseFrame(frame);
                lastItem = pop1WordItem(nextFrame);
                last2Item = pop1WordItem(nextFrame);
                nextFrame.getStack().add(lastItem);
                nextFrame.getStack().add(last2Item);
                break;
            case Opcodes.IADD:
            case Opcodes.ISUB:
            case Opcodes.IMUL:
            case Opcodes.IDIV:
            case Opcodes.IREM:
            case Opcodes.ISHL:
            case Opcodes.ISHR:
            case Opcodes.IUSHR:
            case Opcodes.IAND:
            case Opcodes.IOR:
            case Opcodes.IXOR:
                nextFrame = new BaseFrame(frame);
                popInt(nextFrame);
                popInt(nextFrame);
                nextFrame.getStack().add(FrameItem.INT);
                break;
            case Opcodes.LADD:
            case Opcodes.LSUB:
            case Opcodes.LMUL:
            case Opcodes.LDIV:
            case Opcodes.LREM:
            case Opcodes.LAND:
            case Opcodes.LOR:
            case Opcodes.LXOR:
                nextFrame = new BaseFrame(frame);
                popLong(nextFrame);
                popLong(nextFrame);
                nextFrame.getStack().addAll(FrameItem.LONG);
                break;
            case Opcodes.FADD:
            case Opcodes.FSUB:
            case Opcodes.FMUL:
            case Opcodes.FDIV:
            case Opcodes.FREM:
                nextFrame = new BaseFrame(frame);
                popFloat(nextFrame);
                popFloat(nextFrame);
                nextFrame.getStack().add(FrameItem.FLOAT);
                break;
            case Opcodes.DADD:
            case Opcodes.DSUB:
            case Opcodes.DMUL:
            case Opcodes.DDIV:
            case Opcodes.DREM:
                nextFrame = new BaseFrame(frame);
                popDouble(nextFrame);
                popDouble(nextFrame);
                nextFrame.getStack().addAll(FrameItem.DOUBLE);
                break;
            case Opcodes.INEG:
            case Opcodes.I2B:
            case Opcodes.I2C:
            case Opcodes.I2S:
                checkLastStackItem(frame, FrameItem.INT, false);
                break;
            case Opcodes.LNEG:
                checkLastStackItems(frame, FrameItem.LONG, false);
                break;
            case Opcodes.FNEG:
                checkLastStackItem(frame, FrameItem.FLOAT, false);
                break;
            case Opcodes.DNEG:
                checkLastStackItems(frame, FrameItem.DOUBLE, false);
                break;
            case Opcodes.LSHL:
            case Opcodes.LSHR:
            case Opcodes.LUSHR:
                nextFrame = new BaseFrame(frame);
                popInt(nextFrame);
                popLong(nextFrame);
                nextFrame.getStack().addAll(FrameItem.LONG);
                break;
            case Opcodes.I2L:
                nextFrame = new BaseFrame(frame);
                popInt(nextFrame);
                nextFrame.getStack().addAll(FrameItem.LONG);
                break;
            case Opcodes.I2F:
                nextFrame = new BaseFrame(frame);
                popInt(nextFrame);
                nextFrame.getStack().add(FrameItem.FLOAT);
                break;
            case Opcodes.I2D:
                nextFrame = new BaseFrame(frame);
                popInt(nextFrame);
                nextFrame.getStack().addAll(FrameItem.DOUBLE);
                break;
            case Opcodes.L2I:
                nextFrame = new BaseFrame(frame);
                popLong(nextFrame);
                nextFrame.getStack().add(FrameItem.INT);
                break;
            case Opcodes.L2F:
                nextFrame = new BaseFrame(frame);
                popLong(nextFrame);
                nextFrame.getStack().add(FrameItem.FLOAT);
                break;
            case Opcodes.L2D:
                nextFrame = new BaseFrame(frame);
                popLong(nextFrame);
                nextFrame.getStack().addAll(FrameItem.DOUBLE);
                break;
            case Opcodes.F2I:
                nextFrame = new BaseFrame(frame);
                popFloat(nextFrame);
                nextFrame.getStack().add(FrameItem.INT);
                break;
            case Opcodes.F2L:
                nextFrame = new BaseFrame(frame);
                popFloat(nextFrame);
                nextFrame.getStack().addAll(FrameItem.LONG);
                break;
            case Opcodes.F2D:
                nextFrame = new BaseFrame(frame);
                popFloat(nextFrame);
                nextFrame.getStack().addAll(FrameItem.DOUBLE);
                break;
            case Opcodes.D2I:
                nextFrame = new BaseFrame(frame);
                popDouble(nextFrame);
                nextFrame.getStack().add(FrameItem.INT);
                break;
            case Opcodes.D2L:
                nextFrame = new BaseFrame(frame);
                popDouble(nextFrame);
                nextFrame.getStack().addAll(FrameItem.LONG);
                break;
            case Opcodes.D2F:
                nextFrame = new BaseFrame(frame);
                popDouble(nextFrame);
                nextFrame.getStack().add(FrameItem.FLOAT);
                break;
            case Opcodes.LCMP:
                nextFrame = new BaseFrame(frame);
                popLong(nextFrame);
                popLong(nextFrame);
                nextFrame.getStack().add(FrameItem.INT);
                break;
            case Opcodes.FCMPL:
            case Opcodes.FCMPG:
                nextFrame = new BaseFrame(frame);
                popFloat(nextFrame);
                popFloat(nextFrame);
                nextFrame.getStack().add(FrameItem.INT);
                break;
            case Opcodes.DCMPL:
            case Opcodes.DCMPG:
                nextFrame = new BaseFrame(frame);
                popDouble(nextFrame);
                popDouble(nextFrame);
                nextFrame.getStack().add(FrameItem.INT);
                break;
            case Opcodes.IRETURN:
                checkLastStackItem(frame, FrameItem.INT, false);
                gotoNextInstruction = false;
                checkThisIsInitialized(frame);
                break;
            case Opcodes.LRETURN:
                checkLastStackItems(frame, FrameItem.LONG, false);
                gotoNextInstruction = false;
                checkThisIsInitialized(frame);
                break;
            case Opcodes.FRETURN:
                checkLastStackItem(frame, FrameItem.FLOAT, false);
                gotoNextInstruction = false;
                checkThisIsInitialized(frame);
                break;
            case Opcodes.DRETURN:
                checkLastStackItems(frame, FrameItem.DOUBLE, false);
                gotoNextInstruction = false;
                checkThisIsInitialized(frame);
                break;
            case Opcodes.ARETURN:
                checkLastStackItemIsReference(frame, true, false);
                gotoNextInstruction = false;
                checkThisIsInitialized(frame);
                break;
            case Opcodes.RETURN:
                gotoNextInstruction = false;
                checkThisIsInitialized(frame);
                break;
            case Opcodes.ARRAYLENGTH:
                nextFrame = new BaseFrame(frame);
                popArrayOrNull(nextFrame);
                nextFrame.getStack().add(FrameItem.INT);
                break;
            case Opcodes.ATHROW:
                checkLastStackItemIsNullOrInstanceOf(frame, objectFactory.getObjectFrameItem(Throwable.class.getName()), false);
                gotoNextInstruction = false;
                break;
            case Opcodes.MONITORENTER:
            case Opcodes.MONITOREXIT:
                nextFrame = new BaseFrame(frame);
                popReference(nextFrame, false);
                break;
            default:
                throw new IllegalArgumentException("invalid opcode: " + instruction.getOpcode());
        }
        PerformInstructionResult result = new PerformInstructionResult();
        if (nextFrame == null) {
            result.setFrame(frame);
        } else {
            result.setFrame(nextFrame);
        }
        if (gotoNextInstruction) {
            AbstractInsnNode nextInstruction = getNextInstruction(instruction);
            result.setPossibleNextInstructions(Collections.singleton(nextInstruction));
        } else {
            result.setPossibleNextInstructions(Collections.emptySet());
        }
        return result;
    }

    private static void checkThisIsInitialized(Frame frame) {
        if (!frame.isThisInitialized()) {
            throw new AnalyzeMethodException("this is uninitialized in return");
        }
    }

    private static AbstractInsnNode getNextInstruction(AbstractInsnNode instruction) {
        AbstractInsnNode result = AsmUtils.getCodeInstruction(instruction.getNext());
        if (result == null) {
            throw new AnalyzeMethodException("unexpected method end after instruction " + instruction);
        }
        return result;
    }

}
