package org.jcontinue.analyzer;

import java.util.List;

public interface Frame {
    List<? extends FrameItem> getLocals();
    List<? extends FrameItem> getStack();
    boolean isThisInitialized();
}
