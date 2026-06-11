package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.errorhandler;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import java.util.Locale;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.companieshouse.api.objections.model.ApiError;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String DEFAULT_RESPONSE_STATUS_MESSAGE = "Request failed";
    private static final String VALIDATION_MESSAGE = "Invalid Message";
    private static final String ERROR_CODE = "error";
    private static final String INTERNAL_SERVER_ERROR_CODE = "internal_server_error";
    private static final String INTERNAL_SERVER_ERROR_MESSAGE = "Internal Server Error";
    private static final String MISSING_REQUIRED_PARAMETER = "MISSING_REQUIRED_PARAMETER";
    private static final String EMAIL_MAX_LENGTH = "EMAIL_MAX_LENGTH";
    private static final String EMAIL_INCORRECT_FORMAT = "EMAIL_INCORRECT_FORMAT";
    private static final String INVALID_LENGTH = "INVALID_LENGTH";
    private static final String INVALID_WORKSTREAM = "INVALID_WORKSTREAM";
    private static final String INVALID_REASON = "INVALID_REASON";
    private static final String SIZE = "Size";
    private static final String EMAIL = "Email";
    private static final String PARTNER_CONTACT_EMAIL = "partnerContactEmail";
    private static final String PARTNER_CASE_REFERENCE = "partnerCaseReference";
    private static final String SUBMISSION_COMPANY_NAME = "submissionCompanyName";
    private static final String PARTNER_OBJECTION_WORKSTREAM = "partnerObjectionWorkstream";
    private static final String PARTNER_OBJECTION_REASON = "partnerObjectionReason";
    private static final String PARTNER_OBJECTION_WORKSTREAM_SNAKE = "partner_objection_workstream";
    private static final String PARTNER_OBJECTION_REASON_SNAKE = "partner_objection_reason";
    private static final String REQUIRED_BODY_MISSING = "Required request body is missing";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationExceptions(MethodArgumentNotValidException ex) {
        List<FieldError> fieldErrors = ex.getBindingResult().getFieldErrors();
        if (fieldErrors.size() > 1) {
            // Collect all error codes from all field errors
            String allErrorCodes = fieldErrors.stream()
                    .map(this::mapFieldError)
                    .distinct()
                    .reduce((a, b) -> a + ", " + b)
                    .orElse(MISSING_REQUIRED_PARAMETER);
            return badRequest(allErrorCodes, VALIDATION_MESSAGE);
        }

        if (fieldErrors.isEmpty()) {
            return badRequest(MISSING_REQUIRED_PARAMETER, VALIDATION_MESSAGE);
        }

        return badRequest(mapFieldError(fieldErrors.getFirst()), VALIDATION_MESSAGE);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableMessage(HttpMessageNotReadableException ex) {
        return badRequest(mapUnreadableMessage(ex), VALIDATION_MESSAGE);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatusException(ResponseStatusException ex) {
        HttpStatusCode statusCode = ex.getStatusCode();
        String message = ex.getReason() == null || ex.getReason().isBlank()
                ? DEFAULT_RESPONSE_STATUS_MESSAGE
                : ex.getReason();

        return ResponseEntity.status(statusCode)
                .body(new ApiError(toErrorCode(statusCode), message));
    }

    @ExceptionHandler(ErrorResponseException.class)
    public ResponseEntity<ApiError> handleErrorResponseException(ErrorResponseException ex) {
        HttpStatusCode statusCode = ex.getStatusCode();
        return ResponseEntity.status(statusCode)
                .body(new ApiError(toErrorCode(statusCode), DEFAULT_RESPONSE_STATUS_MESSAGE));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiError> handleUnexpectedException(RuntimeException ex) {
        return new ResponseEntity<>(
                new ApiError(INTERNAL_SERVER_ERROR_CODE, INTERNAL_SERVER_ERROR_MESSAGE),
                HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private String toErrorCode(HttpStatusCode statusCode) {
        HttpStatus resolvedStatus = HttpStatus.resolve(statusCode.value());
        if (resolvedStatus == null) {
            return ERROR_CODE;
        }
        return resolvedStatus.name().toLowerCase(Locale.ROOT);
    }

    private String mapFieldError(FieldError fieldError) {
        String field = fieldError.getField();
        String code = fieldError.getCode();
        int rejectedLength = toStringLength(fieldError.getRejectedValue());

        if (PARTNER_CONTACT_EMAIL.equals(field)) {
            if (SIZE.equals(code) && rejectedLength > 255) {
                return EMAIL_MAX_LENGTH;
            }
            if (EMAIL.equals(code)) {
                return EMAIL_INCORRECT_FORMAT;
            }
            return MISSING_REQUIRED_PARAMETER;
        }

        if (PARTNER_CASE_REFERENCE.equals(field) || SUBMISSION_COMPANY_NAME.equals(field)) {
            if (SIZE.equals(code) && rejectedLength > 0) {
                return INVALID_LENGTH;
            }
            return MISSING_REQUIRED_PARAMETER;
        }

        if (PARTNER_OBJECTION_WORKSTREAM.equals(field)) {
            return INVALID_WORKSTREAM;
        }

        if (PARTNER_OBJECTION_REASON.equals(field)) {
            return INVALID_REASON;
        }

        return MISSING_REQUIRED_PARAMETER;
    }

    private String mapUnreadableMessage(HttpMessageNotReadableException ex) {
        String message = ex.getMessage();
        if (message != null && message.contains(REQUIRED_BODY_MISSING)) {
            return MISSING_REQUIRED_PARAMETER;
        }

        Throwable cause = ex.getMostSpecificCause();
        if (cause instanceof InvalidFormatException invalidFormatException) {
            String field = invalidFormatException.getPath().isEmpty()
                    ? null
                    : invalidFormatException.getPath().getLast().getFieldName();
            if (PARTNER_OBJECTION_REASON_SNAKE.equals(field) || PARTNER_OBJECTION_REASON.equals(field)) {
                return INVALID_REASON;
            }
            if (PARTNER_OBJECTION_WORKSTREAM_SNAKE.equals(field) || PARTNER_OBJECTION_WORKSTREAM.equals(field)) {
                return INVALID_WORKSTREAM;
            }
        }

        return MISSING_REQUIRED_PARAMETER;
    }

    private int toStringLength(Object value) {
        if (value == null) {
            return 0;
        }
        return String.valueOf(value).length();
    }

    private ResponseEntity<ApiError> badRequest(String errorCode, String message) {
        return new ResponseEntity<>(new ApiError(errorCode, message), HttpStatus.BAD_REQUEST);
    }
}
