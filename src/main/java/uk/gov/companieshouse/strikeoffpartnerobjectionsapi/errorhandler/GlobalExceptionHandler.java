package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.errorhandler;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import java.util.Comparator;
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
    private static final String MAX_LENGTH_EXCEEDED = "MAX_LENGTH_EXCEEDED";
    private static final String INVALID_WORKSTREAM = "INVALID_WORKSTREAM";
    private static final String MISSING_WORKSTREAM = "MISSING_WORKSTREAM";
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
    private static final List<String> ERROR_PRIORITY_ORDER = List.of(
            MISSING_REQUIRED_PARAMETER,
            EMAIL_INCORRECT_FORMAT,
            EMAIL_MAX_LENGTH,
            MAX_LENGTH_EXCEEDED,
            INVALID_REASON,
            INVALID_WORKSTREAM,
            MISSING_WORKSTREAM);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationExceptions(MethodArgumentNotValidException ex) {
        List<FieldError> fieldErrors = ex.getBindingResult().getFieldErrors();
        if (fieldErrors.isEmpty()) {
            return badRequest(MISSING_REQUIRED_PARAMETER, VALIDATION_MESSAGE);
        }

        String allErrorCodes = fieldErrors.stream()
                .map(this::mapFieldError)
                .distinct()
                .sorted(Comparator.comparingInt(this::errorPriorityIndex))
                .reduce((a, b) -> a + ", " + b)
                .orElse(MISSING_REQUIRED_PARAMETER);
        return badRequest(allErrorCodes, VALIDATION_MESSAGE);
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
        return switch (fieldError.getField()) {
            case PARTNER_CONTACT_EMAIL -> mapEmailFieldError(fieldError);
            case PARTNER_CASE_REFERENCE, SUBMISSION_COMPANY_NAME -> mapLengthFieldError(fieldError);
            case PARTNER_OBJECTION_WORKSTREAM -> mapWorkstreamFieldError(fieldError);
            case PARTNER_OBJECTION_REASON -> INVALID_REASON;
            default -> MISSING_REQUIRED_PARAMETER;
        };
    }

    private String mapUnreadableMessage(HttpMessageNotReadableException ex) {
        String message = ex.getMessage();
        if (isRequiredBodyMissing(message)) {
            return MISSING_REQUIRED_PARAMETER;
        }

        String fromInvalidFormat = mapInvalidFormatCause(ex.getMostSpecificCause());
        if (fromInvalidFormat != null) {
            return fromInvalidFormat;
        }

        return mapUnreadableMessageText(message);
    }

    private String mapEmailFieldError(FieldError fieldError) {
        if (isBlank(fieldError.getRejectedValue())) {
            return MISSING_REQUIRED_PARAMETER;
        }

        String code = fieldError.getCode();
        int rejectedLength = toStringLength(fieldError.getRejectedValue());
        if (SIZE.equals(code) && rejectedLength > 255) {
            return EMAIL_MAX_LENGTH;
        }
        if (EMAIL.equals(code)) {
            return EMAIL_INCORRECT_FORMAT;
        }
        return MISSING_REQUIRED_PARAMETER;
    }

    private String mapLengthFieldError(FieldError fieldError) {
        if (SIZE.equals(fieldError.getCode()) && toStringLength(fieldError.getRejectedValue()) > 0) {
            return MAX_LENGTH_EXCEEDED;
        }
        return MISSING_REQUIRED_PARAMETER;
    }

    private String mapWorkstreamFieldError(FieldError fieldError) {
        if (toStringLength(fieldError.getRejectedValue()) == 0) {
            return MISSING_WORKSTREAM;
        }
        return INVALID_WORKSTREAM;
    }

    private boolean isRequiredBodyMissing(String message) {
        return message != null && message.contains(REQUIRED_BODY_MISSING);
    }

    private String mapInvalidFormatCause(Throwable cause) {
        if (!(cause instanceof InvalidFormatException invalidFormatException)) {
            return null;
        }

        String field = invalidFormatException.getPath().isEmpty()
                ? null
                : invalidFormatException.getPath().getLast().getFieldName();
        if (isReasonField(field)) {
            return INVALID_REASON;
        }
        if (isWorkstreamField(field)) {
            return isBlank(invalidFormatException.getValue()) ? MISSING_WORKSTREAM : INVALID_WORKSTREAM;
        }
        return null;
    }

    private String mapUnreadableMessageText(String message) {
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);
        if (isReasonFieldInText(normalized)) {
            return INVALID_REASON;
        }
        if (isWorkstreamFieldInText(normalized)) {
            return isBlankWorkstreamText(normalized) ? MISSING_WORKSTREAM : INVALID_WORKSTREAM;
        }
        return MISSING_REQUIRED_PARAMETER;
    }

    private boolean isReasonField(String field) {
        return PARTNER_OBJECTION_REASON_SNAKE.equals(field) || PARTNER_OBJECTION_REASON.equals(field);
    }

    private boolean isWorkstreamField(String field) {
        return PARTNER_OBJECTION_WORKSTREAM_SNAKE.equals(field) || PARTNER_OBJECTION_WORKSTREAM.equals(field);
    }

    private boolean isReasonFieldInText(String normalizedMessage) {
        return normalizedMessage.contains(PARTNER_OBJECTION_REASON_SNAKE)
                || normalizedMessage.contains(PARTNER_OBJECTION_REASON.toLowerCase(Locale.ROOT));
    }

    private boolean isWorkstreamFieldInText(String normalizedMessage) {
        return normalizedMessage.contains(PARTNER_OBJECTION_WORKSTREAM_SNAKE)
                || normalizedMessage.contains(PARTNER_OBJECTION_WORKSTREAM.toLowerCase(Locale.ROOT));
    }

    private boolean isBlankWorkstreamText(String normalizedMessage) {
        return normalizedMessage.contains("from string \"\"")
                || normalizedMessage.contains("empty string")
                || normalizedMessage.contains("(\"\")");
    }

    private int errorPriorityIndex(String errorCode) {
        int index = ERROR_PRIORITY_ORDER.indexOf(errorCode);
        return index < 0 ? Integer.MAX_VALUE : index;
    }

    private boolean isBlank(Object value) {
        return value == null || String.valueOf(value).trim().isEmpty();
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
