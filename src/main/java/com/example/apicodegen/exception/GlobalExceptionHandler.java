package com.example.apicodegen.exception;

import com.example.apicodegen.parser.SpecValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNoResourceFound(NoResourceFoundException ex) {
        log.debug("Resource not found: {}", ex.getResourcePath());
        return "error/404";
    }

    @ExceptionHandler(SpecValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleSpecValidation(SpecValidationException ex, Model model) {
        log.warn("Spec validation error: {}", ex.getMessage());
        model.addAttribute("errorMessage", ex.getMessage());
        return "fragments/error :: error";
    }

    @ExceptionHandler(AgentException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleAgentError(AgentException ex, Model model) {
        log.error("Agent error: {}", ex.getMessage(), ex);
        model.addAttribute("errorMessage", "Agent error: " + ex.getMessage());
        return "fragments/error :: error";
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleGeneral(Exception ex, Model model) {
        log.error("Unexpected error", ex);
        model.addAttribute("errorMessage", "An unexpected error occurred");
        return "error/500";
    }
}
