package org.jcontinue.continuation;

import org.jcontinue.analyzer.ObjectFrameItem;
import org.jcontinue.analyzer.ObjectFrameItemClassNameSupplier;
import org.jcontinue.analyzer.ObjectFrameItemFactory;
import org.jcontinue.analyzer.SimpleObjectFrameItemFactory;
import org.jcontinue.analyzer.StandardMethodAnalyzer;
import org.jcontinue.base.ClassBodyResolver;
import org.jcontinue.base.ClasspathClassBodyResolver;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;

public class ContinuationClassTransformerClassLoader extends ClassLoader {
    private final ClassBodyResolver classBodyResolver;
    private final ContinuationClassTransformerRegistry registry;
    private final ContinuationMethodTransformer methodTransformer;
    private final ObjectFrameItemFactory objectFactory;
    private final ObjectFrameItemClassNameSupplier classNameSupplier;

    public ContinuationClassTransformerClassLoader(ClassLoader parent, ClassBodyResolver classBodyResolver,
            ContinuationClassTransformerRegistry registry, ContinuationMethodTransformer methodTransformer,
            ObjectFrameItemFactory objectFactory, ObjectFrameItemClassNameSupplier classNameSupplier) {
        super(parent);
        this.classBodyResolver = classBodyResolver;
        this.registry = registry;
        this.methodTransformer = methodTransformer;
        this.objectFactory = objectFactory;
        this.classNameSupplier = classNameSupplier;
    }

    public ContinuationClassTransformerClassLoader(ClassBodyResolver classBodyResolver,
            ContinuationClassTransformerRegistry registry, ContinuationMethodTransformer methodTransformer,
            ObjectFrameItemFactory objectFactory, ObjectFrameItemClassNameSupplier classNameSupplier) {
        this(null, classBodyResolver, registry, methodTransformer, objectFactory, classNameSupplier);
    }

    public ContinuationClassTransformerClassLoader(ClassLoader parent, ClassBodyResolver classBodyResolver) {
        super(parent);
        SimpleObjectFrameItemFactory objectFactory = new SimpleObjectFrameItemFactory(classBodyResolver);
        StandardMethodAnalyzer methodAnalyzer = new StandardMethodAnalyzer(objectFactory);
        SimpleContinuationClassTransformerRegistry registry = new SimpleContinuationClassTransformerRegistry();
        StandardContinuationMethodTransformer methodTransformer =
                new StandardContinuationMethodTransformer(registry, methodAnalyzer, objectFactory);
        this.classBodyResolver = classBodyResolver;
        this.registry = registry;
        this.methodTransformer = methodTransformer;
        this.objectFactory = objectFactory;
        this.classNameSupplier = objectFactory;
    }

    public ContinuationClassTransformerClassLoader(ClassLoader parent) {
        this(parent, new ClasspathClassBodyResolver());
    }

    public ContinuationClassTransformerClassLoader() {
        this(null);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        byte[] classBody;
        if (methodTransformer.isAuxiliaryClass(name)) {
            classBody = methodTransformer.getAuxiliaryClassBody(name);
        } else if (!registry.doTransformClass(name)) {
            classBody = classBodyResolver.getClassBody(name);
        } else {
            byte[] originalClassBody = classBodyResolver.getClassBody(name);
            if (originalClassBody == null) {
                classBody = null;
            } else {
                ClassReader reader = new ClassReader(originalClassBody);
                ClassNode clazz = new ClassNode(Opcodes.ASM5);
                reader.accept(clazz, 0);
                for (MethodNode method : (List<MethodNode>) clazz.methods) {
                    if (registry.doTransformMethod(clazz, method)) {
                        methodTransformer.transformMethod(name, method);
                    }
                }
                ClassWriter writer = new CustomClassWriter();
                clazz.accept(writer);
                classBody = writer.toByteArray();
            }
        }
        if (classBody == null) {
            throw new ClassNotFoundException(name);
        }
        return defineClass(name, classBody, 0, classBody.length);
    }

    // private methods

    private class CustomClassWriter extends ClassWriter {

        private CustomClassWriter() {
            super(ClassWriter.COMPUTE_FRAMES);
        }

        @Override
        protected String getCommonSuperClass(String type1, String type2) {
            String className1 = type1.replace('/', '.');
            String className2 = type2.replace('/', '.');
            ObjectFrameItem object1 = objectFactory.getObjectFrameItem(className1);
            ObjectFrameItem object2 = objectFactory.getObjectFrameItem(className2);
            ObjectFrameItem commonObject = object1.getCommonSuperClass(object2);
            String commonClassName = classNameSupplier.getClassName(commonObject);
            return commonClassName.replace('.', '/');
        }
    }
}
