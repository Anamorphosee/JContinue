package org.jcontinue.analyzer;

import org.jcontinue.base.ClassBodyResolver;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class SimpleObjectFrameItemFactory implements ObjectFrameItemFactory, ObjectFrameItemClassNameSupplier {

    private final ClassBodyResolver classBodyResolver;
    private final Map<String, CustomObjectFrameItem> items = new ConcurrentHashMap<>();
    private final CustomObjectFrameItem objectFrameItem;

    {
        String objectClassName = Object.class.getName();
        objectFrameItem = new CustomObjectFrameItem(objectClassName, null);
        items.put(objectClassName, objectFrameItem);
    }

    public SimpleObjectFrameItemFactory(ClassBodyResolver classBodyResolver) {
        this.classBodyResolver = classBodyResolver;
    }

    @Override
    public CustomObjectFrameItem getObjectFrameItem(String className) {
        CustomObjectFrameItem result = items.get(className);
        if (result == null) {
            byte[] classBody = classBodyResolver.getClassBody(className);
            ClassReader reader = new ClassReader(classBody);
            if ((reader.getAccess() & Opcodes.ACC_INTERFACE) != 0) {
                result = objectFrameItem;
            } else {
                String superClassName = reader.getSuperName().replace('/', '.');
                result = new CustomObjectFrameItem(className, superClassName);
            }
            items.put(className, result);
        }
        return result;
    }

    @Override
    public String getClassName(ObjectFrameItem item) {
        return ((CustomObjectFrameItem) item).className;
    }

    // private methods

    private class CustomObjectFrameItem implements ObjectFrameItem {

        private final String className, parentClassName;

        private CustomObjectFrameItem(String className, String parentClassName) {
            this.className = className;
            this.parentClassName = parentClassName;
        }

        @Override
        public CustomObjectFrameItem getCommonSuperClass(ObjectFrameItem other) {
            List<String> relationChain = getRelationChain(this);
            CustomObjectFrameItem result = (CustomObjectFrameItem) other;
            while (!relationChain.contains(result.className)) {
                result = getObjectFrameItem(result.parentClassName);
            }
            return result;
        }

        @Override
        public boolean isSubClass(ObjectFrameItem superClass) {
            List<String> relationChain = getRelationChain(this);
            return relationChain.contains(((CustomObjectFrameItem) superClass).className);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CustomObjectFrameItem that = (CustomObjectFrameItem) o;
            return Objects.equals(className, that.className);
        }

        @Override
        public int hashCode() {
            return Objects.hash(className);
        }

        @Override
        public String toString() {
            return className;
        }
    }

    private List<String> getRelationChain(CustomObjectFrameItem lastItem) {
        List<String> result = new LinkedList<>();
        result.add(lastItem.className);
        while (lastItem.parentClassName != null) {
            result.add(0, lastItem.parentClassName);
            lastItem = getObjectFrameItem(lastItem.parentClassName);
        }
        return result;
    }
}
