package org.jcontinue.analyzer;

import java.util.Objects;

public class ReferenceArrayFrameItem implements NotNullInitializedReferenceFrameItem {
    private final NotNullInitializedReferenceFrameItem elementType;

    public ReferenceArrayFrameItem(NotNullInitializedReferenceFrameItem elementType, int dimensionsNumber) {
        Objects.requireNonNull(elementType);
        if (dimensionsNumber < 1) {
            throw new IllegalArgumentException("dimensionsNumber cannot be " + dimensionsNumber);
        }
        if (dimensionsNumber == 1) {
            this.elementType = elementType;
        } else {
            this.elementType = new ReferenceArrayFrameItem(elementType, dimensionsNumber - 1);
        }
    }

    public ReferenceArrayFrameItem(NotNullInitializedReferenceFrameItem elementType) {
        this(elementType, 1);
    }

    public NotNullInitializedReferenceFrameItem getElementType() {
        return elementType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReferenceArrayFrameItem that = (ReferenceArrayFrameItem) o;
        return Objects.equals(elementType, that.elementType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(elementType);
    }

    @Override
    public String toString() {
        return elementType.toString() + "[]";
    }
}
