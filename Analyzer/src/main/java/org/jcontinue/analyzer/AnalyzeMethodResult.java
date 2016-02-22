package org.jcontinue.analyzer;

import org.objectweb.asm.tree.AbstractInsnNode;

import java.util.Map;

public interface AnalyzeMethodResult {
    Map<? super AbstractInsnNode, ? extends Frame> getFrames();
    int getLocalsNumber();
    int getStackSize();

    default boolean isInstructionReachable(AbstractInsnNode instruction) {
        return getFrames().containsKey(instruction);
    }
}
