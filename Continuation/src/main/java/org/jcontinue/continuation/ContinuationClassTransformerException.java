package org.jcontinue.continuation;


public class ContinuationClassTransformerException extends Exception {
    public ContinuationClassTransformerException() {
    }

    public ContinuationClassTransformerException(String message) {
        super(message);
    }

    public ContinuationClassTransformerException(String message, Throwable cause) {
        super(message, cause);
    }

    public ContinuationClassTransformerException(Throwable cause) {
        super(cause);
    }

    public ContinuationClassTransformerException(String message, Throwable cause, boolean enableSuppression,
            boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
