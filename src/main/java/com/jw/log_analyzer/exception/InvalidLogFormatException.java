package com.jw.log_analyzer.exception;

public class InvalidLogFormatException extends RuntimeException {

    public InvalidLogFormatException(String message) {
        super(message);
    }

    public InvalidLogFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
