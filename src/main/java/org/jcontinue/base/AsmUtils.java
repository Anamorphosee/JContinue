package org.jcontinue.base;

import com.google.common.collect.Sets;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class AsmUtils {

    public static final Type OBJECT_ASM_TYPE = Type.getType(Object.class);
    public static final Type THROWABLE_ASM_TYPE = Type.getType(Throwable.class);
    public static final Set<Type> PRIMITIVE_ASM_TYPE_SET = Sets.newHashSet(Type.VOID_TYPE, Type.BOOLEAN_TYPE,
            Type.BYTE_TYPE, Type.CHAR_TYPE, Type.SHORT_TYPE, Type.INT_TYPE, Type.FLOAT_TYPE, Type.LONG_TYPE,
            Type.DOUBLE_TYPE);
    public static InsnList emptyInstuctionList = new InsnList();


    public static boolean isStatic(int access) {
        return (access & Opcodes.ACC_STATIC) != 0;
    }

    public static Type getArrayAsmType(Type elementType, int dimensions) {
        if (dimensions < 0) {
            throw new IllegalArgumentException("dimensions cannot be " + dimensions);
        }
        if (dimensions == 0) {
            return elementType;
        }
        String elementTypeDescriptor = elementType.getDescriptor();
        StringBuilder descriptorBuilder = new StringBuilder(dimensions + elementTypeDescriptor.length());
        for (int i = 0; i < dimensions; ++i) {
            descriptorBuilder.append('[');
        }
        descriptorBuilder.append(elementType.getDescriptor());
        return Type.getType(descriptorBuilder.toString());
    }

    public static boolean isPrimitive(Type type) {
        return PRIMITIVE_ASM_TYPE_SET.contains(type);
    }

    public static int getArrayDimensionsNumber(Type type) {
        if (type.getSort() != Type.ARRAY) {
            return 0;
        }
        return type.getDimensions();
    }

    public static Type getArrayElementAsmType(Type type, int dimensionsNumber) {
        Objects.requireNonNull(type);
        if (dimensionsNumber < 0) {
            throw new IllegalArgumentException("dimensionsNumber cannot be " + dimensionsNumber);
        }
        if (dimensionsNumber == 0) {
            return type;
        }
        if (type.getSort() != Type.ARRAY) {
            throw new IllegalArgumentException(
                    "type is not an array.\n" +
                            "type: " + type + "\n" +
                            "dimensionsNumber: " + dimensionsNumber + "\n"
            );
        }
        if (type.getDimensions() < dimensionsNumber) {
            throw new IllegalArgumentException(
                    "type dimensions number is less than required.\n" +
                            "type: " + type + "\n" +
                            "dimensionsNumber: " + dimensionsNumber + "\n"
            );
        }
        return Type.getType(type.getDescriptor().substring(dimensionsNumber));
    }

    public static Type getDeepestArrayElementAsmType(Type type) {
        return getArrayElementAsmType(type, getArrayDimensionsNumber(type));
    }

    public static boolean isCodeInstruction(AbstractInsnNode instruction) {
        return instruction.getOpcode() != -1;
    }

    public static AbstractInsnNode getCodeInstruction(AbstractInsnNode instruction) {
        while (instruction != null && !isCodeInstruction(instruction)) {
            instruction = instruction.getNext();
        }
        return instruction;
    }

    public static boolean isStatic(MethodNode method) {
        return isStatic(method.access);
    }

    public static AbstractInsnNode getHandlerCodeInstruction(TryCatchBlockNode tryCatchBlock) {
        return getCodeInstruction(tryCatchBlock.handler);
    }

    public static boolean isConstructor(MethodNode method) {
        return method.name.equals("<init>") && !isStatic(method);
    }

    public static boolean isContainInstructions(MethodNode method) {
        return method.instructions != null && method.instructions.size() > 0;
    }

    public static AbstractInsnNode getPushIntInstruction(int value) {
        switch (value) {
            case -1:
                return new InsnNode(Opcodes.ICONST_M1);
            case 0:
                return new InsnNode(Opcodes.ICONST_0);
            case 1:
                return new InsnNode(Opcodes.ICONST_1);
            case 2:
                return new InsnNode(Opcodes.ICONST_2);
            case 3:
                return new InsnNode(Opcodes.ICONST_3);
            case 4:
                return new InsnNode(Opcodes.ICONST_4);
            case 5:
                return new InsnNode(Opcodes.ICONST_5);
        }
        if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
            return new IntInsnNode(Opcodes.BIPUSH, value);
        }
        if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
            return new IntInsnNode(Opcodes.SIPUSH, value);
        }
        return new LdcInsnNode(value);
    }

    public static AbstractInsnNode getPushFloatInstruction(float value) {
        if (value == 0) {
            return new InsnNode(Opcodes.FCONST_0);
        }
        if (value == 1) {
            return new InsnNode(Opcodes.FCONST_1);
        }
        if (value == 2) {
            return new InsnNode(Opcodes.FCONST_2);
        }
        return new LdcInsnNode(value);
    }

    public static AbstractInsnNode getPushLongInstruction(long value) {
        if (value == 0) {
            return new InsnNode(Opcodes.LCONST_0);
        }
        if (value == 1) {
            return new InsnNode(Opcodes.LCONST_1);
        }
        return new LdcInsnNode(value);
    }

    public static AbstractInsnNode getPushDoubleInstruction(double value) {
        if (value == 0) {
            return new InsnNode(Opcodes.DCONST_0);
        }
        if (value == 1) {
            return new InsnNode(Opcodes.DCONST_1);
        }
        return new LdcInsnNode(value);
    }

    public static InsnNode getPushNullInstruction() {
        return new InsnNode(Opcodes.ACONST_NULL);
    }

    public static InsnNode getDupInstruction() {
        return new InsnNode(Opcodes.DUP);
    }

    public static VarInsnNode getStoreInstruction(Type asmType, int localIndex) {
        int opcode = asmType.getOpcode(Opcodes.ISTORE);
        return new VarInsnNode(opcode, localIndex);
    }

    public static InsnList getPushAndStoreIntInstructions(int value, int localIndex) {
        InsnList result = new InsnList();
        result.add(getPushIntInstruction(value));
        result.add(getStoreInstruction(Type.INT_TYPE, localIndex));
        return result;
    }

    public static InsnList getPushAndStoreFloatInstructions(float value, int localIndex) {
        InsnList result = new InsnList();
        result.add(getPushFloatInstruction(value));
        result.add(getStoreInstruction(Type.FLOAT_TYPE, localIndex));
        return result;
    }

    public static InsnList getPushAndStoreLongInstructions(long value, int localIndex) {
        InsnList result = new InsnList();
        result.add(getPushLongInstruction(value));
        result.add(getStoreInstruction(Type.LONG_TYPE, localIndex));
        return result;
    }

    public static InsnList getPushAndStoreDoubleInstructions(double value, int localIndex) {
        InsnList result = new InsnList();
        result.add(getPushDoubleInstruction(value));
        result.add(getStoreInstruction(Type.DOUBLE_TYPE, localIndex));
        return result;
    }

    public static InsnList getPushAndStoreNullInstructions(int localIndex) {
        InsnList result = new InsnList();
        result.add(getPushNullInstruction());
        result.add(getStoreInstruction(OBJECT_ASM_TYPE, localIndex));
        return result;
    }



    public static VarInsnNode getStoreReferenceInstruction(int localIndex) {
        return getStoreInstruction(OBJECT_ASM_TYPE, localIndex);
    }

    public static InsnNode getPop1WordInstruction() {
        return new InsnNode(Opcodes.POP);
    }

    public static InsnNode getPop2WordInstruction() {
        return new InsnNode(Opcodes.POP2);
    }

    public static InsnNode getPopInstruction(Type asmType) {
        int opcode;
        if (asmType.getSize() == 1) {
            return getPop1WordInstruction();
        } else if (asmType.getSize() == 2) {
            return getPop2WordInstruction();
        } else {
            throw new IllegalArgumentException("invalid asmType: " + asmType);
        }
    }

    public static VarInsnNode getLoadInstruction(Type asmType, int localIndex) {
        int opcode = asmType.getOpcode(Opcodes.ILOAD);
        return new VarInsnNode(opcode, localIndex);
    }

    public static VarInsnNode getLoadReferenceInstruction(int localIndex) {
        return getLoadInstruction(OBJECT_ASM_TYPE, localIndex);
    }

    public static MethodInsnNode getInvokeInstruction(Type ownerType, String methodName, InvocationType type,
            Type returnType, Type ... argumentTypes) {
        if (ownerType.getSort() != Type.OBJECT) {
            throw new IllegalArgumentException("ownerType cannot be " + ownerType);
        }
        String internalName = ownerType.getInternalName();
        int opcode;
        switch (type) {
            case STATIC:
                opcode = Opcodes.INVOKESTATIC;
                break;
            case VIRTUAL:
                opcode = Opcodes.INVOKEVIRTUAL;
                break;
            case INTERFACE:
                opcode = Opcodes.INVOKEINTERFACE;
                break;
            case SPECIAL:
                opcode = Opcodes.INVOKESPECIAL;
                break;
            default:
                throw new IllegalArgumentException("invalid type " + type);
        }
        String descriptor = Type.getMethodDescriptor(returnType, argumentTypes);
        return new MethodInsnNode(opcode, internalName, methodName, descriptor, type == InvocationType.INTERFACE);
    }

    public static MethodInsnNode getInvocationInstruction(Method method) {
        Class<?> ownerClass = method.getDeclaringClass();
        Type ownerType = Type.getType(ownerClass);
        String methodName = method.getName();
        InvocationType invocationType;
        if ((method.getModifiers() & Modifier.STATIC) != 0) {
            invocationType = InvocationType.STATIC;
        } else if (ownerClass.isInterface()) {
            invocationType = InvocationType.INTERFACE;
        } else {
            invocationType = InvocationType.VIRTUAL;
        }
        Type returnType = Type.getType(method.getReturnType());
        Type[] argumentTypes = new Type[method.getParameterTypes().length];
        for (int i = 0; i < method.getParameterTypes().length; i++) {
            argumentTypes[i] = Type.getType(method.getParameterTypes()[i]);
        }
        MethodInsnNode result = getInvokeInstruction(ownerType, methodName, invocationType, returnType, argumentTypes);
        return result;
    }

    public static TypeInsnNode getCheckcastInstruction(Type targetObjectType) {
        if (targetObjectType.getSort() != Type.OBJECT) {
            throw new IllegalArgumentException("targetObjectType cannot be " + targetObjectType);
        }
        TypeInsnNode result = new TypeInsnNode(Opcodes.CHECKCAST, targetObjectType.getInternalName());
        return result;
    }

    public static FieldInsnNode getGetFieldInstruction(Type ownerType, String fieldName, Type fieldType,
            boolean fieldStatic) {
        if (ownerType.getSort() != Type.OBJECT) {
            throw new IllegalArgumentException("ownerType cannot be " + ownerType);
        }
        int opcode = fieldStatic ? Opcodes.GETSTATIC : Opcodes.GETFIELD;
        String descriptor = fieldType.getDescriptor();
        return new FieldInsnNode(opcode, ownerType.getInternalName(), fieldName, descriptor);
    }

    public static AbstractInsnNode getPushAnyValueInstruction(Type type) {
        switch (type.getSort()) {
            case Type.OBJECT:
            case Type.ARRAY:
                return getPushNullInstruction();
            case Type.BOOLEAN:
            case Type.CHAR:
            case Type.BYTE:
            case Type.SHORT:
            case Type.INT:
                return getPushIntInstruction(0);
            case Type.FLOAT:
                return getPushFloatInstruction(0);
            case Type.LONG:
                return getPushLongInstruction(0);
            case Type.DOUBLE:
                return getPushDoubleInstruction(0);
            default:
                throw new IllegalArgumentException("type cannot be " + type);
        }
    }

    public static TypeInsnNode getNewInstruction(Type asmType) {
        if (asmType.getSort() != Type.OBJECT) {
            throw new IllegalArgumentException("asmType cannot be " + asmType);
        }
        return new TypeInsnNode(Opcodes.NEW, asmType.getInternalName());
    }

    public static InsnList getConstructNewObjectInstrcutions(Type asmType, Type ... constructorArgumentTypes) {
        InsnList result = new InsnList();
        result.add(getNewInstruction(asmType));
        result.add(getDupInstruction());
        result.add(getInvokeInstruction(asmType, "<init>", InvocationType.SPECIAL, Type.VOID_TYPE,
                constructorArgumentTypes));
        return result;
    }

    public static InsnNode getSwapInstruction() {
        return new InsnNode(Opcodes.SWAP);
    }

    public static InsnNode getDupX2Instruction() {
        return new InsnNode(Opcodes.DUP_X2);
    }

    public static InsnNode getDup2X1Instruction() {
        return new InsnNode(Opcodes.DUP2_X1);
    }

    public static InsnNode getDup2X2Instruction() {
        return new InsnNode(Opcodes.DUP2_X2);
    }

    public static InsnList getSwapInstructions(Type preLast, Type last) {
        InsnList result = new InsnList();
        if (preLast.getSize() == 1 && last.getSize() == 1) {
            result.add(getSwapInstruction());
        } else if (preLast.getSize() == 2 && last.getSize() == 1) {
            result.add(getDupX2Instruction());
            result.add(getPop1WordInstruction());
        } else if (preLast.getSize() == 1 && last.getSize() == 2) {
            result.add(getDup2X1Instruction());
            result.add(getPop2WordInstruction());
        } else if (preLast.getSize() == 2 && last.getSize() == 2) {
            result.add(getDup2X2Instruction());
            result.add(getPop2WordInstruction());
        } else {
            throw new IllegalArgumentException("invalid arguments: preLast = " + preLast + ", last = " + last);
        }
        return result;
    }

    public static FieldInsnNode getSetFieldInstruction(Type ownerType, String fieldName, Type fieldType,
            boolean fieldStatic) {
        if (ownerType.getSort() != Type.OBJECT) {
            throw new IllegalArgumentException("ownerType cannot be " + ownerType);
        }
        int opcode = fieldStatic ? Opcodes.PUTSTATIC : Opcodes.PUTFIELD;
        String descriptor = fieldType.getDescriptor();
        return new FieldInsnNode(opcode, ownerType.getInternalName(), fieldName, descriptor);
    }

    public static FieldInsnNode getSetFieldInstruction(Field field) {
        Type ownerType = Type.getType(field.getDeclaringClass());
        String fieldName = field.getName();
        Type fieldType = Type.getType(field.getType());
        boolean fieldStatic = (field.getModifiers() & Modifier.STATIC) != 0;
        return getSetFieldInstruction(ownerType, fieldName, fieldType, fieldStatic);
    }

    public static LdcInsnNode getPushStringInstruction(String value) {
        return new LdcInsnNode(value);
    }

    public static VarInsnNode getStoreIntInstruction(int localIndex) {
        return getStoreInstruction(Type.INT_TYPE, localIndex);
    }

    public static JumpInsnNode getGotoInstruction(LabelNode label) {
        return new JumpInsnNode(Opcodes.GOTO, label);
    }

    public static LabelNode getLabelOnInstruction(AbstractInsnNode instruction, InsnList instructionList) {
        instruction = getCodeInstruction(instruction);
        AbstractInsnNode prevInstruction = instruction.getPrevious();
        while (prevInstruction != null && !isCodeInstruction(prevInstruction) &&
                prevInstruction.getType() != AbstractInsnNode.LABEL) {
            prevInstruction = prevInstruction.getPrevious();
        }
        if (prevInstruction != null && prevInstruction.getType() == AbstractInsnNode.LABEL) {
            return (LabelNode) prevInstruction;
        }
        LabelNode result = new LabelNode();
        instructionList.insertBefore(instruction, result);
        return result;
    }

    public static JumpInsnNode getGotoInstruction(AbstractInsnNode instruction, InsnList instructionList) {
        LabelNode label = getLabelOnInstruction(instruction, instructionList);
        return getGotoInstruction(label);
    }

    public static JumpInsnNode getGotoIfNotZeroInstruction(LabelNode label) {
        return new JumpInsnNode(Opcodes.IFNE, label);
    }

    public static JumpInsnNode getGotoIfNotZeroInstruction(AbstractInsnNode instruction, InsnList instructionList) {
        LabelNode label = getLabelOnInstruction(instruction, instructionList);
        return getGotoIfNotZeroInstruction(label);
    }

    public static InsnNode getReturnInstruction(Type type) {
        int opcode = type.getOpcode(Opcodes.IRETURN);
        return new InsnNode(opcode);
    }

    public static InsnList getReturnAnyValueInstructions(Type type) {
        InsnList result = new InsnList();
        if (!type.equals(Type.VOID_TYPE)) {
            result.add(getPushAnyValueInstruction(type));
        }
        result.add(getReturnInstruction(type));
        return result;
    }

    public static AbstractInsnNode getSwitchInstruction(Map<Integer, LabelNode> caseLabels, LabelNode defaultLabel) {
        if (caseLabels.isEmpty()) {
            return getGotoInstruction(defaultLabel);
        }
        List<Integer> keys = new ArrayList<>(caseLabels.keySet());
        Collections.sort(keys);
        int min = keys.get(0);
        int max = keys.get(keys.size() - 1);
        int rangeLength = max - min + 1;
        if (rangeLength / 2 > keys.size()) {
            LabelNode[] caseLabelArray = new LabelNode[keys.size()];
            int[] keyArray = new int[keys.size()];
            for (int i = 0; i < keys.size(); i++) {
                caseLabelArray[i] = caseLabels.get(keys.get(i));
                keyArray[i] = keys.get(i);
            }
            return new LookupSwitchInsnNode(defaultLabel, keyArray, caseLabelArray);
        } else {
            LabelNode[] caseLabelArray = new LabelNode[rangeLength];
            for (int i = 0; i < rangeLength; i++) {
                if (keys.contains(min + i)) {
                    caseLabelArray[i] = caseLabels.get(min + i);
                } else {
                    caseLabelArray[i] = caseLabelArray[i - 1];
                }
            }
            return new TableSwitchInsnNode(min, max, defaultLabel, caseLabelArray);
        }
    }

    public static JumpInsnNode getGotoIfZeroInstruction(LabelNode label) {
        return new JumpInsnNode(Opcodes.IFEQ, label);
    }

    public static JumpInsnNode getGotoIfZeroInstruction(AbstractInsnNode instruction, InsnList instructionList) {
        LabelNode label = getLabelOnInstruction(instruction, instructionList);
        return getGotoIfZeroInstruction(label);
    }

    public static VarInsnNode getLoadIntInstruction(int localIndex) {
        return getLoadInstruction(Type.INT_TYPE, localIndex);
    }

}
