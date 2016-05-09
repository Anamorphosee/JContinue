package org.jcontinue.utils;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

public class ContinuationAwareRunnableFuture<T> implements RunnableFuture<T> {
    private final Callable<? extends T> callable;
    private final AtomicReference<State> state = new AtomicReference<>();

    private enum State {
        INITIAL, RUNNING, CANCELLED, INTERRUPTED, DONE;
    }

    public ContinuationAwareRunnableFuture(Callable<? extends T> callable) {
        Objects.requireNonNull(callable);
        this.callable = callable;
    }

    @Override
    public void run() {
        while (true) {
            if (state.compareAndSet(State.INITIAL, State.RUNNING)) {
                //
            }
        }
    }

    private void perform() {
        try {
            T result = callable.call();
        } catch (Throwable e) {
            //
        }
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        while (true) {
            if (state.compareAndSet(State.INITIAL, State.CANCELLED)) {
                return true;
            }
            if (state.get() == State.CANCELLED || state.get() == State.DONE) {
                return false;
            }
            if (mayInterruptIfRunning && state.compareAndSet(State.RUNNING, State.INTERRUPTED)) {
                //
            }
        }
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
        return null;
    }

    @Override
    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return null;
    }

    private void await() {
        //
    }
}
