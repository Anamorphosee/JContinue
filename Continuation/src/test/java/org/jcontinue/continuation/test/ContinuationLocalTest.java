package org.jcontinue.continuation.test;

import org.jcontinue.continuation.Continuation;
import org.jcontinue.continuation.ContinuationClassTransformerClassLoader;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;

public class ContinuationLocalTest {
    private ClassLoader continuationClassLoader;



    @Before
    public void setUp() {
        continuationClassLoader = new ContinuationClassTransformerClassLoader();
    }

    @Test
    public void test1() throws ClassNotFoundException, IllegalAccessException, InstantiationException,
            NoSuchMethodException, InvocationTargetException {
        Class<?> testClass = continuationClassLoader.loadClass(Test1.class.getName());
        Object testInst = testClass.newInstance();
        testClass.getMethod("run").invoke(testInst);
    }

    public static class Test1 {

        private final Continuation.Local<String> local = new Continuation.Local<>();

        private static final Logger log = LoggerFactory.getLogger(Test1.class);

        public void run() {

            Continuation.Context context = Continuation.perform(() -> {
                log.debug("started continuation method");

                Assert.assertNull(local.get());

                log.debug("setting local to val1");
                local.set("val1");
                Assert.assertEquals(local.get(), "val1");

                log.debug("suspending");
                Continuation.suspend();

                log.debug("resumed");
                Assert.assertEquals(local.get(), "val2");

                log.debug("setting local to val3");
                local.set("val3");
                Assert.assertEquals(local.get(), "val3");
            });

            Assert.assertFalse(context.isFinished());
            Assert.assertEquals(context.get(local), "val1");

            log.debug("setting local to val2");
            context.set(local, "val2");
            Assert.assertEquals(context.get(local), "val2");

            log.debug("resuming");
            context = Continuation.resume(context);

            log.debug("finished");
            Assert.assertTrue(context.isFinished());
            Assert.assertTrue(context.isSucceed());
            Assert.assertEquals(context.get(local), "val3");
        }
    }
}
