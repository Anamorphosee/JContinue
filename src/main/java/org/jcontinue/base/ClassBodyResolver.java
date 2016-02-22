package org.jcontinue.base;


@FunctionalInterface
public interface ClassBodyResolver {
    byte[] getClassBody(String className);
}
