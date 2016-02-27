package org.jcontinue.continuation;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Continuation {

    public static class Context {
        public boolean isFinished() {
            return finished;
        }

        public boolean isSucceed() {
            if (!finished) {
                throw new IllegalStateException("Continuation.Context task is not finished");
            }
            return succeed;
        }

        public Throwable getException() {
            if (!finished) {
                throw new IllegalStateException("Continuation.Context task is not finished");
            }
            if (succeed) {
                throw new IllegalStateException("Continuation.Context has finished successfully");
            }
            return exception;
        }

        // private methods

        private final boolean finished;
        private final boolean succeed;
        private final Throwable exception;
        private final List<__SavedFrameContext> frames;
        private final Task task;
        private final Map<Local<?>, Object> locals;

        private Context(Map<Local<?>, Object> locals) {
            finished = true;
            succeed = true;
            exception = null;
            frames = null;
            task = null;
            this.locals = locals;
        }

        private Context(Throwable exception, Map<Local<?>, Object> locals) {
            Objects.requireNonNull(exception);
            finished = true;
            succeed = false;
            this.exception = exception;
            frames = null;
            task = null;
            this.locals = locals;
        }

        private Context(List<__SavedFrameContext> frames, Task task, Map<Local<?>, Object> locals) {
            Objects.requireNonNull(frames);
            Objects.requireNonNull(task);
            finished = false;
            succeed = false;
            exception = null;
            this.frames = frames;
            this.task = task;
            this.locals = locals;
        }

        public <T> T get(Local<T> local) {
            return (T) locals.get(local);
        }

        public <T> void set(Local<? super T> local, T value) {
            locals.put(local, value);
        }
    }

    public static class Local<T> {
        public T get() {
            ThreadContext threadContext = getThreadContext();
            if (threadContext == null) {
                throw new IllegalStateException("Continuation.Local is used out of Continuation context");
            }
            return (T) threadContext.locals.get(this);
        }

        public void set(T value) {
            ThreadContext threadContext = getThreadContext();
            if (threadContext == null) {
                throw new IllegalStateException("Continuation.Local is used out of Continuation context");
            }
            threadContext.locals.put(this, value);
        }
    }

    public static Context perform(Task task) {
        ThreadContext threadContext = new ThreadContext();
        threadContext.status = ThreadContextStatus.RUNNING;
        threadContext.locals = new HashMap<>();
        return perform(threadContext, task);
    }

    public static void suspend() {
        ThreadContext threadContext = getThreadContext();
        if (threadContext == null) {
            throw new ContinuationException("Continuation.suspend() has been called out of continuation context");
        }
        if (threadContext.status == ThreadContextStatus.RUNNING) {
            threadContext.status = ThreadContextStatus.SUSPENDING;
            threadContext.savedFrameContexts = new ArrayList<>();
            threadContext.nextCalledObject = null;
        } else if (threadContext.status == ThreadContextStatus.RESUMING) {
            threadContext.status = ThreadContextStatus.RUNNING;
            threadContext.savedFrameContexts = null;
        } else {
            throw new ContinuationException("invalid threadContext.status " + threadContext.status);
        }
    }

    public static Context resume(Context context) {
        if (context.finished) {
            throw new ContinuationException("trying to continue already finished Continuation.Context");
        }
        ThreadContext threadContext = new ThreadContext();
        threadContext.savedFrameContexts = new ArrayList<>(context.frames);
        threadContext.status = ThreadContextStatus.RESUMING;
        threadContext.locals = new HashMap<>(context.locals);
        return perform(threadContext, context.task);
    }

    // api methods

    public static int __startingMethod() {
        ThreadContext threadContext = getThreadContext();
        if (threadContext == null) {
            return 0;
        }
        if (threadContext.status == ThreadContextStatus.RUNNING) {
            return 0;
        }
        if (threadContext.status == ThreadContextStatus.RESUMING) {
            int lastSavedFrameIndex = threadContext.savedFrameContexts.size() - 1;
            __SavedFrameContext savedFrameContext = threadContext.savedFrameContexts.get(lastSavedFrameIndex);
            return savedFrameContext.pointcut;
        }
        throw new ContinuationException("invalid threadContext.status: " + threadContext.status);
    }

    public static __SavedFrameContext __getSavedFrameContext() {
        ThreadContext threadContext = getThreadContext();
        if (threadContext.status != ThreadContextStatus.RESUMING) {
            throw new ContinuationException("invalid threadContext.status: " + threadContext.status);
        }
        int lastSavedFrameIndex = threadContext.savedFrameContexts.size() - 1;
        __SavedFrameContext savedFrameContext = threadContext.savedFrameContexts.get(lastSavedFrameIndex);
        threadContext.savedFrameContexts.remove(lastSavedFrameIndex);
        return savedFrameContext;
    }

    public static boolean __finishedMethod() {
        ThreadContext threadContext = getThreadContext();
        if (threadContext == null) {
            return false;
        }
        if (threadContext.status == ThreadContextStatus.RUNNING) {
            return false;
        }
        if (threadContext.status == ThreadContextStatus.SUSPENDING) {
            return true;
        }
        throw new ContinuationException("invalid threadContext.status: " + threadContext.status);
    }

    public static void __addSavedFrameContext(__SavedFrameContext savedFrameContext, String nextCalledObjectFieldName,
            Object currentCalledObject) {
        ThreadContext threadContext = getThreadContext();
        if (threadContext.status != ThreadContextStatus.SUSPENDING) {
            throw new ContinuationException("invalid threadContext.status: " + threadContext.status);
        }
        if (threadContext.nextCalledObject == null) {
            throw new ContinuationException("nextCalledObject cannot be null");
        }
        try {
            Field nexCalledObjectField = savedFrameContext.getClass().getField(nextCalledObjectFieldName);
            nexCalledObjectField.set(savedFrameContext, threadContext.nextCalledObject);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            throw new ContinuationException(e);
        }
        threadContext.nextCalledObject = currentCalledObject;
        threadContext.savedFrameContexts.add(savedFrameContext);
    }

    public static void __addSavedFrameContext(__SavedFrameContext savedFrameContext, Object currentCalledObject) {
        ThreadContext threadContext = getThreadContext();
        if (threadContext.status != ThreadContextStatus.SUSPENDING) {
            throw new ContinuationException("invalid threadContext.status: " + threadContext.status);
        }
        if (threadContext.nextCalledObject != null) {
            throw new ContinuationException("invalid threadContext.nextCalledObject: " + threadContext.nextCalledObject);
        }
        threadContext.nextCalledObject = currentCalledObject;
        threadContext.savedFrameContexts.add(savedFrameContext);
    }

    public static Object __transformedReflectionMethodInvocation(Method method, Object owner, Object[] args)
            throws InvocationTargetException, IllegalAccessException {
        int pointcutNumber = __startingMethod();
        if (pointcutNumber == 1) {
            __TransformedReflectionMethodSavedContext savedFrameContext =
                    (__TransformedReflectionMethodSavedContext) __getSavedFrameContext();
            method = savedFrameContext.method;
            owner = savedFrameContext.owner;
            args = savedFrameContext.args;
        }
        Object result = method.invoke(owner, args);
        if (__finishedMethod()) {
            __TransformedReflectionMethodSavedContext savedContext = new __TransformedReflectionMethodSavedContext();
            savedContext.method = method;
            savedContext.args = args;
            savedContext.pointcut = 1;
            __addSavedFrameContext(savedContext, "owner", null);
        }
        return result;
    }

    public static class __TransformedReflectionMethodSavedContext extends __SavedFrameContext {
        public Method method;
        public Object owner;
        public Object[] args;
    }

    // private methods

    private static ThreadLocal<List<ThreadContext>> threadContextStack = new ThreadLocal<List<ThreadContext>>() {
        @Override
        protected List<ThreadContext> initialValue() {
            return new ArrayList<>();
        }
    };

    private static class ThreadContext {
        private ThreadContextStatus status;
        private List<__SavedFrameContext> savedFrameContexts;
        private Object nextCalledObject;
        private Map<Local<?>, Object> locals;
    }

    private enum ThreadContextStatus {
        RUNNING, SUSPENDING, RESUMING;
    }

    private static Context perform(ThreadContext threadContext, Task task) {
        List<ThreadContext> threadContextStack = Continuation.threadContextStack.get();
        int lastItemIndex = threadContextStack.size();
        threadContextStack.add(threadContext);
        try {
            task.perform();
        } catch (Throwable exception) {
            return new Context(exception, threadContext.locals);
        } finally {
            threadContextStack.remove(lastItemIndex);
        }
        if (threadContext.status == ThreadContextStatus.RUNNING) {
            return new Context(threadContext.locals);
        }
        if (threadContext.status == ThreadContextStatus.SUSPENDING) {
            return new Context(threadContext.savedFrameContexts, task, threadContext.locals);
        }
        throw new ContinuationException("invalid threadContext.status " + threadContext.status);
    }

    private static ThreadContext getThreadContext() {
        List<ThreadContext> threadContextStack = Continuation.threadContextStack.get();
        if (threadContextStack.isEmpty()) {
            return null;
        }
        int lastItemIndex = threadContextStack.size() - 1;
        return threadContextStack.get(lastItemIndex);
    }
}

