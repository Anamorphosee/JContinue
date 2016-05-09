package org.jcontinue.utils;

import com.google.common.base.Throwables;
import org.jcontinue.continuation.Continuation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class ContinuableThreadPoolExecutor extends ThreadPoolExecutor {
    public ContinuableThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit,
            BlockingQueue<Runnable> workQueue) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
    }

    public ContinuableThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit,
            BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
    }

    public ContinuableThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit,
            BlockingQueue<Runnable> workQueue, RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, handler);
    }

    public ContinuableThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit,
            BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory, RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
    }

    public static final Continuation.Local<ContinuableThreadPoolExecutor> currentExecutor = new Continuation.Local<>();

    public static final Continuation.Local<Consumer<? super Continuation.Context>> postSuspendAction =
            new Continuation.Local<>();

    private static final Logger log = LoggerFactory.getLogger(ContinuableThreadPoolExecutor.class);

    @Override
    public void execute(Runnable command) {
        super.execute(() -> {
            Continuation.Context context = Continuation.perform(command::run);
            if (context.isFinished()) {
                if (!context.isSucceed()) {
                    throw Throwables.propagate(context.getException());
                }
            } else {
                context.set(currentExecutor, ContinuableThreadPoolExecutor.this);
                Consumer<? super Continuation.Context> postSuspendAction =
                        context.get(ContinuableThreadPoolExecutor.postSuspendAction);
                if (postSuspendAction != null) {
                    postSuspendAction.accept(context);
                    context.set(ContinuableThreadPoolExecutor.postSuspendAction, null);
                } else {
                    log.warn("post suspend action is null for context {}", context);
                }
            }
        });
    }

    @Override
    protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
        return super.newTaskFor(runnable, value);
    }

    @Override
    protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
        return super.newTaskFor(callable);
    }
}
