package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.dto.ErrorResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {
        return new ResponseEntity<>(
                new ErrorResponse("Invalid request: missing or invalid required fields"),
                HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException ex) {
        return new ResponseEntity<>(
                new ErrorResponse("Internal Server Error"),
                HttpStatus.INTERNAL_SERVER_ERROR);
    }
}





