package com.example.apicodegen.exception;

import com.example.apicodegen.parser.SpecValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(SpecValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleSpecValidation(SpecValidationException ex, Model model) {
        model.addAttribute("errorMessage", ex.getMessage());
        return "fragments/error :: error";
    }

    @ExceptionHandler(AgentException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleAgentError(AgentException ex, Model model) {
        model.addAttribute("errorMessage", "Agent error: " + ex.getMessage());
        return "fragments/error :: error";
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleGeneral(Exception ex, Model model) {
        model.addAttribute("errorMessage", "An unexpected error occurred");
        return "error/500";
    }
}
