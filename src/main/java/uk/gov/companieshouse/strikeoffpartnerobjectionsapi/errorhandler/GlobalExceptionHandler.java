package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.errorhandler;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import uk.gov.companieshouse.api.objections.model.ApiError;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationExceptions(MethodArgumentNotValidException ex) {
        return new ResponseEntity<>(
                new ApiError("bad_request", "Invalid request: missing or invalid required fields"),
                HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableMessage(HttpMessageNotReadableException ex) {
        return new ResponseEntity<>(
                new ApiError("bad_request", "Invalid request: missing or invalid required fields"),
                HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiError> handleRuntimeException(RuntimeException ex) {
        return new ResponseEntity<>(
                new ApiError("internal_server_error", "Internal Server Error"),
                HttpStatus.INTERNAL_SERVER_ERROR);
    }
}




