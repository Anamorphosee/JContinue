package org.jcontinue.analyzer;

@FunctionalInterface
public interface ObjectFrameItemClassNameSupplier {
    String getClassName(ObjectFrameItem item);
}
