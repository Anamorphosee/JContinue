package org.jcontinue.analyzer.test;

import com.google.common.base.Throwables;
import com.google.common.io.ByteStreams;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.util.ASMifier;
import org.objectweb.asm.util.Printer;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceClassVisitor;
import org.objectweb.asm.util.TraceMethodVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;

/**
 * Created by mylenium on 08.08.15.
 */
public class TestUtils {

    private static final Logger log = LoggerFactory.getLogger(TestUtils.class);

    public static byte[] getClassBody(Class<?> clazz) throws IOException {
        return ByteStreams.toByteArray(ClassLoader.getSystemResourceAsStream(clazz.getName().replace('.', '/') + ".class"));
    }

    public static void printMethod(byte[] classBody, Method method, boolean asmifier) throws UnsupportedEncodingException {
        Printer printer;
        if (asmifier) {
            printer = new ASMifier();
        } else {
            printer = new Textifier();
        }
        ClassVisitor visitor = new ClassVisitor(Opcodes.ASM5) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                if (method.getName().equals(name) && method.getDescriptor().equals(desc)) {
                    return new TraceMethodVisitor(printer);
                }
                return null;
            }
        };
        ClassReader reader = new ClassReader(classBody);
        reader.accept(visitor, 0);
        PrintWriter printWriter = new PrintWriter(new OutputStreamWriter(System.out, "utf-8"));
        printer.print(printWriter);
        printWriter.println("-----END-----");
        printWriter.println();
        printWriter.flush();
    }

    public static void printMethod(byte[] classBody, Method method) throws UnsupportedEncodingException {
        printMethod(classBody, method, false);
    }


    public static Class<?> loadClass(ClassLoader loader, byte[] classBody) throws NoSuchMethodException,
            InvocationTargetException, IllegalAccessException {
        java.lang.reflect.Method method =
                ClassLoader.class.getDeclaredMethod("defineClass", byte[].class, int.class, int.class);
        method.setAccessible(true);
        return (Class<?>) method.invoke(loader, classBody, 0, classBody.length);
    }

    public static Class<?> loadClass(byte[] classBody) throws NoSuchMethodException, IllegalAccessException,
            InvocationTargetException {
        return loadClass(TestUtils.class.getClassLoader(), classBody);
    }

    public static ClassLoader getClearClassLoader() {
        return new ClassLoader() {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                byte[] body;
                try {
                    body = ByteStreams.toByteArray(ClassLoader.getSystemResourceAsStream(name.replace('.', '/') + ".class"));
                } catch (Exception e) {
                    throw new ClassNotFoundException(name, e);
                }
                return defineClass(name, body, 0, body.length);
            }
        };
    }

    public static void printMethod(java.lang.reflect.Method method, boolean asmifier) throws IOException {
        Class<?> ownerClass = method.getDeclaringClass();
        printMethod(getClassBody(ownerClass), Method.getMethod(method), asmifier);
    }

    public static void printMethod(Class<?> clazz, Method method, boolean asmifier) throws IOException {
        printMethod(getClassBody(clazz), method, asmifier);
    }

    public static void printMethod(java.lang.reflect.Method method) throws IOException {
        printMethod(method, false);
    }

    public static void printClass(ClassNode clazz, boolean asmifier) {
        Printer printer;
        if (asmifier) {
            printer = new ASMifier();
        } else {
            printer = new Textifier();
        }
        PrintWriter printWriter = null;
        try {
            printWriter = new PrintWriter(new OutputStreamWriter(System.out, "utf-8"));
        } catch (UnsupportedEncodingException e) {
            throw Throwables.propagate(e);
        }
        ClassVisitor traceVisitor = new TraceClassVisitor(null, printer, printWriter);
        clazz.accept(traceVisitor);
        printWriter.println("-----END-----");
        printWriter.println();
        printWriter.flush();
    }

    public static void printClass(ClassNode clazz) {
        printClass(clazz, false);
    }
}
