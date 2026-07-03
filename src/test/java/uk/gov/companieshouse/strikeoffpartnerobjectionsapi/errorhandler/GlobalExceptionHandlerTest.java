package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.errorhandler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpResponseException;
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
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.companieshouse.api.error.ApiErrorResponseException;
import uk.gov.companieshouse.api.objections.model.ApiError;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.CompanyValidationException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.ServiceException;

@Tag("unit-test")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleValidationExceptions_whenNoFieldErrors_returnsMissingRequired() {
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
    void handleValidationExceptions_whenDistinctErrorsExist_mapsInPriorityOrder() {
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
    void handleUnreadableMessage_whenBodyIsMissing_returnsMissingRequired() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                "Required request body is missing", new MockHttpInputMessage(new byte[0]));

        ResponseEntity<ApiError> response = handler.handleUnreadableMessage(ex);
        ApiError body = requireBody(response);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("MISSING_REQUIRED_PARAMETER", body.getErrorCode());
    }

    @ParameterizedTest
    @MethodSource("unreadableMessageMappedPathCases")
    void handleUnreadableMessage_whenMappedPathIsPresent_returnsExpectedErrorCode(Throwable cause, String expectedErrorCode) {
        HttpMessageNotReadableException ex = unreadable("Cannot deserialize value", cause);

        ResponseEntity<ApiError> response = handler.handleUnreadableMessage(ex);
        ApiError body = requireBody(response);

        assertEquals(expectedErrorCode, body.getErrorCode());
    }

    @Test
    void handleUnreadableMessage_whenWorkstreamPathAndBlankTextWithoutInvalidFormat_returnsMissingWorkstream() {
        JsonMappingException cause = workstreamJsonMapping("Cannot deserialize partner_objection_workstream from string \"\"");
        HttpMessageNotReadableException ex = unreadable("Cannot deserialize value", cause);

        ResponseEntity<ApiError> response = handler.handleUnreadableMessage(ex);
        ApiError body = requireBody(response);

        assertEquals("MISSING_WORKSTREAM", body.getErrorCode());
    }

    @Test
    void handleUnreadableMessage_whenWorkstreamInvalidFormatHasNullValue_returnsMissingWorkstream() {
        InvalidFormatException cause = workstreamInvalidFormatWithNullValue();
        HttpMessageNotReadableException ex = unreadable("Cannot deserialize value", cause);

        ResponseEntity<ApiError> response = handler.handleUnreadableMessage(ex);
        ApiError body = requireBody(response);

        assertEquals("MISSING_WORKSTREAM", body.getErrorCode());
    }

    @Test
    void handleUnreadableMessage_whenWorkstreamMappingCauseMessageIsNull_usesPrimaryMessageFallback() {
        JsonMappingException cause = workstreamJsonMapping(null);
        HttpMessageNotReadableException ex = unreadable(
                "Cannot deserialize partner_objection_workstream from string \"\"",
                cause);

        ResponseEntity<ApiError> response = handler.handleUnreadableMessage(ex);
        ApiError body = requireBody(response);

        assertEquals("MISSING_WORKSTREAM", body.getErrorCode());
    }

    @Test
    void handleUnreadableMessage_whenWorkstreamCauseHasNullMessage_usesPrimaryMessage() {
        JsonMappingException cause = mock(JsonMappingException.class);
        when(cause.getPath()).thenReturn(List.of(new JsonMappingException.Reference(new Object(), "partner_objection_workstream")));
        when(cause.getMessage()).thenReturn(null);

        HttpMessageNotReadableException ex = unreadable(
                "Cannot deserialize partner_objection_workstream from string \"\"",
                cause);

        ResponseEntity<ApiError> response = handler.handleUnreadableMessage(ex);
        ApiError body = requireBody(response);

        assertEquals("MISSING_WORKSTREAM", body.getErrorCode());
    }

    @Test
    void handleUnreadableMessage_whenInvalidFormatHasNoPath_fallsBackToMessageText() {
        InvalidFormatException cause = InvalidFormatException.from(
                null,
                "Cannot deserialize partner_objection_reason",
                "bad",
                String.class);
        HttpMessageNotReadableException ex = unreadable("Cannot deserialize partner_objection_reason", cause);

        ResponseEntity<ApiError> response = handler.handleUnreadableMessage(ex);
        ApiError body = requireBody(response);

        assertEquals("INVALID_REASON", body.getErrorCode());
    }

    @ParameterizedTest
    @MethodSource("unreadableMessageFallbackCases")
    void handleUnreadableMessage_whenUsingFallbackCases_usesExpectedErrorCode(String message, String expectedErrorCode) {
        HttpMessageNotReadableException ex = unreadable(message, new RuntimeException("root"));

        ResponseEntity<ApiError> response = handler.handleUnreadableMessage(ex);
        ApiError body = requireBody(response);

        assertEquals(expectedErrorCode, body.getErrorCode());
    }

    @Test
    void handleResponseStatusException_whenReasonIsBlank_usesFallbackMessage() {
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.BAD_REQUEST, "   ");

        ResponseEntity<ApiError> response = handler.handleResponseStatusException(ex);
        ApiError body = requireBody(response);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("bad_request", body.getErrorCode());
        assertEquals("Request failed", body.getMessage());
    }

    @Test
    void handleResponseStatusException_whenReasonIsPresent_usesProvidedMessage() {
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bad request from upstream");

        ResponseEntity<ApiError> response = handler.handleResponseStatusException(ex);
        ApiError body = requireBody(response);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("bad_request", body.getErrorCode());
        assertEquals("Bad request from upstream", body.getMessage());
    }

    @Test
    void handleResponseStatusException_whenStatusCodeIsUnresolvable_usesDefaultErrorCode() {
        ResponseStatusException ex = new ResponseStatusException(HttpStatusCode.valueOf(777), null);

        ResponseEntity<ApiError> response = handler.handleResponseStatusException(ex);
        ApiError body = requireBody(response);

        assertEquals(777, response.getStatusCode().value());
        assertEquals("error", body.getErrorCode());
        assertEquals("Request failed", body.getMessage());
    }

    @Test
    void handleErrorResponseException_whenInvoked_returnsDefaultMessage() {
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
    void handleSocketTimeoutException_whenInvoked_returnsGatewayTimeout() {
        ResponseEntity<ApiError> response = handler.handleSocketTimeoutException(new SocketTimeoutException("timeout"));
        ApiError body = requireBody(response);

        assertEquals(HttpStatus.GATEWAY_TIMEOUT, response.getStatusCode());
        assertEquals("gateway_timeout", body.getErrorCode());
        assertEquals("Gateway Timeout", body.getMessage());
    }

    @Test
    void handleIOException_whenInvoked_returnsServiceUnavailable() {
        ResponseEntity<ApiError> response = handler.handleIOException(new IOException("connection failed"));
        ApiError body = requireBody(response);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("service_unavailable", body.getErrorCode());
        assertEquals("Service Unavailable", body.getMessage());
    }

    @Test
    void handleServiceException_whenValidUpstreamException_returnsUpstreamStatusAndComposedMessage() {
        ApiErrorResponseException upstream = apiErrorResponseException(404, "Not Found");
        ServiceException ex = new ServiceException("Error retrieving company profile", upstream);

        ResponseEntity<ApiError> response = handler.handleServiceException(ex);
        ApiError body = requireBody(response);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("not_found", body.getErrorCode());
        assertEquals("Error retrieving company profile: Not Found", body.getMessage());
    }

    @Test
    void handleServiceException_whenUpstreamMessageAndServiceMessageAreBlank_usesDefaultMessage() {
        ApiErrorResponseException upstream = apiErrorResponseException(429, "   ");
        ServiceException ex = new ServiceException("   ", upstream);

        ResponseEntity<ApiError> response = handler.handleServiceException(ex);
        ApiError body = requireBody(response);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertEquals("too_many_requests", body.getErrorCode());
        assertEquals("Request failed", body.getMessage());
    }

    @Test
    void handleServiceException_whenServiceMessageBlankAndUpstreamMessagePresent_usesUpstreamMessageOnly() {
        ApiErrorResponseException upstream = apiErrorResponseException(429, "Rate limit reached");
        ServiceException ex = new ServiceException(" ", upstream);

        ResponseEntity<ApiError> response = handler.handleServiceException(ex);
        ApiError body = requireBody(response);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertEquals("too_many_requests", body.getErrorCode());
        assertEquals("Rate limit reached", body.getMessage());
    }

    @Test
    void handleServiceException_whenUpstreamStatusCannotBeResolved_returnsInternalServerError() {
        ApiErrorResponseException upstream = apiErrorResponseException(599, "upstream error");
        ServiceException ex = new ServiceException("service failure", upstream);

        ResponseEntity<ApiError> response = handler.handleServiceException(ex);
        ApiError body = requireBody(response);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("internal_server_error", body.getErrorCode());
        assertEquals("Internal Server Error", body.getMessage());
    }

    @Test
    void handleServiceException_whenCauseIsNotApiErrorResponseException_returnsInternalServerError() {
        ServiceException ex = new ServiceException("service failure", new RuntimeException("boom"));

        ResponseEntity<ApiError> response = handler.handleServiceException(ex);
        ApiError body = requireBody(response);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("internal_server_error", body.getErrorCode());
        assertEquals("Internal Server Error", body.getMessage());
    }

    @Test
    void handleUnexpectedException_whenInvoked_returnsInternalServerError() {
        ResponseEntity<ApiError> response = handler.handleUnexpectedException();
        ApiError body = requireBody(response);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("internal_server_error", body.getErrorCode());
        assertEquals("Internal Server Error", body.getMessage());
    }

    @Test
    void handleCompanyValidationException_whenInvoked_returnsBadRequestWithErrorCode() {
        CompanyValidationException ex = new CompanyValidationException("Company check failed", "COMPANY_NUMBER_NOT_EXIST");

        ResponseEntity<ApiError> response = handler.handleCompanyValidationException(ex);
        ApiError body = requireBody(response);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("COMPANY_NUMBER_NOT_EXIST", body.getErrorCode());
        assertEquals("Invalid Message", body.getMessage());
    }

    @ParameterizedTest
    @MethodSource("validationEdgeCases")
    void handleValidationExceptions_whenFieldValidationEdgeCasesApplied_returnsExpectedErrorCode(
            FieldError fieldError,
            String expectedErrorCode) {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

        ResponseEntity<ApiError> response = handler.handleValidationExceptions(ex);
        ApiError body = requireBody(response);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(expectedErrorCode, body.getErrorCode());
    }

    private FieldError fieldError(String field, Object rejectedValue, String code) {
        return new FieldError("createObjectionRequest", field, rejectedValue, false, new String[]{code}, null, null);
    }

    private HttpMessageNotReadableException unreadable(String message, Throwable cause) {
        return new HttpMessageNotReadableException(message, cause, new MockHttpInputMessage(new byte[0]));
    }

    private InvalidFormatException workstreamInvalidFormatWithNullValue() {
        InvalidFormatException exception = InvalidFormatException.from(null, "bad format", null, String.class);
        exception.prependPath(new Object(), "partner_objection_workstream");
        return exception;
    }

    private JsonMappingException workstreamJsonMapping(String message) {
        JsonMappingException exception = JsonMappingException.from((com.fasterxml.jackson.core.JsonParser) null, message);
        exception.prependPath(new Object(), "partner_objection_workstream");
        return exception;
    }

    private ApiErrorResponseException apiErrorResponseException(int statusCode, String statusMessage) {
        HttpResponseException.Builder builder = new HttpResponseException.Builder(
                statusCode,
                statusMessage,
                new HttpHeaders());
        return new ApiErrorResponseException(builder);
    }

    private static Stream<Arguments> unreadableMessageFallbackCases() {
        return Stream.of(
                Arguments.of("Value for partner_objection_reason is invalid", "INVALID_REASON"),
                Arguments.of("Cannot deserialize partner_objection_workstream from string \"\"", "MISSING_WORKSTREAM"),
                Arguments.of("Cannot deserialize partner_objection_workstream from string \\\"\\\"",
                        "MISSING_WORKSTREAM"),
                Arguments.of("Cannot deserialize partner_objection_workstream from value ''", "MISSING_WORKSTREAM"),
                Arguments.of("Cannot deserialize partner_objection_workstream from string \"invalid\"",
                        "INVALID_WORKSTREAM"),
                Arguments.of("completely unknown parse issue", "MISSING_REQUIRED_PARAMETER")
        );
    }

    private static Stream<Arguments> unreadableMessageMappedPathCases() {
        InvalidFormatException blankWorkstream = InvalidFormatException.from(null, "bad format", "", String.class);
        blankWorkstream.prependPath(new Object(), "partner_objection_workstream");

        InvalidFormatException invalidWorkstream = InvalidFormatException.from(null, "bad format", "other", String.class);
        invalidWorkstream.prependPath(new Object(), "partner_objection_workstream");

        JsonMappingException invalidReason = JsonMappingException.from((com.fasterxml.jackson.core.JsonParser) null, "bad mapping");
        invalidReason.prependPath(new Object(), "partner_objection_reason");

        return Stream.of(
                Arguments.of(blankWorkstream, "MISSING_WORKSTREAM"),
                Arguments.of(invalidWorkstream, "INVALID_WORKSTREAM"),
                Arguments.of(invalidReason, "INVALID_REASON")
        );
    }

    private static Stream<Arguments> validationEdgeCases() {
        return Stream.of(
                Arguments.of(
                        new FieldError("createObjectionRequest", "partnerContactEmail", null, false,
                                new String[]{"Size"}, null, null),
                        "MISSING_REQUIRED_PARAMETER"),
                Arguments.of(
                        new FieldError("createObjectionRequest", "partnerContactEmail", "abc", false,
                                new String[]{"Size"}, null, null),
                        "MISSING_REQUIRED_PARAMETER"),
                Arguments.of(
                        new FieldError("createObjectionRequest", "partnerCaseReference", "", false,
                                new String[]{"Size"}, null, null),
                        "MISSING_REQUIRED_PARAMETER"),
                Arguments.of(
                        new FieldError("createObjectionRequest", "partnerCaseReference", null, false,
                                new String[]{"Size"}, null, null),
                        "MISSING_REQUIRED_PARAMETER")
        );
    }

    private ApiError requireBody(ResponseEntity<ApiError> response) {
        ApiError body = response.getBody();
        assertNotNull(body);
        return body;
    }
}
