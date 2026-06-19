package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import uk.gov.companieshouse.api.objections.model.PartnerObjectionWorkstream;
import uk.gov.companieshouse.api.objections.model.WithdrawAllObjections201Response;
import uk.gov.companieshouse.api.objections.model.WithdrawAllObjectionsRequest;
import uk.gov.companieshouse.api.objections.model.WithdrawAllObjectionsResponse;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.errorhandler.GlobalExceptionHandler;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service.StrikeOffPartnerWithdrawalsService;

@ExtendWith(MockitoExtension.class)
class StrikeOffPartnerWithdrawalsControllerTest {

    private static final String WITHDRAWALS_PATH = "/company/12345678/strike-off-partner-objections-withdrawals";
    private static final String COMPANY_NUMBER = "12345678";
    private static final String MISSING_REQUIRED_PARAMETER = "MISSING_REQUIRED_PARAMETER";
    private static final String EMAIL_INCORRECT_FORMAT = "EMAIL_INCORRECT_FORMAT";
    private static final String EMAIL_MAX_LENGTH = "EMAIL_MAX_LENGTH";
    private static final String MAX_LENGTH_EXCEEDED = "MAX_LENGTH_EXCEEDED";
    private static final String INVALID_WORKSTREAM = "INVALID_WORKSTREAM";
    private static final String MISSING_WORKSTREAM = "MISSING_WORKSTREAM";
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

    @InjectMocks
    private StrikeOffPartnerWithdrawalsController strikeOffPartnerWithdrawalsController;

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(strikeOffPartnerWithdrawalsController)
                .setControllerAdvice(new CreateObjectionRequestBodyAdvice(), new GlobalExceptionHandler())
                .build();
    }

    // ===== GET Withdrawal Tests =====

    @Test
    void getAllWithdrawals_returnsOkAndDelegatesToService_whenWithdrawalFound() {
        WithdrawAllObjectionsResponse response = new WithdrawAllObjectionsResponse();
        response.setWithdrawalId("withdrawal-123");

        when(strikeOffPartnerWithdrawalsService.getWithdrawal("12345678", "withdrawal-123"))
                .thenReturn(response);

        ResponseEntity<WithdrawAllObjectionsResponse> result =
                strikeOffPartnerWithdrawalsController.getAllWithdrawals("12345678", "withdrawal-123");

        assertEquals(200, result.getStatusCode().value());
        assertSame(response, result.getBody());
        verify(strikeOffPartnerWithdrawalsService).getWithdrawal("12345678", "withdrawal-123");
    }


    // ===== POST Withdrawal Tests (Existing Tests) =====

    @Test
    void withdrawAllObjections_returnsCreatedAndDelegatesToService_whenRequestIsValid() {
        WithdrawAllObjectionsRequest request = new WithdrawAllObjectionsRequest();
        request.setSubmissionCompanyName("ACME LTD");
        request.setPartnerCaseReference("CASE-001");
        request.setPartnerContactEmail("owner@example.com");
        request.setPartnerObjectionWorkstream(PartnerObjectionWorkstream.values()[0]);

        WithdrawAllObjections201Response serviceResponse = new WithdrawAllObjections201Response();
        serviceResponse.setWithdrawalId("withdrawal-123");

        when(strikeOffPartnerWithdrawalsService.withdrawAllObjections("12345678", request))
                .thenReturn(serviceResponse);

        ResponseEntity<WithdrawAllObjections201Response> response =
                strikeOffPartnerWithdrawalsController.withdrawAllObjections("12345678", request);

        assertEquals(201, response.getStatusCode().value());
        assertSame(serviceResponse, response.getBody());
        verify(strikeOffPartnerWithdrawalsService).withdrawAllObjections("12345678", request);
    }

    @Test
    void withdrawAllObjections_returnsBadRequestErrorResponse_whenRequiredFieldsAreMissing() throws Exception {
        mockMvc().perform(post(WITHDRAWALS_PATH)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "submission_company_name": "ACME LTD"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value(MISSING_REQUIRED_PARAMETER + ", " + MISSING_WORKSTREAM))
                .andExpect(jsonPath("$.message").value("Invalid Message"));
        verifyNoInteractions(strikeOffPartnerWithdrawalsService);
    }

    @Test
    void withdrawAllObjections_returnsMissingRequiredParameter_whenBodyIsMissing() throws Exception {
        mockMvc().perform(post(WITHDRAWALS_PATH).contentType(APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value(MISSING_REQUIRED_PARAMETER))
                .andExpect(jsonPath("$.message").value("Invalid Message"));
        verifyNoInteractions(strikeOffPartnerWithdrawalsService);
    }

    @Test
    void withdrawAllObjections_returnsMissingRequiredParameter_whenJsonIsMalformed() throws Exception {
        mockMvc().perform(post(WITHDRAWALS_PATH)
                        .contentType(APPLICATION_JSON)
                        .content("{\"partner_contact_email\":\"valid@email.com\","))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value(MISSING_REQUIRED_PARAMETER))
                .andExpect(jsonPath("$.message").value("Invalid Message"));
        verifyNoInteractions(strikeOffPartnerWithdrawalsService);
    }

    @ParameterizedTest
    @MethodSource("missingOrBlankEmailCases")
    void withdrawAllObjections_returnsMissingRequiredParameter_whenEmailIsMissingOrBlank(
            Consumer<ObjectNode> requestMutator) throws Exception {
        ObjectNode request = baseValidRequest();
        requestMutator.accept(request);

        assertBadRequestWithoutServiceCall(request, MISSING_REQUIRED_PARAMETER);
    }

    @ParameterizedTest
    @MethodSource("invalidEmailCases")
    void withdrawAllObjections_returnsEmailIncorrectFormat_whenEmailIsInvalid(String invalidEmail) throws Exception {
        ObjectNode request = baseValidRequest();
        request.put("partner_contact_email", invalidEmail);

        assertBadRequestWithoutServiceCall(request, EMAIL_INCORRECT_FORMAT);
    }

    @Test
    void withdrawAllObjections_returnsEmailIncorrectFormatAndMaxLength_whenEmailExceeds255() throws Exception {
        ObjectNode request = baseValidRequest();
        request.put("partner_contact_email", "a".repeat(247) + "@test.com");
        assertBadRequestWithoutServiceCall(request, EMAIL_INCORRECT_FORMAT + ", " + EMAIL_MAX_LENGTH);
    }

    @Test
    void withdrawAllObjections_returnsMaxLengthExceeded_whenCaseReferenceExceeds64() throws Exception {
        ObjectNode request = baseValidRequest();
        request.put("partner_case_reference", "a".repeat(65));

        assertBadRequestWithoutServiceCall(request, MAX_LENGTH_EXCEEDED);
    }

    @Test
    void withdrawAllObjections_returnsMaxLengthExceeded_whenCompanyNameExceeds160() throws Exception {
        ObjectNode request = baseValidRequest();
        request.put("submission_company_name", "a".repeat(161));

        assertBadRequestWithoutServiceCall(request, MAX_LENGTH_EXCEEDED);
    }

    @Test
    void withdrawAllObjections_returnsCreated_whenEmailIsAt255CharBoundary() throws Exception {
        ObjectNode request = baseValidRequest();
        request.put("partner_contact_email", "a".repeat(64) + "@"
                + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(62));

        postWithdrawals(request)
                .andExpect(status().isCreated());
        verify(strikeOffPartnerWithdrawalsService).withdrawAllObjections(
                eq(COMPANY_NUMBER),
                any());
    }

    @Test
    void withdrawAllObjections_returnsCreated_whenCaseReferenceIsAt64CharBoundary() throws Exception {
        ObjectNode request = baseValidRequest();
        request.put("partner_case_reference", "a".repeat(64));

        postWithdrawals(request)
                .andExpect(status().isCreated());
        verify(strikeOffPartnerWithdrawalsService).withdrawAllObjections(
                eq(COMPANY_NUMBER),
                any());
    }

    @Test
    void withdrawAllObjections_returnsCreated_whenCompanyNameIsAt160CharBoundary() throws Exception {
        ObjectNode request = baseValidRequest();
        request.put("submission_company_name", "a".repeat(160));

        postWithdrawals(request)
                .andExpect(status().isCreated());
        verify(strikeOffPartnerWithdrawalsService).withdrawAllObjections(
                eq(COMPANY_NUMBER),
                any());
    }

    @ParameterizedTest
    @MethodSource("wrongTypeCases")
    void withdrawAllObjections_returnsMissingRequiredParameter_whenFieldTypeIsInvalid(
            String fieldName,
            JsonNode wrongTypeValue) throws Exception {
        ObjectNode request = baseValidRequest();
        request.set(fieldName, wrongTypeValue);

        assertBadRequestWithoutServiceCall(request, MISSING_REQUIRED_PARAMETER);
    }

    @ParameterizedTest
    @MethodSource("workstreamCases")
    void withdrawAllObjections_returnsExpectedWorkstreamErrorCode(
            Consumer<ObjectNode> requestMutator,
            String expectedErrorCode) throws Exception {
        ObjectNode request = baseValidRequest();
        requestMutator.accept(request);

        assertBadRequestWithoutServiceCall(request, expectedErrorCode);
    }

    @Test
    void withdrawAllObjections_callsService_whenRequestIsValid() throws Exception {
        WithdrawAllObjections201Response serviceResponse = new WithdrawAllObjections201Response();
        serviceResponse.setWithdrawalId("withdrawal-123");
        when(strikeOffPartnerWithdrawalsService.withdrawAllObjections(
                eq(COMPANY_NUMBER),
                any())).thenReturn(serviceResponse);

        postWithdrawals(baseValidRequest())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.withdrawal_id").value("withdrawal-123"));

        verify(strikeOffPartnerWithdrawalsService).withdrawAllObjections(
                eq(COMPANY_NUMBER),
                any());
    }

    @Test
    void withdrawAllObjections_trimsWhitespaceFields_beforeCallingService() throws Exception {
        WithdrawAllObjections201Response serviceResponse = new WithdrawAllObjections201Response();
        serviceResponse.setWithdrawalId("withdrawal-123");
        when(strikeOffPartnerWithdrawalsService.withdrawAllObjections(
                eq(COMPANY_NUMBER),
                any())).thenReturn(serviceResponse);

        ObjectNode request = baseValidRequest();
        request.put("partner_contact_email", " case.owner@example.com ");
        request.put("partner_case_reference", " CASE-123 ");
        request.put("submission_company_name", " ACME LTD ");

        postWithdrawals(request)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.withdrawal_id").value("withdrawal-123"));

        ArgumentCaptor<WithdrawAllObjectionsRequest> requestCaptor =
                ArgumentCaptor.forClass(WithdrawAllObjectionsRequest.class);
        verify(strikeOffPartnerWithdrawalsService).withdrawAllObjections(
                eq(COMPANY_NUMBER),
                requestCaptor.capture());
        assertEquals("case.owner@example.com", requestCaptor.getValue().getPartnerContactEmail());
        assertEquals("CASE-123", requestCaptor.getValue().getPartnerCaseReference());
        assertEquals("ACME LTD", requestCaptor.getValue().getSubmissionCompanyName());
    }

    @Test
    void withdrawAllObjections_returnsInternalServerErrorResponse_whenServiceThrowsRuntimeException() throws Exception {
        when(strikeOffPartnerWithdrawalsService.withdrawAllObjections(eq("12345678"),
                any()))
                .thenThrow(new RuntimeException("Downstream unavailable"));

        mockMvc().perform(post(WITHDRAWALS_PATH)
                        .contentType(APPLICATION_JSON)
                        .content(VALID_WITHDRAWAL_REQUEST))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error_code").value("internal_server_error"))
                .andExpect(jsonPath("$.message").value("Internal Server Error"));
    }

    @Test
    void withdrawAllObjections_returnsConflictErrorResponse_whenServiceThrowsConflictResponseStatusException() throws Exception {
        when(strikeOffPartnerWithdrawalsService.withdrawAllObjections(eq("12345678"),
                any()))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Duplicate withdrawal request"));

        mockMvc().perform(post(WITHDRAWALS_PATH)
                        .contentType(APPLICATION_JSON)
                        .content(VALID_WITHDRAWAL_REQUEST))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error_code").value("conflict"))
                .andExpect(jsonPath("$.message").value("Duplicate withdrawal request"));
    }

    @Test
    void withdrawAllObjections_returnsUnauthorizedErrorResponse_whenServiceThrowsUnauthorizedResponseStatusException() throws Exception {
        assertResponseStatusExceptionResponse(HttpStatus.UNAUTHORIZED, "Unauthorized access", "unauthorized");
    }

    @Test
    void withdrawAllObjections_returnsForbiddenErrorResponse_whenServiceThrowsForbiddenResponseStatusException() throws Exception {
        assertResponseStatusExceptionResponse(HttpStatus.FORBIDDEN, "Forbidden action", "forbidden");
    }

    @Test
    void withdrawAllObjections_returnsBadGatewayErrorResponse_whenServiceThrowsBadGatewayResponseStatusException() throws Exception {
        assertResponseStatusExceptionResponse(HttpStatus.BAD_GATEWAY, "Upstream bad gateway", "bad_gateway");
    }

    @Test
    void withdrawAllObjections_returnsServiceUnavailableErrorResponse_whenServiceThrowsServiceUnavailableResponseStatusException() throws Exception {
        assertResponseStatusExceptionResponse(HttpStatus.SERVICE_UNAVAILABLE,
                "Dependency unavailable",
                "service_unavailable");
    }

    @Test
    void withdrawAllObjections_returnsGatewayTimeoutErrorResponse_whenServiceThrowsGatewayTimeoutResponseStatusException() throws Exception {
        assertResponseStatusExceptionResponse(HttpStatus.GATEWAY_TIMEOUT, "Upstream timeout", "gateway_timeout");
    }

    @Test
    void withdrawAllObjections_returnsInternalServerErrorResponse_whenServiceThrowsInternalServerErrorResponseStatusException() throws Exception {
        assertResponseStatusExceptionResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                "Unexpected downstream failure",
                "internal_server_error");
    }

    private void assertResponseStatusExceptionResponse(
            final HttpStatus status,
            final String reason,
            final String expectedErrorCode) throws Exception {
        when(strikeOffPartnerWithdrawalsService.withdrawAllObjections(eq(COMPANY_NUMBER),
                any()))
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

    private static Stream<Arguments> workstreamCases() {
        return Stream.of(
                Arguments.of((Consumer<ObjectNode>) request -> request.remove("partner_objection_workstream"),
                        MISSING_WORKSTREAM),
                Arguments.of((Consumer<ObjectNode>) request -> request.putNull("partner_objection_workstream"),
                        MISSING_WORKSTREAM),
                Arguments.of((Consumer<ObjectNode>) request -> request.put("partner_objection_workstream", ""),
                        MISSING_WORKSTREAM),
                Arguments.of((Consumer<ObjectNode>) request -> request.put("partner_objection_workstream", "other"),
                        INVALID_WORKSTREAM),
                Arguments.of((Consumer<ObjectNode>) request -> request.put("partner_objection_workstream", "a".repeat(101)),
                        INVALID_WORKSTREAM)
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

    private void assertBadRequestWithoutServiceCall(JsonNode payload, String expectedErrorCode) throws Exception {
        postWithdrawals(payload)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value(expectedErrorCode))
                .andExpect(jsonPath("$.message").value("Invalid Message"));
        verifyNoInteractions(strikeOffPartnerWithdrawalsService);
    }
}
