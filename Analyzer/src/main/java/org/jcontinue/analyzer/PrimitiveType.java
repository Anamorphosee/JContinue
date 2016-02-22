package org.jcontinue.analyzer;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public enum PrimitiveType {
    BOOLEAN(Type.BOOLEAN_TYPE), BYTE(Type.BYTE_TYPE), CHAR(Type.CHAR_TYPE), SHORT(Type.SHORT_TYPE), INT(Type.INT_TYPE),
    FLOAT(Type.FLOAT_TYPE), LONG(Type.LONG_TYPE), DOUBLE(Type.DOUBLE_TYPE);

    private final Type asmType;

    PrimitiveType(Type asmType) {
        this.asmType = asmType;
    }

    public Type getAsmType() {
        return asmType;
    }

    public static PrimitiveType valueOf(Type asmType) {
        for (PrimitiveType candidate : values()) {
            if (candidate.getAsmType().equals(asmType)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("invalid asmType: " + asmType);
    }

    public static PrimitiveType valueOfNewArrayInstructionOperand(int newArrayInstructionOperand) {
        switch (newArrayInstructionOperand) {
            case Opcodes.T_BOOLEAN:
                return PrimitiveType.BOOLEAN;
            case Opcodes.T_CHAR:
                return PrimitiveType.CHAR;
            case Opcodes.T_FLOAT:
                return PrimitiveType.FLOAT;
            case Opcodes.T_DOUBLE:
                return PrimitiveType.DOUBLE;
            case Opcodes.T_BYTE:
                return PrimitiveType.BYTE;
            case Opcodes.T_SHORT:
                return PrimitiveType.SHORT;
            case Opcodes.T_INT:
                return PrimitiveType.INT;
            case Opcodes.T_LONG:
                return PrimitiveType.LONG;
            default:
                throw new IllegalArgumentException("invalid NEWARRAY instruction operand " + newArrayInstructionOperand);
        }
    }

    @Override
    public String toString() {
        return name().toLowerCase();
    }
}
