package org.jcontinue.analyzer.test;

import com.google.common.collect.Sets;
import org.jcontinue.analyzer.AnalyzeMethodResult;
import org.jcontinue.analyzer.BaseFrame;
import org.jcontinue.analyzer.Frame;
import org.jcontinue.analyzer.FrameItem;
import org.jcontinue.analyzer.MethodAnalyzer;
import org.jcontinue.analyzer.MethodAnalyzerUtils;
import org.jcontinue.analyzer.ObjectFrameItem;
import org.jcontinue.analyzer.ObjectFrameItemFactory;
import org.jcontinue.analyzer.SimpleReflectObjectFrameItemFactory;
import org.jcontinue.analyzer.StandardMethodAnalyzer;
import org.jcontinue.analyzer.UninitializedObjectFrameItem;
import org.jcontinue.analyzer.UninitializedThisFrameItem;
import org.jcontinue.base.AsmUtils;
import org.junit.Assert;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.SimpleVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Created by mylenium on 25.10.15.
 */
public class StandardMethodAnalyzerTest {
    private static final Logger log = LoggerFactory.getLogger(StandardMethodAnalyzerTest.class);

    private static final ObjectFrameItemFactory objectFactory = new SimpleReflectObjectFrameItemFactory();
    private static final MethodAnalyzer analyzer = new StandardMethodAnalyzer(objectFactory);

    public static class Stress {
        public void testTransformations() {
            int intVal = 10;
            byte byteVal = (byte) intVal;
            intVal = byteVal;
            short shortVal = (short) intVal;
            shortVal = byteVal;
            intVal = shortVal;
            byteVal = (byte) shortVal;
            float floatVal = intVal;
            floatVal = byteVal;
            floatVal = shortVal;
            intVal = (int) floatVal;
            byteVal = (byte) floatVal;
            shortVal = (short) -floatVal;
            long longVal = intVal;
            longVal = byteVal;
            longVal = shortVal;
            longVal = (long) floatVal;
            intVal = (int) longVal;
            byteVal = (byte) longVal;
            shortVal = (short) longVal;
            floatVal = longVal;
            double doubleVal = intVal;
            doubleVal = byteVal;
            doubleVal = shortVal;
            doubleVal = floatVal;
            doubleVal = longVal;
            intVal = (int) -doubleVal;
            byteVal = (byte) doubleVal;
            shortVal = (short) doubleVal;
            floatVal = (float) doubleVal;
            longVal = (long) doubleVal;
        }

        public static boolean isEqual(Frame frame, org.objectweb.asm.tree.analysis.Frame asmFrame) {
            BaseFrame frame2 = new BaseFrame();
            for (int i = 0; i < frame.getLocals().size(); i++) {
                FrameItem value = frame.getLocals().get(i);
                frame2.getLocals().add(getFrameItem(value));
            }
            for (int i = 0; i < frame.getStack().size(); i++) {
                FrameItem value = frame.getStack().get(i);
                frame2.getStack().add(getFrameItem(value));
            }
            BaseFrame asmFrame2 = new BaseFrame();
            for (int i = 0; i < asmFrame.getLocals();) {
                BasicValue value = (BasicValue) asmFrame.getLocal(i);
                List<? extends FrameItem> frameItems = getFrameItems(value);
                asmFrame2.getLocals().addAll(frameItems);
                i += frameItems.size();
            }
            for (int i = 0; i < asmFrame.getStackSize(); i++) {
                BasicValue value = (BasicValue) asmFrame.getStack(i);
                List<? extends FrameItem> frameItems = getFrameItems(value);
                asmFrame2.getStack().addAll(frameItems);
            }
            MethodAnalyzerUtils.normalizeLocals(asmFrame2);
            if (frame2.getLocals().size() != asmFrame2.getLocals().size() ||
                    frame2.getStack().size() != asmFrame2.getStack().size()) {
                return false;
            }
            for (int i = 0; i < frame2.getLocals().size(); i++) {
                FrameItem item1 = frame2.getLocals().get(i);
                FrameItem item2 = asmFrame2.getLocals().get(i);
                if (!isEqual(item1, item2)) {
                    return false;
                }
            }
            for (int i = 0; i < frame2.getStack().size(); i++) {
                FrameItem item1 = frame2.getStack().get(i);
                FrameItem item2 = asmFrame2.getStack().get(i);
                if (!isEqual(item1, item2)) {
                    return false;
                }
            }
            return true;
        }

        public static boolean isEqual(FrameItem item1, FrameItem item2) {
            if (item1.equals(item2)) {
                return true;
            }
            if (item1 instanceof ObjectFrameItem && item2 instanceof ObjectFrameItem) {
                ObjectFrameItem oItem1 = (ObjectFrameItem) item1;
                ObjectFrameItem oItem2 = (ObjectFrameItem) item2;
                return oItem1.isSubClass(oItem2) && oItem2.isSubClass(oItem1);
            }
            return false;
        }

        public static FrameItem getFrameItem(FrameItem item) {
            if (item instanceof UninitializedObjectFrameItem) {
                return objectFactory.getObjectFrameItem(((UninitializedObjectFrameItem) item).getClassName());
            } else if (item instanceof UninitializedThisFrameItem) {
                return objectFactory.getObjectFrameItem(((UninitializedThisFrameItem) item).getClassName());
            } else {
                return item;
            }
        }

        public static List<? extends FrameItem> getFrameItems(BasicValue value) {
            if (value == BasicValue.UNINITIALIZED_VALUE) {
                return Collections.singletonList(FrameItem.TOP);
            } else if ("Lnull;".equals(value.getType().getDescriptor())) {
                return Collections.singletonList(FrameItem.NULL);
            } else {
                return MethodAnalyzerUtils.getFrameItems(value.getType(), objectFactory);
            }
        }

        public void testArrays(int[][][] _3dIntArray, char[] charArray, String[][] _2dStringArray) {
            int[][] _2dIntArray = _3dIntArray[3];
            charArray[2] = (char) _2dIntArray[3][4];
            int[] intArray = _2dIntArray[1];
            Object merge;
            if (intArray[0] == 1) {
                merge = intArray;
            } else {
                merge = charArray;
            }
        }
    }

    @Test
    public void testStress() throws IOException, NoSuchMethodException, IllegalAccessException,
            InvocationTargetException, AnalyzerException {
        Set<Class<?>> classes = Sets.newHashSet(HashSet.class, Class.class, Math.class, StrictMath.class, Object.class,
                Arrays.class, Thread.class, Stream.class, ClassLoader.class, Stress.class, MethodAnalyzerUtils.class);
        Set<byte[]> classBodies = new HashSet<>();
        classes.stream().map(it -> {
            try {
                return TestUtils.getClassBody(it);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).forEach(classBodies::add);
        Analyzer asmAnalyzer = new Analyzer(new SimpleVerifier());
        for (byte[] classBody : classBodies) {
            ClassReader reader = new ClassReader(classBody);
            ClassNode classNode = new ClassNode(Opcodes.ASM5);
            reader.accept(classNode, 0);
            String className = Type.getObjectType(classNode.name).getClassName();
            for (MethodNode method : (List<MethodNode>) classNode.methods) {
                if (method.instructions != null && method.instructions.size() > 0) {
                    AnalyzeMethodResult analyzeMethodResult = analyzer.analyzeMethod(className, method);
                    org.objectweb.asm.tree.analysis.Frame[] asmFrames = asmAnalyzer.analyze(classNode.name, method);
                    int index = 0;
                    AbstractInsnNode instruction = method.instructions.getFirst();
                    while (instruction != null) {
                        org.objectweb.asm.tree.analysis.Frame asmFrame = asmFrames[index];
                        Frame frame = analyzeMethodResult.getFrames().get(instruction);
                        if (AsmUtils.isCodeInstruction(instruction)) {
                            if (asmFrame == null) {
                                Assert.assertNull(frame);
                            } else {
                                Assert.assertTrue(Stress.isEqual(frame, asmFrame));
                            }
                        } else {
                            Assert.assertNull(frame);
                        }
                        instruction = instruction.getNext();
                        index++;
                    }
                }
            }
        }
    }
}
