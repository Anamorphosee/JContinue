package org.jcontinue.base;

import com.google.common.base.Throwables;
import com.google.common.io.ByteStreams;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class ClasspathClassBodyResolver implements ClassBodyResolver {
    @Override
    public byte[] getClassBody(String className) {
        InputStream stream = ClassLoader.getSystemResourceAsStream(className.replace('.', File.separatorChar) + ".class");
        try {
            return ByteStreams.toByteArray(stream);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }
}
