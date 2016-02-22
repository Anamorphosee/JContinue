package org.jcontinue.analyzer;

import org.jcontinue.base.AsmUtils;
import org.objectweb.asm.Type;

import java.util.Objects;

public class PrimitiveArrayFrameItem implements NotNullInitializedReferenceFrameItem {
    private final PrimitiveType primitiveType;

    public PrimitiveArrayFrameItem(PrimitiveType primitiveType) {
        Objects.requireNonNull(primitiveType);
        this.primitiveType = primitiveType;
    }

    public PrimitiveType getPrimitiveType() {
        return primitiveType;
    }

    public Type getAsmType() {
        return AsmUtils.getArrayAsmType(primitiveType.getAsmType(), 1);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PrimitiveArrayFrameItem that = (PrimitiveArrayFrameItem) o;
        return Objects.equals(primitiveType, that.primitiveType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(primitiveType);
    }

    @Override
    public String toString() {
        return primitiveType + "[]";
    }
}
