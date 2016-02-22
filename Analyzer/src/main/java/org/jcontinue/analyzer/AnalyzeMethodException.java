package org.jcontinue.analyzer;

public class AnalyzeMethodException extends RuntimeException {
    public AnalyzeMethodException() {
    }

    public AnalyzeMethodException(String message) {
        super(message);
    }

    public AnalyzeMethodException(String message, Throwable cause) {
        super(message, cause);
    }

    public AnalyzeMethodException(Throwable cause) {
        super(cause);
    }

    public AnalyzeMethodException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
