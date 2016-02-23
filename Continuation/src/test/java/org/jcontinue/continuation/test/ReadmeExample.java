package org.jcontinue.continuation.test;

import org.jcontinue.continuation.Continuation;
import org.jcontinue.continuation.ContinuationClassTransformerClassLoader;
import org.jcontinue.continuation.Task;

public class ReadmeExample {

    public static void main(String[] args) throws Exception {
        ClassLoader continuationClassLoader = new ContinuationClassTransformerClassLoader();

        // transform ReadmeExample class for continuation
        Class<?> transformedReadmeExample = continuationClassLoader.loadClass(ReadmeExample.class.getName());
        Object transformedReadmeExampleInstance = transformedReadmeExample.newInstance();

        // invoke 'performExample' method on transformed class
        transformedReadmeExample.getMethod("performExample").invoke(transformedReadmeExampleInstance);
    }

    public void performExample() {
        ExampleTask task = new ExampleTask();
        Continuation.Context context = Continuation.perform(task);
        while (!context.isFinished()) {
            System.out.println("counter: " + task.counter);
            context = Continuation.resume(context);
        }
    }

    public static class ExampleTask implements Task {

        public int counter;

        @Override
        public void perform() {
            for (int i = 0; i < 10; i++) {
                setCounterAndSuspend(i);
            }
        }

        private void setCounterAndSuspend(int counter) {
            this.counter = counter;
            Continuation.suspend();
        }
    }
}
