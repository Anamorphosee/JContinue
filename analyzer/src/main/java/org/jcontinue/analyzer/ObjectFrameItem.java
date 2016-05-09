package org.jcontinue.analyzer;

public interface ObjectFrameItem extends NotNullInitializedReferenceFrameItem {
    ObjectFrameItem getCommonSuperClass(ObjectFrameItem other);
    boolean isSubClass(ObjectFrameItem superClass);
}
