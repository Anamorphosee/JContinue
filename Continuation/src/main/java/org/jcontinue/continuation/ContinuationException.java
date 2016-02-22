package org.jcontinue.continuation;

public class ContinuationException extends RuntimeException {
    public ContinuationException() {
    }

    public ContinuationException(String message) {
        super(message);
    }

    public ContinuationException(String message, Throwable cause) {
        super(message, cause);
    }

    public ContinuationException(Throwable cause) {
        super(cause);
    }

    public ContinuationException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
