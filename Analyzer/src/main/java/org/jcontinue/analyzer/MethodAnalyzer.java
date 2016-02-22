package org.jcontinue.analyzer;

import org.objectweb.asm.tree.MethodNode;


public interface MethodAnalyzer {
    AnalyzeMethodResult analyzeMethod(String ownerClassName, MethodNode method);
}
