package org.jcontinue.analyzer;

import java.util.Objects;

/**
 * Created by mylenium on 01.11.15.
 */
public class UninitializedThisFrameItem implements ReferenceFrameItem {
    private final String className;

    public UninitializedThisFrameItem(String className) {
        Objects.requireNonNull(className);
        this.className = className;
    }

    public String getClassName() {
        return className;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UninitializedThisFrameItem that = (UninitializedThisFrameItem) o;
        return Objects.equals(className, that.className);
    }

    @Override
    public int hashCode() {
        return Objects.hash(className);
    }

    @Override
    public String toString() {
        return "uninitializedThis_" + getClassName();
    }
}
