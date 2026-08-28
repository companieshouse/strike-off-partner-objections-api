package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.errorhandler;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.JsonMappingException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Comparator;
import java.util.Locale;
import java.util.List;
import java.util.Map;
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
    private static final String CONFLICT_CODE = "conflict";
    private static final String CONFLICT_MESSAGE = "conflict";
    private static final String UNAUTHORIZED_CODE = "unauthorized";
    private static final String UNAUTHORIZED_MESSAGE = "unauthorized";
    private static final String FORBIDDEN_CODE = "forbidden";
    private static final String FORBIDDEN_MESSAGE = "forbidden";
    private static final String NOT_FOUND_CODE = "not_found";
    private static final String NOT_FOUND_MESSAGE = "not_found";
    private static final String BAD_GATEWAY_CODE = "bad_gateway";
    private static final String BAD_GATEWAY_MESSAGE = "bad_gateway";
    private static final String MISSING_REQUIRED_PARAMETER = "MISSING_REQUIRED_PARAMETER";
    private static final String COMPANY_NUMBER_NOT_EXIST = "COMPANY_NUMBER_NOT_EXIST";
    private static final String SUBMISSION_COMPANY_NAME_MISMATCH = "SUBMISSION_COMPANY_NAME_MISMATCH";
    private static final String INVALID_COMPANY_TYPE = "INVALID_COMPANY_TYPE";
    private static final String INVALID_COMPANY_STATUS = "INVALID_COMPANY_STATUS";
    private static final String NO_OBJECTIONS_FOR_PARTNER_ORGANISATION = "NO_OBJECTIONS_FOR_PARTNER_ORGANISATION";
    private static final String EMAIL_MAX_LENGTH = "EMAIL_MAX_LENGTH";
    private static final String EMAIL_INCORRECT_FORMAT = "EMAIL_INCORRECT_FORMAT";
    private static final String EMAIL_NOT_RECOGNISED = "EMAIL_NOT_RECOGNISED";
    private static final String MAX_LENGTH_EXCEEDED = "MAX_LENGTH_EXCEEDED";
    private static final String MAX_LENGTH_EXCEEDED_MESSAGE = "Max length exceeded.";
    private static final String CASE_REFERENCE_MAX_LENGTH_EXCEEDED_MESSAGE = "Case reference max length exceeded.";
    private static final String INVALID_REASON = "INVALID_REASON";
    private static final String SIZE = "Size";
    private static final String EMAIL = "Email";
    private static final String PARTNER_CONTACT_EMAIL = "partnerContactEmail";
    private static final String PARTNER_CASE_REFERENCE = "partnerCaseReference";
    private static final String SUBMISSION_COMPANY_NAME = "submissionCompanyName";
    private static final String PARTNER_OBJECTION_REASON = "partnerObjectionReason";
    private static final String PARTNER_OBJECTION_REASON_SNAKE = "partner_objection_reason";
    private static final String REQUIRED_BODY_MISSING = "Required request body is missing";
    private static final List<String> ERROR_PRIORITY_ORDER = List.of(
            MISSING_REQUIRED_PARAMETER,
            EMAIL_INCORRECT_FORMAT,
            EMAIL_MAX_LENGTH,
            MAX_LENGTH_EXCEEDED,
            INVALID_REASON);

    private static final Map<String, String> MAX_LENGTH_MESSAGES_BY_FIELD = Map.of(
            PARTNER_CASE_REFERENCE, CASE_REFERENCE_MAX_LENGTH_EXCEEDED_MESSAGE
    );

    private static final Map<String, String> ERROR_MESSAGES = Map.ofEntries(
            Map.entry(MISSING_REQUIRED_PARAMETER, "The request is missing fields that are required."),
            Map.entry(COMPANY_NUMBER_NOT_EXIST, "There is no company registered with this number."),
            Map.entry(SUBMISSION_COMPANY_NAME_MISMATCH, "Company name does not match."),
            Map.entry(INVALID_COMPANY_TYPE, "You cannot create an objection for this company type."),
            Map.entry(INVALID_COMPANY_STATUS, "The company does not have an active proposal to strike off."),
            Map.entry(NO_OBJECTIONS_FOR_PARTNER_ORGANISATION,
                    "There are no objections for this partner organisation."),
            Map.entry(EMAIL_INCORRECT_FORMAT, "Invalid partner_contact_email. Must be a valid email format."),
            Map.entry(EMAIL_MAX_LENGTH, "Invalid partner_contact_email. Must not exceed 255 characters."),
            Map.entry(MAX_LENGTH_EXCEEDED, MAX_LENGTH_EXCEEDED_MESSAGE),
            Map.entry(EMAIL_NOT_RECOGNISED, "The email is not a recognised email address"),
            Map.entry(INVALID_REASON, "partner_objection_reason is not recognised.")
    );

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationExceptions(MethodArgumentNotValidException ex) {
        List<FieldError> fieldErrors = ex.getBindingResult().getFieldErrors();
        if (fieldErrors.isEmpty()) {
            return badRequest(MISSING_REQUIRED_PARAMETER);
        }

        String allErrorCodes = fieldErrors.stream()
                .map(GlobalExceptionHandler::mapFieldError)
                .distinct()
                .sorted(Comparator.comparingInt(GlobalExceptionHandler::errorPriorityIndex))
                .reduce((a, b) -> a + ", " + b)
                .orElse(MISSING_REQUIRED_PARAMETER);
        return badRequest(allErrorCodes, fieldErrors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableMessage(HttpMessageNotReadableException ex) {
        return badRequest(mapUnreadableMessage(ex));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatusException(ResponseStatusException ex) {
        return switch (ex.getStatusCode()) {
            case HttpStatus.UNAUTHORIZED -> new ResponseEntity<>(
                    new ApiError(UNAUTHORIZED_CODE, resolveMessage(ex, UNAUTHORIZED_MESSAGE)),
                    HttpStatus.UNAUTHORIZED);
            case HttpStatus.FORBIDDEN -> new ResponseEntity<>(
                    new ApiError(FORBIDDEN_CODE, resolveMessage(ex, FORBIDDEN_MESSAGE)),
                    HttpStatus.FORBIDDEN);
            case HttpStatus.NOT_FOUND -> new ResponseEntity<>(
                    new ApiError(NOT_FOUND_CODE, resolveMessage(ex, NOT_FOUND_MESSAGE)),
                    HttpStatus.NOT_FOUND);
            case HttpStatus.CONFLICT -> new ResponseEntity<>(
                    new ApiError(CONFLICT_CODE, resolveMessage(ex, CONFLICT_MESSAGE)),
                    HttpStatus.CONFLICT);
            case HttpStatus.BAD_GATEWAY -> new ResponseEntity<>(
                    new ApiError(BAD_GATEWAY_CODE, resolveMessage(ex, BAD_GATEWAY_MESSAGE)),
                    HttpStatus.BAD_GATEWAY);
            default -> {
                HttpStatusCode statusCode = ex.getStatusCode();
                yield ResponseEntity.status(statusCode)
                        .body(new ApiError(toErrorCode(statusCode), resolveMessage(ex, DEFAULT_RESPONSE_STATUS_MESSAGE)));
            }
        };
    }

    private static String resolveMessage(ResponseStatusException ex, String defaultMessage) {
        String reason = ex.getReason();
        return reason == null || reason.isBlank() ? defaultMessage : reason;
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

    private static String toErrorCode(HttpStatusCode statusCode) {
        HttpStatus resolvedStatus = HttpStatus.resolve(statusCode.value());
        if (resolvedStatus == null) {
            return ERROR_CODE;
        }
        return resolvedStatus.name().toLowerCase(Locale.ROOT);
    }

    private static String mapFieldError(FieldError fieldError) {
        return switch (fieldError.getField()) {
            case PARTNER_CONTACT_EMAIL -> mapEmailFieldError(fieldError);
            case PARTNER_CASE_REFERENCE, SUBMISSION_COMPANY_NAME -> mapLengthFieldError(fieldError);
            case PARTNER_OBJECTION_REASON -> INVALID_REASON;
            default -> MISSING_REQUIRED_PARAMETER;
        };
    }

    private static String mapUnreadableMessage(HttpMessageNotReadableException ex) {
        String message = ex.getMessage();
        if (isRequiredBodyMissing(message)) {
            return MISSING_REQUIRED_PARAMETER;
        }

        String field = resolveUnreadableField(ex);
        if (isReasonField(field)) {
            return INVALID_REASON;
        }

        String fromMessage = mapUnreadableMessageText(message);
        if (fromMessage != null) {
            return fromMessage;
        }

        return MISSING_REQUIRED_PARAMETER;
    }

    private static String mapEmailFieldError(FieldError fieldError) {
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

    private static String mapLengthFieldError(FieldError fieldError) {
        if (SIZE.equals(fieldError.getCode()) && toStringLength(fieldError.getRejectedValue()) > 0) {
            return MAX_LENGTH_EXCEEDED;
        }
        return MISSING_REQUIRED_PARAMETER;
    }


    private static boolean isRequiredBodyMissing(String message) {
        return message != null && message.contains(REQUIRED_BODY_MISSING);
    }

    private static InvalidFormatException findInvalidFormatCause(Throwable cause) {
        Throwable current = cause;
        while (current != null) {
            if (current instanceof InvalidFormatException invalidFormatException) {
                return invalidFormatException;
            }
            current = current.getCause();
        }
        return null;
    }

    private static JsonMappingException findJsonMappingCause(Throwable cause) {
        Throwable current = cause;
        while (current != null) {
            if (current instanceof JsonMappingException jsonMappingException) {
                return jsonMappingException;
            }
            current = current.getCause();
        }
        return null;
    }

    private static String resolveUnreadableField(HttpMessageNotReadableException ex) {
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


    private static String mapUnreadableMessageText(String message) {
        String normalized = normalize(message);
        if (normalized.contains(PARTNER_OBJECTION_REASON_SNAKE)
                || normalized.contains(PARTNER_OBJECTION_REASON.toLowerCase(Locale.ROOT))) {
            return INVALID_REASON;
        }
        return null;
    }

    private static boolean isReasonField(String field) {
        return PARTNER_OBJECTION_REASON_SNAKE.equals(field) || PARTNER_OBJECTION_REASON.equals(field);
    }


    private static String normalize(String message) {
        return message == null ? "" : message.toLowerCase(Locale.ROOT);
    }

    private static int errorPriorityIndex(String errorCode) {
        int index = ERROR_PRIORITY_ORDER.indexOf(errorCode);
        return index < 0 ? Integer.MAX_VALUE : index;
    }

    private static int toStringLength(Object value) {
        if (value == null) {
            return 0;
        }
        return String.valueOf(value).length();
    }

    private static ResponseEntity<ApiError> badRequest(String errorCode) {
        return badRequest(errorCode, List.of());
    }

    private static ResponseEntity<ApiError> badRequest(String errorCode, List<FieldError> fieldErrors) {
        String primaryCode = extractPrimaryCode(errorCode);
        String message = resolveValidationMessage(primaryCode, fieldErrors);
        return new ResponseEntity<>(new ApiError(errorCode, message), HttpStatus.BAD_REQUEST);
    }

    private static String extractPrimaryCode(String errorCode) {
        int separatorIndex = errorCode.indexOf(", ");
        if (separatorIndex < 0) {
            return errorCode;
        }
        return errorCode.substring(0, separatorIndex);
    }

    private static String resolveValidationMessage(String primaryCode, List<FieldError> fieldErrors) {
        if (!MAX_LENGTH_EXCEEDED.equals(primaryCode)) {
            return ERROR_MESSAGES.getOrDefault(primaryCode, VALIDATION_MESSAGE);
        }

        return resolveMaxLengthMessage(fieldErrors);
    }

    private static String resolveMaxLengthMessage(List<FieldError> fieldErrors) {
        return fieldErrors.stream()
                .filter(GlobalExceptionHandler::isMaxLengthExceededFieldError)
                .map(FieldError::getField)
                .map(field -> MAX_LENGTH_MESSAGES_BY_FIELD.getOrDefault(field, MAX_LENGTH_EXCEEDED_MESSAGE))
                .findFirst()
                .orElse(MAX_LENGTH_EXCEEDED_MESSAGE);
    }

    private static boolean isMaxLengthExceededFieldError(FieldError fieldError) {
        return MAX_LENGTH_EXCEEDED.equals(mapFieldError(fieldError));
    }
}
