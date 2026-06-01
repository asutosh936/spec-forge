package com.example.apicodegen.parser;

public class SpecValidationException extends RuntimeException {

    public SpecValidationException(String message) {
        super(message);
    }

    public SpecValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
