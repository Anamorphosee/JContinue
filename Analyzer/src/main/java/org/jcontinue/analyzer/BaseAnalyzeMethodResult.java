package org.jcontinue.analyzer;

import org.objectweb.asm.tree.AbstractInsnNode;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class BaseAnalyzeMethodResult implements AnalyzeMethodResult {
    private final Map<AbstractInsnNode, Frame> frames;
    private int localsNumber, stackSize;

    public BaseAnalyzeMethodResult(Map<AbstractInsnNode, Frame> frames) {
        Objects.requireNonNull(frames);
        this.frames = frames;
    }

    public BaseAnalyzeMethodResult() {
        this(new HashMap<>());
    }

    @Override
    public Map<AbstractInsnNode, Frame> getFrames() {
        return frames;
    }

    @Override
    public int getLocalsNumber() {
        return localsNumber;
    }

    @Override
    public int getStackSize() {
        return stackSize;
    }

    public void setLocalsNumber(int localsNumber) {
        this.localsNumber = localsNumber;
    }

    public void setStackSize(int stackSize) {
        this.stackSize = stackSize;
    }
}
