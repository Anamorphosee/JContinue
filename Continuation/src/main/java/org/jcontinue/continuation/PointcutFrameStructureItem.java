package org.jcontinue.continuation;

import org.jcontinue.analyzer.FrameItem;
import org.objectweb.asm.Type;

public class PointcutFrameStructureItem {
    private Type asmType;
    private String fieldName;
    private FrameItem frameItem;

    public Type getAsmType() {
        return asmType;
    }

    public void setAsmType(Type asmType) {
        this.asmType = asmType;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public FrameItem getFrameItem() {
        return frameItem;
    }

    public void setFrameItem(FrameItem frameItem) {
        this.frameItem = frameItem;
    }
}
