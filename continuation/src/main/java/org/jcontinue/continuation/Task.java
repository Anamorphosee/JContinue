package org.jcontinue.continuation;

@FunctionalInterface
public interface Task {
    void perform() throws Throwable;
}
