package org.jcontinue.continuation;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodNode;

public interface ContinuationMethodTransformerRegistry {
    boolean doTransformInvokeInstruction(String className, MethodNode method, AbstractInsnNode invokeInstruction);
}
