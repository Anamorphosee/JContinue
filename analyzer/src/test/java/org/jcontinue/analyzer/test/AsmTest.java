package org.jcontinue.analyzer.test;

import org.junit.Assert;
import org.junit.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Objects;

/**
 * Created by mylenium on 30.08.15.
 */
public class AsmTest {

    private static final Logger log = LoggerFactory.getLogger(AsmTest.class);

    public static class Temp {
        public void method() {
            int[] hui = null;
            hui[4] = 3;
        }
    }

    @Test
    public void test() throws NoSuchMethodException, IOException {
        TestUtils.printMethod(Temp.class.getMethod("method"));
    }

    @Test(expected = InvocationTargetException.class)
    public void testAastore() throws NoSuchMethodException, IOException, InvocationTargetException, IllegalAccessException {
        Type testClassType = Type.getObjectType("org/mylenium/common/asm/test/<testclass>");

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(
                Opcodes.V1_8,
                Opcodes.ACC_PUBLIC,
                testClassType.getInternalName(),
                null, // signature
                Type.getType(Object.class).getInternalName(),
                null // interfaces
        );
        Method testMethod = Method.getMethod("void test ()");
        GeneratorAdapter method = new GeneratorAdapter(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                testMethod,
                null, null, // signature, exceptions
                writer
        );

        method.push(1);
        method.newArray(Type.getType(int[].class));
        method.push(2);
        method.push(10);
        method.newArray(Type.getType(Boolean.class));
        method.arrayStore(Type.getType(Object.class));
        method.returnValue();

        method.endMethod();
        writer.visitEnd();

        byte[] testClassBody = writer.toByteArray();
        Class<?> testClass = TestUtils.loadClass(testClassBody);
        testClass.getMethod(testMethod.getName()).invoke(null);
        Assert.fail();
    }

    @Test
    public void testStack() throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, UnsupportedEncodingException {
        Type testClassType = Type.getObjectType("org/mylenium/common/asm/test/<TestStackClass>");
        Type objectClassType = Type.getType(Object.class);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        writer.visit(
                Opcodes.V1_8,
                Opcodes.ACC_PUBLIC,
                testClassType.getInternalName(),
                null, // signature
                objectClassType.getInternalName(),
                null // interfaces
        );
        Method testMethod = Method.getMethod("void test (int)");
        GeneratorAdapter method = new GeneratorAdapter(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                testMethod,
                null, null, // signature, exceptions
                writer
        );
        method.loadArg(0);
        Label label = method.newLabel();
        method.ifZCmp(GeneratorAdapter.EQ, label);
        method.push(Long.MAX_VALUE);
        Label commonLabel = method.newLabel();
        method.goTo(commonLabel);
        method.mark(label);
        method.push(Integer.MAX_VALUE);
        method.push(Integer.MAX_VALUE);
        method.mark(commonLabel);
        method.pop2();
        method.returnValue();
        //method.endMethod();
        method.visitMaxs(3, 1);
        method.visitEnd();
        writer.visitEnd();
        byte[] testClassBody = writer.toByteArray();
        TestUtils.printMethod(testClassBody, testMethod);
        Class<?> testClass = TestUtils.loadClass(testClassBody);
        testClass.getMethod(testMethod.getName(), int.class).invoke(null, 0);
    }

    @Test(expected = VerifyError.class)
    public void testObjectUninitializedObjectMerge() throws UnsupportedEncodingException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        Type testClassType = Type.getObjectType("org/mylenium/common/asm/test/<TestObjectUninitializedObjectMerge>");
        Type objectClassType = Type.getType(Object.class);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(
                Opcodes.V1_8,
                Opcodes.ACC_PUBLIC,
                testClassType.getInternalName(),
                null, // signature
                objectClassType.getInternalName(),
                null // interfaces
        );
        Method testMethod = Method.getMethod("void test (Object)");
        GeneratorAdapter method = new GeneratorAdapter(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                testMethod,
                null, null, // signature, exceptions
                writer
        );
        method.loadArg(0);
        Label nullLabel = method.newLabel();
        method.ifNull(nullLabel);
        method.loadArg(0);
        Label mergeLabel = method.newLabel();
        method.goTo(mergeLabel);
        method.mark(nullLabel);
        method.newInstance(objectClassType);
        method.mark(mergeLabel);
        method.storeLocal(1, objectClassType);
        method.returnValue();
        method.endMethod();
        writer.visitEnd();
        byte[] testClassBody = writer.toByteArray();
        TestUtils.printMethod(testClassBody, testMethod);
        Class<?> testClass = TestUtils.loadClass(testClassBody);
        testClass.getMethod(testMethod.getName(), Object.class);
        Assert.fail();
    }

    @Test(expected = VerifyError.class)
    public void testMergeFramesWithDifferentStackSizes() throws UnsupportedEncodingException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Type testClassType = Type.getObjectType("org/mylenium/common/asm/test/<TestMergeFramesWithDifferentStackSizes>");
        Type objectClassType = Type.getType(Object.class);
        ClassWriter writer = new ClassWriter(0);
        writer.visit(
                Opcodes.V1_5,
                Opcodes.ACC_PUBLIC,
                testClassType.getInternalName(),
                null, // signature
                objectClassType.getInternalName(),
                null // interfaces
        );
        Method testMethod = Method.getMethod("void test (int)");
        GeneratorAdapter method = new GeneratorAdapter(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                testMethod,
                null, null, // signature, exceptions
                writer
        );
        method.loadArg(0);
        Label zeroLabel = method.newLabel();
        method.ifZCmp(GeneratorAdapter.EQ, zeroLabel);
        method.loadArg(0);
        method.loadArg(0);
        Label mergeLabel = method.newLabel();
        method.goTo(mergeLabel);
        method.mark(zeroLabel);
        method.push(100);
        method.mark(mergeLabel);
        method.returnValue();
        method.visitMaxs(2, 1);
        method.visitEnd();
        writer.visitEnd();
        byte[] testClassBody = writer.toByteArray();
        TestUtils.printMethod(testClassBody, testMethod);
        Class<?> testClass = TestUtils.loadClass(testClassBody);
        testClass.getMethod(testMethod.getName(), int.class);
        Assert.fail();
    }

    @Test
    public void testPopTop() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException,
            InstantiationException, UnsupportedEncodingException {
        Type testClassType = Type.getObjectType("org/mylenium/common/asm/test/<TestPopTop>");
        Type objectType = Type.getType(Object.class);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        writer.visit(
                Opcodes.V1_8,
                Opcodes.ACC_PUBLIC,
                testClassType.getInternalName(),
                null, // signature
                Type.getType(Object.class).getInternalName(),
                null // interfaces
        );
        Method testMethod = Method.getMethod("void test ()");
        GeneratorAdapter method = new GeneratorAdapter(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                testMethod,
                null, null, // signature, exceptions
                writer
        );
        Label mergeLabel = method.newLabel();
        Label pushFloatLabel = method.newLabel();
        method.newInstance(objectType);
        method.dup();
        method.invokeConstructor(objectType, Method.getMethod(Object.class.getConstructor()));
        method.invokeVirtual(objectType, Method.getMethod(Object.class.getMethod("hashCode")));
        method.ifZCmp(GeneratorAdapter.GE, pushFloatLabel);
        method.push(1);
        method.goTo(mergeLabel);
        method.mark(pushFloatLabel);
        method.push(1.0F);
        method.mark(mergeLabel);
        method.pop();
        method.returnValue();
        method.endMethod();
        writer.visitEnd();
        byte[] testClassBody = writer.toByteArray();
        TestUtils.printMethod(testClassBody, testMethod);
        Class<?> testClass = TestUtils.loadClass(testClassBody);
        testClass.getMethod(testMethod.getName()).invoke(null);
    }

    @Test(expected = VerifyError.class)
    public void testPop2WordFrameItem() throws UnsupportedEncodingException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        Type testClassType = Type.getObjectType("org/mylenium/common/asm/test/<TestPop2WordFrameItem>");
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        writer.visit(
                Opcodes.V1_8,
                Opcodes.ACC_PUBLIC,
                testClassType.getInternalName(),
                null, // signature
                Type.getType(Object.class).getInternalName(),
                null // interfaces
        );
        Method testMethod = Method.getMethod("void test ()");
        GeneratorAdapter method = new GeneratorAdapter(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                testMethod,
                null, null, // signature, exceptions
                writer
        );
        method.push(10L);
        method.pop();
        method.returnValue();
        method.endMethod();
        writer.visitEnd();
        byte[] testClassBody = writer.toByteArray();
        TestUtils.printMethod(testClassBody, testMethod);
        Class<?> testClass = TestUtils.loadClass(testClassBody);
        testClass.getMethod(testMethod.getName()).invoke(null);
        Assert.fail();
    }

    @Test
    public void testPop2IntTop() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException,
            InstantiationException, UnsupportedEncodingException {
        Type testClassType = Type.getObjectType("org/mylenium/common/asm/test/<TestPop2IntTop>");
        Type objectType = Type.getType(Object.class);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        writer.visit(
                Opcodes.V1_8,
                Opcodes.ACC_PUBLIC,
                testClassType.getInternalName(),
                null, // signature
                Type.getType(Object.class).getInternalName(),
                null // interfaces
        );
        Method testMethod = Method.getMethod("void test ()");
        GeneratorAdapter method = new GeneratorAdapter(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                testMethod,
                null, null, // signature, exceptions
                writer
        );
        Label mergeLabel = method.newLabel();
        Label pushFloatLabel = method.newLabel();
        method.push(2);
        method.newInstance(objectType);
        method.dup();
        method.invokeConstructor(objectType, Method.getMethod(Object.class.getConstructor()));
        method.invokeVirtual(objectType, Method.getMethod(Object.class.getMethod("hashCode")));
        method.ifZCmp(GeneratorAdapter.GE, pushFloatLabel);
        method.push(1);
        method.goTo(mergeLabel);
        method.mark(pushFloatLabel);
        method.push(1.0F);
        method.mark(mergeLabel);
        method.pop2();
        method.returnValue();
        method.endMethod();
        writer.visitEnd();
        byte[] testClassBody = writer.toByteArray();
        TestUtils.printMethod(testClassBody, testMethod);
        Class<?> testClass = TestUtils.loadClass(testClassBody);
        testClass.getMethod(testMethod.getName()).invoke(null);
    }

    @Test
    public void testPop2TopInt() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException,
            InstantiationException, UnsupportedEncodingException {
        Type testClassType = Type.getObjectType("org/mylenium/common/asm/test/<TestPop2TopInt>");
        Type objectType = Type.getType(Object.class);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        writer.visit(
                Opcodes.V1_8,
                Opcodes.ACC_PUBLIC,
                testClassType.getInternalName(),
                null, // signature
                Type.getType(Object.class).getInternalName(),
                null // interfaces
        );
        Method testMethod = Method.getMethod("void test ()");
        GeneratorAdapter method = new GeneratorAdapter(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                testMethod,
                null, null, // signature, exceptions
                writer
        );
        Label mergeLabel = method.newLabel();
        Label pushFloatLabel = method.newLabel();
        method.newInstance(objectType);
        method.dup();
        method.invokeConstructor(objectType, Method.getMethod(Object.class.getConstructor()));
        method.invokeVirtual(objectType, Method.getMethod(Object.class.getMethod("hashCode")));
        method.ifZCmp(GeneratorAdapter.GE, pushFloatLabel);
        method.push(1);
        method.goTo(mergeLabel);
        method.mark(pushFloatLabel);
        method.push(1.0F);
        method.mark(mergeLabel);
        method.push(2);
        method.pop2();
        method.returnValue();
        method.endMethod();
        writer.visitEnd();
        byte[] testClassBody = writer.toByteArray();
        TestUtils.printMethod(testClassBody, testMethod);
        Class<?> testClass = TestUtils.loadClass(testClassBody);
        testClass.getMethod(testMethod.getName()).invoke(null);
    }

    @Test
    public void testPop2TopTop() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException,
            InstantiationException, UnsupportedEncodingException {
        Type testClassType = Type.getObjectType("org/mylenium/common/asm/test/<TestPop2TopTop>");
        Type objectType = Type.getType(Object.class);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        writer.visit(
                Opcodes.V1_8,
                Opcodes.ACC_PUBLIC,
                testClassType.getInternalName(),
                null, // signature
                Type.getType(Object.class).getInternalName(),
                null // interfaces
        );
        Method testMethod = Method.getMethod("void test ()");
        GeneratorAdapter method = new GeneratorAdapter(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                testMethod,
                null, null, // signature, exceptions
                writer
        );
        Label mergeLabel = method.newLabel();
        Label pushFloatLabel = method.newLabel();
        method.newInstance(objectType);
        method.dup();
        method.invokeConstructor(objectType, Method.getMethod(Object.class.getConstructor()));
        method.invokeVirtual(objectType, Method.getMethod(Object.class.getMethod("hashCode")));
        method.ifZCmp(GeneratorAdapter.GE, pushFloatLabel);
        method.push(1);
        method.goTo(mergeLabel);
        method.mark(pushFloatLabel);
        method.push(1.0F);
        method.mark(mergeLabel);
        method.dup();
        method.pop2();
        method.returnValue();
        method.endMethod();
        writer.visitEnd();
        byte[] testClassBody = writer.toByteArray();
        TestUtils.printMethod(testClassBody, testMethod);
        Class<?> testClass = TestUtils.loadClass(testClassBody);
        testClass.getMethod(testMethod.getName()).invoke(null);
    }

    @Test(expected = VerifyError.class)
    public void testPop2LongTop() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException,
            InstantiationException, UnsupportedEncodingException {
        Type testClassType = Type.getObjectType("org/mylenium/common/asm/test/<TestPop2LongTop>");
        Type objectType = Type.getType(Object.class);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        writer.visit(
                Opcodes.V1_8,
                Opcodes.ACC_PUBLIC,
                testClassType.getInternalName(),
                null, // signature
                Type.getType(Object.class).getInternalName(),
                null // interfaces
        );
        Method testMethod = Method.getMethod("void test ()");
        GeneratorAdapter method = new GeneratorAdapter(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                testMethod,
                null, null, // signature, exceptions
                writer
        );
        Label mergeLabel = method.newLabel();
        Label pushFloatLabel = method.newLabel();
        method.push(2L);
        method.newInstance(objectType);
        method.dup();
        method.invokeConstructor(objectType, Method.getMethod(Object.class.getConstructor()));
        method.invokeVirtual(objectType, Method.getMethod(Object.class.getMethod("hashCode")));
        method.ifZCmp(GeneratorAdapter.GE, pushFloatLabel);
        method.push(1);
        method.goTo(mergeLabel);
        method.mark(pushFloatLabel);
        method.push(1.0F);
        method.mark(mergeLabel);
        method.pop2();
        method.returnValue();
        method.endMethod();
        writer.visitEnd();
        byte[] testClassBody = writer.toByteArray();
        TestUtils.printMethod(testClassBody, testMethod);
        Class<?> testClass = TestUtils.loadClass(testClassBody);
        testClass.getMethod(testMethod.getName()).invoke(null);
        Assert.fail();
    }

    @Test
    public void testDupX1() throws UnsupportedEncodingException, NoSuchMethodException, IllegalAccessException,
            InvocationTargetException {
        Type testClassType = Type.getObjectType("org/mylenium/common/asm/test/<TestDupX1>");
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        writer.visit(
                Opcodes.V1_8,
                Opcodes.ACC_PUBLIC,
                testClassType.getInternalName(),
                null, // signature
                Type.getType(Object.class).getInternalName(),
                null // interfaces
        );
        Method testMethod = Method.getMethod("void test ()");
        GeneratorAdapter method = new GeneratorAdapter(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                testMethod,
                null, null, // signature, exceptions
                writer
        );
        Label label = method.newLabel();
        method.push(1);
        method.push(1.0F);
        method.dupX1();
        method.goTo(label);
        method.mark(label);
        method.returnValue();
        method.endMethod();
        writer.visitEnd();
        byte[] testClassBody = writer.toByteArray();
        TestUtils.printMethod(testClassBody, testMethod);
        Class<?> testClass = TestUtils.loadClass(testClassBody);
        testClass.getMethod(testMethod.getName()).invoke(null);
    }

    @Test
    public void testDupX2() throws UnsupportedEncodingException, NoSuchMethodException, IllegalAccessException,
            InvocationTargetException {
        Type testClassType = Type.getObjectType("org/mylenium/common/asm/test/<TestDupX2>");
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        writer.visit(
                Opcodes.V1_8,
                Opcodes.ACC_PUBLIC,
                testClassType.getInternalName(),
                null, // signature
                Type.getType(Object.class).getInternalName(),
                null // interfaces
        );
        Method testMethod = Method.getMethod("void test ()");
        GeneratorAdapter method = new GeneratorAdapter(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                testMethod,
                null, null, // signature, exceptions
                writer
        );
        method.push(1);
        method.push(1.0F);
        method.push((String) null);
        method.dupX2();
        Label label = method.newLabel();
        method.goTo(label);
        method.mark(label);
        method.pop2();
        label = method.newLabel();
        method.goTo(label);
        method.mark(label);
        method.returnValue();
        method.endMethod();
        writer.visitEnd();
        byte[] testClassBody = writer.toByteArray();
        TestUtils.printMethod(testClassBody, testMethod);
        Class<?> testClass = TestUtils.loadClass(testClassBody);
        testClass.getMethod(testMethod.getName()).invoke(null);
    }

    @Test
    public void testDup2() throws UnsupportedEncodingException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        Type testClassType = Type.getObjectType("org/mylenium/common/asm/test/<TestDup2>");
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        writer.visit(
                Opcodes.V1_8,
                Opcodes.ACC_PUBLIC,
                testClassType.getInternalName(),
                null, // signature
                Type.getType(Object.class).getInternalName(),
                null // interfaces
        );
        Method testMethod = Method.getMethod("void test ()");
        GeneratorAdapter method = new GeneratorAdapter(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                testMethod,
                null, null, // signature, exceptions
                writer
        );
        method.push(1);
        method.push(1.0F);
        method.dup2();
        Label label = method.newLabel();
        method.goTo(label);
        method.mark(label);
        method.returnValue();
        method.endMethod();
        writer.visitEnd();
        byte[] testClassBody = writer.toByteArray();
        TestUtils.printMethod(testClassBody, testMethod);
        Class<?> testClass = TestUtils.loadClass(testClassBody);
        testClass.getMethod(testMethod.getName()).invoke(null);
    }

    @Test
    public void testDup2X1() throws UnsupportedEncodingException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        Type testClassType = Type.getObjectType("org/mylenium/common/asm/test/<TestDup2X1>");
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        writer.visit(
                Opcodes.V1_8,
                Opcodes.ACC_PUBLIC,
                testClassType.getInternalName(),
                null, // signature
                Type.getType(Object.class).getInternalName(),
                null // interfaces
        );
        Method testMethod = Method.getMethod("void test ()");
        GeneratorAdapter method = new GeneratorAdapter(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                testMethod,
                null, null, // signature, exceptions
                writer
        );
        method.push(1);
        method.push(1.0F);
        method.push((String) null);
        Label label = method.newLabel();
        method.goTo(label);
        method.mark(label);
        method.dup2X1();
        label = method.newLabel();
        method.goTo(label);
        method.mark(label);
        method.returnValue();
        method.endMethod();
        writer.visitEnd();
        byte[] testClassBody = writer.toByteArray();
        TestUtils.printMethod(testClassBody, testMethod);
        Class<?> testClass = TestUtils.loadClass(testClassBody);
        testClass.getMethod(testMethod.getName()).invoke(null);
    }

    @Test
    public void testDup2X2() throws UnsupportedEncodingException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        Type testClassType = Type.getObjectType("org/mylenium/common/asm/test/<TestDup2X2>");
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        writer.visit(
                Opcodes.V1_8,
                Opcodes.ACC_PUBLIC,
                testClassType.getInternalName(),
                null, // signature
                Type.getType(Object.class).getInternalName(),
                null // interfaces
        );
        Method testMethod = Method.getMethod("void test ()");
        GeneratorAdapter method = new GeneratorAdapter(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                testMethod,
                null, null, // signature, exceptions
                writer
        );
        method.push(1);
        method.push(1.0F);
        method.push((String) null);
        method.push("hui");
        Label label = method.newLabel();
        method.goTo(label);
        method.mark(label);
        method.dup2X2();
        label = method.newLabel();
        method.goTo(label);
        method.mark(label);
        method.returnValue();
        method.endMethod();
        writer.visitEnd();
        byte[] testClassBody = writer.toByteArray();
        TestUtils.printMethod(testClassBody, testMethod);
        Class<?> testClass = TestUtils.loadClass(testClassBody);
        testClass.getMethod(testMethod.getName()).invoke(null);
    }

    @Test
    public void testLshl() throws UnsupportedEncodingException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        Type testClassType = Type.getObjectType("org/mylenium/common/asm/test/<TestLshl>");
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        writer.visit(
                Opcodes.V1_8,
                Opcodes.ACC_PUBLIC,
                testClassType.getInternalName(),
                null, // signature
                Type.getType(Object.class).getInternalName(),
                null // interfaces
        );
        Method testMethod = Method.getMethod("void test ()");
        GeneratorAdapter method = new GeneratorAdapter(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                testMethod,
                null, null, // signature, exceptions
                writer
        );
        method.push(10L);
        method.push(1);
        Label label = method.newLabel();
        method.goTo(label);
        method.mark(label);
        method.visitInsn(Opcodes.LSHL);
        label = method.newLabel();
        method.goTo(label);
        method.mark(label);
        method.returnValue();
        method.endMethod();
        writer.visitEnd();
        byte[] testClassBody = writer.toByteArray();
        TestUtils.printMethod(testClassBody, testMethod);
        Class<?> testClass = TestUtils.loadClass(testClassBody);
        testClass.getMethod(testMethod.getName()).invoke(null);
    }

    @Test(expected = InvocationTargetException.class)
    public void testArrayLength() throws UnsupportedEncodingException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        Type testClassType = Type.getObjectType("org/mylenium/common/asm/test/<TestArrayLength>");
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        writer.visit(
                Opcodes.V1_8,
                Opcodes.ACC_PUBLIC,
                testClassType.getInternalName(),
                null, // signature
                Type.getType(Object.class).getInternalName(),
                null // interfaces
        );
        Method testMethod = Method.getMethod("void test ()");
        GeneratorAdapter method = new GeneratorAdapter(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                testMethod,
                null, null, // signature, exceptions
                writer
        );
        method.push(3);
        method.newArray(Type.INT_TYPE);
        method.arrayLength();
        method.newArray(Type.getType(Class.class));
        Label label = method.newLabel();
        method.goTo(label);
        method.mark(label);
        method.arrayLength();
        method.push((String) null);
        method.arrayLength();
        label = method.newLabel();
        method.goTo(label);
        method.mark(label);
        method.returnValue();
        method.endMethod();
        writer.visitEnd();
        byte[] testClassBody = writer.toByteArray();
        TestUtils.printMethod(testClassBody, testMethod);
        Class<?> testClass = TestUtils.loadClass(testClassBody);
        testClass.getMethod(testMethod.getName()).invoke(null);
        Assert.fail();
    }

    @Test(expected = InvocationTargetException.class)
    public void testAThrow() throws UnsupportedEncodingException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        Type testClassType = Type.getObjectType("org/mylenium/common/asm/test/<TestAThrow>");
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        writer.visit(
                Opcodes.V1_8,
                Opcodes.ACC_PUBLIC,
                testClassType.getInternalName(),
                null, // signature
                Type.getType(Object.class).getInternalName(),
                null // interfaces
        );
        Method testMethod = Method.getMethod("void test ()");
        GeneratorAdapter method = new GeneratorAdapter(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                testMethod,
                null, null, // signature, exceptions
                writer
        );
        method.push((String) null);
        method.throwException();
        method.returnValue();
        method.endMethod();
        writer.visitEnd();
        byte[] testClassBody = writer.toByteArray();
        TestUtils.printMethod(testClassBody, testMethod);
        Class<?> testClass = TestUtils.loadClass(testClassBody);
        testClass.getMethod(testMethod.getName()).invoke(null);
        Assert.fail();
    }

    @Test
    public void testMonitorEnter() throws UnsupportedEncodingException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        Type testClassType = Type.getObjectType("org/mylenium/common/asm/test/<TestAThrow>");
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        writer.visit(
                Opcodes.V1_8,
                Opcodes.ACC_PUBLIC,
                testClassType.getInternalName(),
                null, // signature
                Type.getType(Object.class).getInternalName(),
                null // interfaces
        );
        Method testMethod = Method.getMethod("void test ()");
        GeneratorAdapter method = new GeneratorAdapter(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                testMethod,
                null, null, // signature, exceptions
                writer
        );
        method.newInstance(Type.getType(Object.class));
        method.dup();
        Label label = method.newLabel();
        method.goTo(label);
        method.mark(label);
        method.monitorEnter();
        label = method.newLabel();
        method.goTo(label);
        method.mark(label);
        method.monitorExit();
        method.returnValue();
        method.endMethod();
        writer.visitEnd();
        byte[] testClassBody = writer.toByteArray();
        TestUtils.printMethod(testClassBody, testMethod);
        Class<?> testClass = TestUtils.loadClass(testClassBody);
        testClass.getMethod(testMethod.getName()).invoke(null);
    }

    public enum TestEnum {
        VAL1, VAL2, VAL3;
    }


    public static class TestJava {

        public static int intVal;
        public static Object[] objArrayVal;
        public Number numVal = 1;

        public void testJavaMonitor() {
            Integer x = null;
            synchronized (x) {
                Integer.valueOf(2);
            }
        }

        public TestJava() {}

        public TestJava(int intVal, boolean boolVal, String strVal) { }

        public char testJavaReturnChar() {
            return 'c';
        }

        public int testNot(int x) {
            return ~x;
        }

        public void testNewArray() {
            new double[3].hashCode();
        }

        public void testNewRefArray() {
            Class[] dim1 = new Class[3];
            Class[][] dim2 = new Class[2][];
        }

        public void testPutStatic() {
            intVal = 123;
            objArrayVal = new Integer[10];
        }

        public Number testGetField() {
            return ((TestJava) (new TestJava() {
                public Number numVal = 2;
            })).numVal;
        }

        public void testPutField() {
            TestJava next = new TestJava() {};
            next.numVal = 10;
        }

        public void testInvoke(int intVal, boolean boolVal, String strVal) {
            new TestJava(3, false, "str");
            testInvoke(intVal, boolVal, strVal);
            Objects.equals(this, null);
            Runnable inter = () -> {};
            inter.run();
        }

        public void testLdc() {
            Class clInt = int.class;
            Class clInteger = Integer.class;
            String str = "sdfsefsf";
        }

        public static int testSwitch(TestEnum val) {
            switch (val) {
                case VAL1:
                    return 1;
                case VAL2:
                    return 2;
                case VAL3:
                    return 3;
            }
            return 4;
        }

        public static void testMultiANewArray() {
            Object[][][] val = new String[2][3][];
            boolean[][][] boolVal = new boolean[3][4][5];
        }

    }

    @Test
    public void testJava() throws NoSuchMethodException, IOException {
        TestUtils.printMethod(TestJava.class.getMethod("testJavaMonitor"));
        TestUtils.printMethod(TestJava.class.getMethod("testJavaReturnChar"));
        TestUtils.printMethod(TestJava.class.getMethod("testNot", int.class));
        TestUtils.printMethod(TestJava.class.getMethod("testNewArray"));
        TestUtils.printMethod(TestJava.class.getMethod("testNewRefArray"));
        TestUtils.printMethod(TestJava.class.getMethod("testPutStatic"));
        TestUtils.printMethod(TestJava.class.getMethod("testGetField"));
        TestUtils.printMethod(TestJava.class.getMethod("testPutField"));
        java.lang.reflect.Method testInvokeReflectMethod =
                TestJava.class.getMethod("testInvoke", int.class, boolean.class, String.class);
        TestUtils.printMethod(testInvokeReflectMethod);
        log.info(Arrays.toString(Type.getArgumentTypes(testInvokeReflectMethod)));
        TestUtils.printMethod(TestJava.class.getMethod("testLdc"));
        TestUtils.printMethod(TestJava.class.getMethod("testSwitch", TestEnum.class));
        TestUtils.printMethod(TestJava.class.getMethod("testMultiANewArray"));
    }
}
