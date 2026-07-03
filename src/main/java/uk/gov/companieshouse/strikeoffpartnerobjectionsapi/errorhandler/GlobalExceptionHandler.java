package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.errorhandler;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.JsonMappingException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Comparator;
import java.util.Locale;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
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
import uk.gov.companieshouse.api.error.ApiErrorResponseException;
import uk.gov.companieshouse.api.objections.model.ApiError;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.CompanyValidationException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.ServiceException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String DEFAULT_RESPONSE_STATUS_MESSAGE = "Request failed";
    private static final String VALIDATION_MESSAGE = "Invalid Message";
    private static final String ERROR_CODE = "error";
    private static final String INTERNAL_SERVER_ERROR_CODE = "internal_server_error";
    private static final String INTERNAL_SERVER_ERROR_MESSAGE = "Internal Server Error";
    private static final String SERVICE_UNAVAILABLE_CODE = "service_unavailable";
    private static final String SERVICE_UNAVAILABLE_MESSAGE = "Service Unavailable";
    private static final String GATEWAY_TIMEOUT_CODE = "gateway_timeout";
    private static final String GATEWAY_TIMEOUT_MESSAGE = "Gateway Timeout";
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
    private static final List<String> BLANK_WORKSTREAM_TOKENS = List.of(
            "from string \"\"",
            "from string ''",
            "from value ''",
            "unexpected value ''",
            "unexpected value \"\"",
            "coerce empty string",
            "empty string",
            "(\"\")");
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
            return badRequest(MISSING_REQUIRED_PARAMETER);
        }

        String allErrorCodes = fieldErrors.stream()
                .map(this::mapFieldError)
                .distinct()
                .sorted(Comparator.comparingInt(this::errorPriorityIndex))
                .reduce((a, b) -> a + ", " + b)
                .orElse(MISSING_REQUIRED_PARAMETER);
        return badRequest(allErrorCodes);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableMessage(HttpMessageNotReadableException ex) {
        return badRequest(mapUnreadableMessage(ex));
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

    @SuppressWarnings("unused")
    @ExceptionHandler(SocketTimeoutException.class)
    public ResponseEntity<ApiError> handleSocketTimeoutException(SocketTimeoutException ex) {
        return new ResponseEntity<>(
                new ApiError(GATEWAY_TIMEOUT_CODE, GATEWAY_TIMEOUT_MESSAGE),
                HttpStatus.GATEWAY_TIMEOUT);
    }

    @SuppressWarnings("unused")
    @ExceptionHandler(IOException.class)
    public ResponseEntity<ApiError> handleIOException(IOException ex) {
        return new ResponseEntity<>(
                new ApiError(SERVICE_UNAVAILABLE_CODE, SERVICE_UNAVAILABLE_MESSAGE),
                HttpStatus.SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<ApiError> handleServiceException(ServiceException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof ApiErrorResponseException apiErrorResponseException) {
            HttpStatus status = HttpStatus.resolve(apiErrorResponseException.getStatusCode());
            if (status != null) {
                String upstreamMessage = apiErrorResponseException.getStatusMessage();
                String message = (upstreamMessage == null || upstreamMessage.isBlank())
                        ? DEFAULT_RESPONSE_STATUS_MESSAGE
                        : upstreamMessage;
                if (ex.getMessage() != null && !ex.getMessage().isBlank()) {
                    message = ex.getMessage() + ": " + message;
                }
                return ResponseEntity.status(status)
                        .body(new ApiError(toErrorCode(status), message));
            }
        }
        return new ResponseEntity<>(
                new ApiError(INTERNAL_SERVER_ERROR_CODE, INTERNAL_SERVER_ERROR_MESSAGE),
                HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @SuppressWarnings("unused")
    @ExceptionHandler(CompanyValidationException.class)
    public ResponseEntity<ApiError> handleCompanyValidationException(CompanyValidationException ex) {
        return badRequest(ex.getErrorCode());
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiError> handleUnexpectedException() {
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

        String field = resolveUnreadableField(ex);
        if (isReasonField(field)) {
            return INVALID_REASON;
        }
        if (isWorkstreamField(field)) {
            return isBlankWorkstreamValue(ex, message) ? MISSING_WORKSTREAM : INVALID_WORKSTREAM;
        }

        String fromMessage = mapUnreadableMessageText(message);
        if (fromMessage != null) {
            return fromMessage;
        }

        return MISSING_REQUIRED_PARAMETER;
    }

    private String mapEmailFieldError(FieldError fieldError) {
        if (StringUtils.isBlank(fieldError.getRejectedValue() == null
                ? null
                : String.valueOf(fieldError.getRejectedValue()))) {
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

    private InvalidFormatException findInvalidFormatCause(Throwable cause) {
        Throwable current = cause;
        while (current != null) {
            if (current instanceof InvalidFormatException invalidFormatException) {
                return invalidFormatException;
            }
            current = current.getCause();
        }
        return null;
    }

    private JsonMappingException findJsonMappingCause(Throwable cause) {
        Throwable current = cause;
        while (current != null) {
            if (current instanceof JsonMappingException jsonMappingException) {
                return jsonMappingException;
            }
            current = current.getCause();
        }
        return null;
    }

    private String resolveUnreadableField(HttpMessageNotReadableException ex) {
        Throwable cause = ex.getMostSpecificCause();
        InvalidFormatException invalidFormatException = findInvalidFormatCause(cause);
        if (invalidFormatException != null && !invalidFormatException.getPath().isEmpty()) {
            return invalidFormatException.getPath().getLast().getFieldName();
        }

        JsonMappingException jsonMappingException = findJsonMappingCause(cause);
        if (jsonMappingException == null || jsonMappingException.getPath().isEmpty()) {
            return null;
        }
        return jsonMappingException.getPath().getLast().getFieldName();
    }

    private boolean isBlankWorkstreamValue(HttpMessageNotReadableException ex, String message) {
        String combinedMessage = combineMessages(message, ex.getMostSpecificCause());
        InvalidFormatException invalidFormatException = findInvalidFormatCause(ex.getMostSpecificCause());
        if (invalidFormatException != null) {
            return StringUtils.isBlank(invalidFormatException.getValue() == null
                    ? null
                    : String.valueOf(invalidFormatException.getValue()))
                    || isBlankWorkstreamText(combinedMessage);
        }
        return isBlankWorkstreamText(combinedMessage);
    }

    private String mapUnreadableMessageText(String message) {
        String normalized = normalize(message);
        if (normalized.contains(PARTNER_OBJECTION_REASON_SNAKE)
                || normalized.contains(PARTNER_OBJECTION_REASON.toLowerCase(Locale.ROOT))) {
            return INVALID_REASON;
        }
        if (normalized.contains(PARTNER_OBJECTION_WORKSTREAM_SNAKE)
                || normalized.contains(PARTNER_OBJECTION_WORKSTREAM.toLowerCase(Locale.ROOT))) {
            return isBlankWorkstreamText(normalized) ? MISSING_WORKSTREAM : INVALID_WORKSTREAM;
        }
        return null;
    }

    private boolean isReasonField(String field) {
        return PARTNER_OBJECTION_REASON_SNAKE.equals(field) || PARTNER_OBJECTION_REASON.equals(field);
    }

    private boolean isWorkstreamField(String field) {
        return PARTNER_OBJECTION_WORKSTREAM_SNAKE.equals(field) || PARTNER_OBJECTION_WORKSTREAM.equals(field);
    }

    // Normalizes common Jackson parser text variants so blank-workstream inputs
    // are consistently treated as missing workstream rather than invalid value.
    private boolean isBlankWorkstreamText(String normalizedMessage) {
        String unescapedQuotes = normalizedMessage.replace("\\\"\\\"", "\"\"");
        return BLANK_WORKSTREAM_TOKENS.stream().anyMatch(unescapedQuotes::contains);
    }

    private String normalize(String message) {
        return message == null ? "" : message.toLowerCase(Locale.ROOT);
    }

    private String combineMessages(String primaryMessage, Throwable cause) {
        if (cause == null || cause.getMessage() == null) {
            return normalize(primaryMessage);
        }
        return normalize(primaryMessage) + " " + normalize(cause.getMessage());
    }

    private int errorPriorityIndex(String errorCode) {
        int index = ERROR_PRIORITY_ORDER.indexOf(errorCode);
        return index < 0 ? Integer.MAX_VALUE : index;
    }

    private int toStringLength(Object value) {
        if (value == null) {
            return 0;
        }
        return String.valueOf(value).length();
    }

    private ResponseEntity<ApiError> badRequest(String errorCode) {
        return new ResponseEntity<>(new ApiError(errorCode, VALIDATION_MESSAGE), HttpStatus.BAD_REQUEST);
    }
}
