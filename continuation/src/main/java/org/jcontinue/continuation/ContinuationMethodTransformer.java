package org.jcontinue.continuation;

import org.objectweb.asm.tree.MethodNode;

import java.util.Map;

public interface ContinuationMethodTransformer {
    void transformMethod(String className, MethodNode method);

    Map<String, byte[]> getAuxiliaryClasses();

    default boolean isAuxiliaryClass(String className) {
        return getAuxiliaryClasses().containsKey(className);
    }

    default byte[] getAuxiliaryClassBody(String className) {
        return getAuxiliaryClasses().get(className);
    }
}
