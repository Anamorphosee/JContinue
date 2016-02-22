package org.jcontinue.analyzer;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.TypeInsnNode;

import java.util.Objects;

/**
 * Created by mylenium on 13.09.15.
 */
public class UninitializedObjectFrameItem implements ReferenceFrameItem {
    private final TypeInsnNode newInstruction;

    public UninitializedObjectFrameItem(TypeInsnNode newInstruction) {
        if (newInstruction.getOpcode() != Opcodes.NEW) {
            throw new IllegalArgumentException("newInstruction.opcode cannot be [" + newInstruction.getOpcode() + "]");
        }
        this.newInstruction = newInstruction;
    }

    public String getClassName() {
        return newInstruction.desc.replaceAll("/", ".");
    }

    public String getAsmInternalName() {
        return newInstruction.desc;
    }

    public TypeInsnNode getNewInstruction() {
        return newInstruction;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UninitializedObjectFrameItem that = (UninitializedObjectFrameItem) o;
        return Objects.equals(newInstruction, that.newInstruction);
    }

    @Override
    public int hashCode() {
        return Objects.hash(newInstruction);
    }

    @Override
    public String toString() {
        return "uninitialized_" + getClassName() + "@" + Integer.toHexString(newInstruction.hashCode());
    }
}
