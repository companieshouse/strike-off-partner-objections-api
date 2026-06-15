package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
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
import java.time.OffsetDateTime;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ProblemDetail;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.companieshouse.api.objections.model.BaseObjectionResponse;
import uk.gov.companieshouse.api.objections.model.BaseObjectionResponseLinks;
import uk.gov.companieshouse.api.objections.model.CreateObjectionRequest;
import uk.gov.companieshouse.api.objections.model.ObjectionProcessingStatus;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service.StrikeOffObjectionPartnerService;

@Tag("unit-test")
@WebMvcTest(controllers = StrikeOffObjectionPartnerController.class)
class StrikeOffObjectionPartnerControllerTest {

    private static final String COMPANY_NUMBER = "12345678";
    private static final String CREATE_OBJECTION_URL = "/company/" + COMPANY_NUMBER + "/strike-off-partner-objections";
    private static final String VALID_WORKSTREAM = "individuals-and-small-business-compliance";
    private static final String VALID_REASON = "compliance-issue-outstanding";
    private static final String MISSING_REQUIRED_PARAMETER = "MISSING_REQUIRED_PARAMETER";
    private static final String EMAIL_INCORRECT_FORMAT = "EMAIL_INCORRECT_FORMAT";
    private static final String EMAIL_MAX_LENGTH = "EMAIL_MAX_LENGTH";
    private static final String MAX_LENGTH_EXCEEDED = "MAX_LENGTH_EXCEEDED";
    private static final String INVALID_REASON = "INVALID_REASON";
    private static final String INVALID_WORKSTREAM = "INVALID_WORKSTREAM";
    private static final String MISSING_WORKSTREAM = "MISSING_WORKSTREAM";
    private static final ObjectMapper STATIC_OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private StrikeOffObjectionPartnerService strikeOffObjectionPartnerService;

    @BeforeEach
    void setUp() {
        when(strikeOffObjectionPartnerService.createObjection(eq(COMPANY_NUMBER), any()))
                .thenReturn(defaultCreatedResponse());
        clearInvocations(strikeOffObjectionPartnerService);
    }

    @Test
    void validRequestReturnsCreated() throws Exception {
        postCreateObjection(baseValidRequest())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.objection_id").value("objection-123"))
                .andExpect(jsonPath("$.processing_status").value("objection-submitted"));

        verify(strikeOffObjectionPartnerService).createObjection(eq(COMPANY_NUMBER), any());
    }

    @Test
    void getObjectionReturnsNotImplemented() {
        StrikeOffObjectionPartnerController controller =
                new StrikeOffObjectionPartnerController(strikeOffObjectionPartnerService);

        ResponseEntity<BaseObjectionResponse> response = controller.getObjection(COMPANY_NUMBER, "objection-123");

        assertEquals(HttpStatus.NOT_IMPLEMENTED, response.getStatusCode());
        assertNull(response.getBody());
    }

    @Test
    void missingBodyReturnsMissingRequiredParameter() throws Exception {
        postCreateObjectionWithoutBody()
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value(MISSING_REQUIRED_PARAMETER));
        verifyNoInteractions(strikeOffObjectionPartnerService);
    }

    @ParameterizedTest
    @MethodSource("missingOrBlankEmailCases")
    void missingOrBlankEmailReturnsMissingRequiredParameter(Consumer<ObjectNode> requestMutator) throws Exception {
        ObjectNode request = baseValidRequest();
        requestMutator.accept(request);

        assertBadRequestWithoutServiceCall(request, MISSING_REQUIRED_PARAMETER);
    }

    @ParameterizedTest
    @MethodSource("invalidEmailCases")
    void invalidEmailCasesReturnExpectedErrorCode(String email, String expectedErrorCode) throws Exception {
        ObjectNode request = baseValidRequest();
        request.put("partner_contact_email", email);

        assertBadRequestWithoutServiceCall(request, expectedErrorCode);
    }

    @Test
    void emailMaxBoundaryValidReturnsCreated() throws Exception {
        ObjectNode request = baseValidRequest();
        request.put("partner_contact_email", "a".repeat(64) + "@"
                + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(62));

        postCreateObjection(request)
                .andExpect(status().isCreated());

        verify(strikeOffObjectionPartnerService).createObjection(eq(COMPANY_NUMBER), any());
    }

    @Test
    void caseReferenceTooLongReturnsInvalidLength() throws Exception {
        ObjectNode request = baseValidRequest();
        request.put("partner_case_reference", "a".repeat(65));

        assertBadRequestWithoutServiceCall(request, MAX_LENGTH_EXCEEDED);
    }

    @Test
    void caseReferenceBoundaryValidReturnsCreated() throws Exception {
        ObjectNode request = baseValidRequest();
        request.put("partner_case_reference", "a".repeat(64));

        postCreateObjection(request)
                .andExpect(status().isCreated());

        verify(strikeOffObjectionPartnerService).createObjection(eq(COMPANY_NUMBER), any());
    }

    @Test
    void companyNameTooLongReturnsInvalidLength() throws Exception {
        ObjectNode request = baseValidRequest();
        request.put("submission_company_name", "a".repeat(161));

        assertBadRequestWithoutServiceCall(request, MAX_LENGTH_EXCEEDED);
    }

    @Test
    void companyNameBoundaryValidReturnsCreated() throws Exception {
        ObjectNode request = baseValidRequest();
        request.put("submission_company_name", "a".repeat(160));

        postCreateObjection(request)
                .andExpect(status().isCreated());

        verify(strikeOffObjectionPartnerService).createObjection(eq(COMPANY_NUMBER), any());
    }

    @ParameterizedTest
    @MethodSource("invalidReasonCases")
    void invalidReasonCasesReturnInvalidReason(Consumer<ObjectNode> requestMutator) throws Exception {
        ObjectNode request = baseValidRequest();
        requestMutator.accept(request);

        assertBadRequestWithoutServiceCall(request, INVALID_REASON);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "compliance-issue-outstanding",
            "financial-issue-outstanding",
            "compliance-and-financial-issue-outstanding",
            "other"
    })
    void validReasonValuesReturnCreated(String reason) throws Exception {
        ObjectNode request = baseValidRequest();
        request.put("partner_objection_reason", reason);

        postCreateObjection(request)
                .andExpect(status().isCreated());

        verify(strikeOffObjectionPartnerService).createObjection(eq(COMPANY_NUMBER), any());
    }

    @ParameterizedTest
    @MethodSource("workstreamCases")
    void workstreamCasesReturnExpectedErrorCode(Consumer<ObjectNode> requestMutator,
                                                String expectedErrorCode) throws Exception {
        ObjectNode request = baseValidRequest();
        requestMutator.accept(request);

        assertBadRequestWithoutServiceCall(request, expectedErrorCode);
    }

    @Test
    void multipleInvalidFieldsReturnsMultipleErrors() throws Exception {
        ObjectNode request = baseValidRequest();
        request.put("partner_contact_email", "");
        request.put("partner_case_reference", "");
        request.putNull("partner_objection_workstream");

        postCreateObjection(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("MISSING_REQUIRED_PARAMETER, MISSING_WORKSTREAM"));
        verifyNoInteractions(strikeOffObjectionPartnerService);
    }

    @Test
    void validationFailureHasNoSideEffects() throws Exception {
        ObjectNode request = baseValidRequest();
        request.put("partner_contact_email", "bad");

        assertBadRequestWithoutServiceCall(request, EMAIL_INCORRECT_FORMAT);
    }

    @Test
    void validationSuccessTriggersService() throws Exception {
        postCreateObjection(baseValidRequest())
                .andExpect(status().isCreated());

        verify(strikeOffObjectionPartnerService).createObjection(eq(COMPANY_NUMBER), any());
    }

    @Test
    void blankCaseReferenceReturnsMissingRequiredParameter() throws Exception {
        ObjectNode request = baseValidRequest();
        request.put("partner_case_reference", "");

        assertBadRequestWithoutServiceCall(request, MISSING_REQUIRED_PARAMETER);
    }

    @Test
    void nullCaseReferenceReturnsMissingRequiredParameter() throws Exception {
        ObjectNode request = baseValidRequest();
        request.putNull("partner_case_reference");

        assertBadRequestWithoutServiceCall(request, MISSING_REQUIRED_PARAMETER);
    }

    @Test
    void blankCompanyNameReturnsMissingRequiredParameter() throws Exception {
        ObjectNode request = baseValidRequest();
        request.put("submission_company_name", "");

        assertBadRequestWithoutServiceCall(request, MISSING_REQUIRED_PARAMETER);
    }

    @Test
    void nullCompanyNameReturnsMissingRequiredParameter() throws Exception {
        ObjectNode request = baseValidRequest();
        request.putNull("submission_company_name");

        assertBadRequestWithoutServiceCall(request, MISSING_REQUIRED_PARAMETER);
    }

    @ParameterizedTest
    @MethodSource("wrongTypeCases")
    void wrongTypeCasesReturnMissingRequiredParameter(String fieldName, JsonNode wrongTypeValue) throws Exception {
        ObjectNode request = baseValidRequest();
        request.set(fieldName, wrongTypeValue);

        assertBadRequestWithoutServiceCall(request, MISSING_REQUIRED_PARAMETER);
    }

    @Test
    void malformedJsonReturnsMissingRequiredParameter() throws Exception {
        mockMvc.perform(post(CREATE_OBJECTION_URL)
                        .contentType(APPLICATION_JSON)
                        .content("{\"partner_contact_email\":\"valid@email.com\","))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value(MISSING_REQUIRED_PARAMETER));

        verifyNoInteractions(strikeOffObjectionPartnerService);
    }

    @Test
    void whitespaceEmailIsTrimmedAndAccepted() throws Exception {
        ObjectNode request = baseValidRequest();
        request.put("partner_contact_email", " test@test.com ");

        postCreateObjection(request)
                .andExpect(status().isCreated());

        ArgumentCaptor<CreateObjectionRequest> requestCaptor = ArgumentCaptor.forClass(CreateObjectionRequest.class);
        verify(strikeOffObjectionPartnerService).createObjection(eq(COMPANY_NUMBER), requestCaptor.capture());
        assertEquals("test@test.com", requestCaptor.getValue().getPartnerContactEmail());
    }

    @Test
    void whitespaceCaseReferenceIsTrimmedAndAccepted() throws Exception {
        ObjectNode request = baseValidRequest();
        request.put("partner_case_reference", " CASE123 ");

        postCreateObjection(request)
                .andExpect(status().isCreated());

        ArgumentCaptor<CreateObjectionRequest> requestCaptor = ArgumentCaptor.forClass(CreateObjectionRequest.class);
        verify(strikeOffObjectionPartnerService).createObjection(eq(COMPANY_NUMBER), requestCaptor.capture());
        assertEquals("CASE123", requestCaptor.getValue().getPartnerCaseReference());
    }

    @Test
    void whitespaceCompanyNameIsTrimmedAndAccepted() throws Exception {
        ObjectNode request = baseValidRequest();
        request.put("submission_company_name", " Valid Company Ltd ");

        postCreateObjection(request)
                .andExpect(status().isCreated());

        ArgumentCaptor<CreateObjectionRequest> requestCaptor = ArgumentCaptor.forClass(CreateObjectionRequest.class);
        verify(strikeOffObjectionPartnerService).createObjection(eq(COMPANY_NUMBER), requestCaptor.capture());
        assertEquals("Valid Company Ltd", requestCaptor.getValue().getSubmissionCompanyName());
    }

    @Test
    void createObjectionWhenServiceThrowsExceptionReturns500() throws Exception {
        when(strikeOffObjectionPartnerService.createObjection(eq(COMPANY_NUMBER), any()))
                .thenThrow(new RuntimeException("Internal service error"));

        postCreateObjection(baseValidRequest())
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error_code").value("internal_server_error"))
                .andExpect(jsonPath("$.message").value("Internal Server Error"));
    }

    @Test
    void createObjectionWhenServiceThrowsResponseStatusExceptionPreservesStatusAndReason() throws Exception {
        when(strikeOffObjectionPartnerService.createObjection(eq(COMPANY_NUMBER), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Duplicate objection"));

        postCreateObjection(baseValidRequest())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error_code").value("conflict"))
                .andExpect(jsonPath("$.message").value("Duplicate objection"));
    }

    @Test
    void createObjectionWhenResponseStatusExceptionHasNoReasonUsesFallbackMessage() throws Exception {
        when(strikeOffObjectionPartnerService.createObjection(eq(COMPANY_NUMBER), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

        postCreateObjection(baseValidRequest())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error_code").value("not_found"))
                .andExpect(jsonPath("$.message").value("Request failed"));
    }

    @Test
    void createObjectionWhenServiceThrowsErrorResponseExceptionPreservesStatus() throws Exception {
        when(strikeOffObjectionPartnerService.createObjection(eq(COMPANY_NUMBER), any()))
                .thenThrow(new ErrorResponseException(
                        HttpStatus.FORBIDDEN,
                        ProblemDetail.forStatus(HttpStatus.FORBIDDEN),
                        null));

        postCreateObjection(baseValidRequest())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("forbidden"))
                .andExpect(jsonPath("$.message").value("Request failed"));
    }

    @Test
    void createObjectionWhenResponseStatusCodeIsNonStandardUsesGenericErrorCode() throws Exception {
        when(strikeOffObjectionPartnerService.createObjection(eq(COMPANY_NUMBER), any()))
                .thenThrow(new ResponseStatusException(HttpStatusCode.valueOf(499)));

        postCreateObjection(baseValidRequest())
                .andExpect(status().is(499))
                .andExpect(jsonPath("$.error_code").value("error"))
                .andExpect(jsonPath("$.message").value("Request failed"));
    }

    @Test
    void createObjectionWhenServiceThrowsBadGatewayReturns502() throws Exception {
        when(strikeOffObjectionPartnerService.createObjection(eq(COMPANY_NUMBER), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Upstream service unavailable"));

        postCreateObjection(baseValidRequest())
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error_code").value("bad_gateway"))
                .andExpect(jsonPath("$.message").value("Upstream service unavailable"));
    }

    @Test
    void createObjectionWhenServiceThrowsServiceUnavailableReturns503() throws Exception {
        when(strikeOffObjectionPartnerService.createObjection(eq(COMPANY_NUMBER), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Service temporarily unavailable"));

        postCreateObjection(baseValidRequest())
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error_code").value("service_unavailable"))
                .andExpect(jsonPath("$.message").value("Service temporarily unavailable"));
    }

    @Test
    void createObjectionWhenServiceThrowsGatewayTimeoutReturns504() throws Exception {
        when(strikeOffObjectionPartnerService.createObjection(eq(COMPANY_NUMBER), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "Upstream timed out"));

        postCreateObjection(baseValidRequest())
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.error_code").value("gateway_timeout"))
                .andExpect(jsonPath("$.message").value("Upstream timed out"));
    }

    @Test
    void createObjectionWhenServiceThrowsInternalServerErrorResponse() throws Exception {
        when(strikeOffObjectionPartnerService.createObjection(eq(COMPANY_NUMBER), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error occurred"));

        postCreateObjection(baseValidRequest())
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error_code").value("internal_server_error"))
                .andExpect(jsonPath("$.message").value("Unexpected error occurred"));
    }

    private ResultActions postCreateObjection(JsonNode payload) throws Exception {
        return mockMvc.perform(post(CREATE_OBJECTION_URL)
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)));
    }

    private ResultActions postCreateObjectionWithoutBody() throws Exception {
        return mockMvc.perform(post(CREATE_OBJECTION_URL).contentType(APPLICATION_JSON));
    }

    private static Stream<Arguments> invalidEmailCases() {
        return Stream.of(
                Arguments.of("invalid-email", EMAIL_INCORRECT_FORMAT),
                Arguments.of("test@", EMAIL_INCORRECT_FORMAT),
                Arguments.of("a".repeat(247) + "@test.com", EMAIL_INCORRECT_FORMAT + ", " + EMAIL_MAX_LENGTH)
        );
    }

    private static Stream<Arguments> missingOrBlankEmailCases() {
        return Stream.of(
                Arguments.of((Consumer<ObjectNode>) request -> request.remove("partner_contact_email")),
                Arguments.of((Consumer<ObjectNode>) request -> request.put("partner_contact_email", "")),
                Arguments.of((Consumer<ObjectNode>) request -> request.put("partner_contact_email", "   "))
        );
    }

    private static Stream<Arguments> invalidReasonCases() {
        return Stream.of(
                Arguments.of((Consumer<ObjectNode>) request -> request.put("partner_objection_reason", "invalid-value")),
                Arguments.of((Consumer<ObjectNode>) request ->
                        request.put("partner_objection_reason", "Compliance-Issue-Outstanding")),
                Arguments.of((Consumer<ObjectNode>) request -> request.remove("partner_objection_reason")),
                Arguments.of((Consumer<ObjectNode>) request -> request.putNull("partner_objection_reason")),
                Arguments.of((Consumer<ObjectNode>) request -> request.put("partner_objection_reason", ""))
        );
    }

    private static Stream<Arguments> wrongTypeCases() {
        return Stream.of(
                Arguments.of("partner_contact_email", STATIC_OBJECT_MAPPER.createArrayNode().add("not-a-string")),
                Arguments.of("partner_case_reference", STATIC_OBJECT_MAPPER.createObjectNode().put("bad", "value")),
                Arguments.of("submission_company_name", STATIC_OBJECT_MAPPER.createArrayNode().add("not-a-string"))
        );
    }

    private static Stream<Arguments> workstreamCases() {
        return Stream.of(
                Arguments.of((Consumer<ObjectNode>) request -> request.remove("partner_objection_workstream"),
                        MISSING_WORKSTREAM),
                Arguments.of((Consumer<ObjectNode>) request -> request.putNull("partner_objection_workstream"),
                        MISSING_WORKSTREAM),
                Arguments.of((Consumer<ObjectNode>) request -> request.put("partner_objection_workstream", ""),
                        INVALID_WORKSTREAM),
                Arguments.of((Consumer<ObjectNode>) request -> request.put("partner_objection_workstream", "a".repeat(101)),
                        INVALID_WORKSTREAM)
        );
    }

    private void assertBadRequestWithoutServiceCall(JsonNode payload, String expectedErrorCode) throws Exception {
        postCreateObjection(payload)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value(expectedErrorCode));
        verifyNoInteractions(strikeOffObjectionPartnerService);
    }

    private ObjectNode baseValidRequest() {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("partner_contact_email", "valid@email.com");
        request.put("partner_case_reference", "CASE123");
        request.put("submission_company_name", "Valid Company Ltd");
        request.put("partner_objection_reason", VALID_REASON);
        request.put("partner_objection_workstream", VALID_WORKSTREAM);
        return request;
    }

    private BaseObjectionResponse defaultCreatedResponse() {
        BaseObjectionResponse response = new BaseObjectionResponse();
        response.setObjectionId("objection-123");
        response.setProcessingStatus(ObjectionProcessingStatus.OBJECTION_SUBMITTED);
        response.setLinks(new BaseObjectionResponseLinks()
                .self("/company/12345678/strike-off-partner-objections/objection-123"));
        response.setCreatedAt(OffsetDateTime.parse("2026-06-03T12:00:00Z"));
        response.setEtag("etag-1");
        return response;
    }
}
