package org.jcontinue.continuation.test;

import com.google.common.base.Throwables;
import org.jcontinue.analyzer.MethodAnalyzer;
import org.jcontinue.analyzer.ObjectFrameItemClassNameSupplier;
import org.jcontinue.analyzer.ObjectFrameItemFactory;
import org.jcontinue.analyzer.SimpleObjectFrameItemFactory;
import org.jcontinue.analyzer.StandardMethodAnalyzer;
import org.jcontinue.base.ClassBodyResolver;
import org.jcontinue.base.ClasspathClassBodyResolver;
import org.jcontinue.continuation.Continuation;
import org.jcontinue.continuation.ContinuationClassTransformerClassLoader;
import org.jcontinue.continuation.ContinuationClassTransformerRegistry;
import org.jcontinue.continuation.ContinuationMethodTransformer;
import org.jcontinue.continuation.SimpleContinuationClassTransformerRegistry;
import org.jcontinue.continuation.StandardContinuationMethodTransformer;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by mylenium on 07.02.16.
 */
public class StandardContinuationMethodTransformerTest {

    private static final Logger log = LoggerFactory.getLogger(StandardContinuationMethodTransformerTest.class);

    private ClassLoader continuationClassLoader;

    @Before
    public void setUp() {
        BeanFactory beanFactory = new AnnotationConfigApplicationContext(Config.class);
        continuationClassLoader = beanFactory.getBean(ClassLoader.class);
    }

    @Test
    public void test1() throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException,
            InstantiationException, InvocationTargetException {
        Class<?> test1Class = continuationClassLoader.loadClass(Tes1.class.getName());
        Object test1Inst = test1Class.newInstance();
        test1Class.getMethod("start").invoke(test1Inst);
    }

    @Test
    public void test2() throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException,
            InstantiationException, InvocationTargetException {
        Class<?> testClass = continuationClassLoader.loadClass(Test2.class.getName());
        Object testInst = testClass.newInstance();
        testClass.getMethod("start").invoke(testInst);
    }

    public static class Tes1 {

        private final List<Integer> array = new ArrayList<>();

        private void rec(int deep) {
            if (deep == 3) {
                Continuation.suspend();
            } else {
                int index = array.size();
                array.add(null);
                for (int i = 0; i < 10; i++) {
                    array.set(index, i);
                    rec(deep + 1);
                }
                array.remove(index);
            }
        }

        public void start() throws Throwable {
            Continuation.Context context = Continuation.perform(() -> rec(0));
            for (int i = 0; i < 1000; i++) {
                Assert.assertFalse(context.isFinished());
                Assert.assertTrue(array.size() == 3);
                Assert.assertTrue(array.get(0) == i / 100);
                Assert.assertTrue(array.get(1) == (i / 10) % 10);
                Assert.assertTrue(array.get(2) == i % 10);
                log.info("test1 array: {}", array);
                context = Continuation.resume(context);
            }
            Assert.assertTrue(context.isFinished());
            Assert.assertTrue(context.isSucceed());
        }
    }

    public static class Test2 {

        byte[] rec(int deep) {
            log.info("test2 deep = {}", deep);
            if (deep == 3) {
                try {
                    return "test".getBytes("utf-8");
                } catch (UnsupportedEncodingException e) {
                    throw Throwables.propagate(e);
                }
            } else {
                try {
                    Continuation.suspend();
                    return new String(rec(deep + 1), "utf-8").getBytes();
                } catch (UnsupportedEncodingException e) {
                    throw Throwables.propagate(e);
                }
            }
        }

        public void start() {
            Continuation.Context context = Continuation.perform(() -> rec(0));
            for (int i = 0; i < 3; i++) {
                Assert.assertFalse(context.isFinished());
                context = Continuation.resume(context);
            }
            Assert.assertTrue(context.isFinished());
            Assert.assertTrue(context.isSucceed());
        }
    }


    @Configuration
    public static class Config {

        @Bean
        public ClassLoader continuationClassLoader() {
            return new ContinuationClassTransformerClassLoader(classBodyResolver(), registry(),
                    continuationMethodTransformer(), objectFrameItemFactory(), objectFrameItemClassNameSupplier());
        }

        @Bean
        public ClassBodyResolver classBodyResolver() {
            return new ClasspathClassBodyResolver();
        }

        @Bean
        public ContinuationClassTransformerRegistry registry() {
            return new SimpleContinuationClassTransformerRegistry();
        }

        @Bean
        public ContinuationMethodTransformer continuationMethodTransformer() {
            return new StandardContinuationMethodTransformer(registry(), methodAnalyzer(),
                    objectFrameItemClassNameSupplier());
        }

        @Bean
        public MethodAnalyzer methodAnalyzer() {
            return new StandardMethodAnalyzer(objectFrameItemFactory());
        }

        @Bean
        public ObjectFrameItemFactory objectFrameItemFactory() {
            return new SimpleObjectFrameItemFactory(classBodyResolver());
        }

        @Bean
        public ObjectFrameItemClassNameSupplier objectFrameItemClassNameSupplier() {
            return (ObjectFrameItemClassNameSupplier) objectFrameItemFactory();
        }

    }
}
