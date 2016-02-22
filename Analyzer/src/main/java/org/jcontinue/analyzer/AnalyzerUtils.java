package org.jcontinue.analyzer;

import org.jcontinue.base.AsmUtils;
import org.objectweb.asm.Type;

public class AnalyzerUtils {

    public static boolean isInitializedReference(FrameItem item) {
        return item.equals(FrameItem.NULL) || item instanceof ObjectFrameItem || item instanceof PrimitiveArrayFrameItem ||
                item instanceof ReferenceArrayFrameItem;
    }

    public static boolean isUninitializedReference(FrameItem item) {
        return item instanceof UninitializedObjectFrameItem || item instanceof UninitializedThisFrameItem;
    }

    public static boolean isReference(FrameItem frameItem) {
        return isInitializedReference(frameItem) || isUninitializedReference(frameItem);
    }

    public static Type getAsmType(FrameItem frameItem, ObjectFrameItemClassNameSupplier classNameSupplier) {
        if (frameItem.equals(FrameItem.INT)) {
            return Type.INT_TYPE;
        } else if (frameItem.equals(FrameItem.FLOAT)) {
            return Type.FLOAT_TYPE;
        } else if (frameItem.equals(FrameItem.LONG_0) || frameItem.equals(FrameItem.LONG_1)) {
            return Type.LONG_TYPE;
        } else if (frameItem.equals(FrameItem.DOUBLE_0) || frameItem.equals(FrameItem.DOUBLE_1)) {
            return Type.DOUBLE_TYPE;
        } else if (frameItem instanceof PrimitiveArrayFrameItem) {
            PrimitiveArrayFrameItem paFrameItem = (PrimitiveArrayFrameItem) frameItem;
            return paFrameItem.getAsmType();
        } else if (frameItem instanceof ObjectFrameItem) {
            ObjectFrameItem oFrameItem = (ObjectFrameItem) frameItem;
            String className = classNameSupplier.getClassName(oFrameItem);
            String classDescriptor = className.replace('.', '/');
            return Type.getObjectType(classDescriptor);
        } else if (frameItem instanceof ReferenceArrayFrameItem) {
            ReferenceArrayFrameItem raFrameItem = (ReferenceArrayFrameItem) frameItem;
            Type elementAsmType = getAsmType(raFrameItem.getElementType(), classNameSupplier);
            return AsmUtils.getArrayAsmType(elementAsmType, 1);
        } else {
            throw new IllegalArgumentException("invalid frameItem: " + frameItem);
        }
    }
}
