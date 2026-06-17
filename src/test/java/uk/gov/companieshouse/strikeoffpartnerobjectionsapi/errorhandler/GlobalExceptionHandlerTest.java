package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.errorhandler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.companieshouse.api.objections.model.ApiError;

@Tag("unit-test")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleValidationExceptionsReturnsMissingRequiredWhenNoFieldErrors() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of());

        ResponseEntity<ApiError> response = handler.handleValidationExceptions(ex);
        ApiError body = requireBody(response);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("MISSING_REQUIRED_PARAMETER", body.getErrorCode());
        assertEquals("Invalid Message", body.getMessage());
    }

    @Test
    void handleValidationExceptionsMapsDistinctErrorsInPriorityOrder() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(
                fieldError("unknownField", "x", "SomeCode"),
                fieldError("partnerContactEmail", "not-an-email", "Email"),
                fieldError("partnerContactEmail", "a", "Email"),
                fieldError("partnerContactEmail", "a".repeat(260), "Size"),
                fieldError("partnerCaseReference", "abc", "Size"),
                fieldError("partnerObjectionReason", "bad", "Enum"),
                fieldError("partnerObjectionWorkstream", "bad", "Enum"),
                fieldError("partnerObjectionWorkstream", "", "Enum")));

        ResponseEntity<ApiError> response = handler.handleValidationExceptions(ex);
        ApiError body = requireBody(response);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(
                "MISSING_REQUIRED_PARAMETER, EMAIL_INCORRECT_FORMAT, EMAIL_MAX_LENGTH, "
                        + "MAX_LENGTH_EXCEEDED, INVALID_REASON, INVALID_WORKSTREAM, MISSING_WORKSTREAM",
                body.getErrorCode());
    }

    @Test
    void handleUnreadableMessageReturnsMissingRequiredWhenBodyIsMissing() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                "Required request body is missing", new MockHttpInputMessage(new byte[0]));

        ResponseEntity<ApiError> response = handler.handleUnreadableMessage(ex);
        ApiError body = requireBody(response);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("MISSING_REQUIRED_PARAMETER", body.getErrorCode());
    }

    @Test
    void handleUnreadableMessageReturnsMissingWorkstreamForBlankWorkstreamValue() {
        InvalidFormatException cause = invalidFormat("partner_objection_workstream", "");
        HttpMessageNotReadableException ex = unreadable("Cannot deserialize value", cause);

        ResponseEntity<ApiError> response = handler.handleUnreadableMessage(ex);
        ApiError body = requireBody(response);

        assertEquals("MISSING_WORKSTREAM", body.getErrorCode());
    }

    @Test
    void handleUnreadableMessageReturnsInvalidWorkstreamForNonBlankWorkstreamValue() {
        InvalidFormatException cause = invalidFormat("partner_objection_workstream", "other");
        HttpMessageNotReadableException ex = unreadable("Cannot deserialize value", cause);

        ResponseEntity<ApiError> response = handler.handleUnreadableMessage(ex);
        ApiError body = requireBody(response);

        assertEquals("INVALID_WORKSTREAM", body.getErrorCode());
    }

    @Test
    void handleUnreadableMessageReturnsInvalidReasonFromJsonMappingPath() {
        JsonMappingException cause = jsonMapping("partner_objection_reason");
        HttpMessageNotReadableException ex = unreadable("Cannot deserialize value", cause);

        ResponseEntity<ApiError> response = handler.handleUnreadableMessage(ex);
        ApiError body = requireBody(response);

        assertEquals("INVALID_REASON", body.getErrorCode());
    }

    @ParameterizedTest
    @MethodSource("unreadableMessageFallbackCases")
    void handleUnreadableMessageUsesExpectedFallbackErrorCode(String message, String expectedErrorCode) {
        HttpMessageNotReadableException ex = unreadable(message, new RuntimeException("root"));

        ResponseEntity<ApiError> response = handler.handleUnreadableMessage(ex);
        ApiError body = requireBody(response);

        assertEquals(expectedErrorCode, body.getErrorCode());
    }

    @Test
    void handleResponseStatusExceptionUsesFallbackMessageWhenReasonIsBlank() {
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.BAD_REQUEST, "   ");

        ResponseEntity<ApiError> response = handler.handleResponseStatusException(ex);
        ApiError body = requireBody(response);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("bad_request", body.getErrorCode());
        assertEquals("Request failed", body.getMessage());
    }

    @Test
    void handleErrorResponseExceptionReturnsDefaultMessage() {
        ErrorResponseException ex = new ErrorResponseException(
                HttpStatus.TOO_MANY_REQUESTS,
                ProblemDetail.forStatus(HttpStatus.TOO_MANY_REQUESTS),
                null);

        ResponseEntity<ApiError> response = handler.handleErrorResponseException(ex);
        ApiError body = requireBody(response);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertEquals("too_many_requests", body.getErrorCode());
        assertEquals("Request failed", body.getMessage());
    }

    @Test
    void handleSocketTimeoutExceptionReturnsGatewayTimeout() {
        ResponseEntity<ApiError> response = handler.handleSocketTimeoutException(new SocketTimeoutException("timeout"));
        ApiError body = requireBody(response);

        assertEquals(HttpStatus.GATEWAY_TIMEOUT, response.getStatusCode());
        assertEquals("gateway_timeout", body.getErrorCode());
        assertEquals("Gateway Timeout", body.getMessage());
    }

    @Test
    void handleIOExceptionReturnsServiceUnavailable() {
        ResponseEntity<ApiError> response = handler.handleIOException(new IOException("connection failed"));
        ApiError body = requireBody(response);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("service_unavailable", body.getErrorCode());
        assertEquals("Service Unavailable", body.getMessage());
    }

    @Test
    void handleUnexpectedExceptionReturnsInternalServerError() {
        ResponseEntity<ApiError> response = handler.handleUnexpectedException();
        ApiError body = requireBody(response);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("internal_server_error", body.getErrorCode());
        assertEquals("Internal Server Error", body.getMessage());
    }

    private FieldError fieldError(String field, Object rejectedValue, String code) {
        return new FieldError("createObjectionRequest", field, rejectedValue, false, new String[]{code}, null, null);
    }

    private HttpMessageNotReadableException unreadable(String message, Throwable cause) {
        return new HttpMessageNotReadableException(message, cause, new MockHttpInputMessage(new byte[0]));
    }

    private InvalidFormatException invalidFormat(String field, Object value) {
        InvalidFormatException exception = InvalidFormatException.from(null, "bad format", value, String.class);
        exception.prependPath(new Object(), field);
        return exception;
    }

    private JsonMappingException jsonMapping(String field) {
        JsonMappingException exception = JsonMappingException.from((com.fasterxml.jackson.core.JsonParser) null, "bad mapping");
        exception.prependPath(new Object(), field);
        return exception;
    }

    private static Stream<Arguments> unreadableMessageFallbackCases() {
        return Stream.of(
                Arguments.of("Value for partner_objection_reason is invalid", "INVALID_REASON"),
                Arguments.of("Cannot deserialize partner_objection_workstream from string \"\"", "MISSING_WORKSTREAM"),
                Arguments.of("Cannot deserialize partner_objection_workstream from string \\\"\\\"",
                        "MISSING_WORKSTREAM"),
                Arguments.of("Cannot deserialize partner_objection_workstream from value ''", "MISSING_WORKSTREAM"),
                Arguments.of("completely unknown parse issue", "MISSING_REQUIRED_PARAMETER")
        );
    }

    private ApiError requireBody(ResponseEntity<ApiError> response) {
        ApiError body = response.getBody();
        assertNotNull(body);
        return body;
    }
}
