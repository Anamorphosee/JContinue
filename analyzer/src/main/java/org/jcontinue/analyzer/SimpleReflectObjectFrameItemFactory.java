package org.jcontinue.analyzer;

import java.util.Objects;

/**
 * Created by mylenium on 25.10.15.
 */
public class SimpleReflectObjectFrameItemFactory implements ObjectFrameItemFactory, ObjectFrameItemClassNameSupplier {

    private static class Item implements ObjectFrameItem {
        private final Class<?> value;

        private Item(Class<?> value) {
            Objects.requireNonNull(value);
            this.value = value;
        }

        @Override
        public Item getCommonSuperClass(ObjectFrameItem other) {
            Class<?> otherValue = ((Item) other).value;
            if (value.isAssignableFrom(otherValue)) {
                return this;
            }
            while (!otherValue.isAssignableFrom(value)) {
                otherValue = otherValue.getSuperclass();
            }
            return new Item(otherValue);
        }

        @Override
        public boolean isSubClass(ObjectFrameItem superClass) {
            Class<?> superClassValue = ((Item) superClass).value;
            if (superClassValue.isInterface() || superClassValue == Object.class) {
                return true;
            }
            if (value.isInterface()) {
                return false;
            }
            return superClassValue.isAssignableFrom(value);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Item item = (Item) o;
            return Objects.equals(value, item.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }

        @Override
        public String toString() {
            return value.getName();
        }
    }

    @Override
    public Item getObjectFrameItem(String className) {
        try {
            return new Item(Class.forName(className));
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getClassName(ObjectFrameItem item) {
        return ((Item) item).value.getName();
    }
}
