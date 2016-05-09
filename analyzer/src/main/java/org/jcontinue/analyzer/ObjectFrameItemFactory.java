package org.jcontinue.analyzer;

@FunctionalInterface
public interface ObjectFrameItemFactory {
    ObjectFrameItem getObjectFrameItem(String className);
}
