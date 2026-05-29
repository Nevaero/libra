package io.libra.api.controller;

import io.libra.api.dto.ApiError;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.NoSuchElementException;

// Maps the exceptions the module ports throw to HTTP status codes, so controllers stay thin.
// NoSuchElement -> 404, IllegalArgument -> 400 (bad input), IllegalState -> 409 (illegal transition).
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError notFound(NoSuchElementException e) {
        return new ApiError(e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError badRequest(IllegalArgumentException e) {
        return new ApiError(e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError conflict(IllegalStateException e) {
        return new ApiError(e.getMessage());
    }
}
