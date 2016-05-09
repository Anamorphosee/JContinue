package org.jcontinue.continuation;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public class SimpleContinuationClassTransformerRegistry implements ContinuationClassTransformerRegistry {
    @Override
    public boolean doTransformClass(String className) {
        return !className.startsWith(Continuation.class.getName());
    }

    @Override
    public boolean doTransformMethod(ClassNode clazz, MethodNode method) {
        return !method.name.equals("<init>") && !method.name.equals("<clinit>") && method.instructions != null &&
                method.instructions.size() > 0;
    }

    @Override
    public boolean doTransformInvokeInstruction(String className, MethodNode method, AbstractInsnNode invokeInstruction) {
        return invokeInstruction.getType() != AbstractInsnNode.METHOD_INSN ||
                !((MethodInsnNode) invokeInstruction).name.equals("<init>");
    }
}
