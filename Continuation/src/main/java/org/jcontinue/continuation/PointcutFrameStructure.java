package org.jcontinue.continuation;

import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PointcutFrameStructure {
    private final Map<Integer, PointcutFrameStructureItem> locals = new HashMap<>();
    private final List<PointcutFrameStructureItem> stack = new ArrayList<>();
    private PointcutFrameStructureItem invocationObjectItem;
    private final List<Type> sortedAsmTypes = new ArrayList<>();
    private String savedFrameContextClassName;
    private final List<Type> invocationArgumentTypes = new ArrayList<>();
    private boolean methodStatic;
    private Type invocationReturnType;

    public Map<Integer, PointcutFrameStructureItem> getLocals() {
        return locals;
    }

    public List<PointcutFrameStructureItem> getStack() {
        return stack;
    }

    public PointcutFrameStructureItem getInvocationObjectItem() {
        return invocationObjectItem;
    }

    public void setInvocationObjectItem(PointcutFrameStructureItem invocationObjectItem) {
        this.invocationObjectItem = invocationObjectItem;
    }

    public List<Type> getSortedAsmTypes() {
        return sortedAsmTypes;
    }

    public String getSavedFrameContextClassName() {
        return savedFrameContextClassName;
    }

    public void setSavedFrameContextClassName(String savedFrameContextClassName) {
        this.savedFrameContextClassName = savedFrameContextClassName;
    }

    public List<Type> getInvocationArgumentTypes() {
        return invocationArgumentTypes;
    }

    public Type getSavedFrameContextClassType() {
        return Type.getObjectType(savedFrameContextClassName.replace('.', '/'));
    }

    public boolean isMethodStatic() {
        return methodStatic;
    }

    public void setMethodStatic(boolean methodStatic) {
        this.methodStatic = methodStatic;
    }

    public Type getInvocationReturnType() {
        return invocationReturnType;
    }

    public void setInvocationReturnType(Type invocationReturnType) {
        this.invocationReturnType = invocationReturnType;
    }
}
