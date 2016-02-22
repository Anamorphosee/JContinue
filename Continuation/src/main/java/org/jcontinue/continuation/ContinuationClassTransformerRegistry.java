package org.jcontinue.continuation;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public interface ContinuationClassTransformerRegistry extends ContinuationMethodTransformerRegistry {
    boolean doTransformClass(String className);
    boolean doTransformMethod(ClassNode clazz, MethodNode method);
}
