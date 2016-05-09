package org.jcontinue.analyzer;

import java.util.ArrayList;
import java.util.Objects;

public class BaseFrame implements Frame {
    private final ArrayList<FrameItem> locals;
    private final ArrayList<FrameItem> stack;
    private boolean thisInitialized;

    public BaseFrame() {
        locals = new ArrayList<>();
        stack = new ArrayList<>();
        thisInitialized = true;
    }

    public BaseFrame(Frame frame) {
        locals = new ArrayList<>(frame.getLocals());
        stack = new ArrayList<>(frame.getStack());
        thisInitialized = frame.isThisInitialized();
    }

    @Override
    public ArrayList<FrameItem> getLocals() {
        return locals;
    }

    @Override
    public ArrayList<FrameItem> getStack() {
        return stack;
    }

    @Override
    public boolean isThisInitialized() {
        return thisInitialized;
    }

    public void setThisInitialized(boolean thisInitialized) {
        this.thisInitialized = thisInitialized;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BaseFrame baseFrame = (BaseFrame) o;
        return Objects.equals(thisInitialized, baseFrame.thisInitialized) &&
                Objects.equals(locals, baseFrame.locals) &&
                Objects.equals(stack, baseFrame.stack);
    }

    @Override
    public int hashCode() {
        return Objects.hash(locals, stack, thisInitialized);
    }

    @Override
    public String toString() {
        return "{locals: " + locals +", stack: " + stack + "}";
    }
}
