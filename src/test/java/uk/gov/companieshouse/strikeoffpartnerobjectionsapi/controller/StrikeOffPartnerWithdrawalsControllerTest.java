package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils.StrikeoffPartnerObjectionsUtils.ERIC_PARTNER_ORGANISATION_HEADER;
import static uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils.StrikeoffPartnerObjectionsUtils.PARTNER_ORGANISATION;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.function.Consumer;
import java.util.stream.Stream;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.companieshouse.api.objections.model.WithdrawAllObjectionsRequest;
import uk.gov.companieshouse.api.objections.model.WithdrawAllObjectionsResponse;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.errorhandler.GlobalExceptionHandler;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service.StrikeOffPartnerWithdrawalsService;

@ExtendWith(MockitoExtension.class)
class StrikeOffPartnerWithdrawalsControllerTest {

    private static final String COMPANY_NUMBER = "12345678";
    private static final String WITHDRAWALS_PATH = "/company/" + COMPANY_NUMBER + "/strike-off-partner-objections-withdrawals";
    private static final String UPDATE_WITHDRAWAL_STATUS_PATH =
            "/internal/company/%s/strike-off-partner-objections-withdrawals/%s/withdrawal-status";
    private static final String WITHDRAWAL_ID = "withdrawal-123";
    private static final String MISSING_REQUIRED_PARAMETER = "MISSING_REQUIRED_PARAMETER";
    private static final String EMAIL_INCORRECT_FORMAT = "EMAIL_INCORRECT_FORMAT";
    private static final String EMAIL_MAX_LENGTH = "EMAIL_MAX_LENGTH";
    private static final String MAX_LENGTH_EXCEEDED = "MAX_LENGTH_EXCEEDED";
    private static final ObjectMapper STATIC_OBJECT_MAPPER = new ObjectMapper();
    private static final String VALID_WITHDRAWAL_REQUEST = """
            {
              "submission_company_name": "ACME LTD",
              "partner_case_reference": "CASE-123",
              "partner_objection_workstream": "individuals-and-small-business-compliance",
              "partner_contact_email": "case.owner@example.com"
            }
            """;
    private final ObjectMapper objectMapper = new ObjectMapper();


    @Mock
    private StrikeOffPartnerWithdrawalsService strikeOffPartnerWithdrawalsService;

    @Mock
    private HttpServletRequest httpServletRequest;

    @InjectMocks
    private StrikeOffPartnerWithdrawalsController strikeOffPartnerWithdrawalsController;

    @BeforeEach
    void setUp() {
        lenient().when(httpServletRequest.getHeader(ERIC_PARTNER_ORGANISATION_HEADER))
                .thenReturn(PARTNER_ORGANISATION);
    }

    // ===== GET Withdrawal Tests =====

    @Test
    void getWithdrawal_whenWithdrawalIdIsValid_returnsOkAndDelegatesToService() throws Exception {
        WithdrawAllObjectionsResponse response = new WithdrawAllObjectionsResponse();
        response.setWithdrawalId(WITHDRAWAL_ID);

        when(strikeOffPartnerWithdrawalsService.getWithdrawal(COMPANY_NUMBER, WITHDRAWAL_ID, PARTNER_ORGANISATION))
                .thenReturn(response);

        getWithdrawal()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.withdrawal_id").value(WITHDRAWAL_ID));

        verify(strikeOffPartnerWithdrawalsService)
                .getWithdrawal(COMPANY_NUMBER, WITHDRAWAL_ID, PARTNER_ORGANISATION);
    }

    @Test
    void getWithdrawal_whenWorkstreamIsNotPresent_omitsWorkstreamFromResponse() throws Exception {
        WithdrawAllObjectionsResponse response = new WithdrawAllObjectionsResponse();
        response.setWithdrawalId(WITHDRAWAL_ID);
        response.setPartnerObjectionWorkstream(null);

        when(strikeOffPartnerWithdrawalsService.getWithdrawal(COMPANY_NUMBER, WITHDRAWAL_ID, PARTNER_ORGANISATION))
                .thenReturn(response);

        getWithdrawal()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.withdrawal_id").value(WITHDRAWAL_ID))
                .andExpect(jsonPath("$.partner_objection_workstream").doesNotExist());
    }

    // ===== POST Withdrawal Tests (Existing Tests) =====

    @Test
    void withdrawAllObjections_whenRequestIsValid_returnsCreatedAndDelegatesToService() {
        WithdrawAllObjectionsRequest request = new WithdrawAllObjectionsRequest();
        request.setSubmissionCompanyName("ACME LTD");
        request.setPartnerCaseReference("CASE-001");
        request.setPartnerContactEmail("owner@example.com");
        request.setPartnerObjectionWorkstream("individuals-and-small-business-compliance");

        WithdrawAllObjectionsResponse serviceResponse = new WithdrawAllObjectionsResponse();
        serviceResponse.setWithdrawalId(WITHDRAWAL_ID);

        when(strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request, PARTNER_ORGANISATION))
                .thenReturn(serviceResponse);

        ResponseEntity<WithdrawAllObjectionsResponse> response =
                strikeOffPartnerWithdrawalsController.withdrawAllObjections(COMPANY_NUMBER, request);

        assertEquals(201, response.getStatusCode().value());
        assertSame(serviceResponse, response.getBody());
        verify(strikeOffPartnerWithdrawalsService).withdrawAllObjections(COMPANY_NUMBER, request, PARTNER_ORGANISATION);
    }

    @Test
    void withdrawAllObjections_whenWorkstreamIsMissing_returnsCreatedAndPassesNullWorkstream() throws Exception {
        WithdrawAllObjectionsResponse serviceResponse = new WithdrawAllObjectionsResponse();
        serviceResponse.setWithdrawalId(WITHDRAWAL_ID);
        when(strikeOffPartnerWithdrawalsService.withdrawAllObjections(
                eq(COMPANY_NUMBER),
                any(),
                eq(PARTNER_ORGANISATION))).thenReturn(serviceResponse);

        ObjectNode request = baseValidRequest();
        request.remove("partner_objection_workstream");

        postWithdrawals(request)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.withdrawal_id").value(WITHDRAWAL_ID));

        ArgumentCaptor<WithdrawAllObjectionsRequest> requestCaptor =
                ArgumentCaptor.forClass(WithdrawAllObjectionsRequest.class);
        verify(strikeOffPartnerWithdrawalsService).withdrawAllObjections(
                eq(COMPANY_NUMBER),
                requestCaptor.capture(),
                eq(PARTNER_ORGANISATION));
        assertNull(requestCaptor.getValue().getPartnerObjectionWorkstream());
    }

    @Test
    void withdrawAllObjections_whenWorkstreamIsNull_returnsCreatedAndPassesNullWorkstream() throws Exception {
        WithdrawAllObjectionsResponse serviceResponse = new WithdrawAllObjectionsResponse();
        serviceResponse.setWithdrawalId(WITHDRAWAL_ID);
        when(strikeOffPartnerWithdrawalsService.withdrawAllObjections(
                eq(COMPANY_NUMBER),
                any(),
                eq(PARTNER_ORGANISATION))).thenReturn(serviceResponse);

        ObjectNode request = baseValidRequest();
        request.putNull("partner_objection_workstream");

        postWithdrawals(request)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.withdrawal_id").value(WITHDRAWAL_ID));

        ArgumentCaptor<WithdrawAllObjectionsRequest> requestCaptor =
                ArgumentCaptor.forClass(WithdrawAllObjectionsRequest.class);
        verify(strikeOffPartnerWithdrawalsService).withdrawAllObjections(
                eq(COMPANY_NUMBER),
                requestCaptor.capture(),
                eq(PARTNER_ORGANISATION));
        assertNull(requestCaptor.getValue().getPartnerObjectionWorkstream());
    }

    @Test
    void withdrawAllObjections_whenRequiredFieldsAreMissing_returnsBadRequestErrorResponse() throws Exception {
        mockMvc().perform(post(WITHDRAWALS_PATH)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "submission_company_name": "ACME LTD"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value(MISSING_REQUIRED_PARAMETER))
                .andExpect(jsonPath("$.message").value("The request is missing fields that are required."));
        verifyNoInteractions(strikeOffPartnerWithdrawalsService);
    }

    @Test
    void withdrawAllObjections_whenBodyIsMissing_returnsMissingRequiredParameter() throws Exception {
        mockMvc().perform(post(WITHDRAWALS_PATH).contentType(APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value(MISSING_REQUIRED_PARAMETER))
                .andExpect(jsonPath("$.message").value("The request is missing fields that are required."));
        verifyNoInteractions(strikeOffPartnerWithdrawalsService);
    }

    @Test
    void withdrawAllObjections_whenJsonIsMalformed_returnsMissingRequiredParameter() throws Exception {
        mockMvc().perform(post(WITHDRAWALS_PATH)
                        .contentType(APPLICATION_JSON)
                        .content("{\"partner_contact_email\":\"valid@email.com\","))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value(MISSING_REQUIRED_PARAMETER))
                .andExpect(jsonPath("$.message").value("The request is missing fields that are required."));
        verifyNoInteractions(strikeOffPartnerWithdrawalsService);
    }

    @ParameterizedTest
    @MethodSource("missingOrBlankEmailCases")
    void withdrawAllObjections_whenEmailIsMissingOrBlank_returnsMissingRequiredParameter(
            Consumer<ObjectNode> requestMutator) throws Exception {
        ObjectNode request = baseValidRequest();
        requestMutator.accept(request);

        assertBadRequestWithoutServiceCall(request, MISSING_REQUIRED_PARAMETER);
    }

    @ParameterizedTest
    @MethodSource("invalidEmailCases")
    void withdrawAllObjections_whenEmailIsInvalid_returnsEmailIncorrectFormat(String invalidEmail) throws Exception {
        ObjectNode request = baseValidRequest();
        request.put("partner_contact_email", invalidEmail);

        assertBadRequestWithoutServiceCall(request, EMAIL_INCORRECT_FORMAT);
    }

    @Test
    void withdrawAllObjections_whenEmailExceeds255_returnsEmailIncorrectFormatAndMaxLength() throws Exception {
        ObjectNode request = baseValidRequest();
        request.put("partner_contact_email", "a".repeat(247) + "@test.com");
        assertBadRequestWithoutServiceCall(request, EMAIL_INCORRECT_FORMAT + ", " + EMAIL_MAX_LENGTH);
    }

    @Test
    void withdrawAllObjections_whenCaseReferenceExceeds64_returnsMaxLengthExceeded() throws Exception {
        ObjectNode request = baseValidRequest();
        request.put("partner_case_reference", "a".repeat(65));

        assertBadRequestWithoutServiceCall(request, MAX_LENGTH_EXCEEDED, "Case reference max length exceeded.");
    }

    @Test
    void withdrawAllObjections_whenCompanyNameExceeds160_returnsMaxLengthExceeded() throws Exception {
        ObjectNode request = baseValidRequest();
        request.put("submission_company_name", "a".repeat(161));

        assertBadRequestWithoutServiceCall(request, MAX_LENGTH_EXCEEDED, "Max length exceeded.");
    }

    @Test
    void withdrawAllObjections_whenEmailIsAt255CharBoundary_returnsCreated() throws Exception {
        ObjectNode request = baseValidRequest();
        request.put("partner_contact_email", "a".repeat(64) + "@"
                + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(62));

        postWithdrawals(request)
                .andExpect(status().isCreated());
        verify(strikeOffPartnerWithdrawalsService).withdrawAllObjections(
                eq(COMPANY_NUMBER),
                any(),
                eq(PARTNER_ORGANISATION));
    }

    @Test
    void withdrawAllObjections_whenCaseReferenceIsAt64CharBoundary_returnsCreated() throws Exception {
        ObjectNode request = baseValidRequest();
        request.put("partner_case_reference", "a".repeat(64));

        postWithdrawals(request)
                .andExpect(status().isCreated());
        verify(strikeOffPartnerWithdrawalsService).withdrawAllObjections(
                eq(COMPANY_NUMBER),
                any(),
                eq(PARTNER_ORGANISATION));
    }

    @Test
    void withdrawAllObjections_whenCompanyNameIsAt160CharBoundary_returnsCreated() throws Exception {
        ObjectNode request = baseValidRequest();
        request.put("submission_company_name", "a".repeat(160));

        postWithdrawals(request)
                .andExpect(status().isCreated());
        verify(strikeOffPartnerWithdrawalsService).withdrawAllObjections(
                eq(COMPANY_NUMBER),
                any(),
                eq(PARTNER_ORGANISATION));
    }

    @ParameterizedTest
    @MethodSource("wrongTypeCases")
    void withdrawAllObjections_whenFieldTypeIsInvalid_returnsMissingRequiredParameter(
            String fieldName,
            JsonNode wrongTypeValue) throws Exception {
        ObjectNode request = baseValidRequest();
        request.set(fieldName, wrongTypeValue);

        assertBadRequestWithoutServiceCall(request, MISSING_REQUIRED_PARAMETER);
    }


    @Test
    void withdrawAllObjections_whenRequestIsValid_callsService() throws Exception {
        WithdrawAllObjectionsResponse serviceResponse = new WithdrawAllObjectionsResponse();
        serviceResponse.setWithdrawalId(WITHDRAWAL_ID);
        when(strikeOffPartnerWithdrawalsService.withdrawAllObjections(
                eq(COMPANY_NUMBER),
                any(),
                eq(PARTNER_ORGANISATION))).thenReturn(serviceResponse);

        postWithdrawals(baseValidRequest())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.withdrawal_id").value(WITHDRAWAL_ID));

        verify(strikeOffPartnerWithdrawalsService).withdrawAllObjections(
                eq(COMPANY_NUMBER),
                any(),
                eq(PARTNER_ORGANISATION));
    }

    @Test
    void withdrawAllObjections_whenRequestContainsWhitespace_trimsWhitespaceFieldsBeforeCallingService() throws Exception {
        WithdrawAllObjectionsResponse serviceResponse = new WithdrawAllObjectionsResponse();
        serviceResponse.setWithdrawalId(WITHDRAWAL_ID);
        when(strikeOffPartnerWithdrawalsService.withdrawAllObjections(
                eq(COMPANY_NUMBER),
                any(),
                 eq(PARTNER_ORGANISATION))).thenReturn(serviceResponse);

        ObjectNode request = baseValidRequest();
        request.put("partner_contact_email", " case.owner@example.com ");
        request.put("partner_case_reference", " CASE-123 ");
        request.put("submission_company_name", " ACME LTD ");

        postWithdrawals(request)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.withdrawal_id").value(WITHDRAWAL_ID));

        ArgumentCaptor<WithdrawAllObjectionsRequest> requestCaptor =
                ArgumentCaptor.forClass(WithdrawAllObjectionsRequest.class);
        verify(strikeOffPartnerWithdrawalsService).withdrawAllObjections(
                eq(COMPANY_NUMBER),
                requestCaptor.capture(),
                 eq(PARTNER_ORGANISATION));
        assertEquals("case.owner@example.com", requestCaptor.getValue().getPartnerContactEmail());
        assertEquals("CASE-123", requestCaptor.getValue().getPartnerCaseReference());
        assertEquals("ACME LTD", requestCaptor.getValue().getSubmissionCompanyName());
    }

    @Test
    void withdrawAllObjections_whenServiceThrowsRuntimeException_returnsInternalServerErrorResponse() throws Exception {
        when(strikeOffPartnerWithdrawalsService.withdrawAllObjections(eq(COMPANY_NUMBER),
                any(),
                eq(PARTNER_ORGANISATION)))
                .thenThrow(new RuntimeException("Downstream unavailable"));

        mockMvc().perform(post(WITHDRAWALS_PATH)
                        .contentType(APPLICATION_JSON)
                        .content(VALID_WITHDRAWAL_REQUEST))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error_code").value("internal_server_error"))
                .andExpect(jsonPath("$.message").value("Internal Server Error"));
    }

    @Test
    void withdrawAllObjections_whenServiceThrowsConflictResponseStatusException_returnsConflictErrorResponse() throws Exception {
        when(strikeOffPartnerWithdrawalsService.withdrawAllObjections(eq(COMPANY_NUMBER),
                any(),
                eq(PARTNER_ORGANISATION)))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Duplicate withdrawal request"));

        mockMvc().perform(post(WITHDRAWALS_PATH)
                        .contentType(APPLICATION_JSON)
                        .content(VALID_WITHDRAWAL_REQUEST))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error_code").value("conflict"))
                .andExpect(jsonPath("$.message").value("Duplicate withdrawal request"));
    }

    @Test
    void withdrawAllObjections_whenServiceThrowsUnauthorizedResponseStatusException_returnsUnauthorizedErrorResponse() throws Exception {
        assertResponseStatusExceptionResponse(HttpStatus.UNAUTHORIZED, "Unauthorized access", "unauthorized");
    }

    @Test
    void withdrawAllObjections_whenServiceThrowsForbiddenResponseStatusException_returnsForbiddenErrorResponse() throws Exception {
        assertResponseStatusExceptionResponse(HttpStatus.FORBIDDEN, "Forbidden action", "forbidden");
    }

    @Test
    void withdrawAllObjections_whenServiceThrowsBadGatewayResponseStatusException_returnsBadGatewayErrorResponse() throws Exception {
        assertResponseStatusExceptionResponse(HttpStatus.BAD_GATEWAY, "Upstream bad gateway", "bad_gateway");
    }

    @Test
    void withdrawAllObjections_whenServiceThrowsServiceUnavailableResponseStatusException_returnsServiceUnavailableErrorResponse() throws Exception {
        assertResponseStatusExceptionResponse(HttpStatus.SERVICE_UNAVAILABLE,
                "Dependency unavailable",
                "service_unavailable");
    }

    @Test
    void withdrawAllObjections_whenServiceThrowsGatewayTimeoutResponseStatusException_returnsGatewayTimeoutErrorResponse() throws Exception {
        assertResponseStatusExceptionResponse(HttpStatus.GATEWAY_TIMEOUT, "Upstream timeout", "gateway_timeout");
    }

    @Test
    void withdrawAllObjections_whenServiceThrowsInternalServerErrorResponseStatusException_returnsInternalServerErrorResponse() throws Exception {
        assertResponseStatusExceptionResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                "Unexpected downstream failure",
                "internal_server_error");
    }

    @Test
    void updateWithdrawalStatus_whenRequestIsValid_returnsNoContent() throws Exception {
        postUpdateWithdrawalStatus("{\"processing_status\":\"withdrawal-processing\"}")
                .andExpect(status().isNoContent());

        verify(strikeOffPartnerWithdrawalsService)
                .updateWithdrawalProcessingStatus(eq(COMPANY_NUMBER), eq(WITHDRAWAL_ID), any());
    }

    @Test
    void updateWithdrawalStatus_whenWithdrawalDoesNotExist_returnsNotFound() throws Exception {
        org.mockito.Mockito.doThrow(
                        new uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.WithdrawalNotFoundException(
                                "Withdrawal not found"))
                .when(strikeOffPartnerWithdrawalsService)
                .updateWithdrawalProcessingStatus(eq(COMPANY_NUMBER), eq(WITHDRAWAL_ID), any());

        postUpdateWithdrawalStatus("{\"processing_status\":\"withdrawal-processing\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error_code").value("not_found"))
                .andExpect(jsonPath("$.message").value("Withdrawal not found"));
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "{}",
            "{\"processing_status\":\"\"}",
            "{\"processing_status\":\"unsupported-status\"}"
    })
    void updateWithdrawalStatus_whenProcessingStatusIsInvalid_returnsBadRequest(String payload) throws Exception {
        postUpdateWithdrawalStatus(payload)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value(MISSING_REQUIRED_PARAMETER));

        verifyNoInteractions(strikeOffPartnerWithdrawalsService);
    }

    @Test
    void updateWithdrawalStatus_whenServiceThrowsResponseStatusException_preservesStatusAndReason() throws Exception {
        org.mockito.Mockito.doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Invalid status transition"))
                .when(strikeOffPartnerWithdrawalsService)
                .updateWithdrawalProcessingStatus(eq(COMPANY_NUMBER), eq(WITHDRAWAL_ID), any());

        postUpdateWithdrawalStatus("{\"processing_status\":\"withdrawal-processing\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error_code").value("conflict"))
                .andExpect(jsonPath("$.message").value("Invalid status transition"));
    }

    private void assertResponseStatusExceptionResponse(
            final HttpStatus status,
            final String reason,
            final String expectedErrorCode) throws Exception {
        when(strikeOffPartnerWithdrawalsService.withdrawAllObjections(eq(COMPANY_NUMBER),
                any(),
                 eq(PARTNER_ORGANISATION)))
                .thenThrow(new ResponseStatusException(status, reason));

        mockMvc().perform(post(WITHDRAWALS_PATH)
                        .contentType(APPLICATION_JSON)
                        .content(VALID_WITHDRAWAL_REQUEST))
                .andExpect(status().is(status.value()))
                .andExpect(jsonPath("$.error_code").value(expectedErrorCode))
                .andExpect(jsonPath("$.message").value(reason));
    }

    private static Stream<Arguments> missingOrBlankEmailCases() {
        return Stream.of(
                Arguments.of((Consumer<ObjectNode>) request -> request.remove("partner_contact_email")),
                Arguments.of((Consumer<ObjectNode>) request -> request.put("partner_contact_email", "")),
                Arguments.of((Consumer<ObjectNode>) request -> request.put("partner_contact_email", "   "))
        );
    }

    private static Stream<Arguments> invalidEmailCases() {
        return Stream.of(
                Arguments.of("invalid-email"),
                Arguments.of("test@")
        );
    }


    private static Stream<Arguments> wrongTypeCases() {
        return Stream.of(
                Arguments.of("partner_contact_email", STATIC_OBJECT_MAPPER.createArrayNode().add("not-a-string")),
                Arguments.of("partner_case_reference", STATIC_OBJECT_MAPPER.createObjectNode().put("bad", "value")),
                Arguments.of("submission_company_name", STATIC_OBJECT_MAPPER.createArrayNode().add("not-a-string"))
        );
    }

    private ObjectNode baseValidRequest() {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("submission_company_name", "ACME LTD");
        request.put("partner_case_reference", "CASE-123");
        request.put("partner_objection_workstream", "individuals-and-small-business-compliance");
        request.put("partner_contact_email", "case.owner@example.com");
        return request;
    }

    private ResultActions postWithdrawals(JsonNode request) throws Exception {
        return mockMvc().perform(post(WITHDRAWALS_PATH)
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private ResultActions getWithdrawal() throws Exception {
        return mockMvc().perform(get(WITHDRAWALS_PATH + "/" + WITHDRAWAL_ID)
                .contentType(APPLICATION_JSON));
    }

    private ResultActions postUpdateWithdrawalStatus(String payload) throws Exception {
        return mockMvc().perform(patch(String.format(UPDATE_WITHDRAWAL_STATUS_PATH, COMPANY_NUMBER, WITHDRAWAL_ID))
                .contentType(APPLICATION_JSON)
                .content(payload));
    }

    private void assertBadRequestWithoutServiceCall(JsonNode payload, String expectedErrorCode) throws Exception {
        String primaryCode = expectedErrorCode.contains(", ")
                ? expectedErrorCode.substring(0, expectedErrorCode.indexOf(", "))
                : expectedErrorCode;
        String expectedMessage = expectedMessageFor(primaryCode);
        assertBadRequestWithoutServiceCall(payload, expectedErrorCode, expectedMessage);
    }

    private void assertBadRequestWithoutServiceCall(
            JsonNode payload,
            String expectedErrorCode,
            String expectedMessage) throws Exception {
        postWithdrawals(payload)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value(expectedErrorCode))
                .andExpect(jsonPath("$.message").value(expectedMessage));
        verifyNoInteractions(strikeOffPartnerWithdrawalsService);
    }

    private static String expectedMessageFor(String errorCode) {
        return switch (errorCode) {
            case MISSING_REQUIRED_PARAMETER -> "The request is missing fields that are required.";
            case EMAIL_INCORRECT_FORMAT -> "Invalid partner_contact_email. Must be a valid email format.";
            case EMAIL_MAX_LENGTH -> "Invalid partner_contact_email. Must not exceed 255 characters.";
            default -> "Invalid Message";
        };
    }

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(strikeOffPartnerWithdrawalsController)
                .setControllerAdvice(new CreateObjectionRequestBodyAdvice(), new GlobalExceptionHandler())
                .build();
    }
}
