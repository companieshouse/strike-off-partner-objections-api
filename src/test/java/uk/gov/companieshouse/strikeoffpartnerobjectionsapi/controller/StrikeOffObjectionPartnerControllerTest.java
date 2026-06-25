package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import org.springframework.http.ProblemDetail;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.companieshouse.api.InternalApiClient;
import uk.gov.companieshouse.api.objections.model.BaseObjectionResponse;
import uk.gov.companieshouse.api.objections.model.BaseObjectionResponseLinks;
import uk.gov.companieshouse.api.objections.model.CreateObjectionRequest;
import uk.gov.companieshouse.api.objections.model.FailureReason;
import uk.gov.companieshouse.api.objections.model.ObjectionProcessingStatus;
import uk.gov.companieshouse.api.objections.model.PartnerObjectionReason;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.ObjectionNotFoundException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service.StrikeOffPartnerObjectionService;

@Tag("unit-test")
@WebMvcTest(controllers = StrikeOffObjectionPartnerController.class)
class StrikeOffObjectionPartnerControllerTest {

    private static final String COMPANY_NUMBER = "12345678";
    private static final String OBJECTION_ID = "objection-123";
    private static final String CREATE_OBJECTION_URL = "/company/" + COMPANY_NUMBER + "/strike-off-partner-objections";
    private static final String GET_OBJECTION_URL = "/company/%s/strike-off-partner-objections/%s";
    private static final String VALID_WORKSTREAM = "individuals-and-small-business-compliance";
    private static final String VALID_REASON = "compliance-issue-outstanding";
    private static final String MISSING_REQUIRED_PARAMETER = "MISSING_REQUIRED_PARAMETER";
    private static final String EMAIL_INCORRECT_FORMAT = "EMAIL_INCORRECT_FORMAT";
    private static final String EMAIL_MAX_LENGTH = "EMAIL_MAX_LENGTH";
    private static final String MAX_LENGTH_EXCEEDED = "MAX_LENGTH_EXCEEDED";
    private static final String INVALID_REASON = "INVALID_REASON";
    private static final String MISSING_WORKSTREAM = "MISSING_WORKSTREAM";
    private static final ObjectMapper STATIC_OBJECT_MAPPER = new ObjectMapper();
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StrikeOffPartnerObjectionService strikeOffPartnerObjectionService;

    @MockitoBean
    private InternalApiClient internalApiClient;

    @BeforeEach
    void setUp() {
        when(strikeOffPartnerObjectionService.createObjection(eq(COMPANY_NUMBER), any()))
                .thenReturn(defaultCreatedResponse());
        clearInvocations(strikeOffPartnerObjectionService);
    }

    @Test
    void createObjection_whenRequestIsValid_returnsCreated() throws Exception {
        postCreateObjection(baseValidRequest())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.objection_id").value("objection-123"))
                .andExpect(jsonPath("$.processing_status").value("objection-submitted"));

        verify(strikeOffPartnerObjectionService).createObjection(eq(COMPANY_NUMBER), any());
    }

    @Test
    void getObjection_whenRequestIsValid_callsServiceWithCompanyNumberAndObjectionId() throws Exception {
        performGetObjection(COMPANY_NUMBER)
                .andExpect(status().isOk());

        verify(strikeOffPartnerObjectionService, times(1)).getObjection(COMPANY_NUMBER, OBJECTION_ID);
    }

    @Test
    void getObjection_whenObjectionIsFound_returns200AndContainsCorrectAttributes() throws Exception {
        when(strikeOffPartnerObjectionService.getObjection(COMPANY_NUMBER, OBJECTION_ID))
                .thenReturn(defaultCreatedResponse());

        MvcResult result = performGetObjection(COMPANY_NUMBER)
                .andExpect(status().isOk())
                .andReturn();

        JsonNode responseBody = objectMapper.readTree(result.getResponse().getContentAsString());

        String[] expectedFields = {
                "company_number",
                "submission_company_name",
                "objection_id",
                "partner_case_reference",
                "partner_objection_workstream",
                "partner_objection_reason",
                "partner_contact_email",
                "processing_status",
                "links",
                "kind",
                "created_at",
                "etag",
                "processing_status_changed_at",
                "initial_expiration_on",
                "failure_reason"
        };

        for (String field : expectedFields) {
            assertTrue(responseBody.has(field), "Missing field in response body: " + field);
        }
    }

    @Test
    void getObjection_whenObjectionIsNotFound_returns404() throws Exception {
        when(strikeOffPartnerObjectionService.getObjection(COMPANY_NUMBER, OBJECTION_ID))
                .thenThrow(new ObjectionNotFoundException("Objection not found"));

        performGetObjection(COMPANY_NUMBER)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error_code").value("not_found"))
                .andExpect(jsonPath("$.message").value("Objection not found"));
    }

    @Test
    void getObjection_whenCompanyNumberIsIncorrect_returns404() throws Exception {
        when(strikeOffPartnerObjectionService.getObjection("123", OBJECTION_ID))
                .thenThrow(new ObjectionNotFoundException("Objection not found"));
        when(strikeOffPartnerObjectionService.getObjection(COMPANY_NUMBER, OBJECTION_ID))
                .thenReturn(defaultCreatedResponse());

        performGetObjection("123")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error_code").value("not_found"))
                .andExpect(jsonPath("$.message").value("Objection not found"));

        // Proving here that the objection ID is valid; it is the company number that causes the 404.
        performGetObjection(COMPANY_NUMBER)
                .andExpect(status().isOk());
    }

    @Test
    void createObjection_whenBodyIsMissing_returnsMissingRequiredParameter() throws Exception {
        postCreateObjectionWithoutBody()
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value(MISSING_REQUIRED_PARAMETER));
        verifyNoInteractions(strikeOffPartnerObjectionService);
    }

    @ParameterizedTest
    @MethodSource("missingOrBlankEmailCases")
    void createObjection_whenEmailIsMissingOrBlank_returnsMissingRequiredParameter(Consumer<ObjectNode> requestMutator) throws Exception {
        ObjectNode request = baseValidRequest();
        requestMutator.accept(request);

        assertBadRequestWithoutServiceCall(request, MISSING_REQUIRED_PARAMETER);
    }

    @ParameterizedTest
    @MethodSource("invalidEmailCases")
    void createObjection_whenEmailIsInvalid_returnsExpectedErrorCode(String email, String expectedErrorCode) throws Exception {
        ObjectNode request = baseValidRequest();
        request.put("partner_contact_email", email);

        assertBadRequestWithoutServiceCall(request, expectedErrorCode);
    }

    @Test
    void createObjection_whenEmailIsAtMaxBoundary_returnsCreated() throws Exception {
        ObjectNode request = baseValidRequest();
        request.put("partner_contact_email", "a".repeat(64) + "@"
                + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(62));

        postCreateObjection(request)
                .andExpect(status().isCreated());

        verify(strikeOffPartnerObjectionService).createObjection(eq(COMPANY_NUMBER), any());
    }

    @Test
    void createObjection_whenCaseReferenceIsTooLong_returnsInvalidLength() throws Exception {
        ObjectNode request = baseValidRequest();
        request.put("partner_case_reference", "a".repeat(65));

        assertBadRequestWithoutServiceCall(request, MAX_LENGTH_EXCEEDED);
    }

    @Test
    void createObjection_whenCaseReferenceIsAtBoundary_returnsCreated() throws Exception {
        ObjectNode request = baseValidRequest();
        request.put("partner_case_reference", "a".repeat(64));

        postCreateObjection(request)
                .andExpect(status().isCreated());

        verify(strikeOffPartnerObjectionService).createObjection(eq(COMPANY_NUMBER), any());
    }

    @Test
    void createObjection_whenCompanyNameIsTooLong_returnsInvalidLength() throws Exception {
        ObjectNode request = baseValidRequest();
        request.put("submission_company_name", "a".repeat(161));

        assertBadRequestWithoutServiceCall(request, MAX_LENGTH_EXCEEDED);
    }

    @Test
    void createObjection_whenCompanyNameIsAtBoundary_returnsCreated() throws Exception {
        ObjectNode request = baseValidRequest();
        request.put("submission_company_name", "a".repeat(160));

        postCreateObjection(request)
                .andExpect(status().isCreated());

        verify(strikeOffPartnerObjectionService).createObjection(eq(COMPANY_NUMBER), any());
    }

    @ParameterizedTest
    @MethodSource("invalidReasonCases")
    void createObjection_whenReasonIsInvalid_returnsExpectedErrorCode(Consumer<ObjectNode> requestMutator) throws Exception {
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
    void createObjection_whenReasonIsValid_returnsCreated(String reason) throws Exception {
        ObjectNode request = baseValidRequest();
        request.put("partner_objection_reason", reason);

        postCreateObjection(request)
                .andExpect(status().isCreated());

        verify(strikeOffPartnerObjectionService).createObjection(eq(COMPANY_NUMBER), any());
    }

    @ParameterizedTest
    @MethodSource("workstreamCases")
    void createObjection_whenWorkstreamIsInvalid_returnsExpectedErrorCode(Consumer<ObjectNode> requestMutator,
                                                String expectedErrorCode) throws Exception {
        ObjectNode request = baseValidRequest();
        requestMutator.accept(request);

        assertBadRequestWithoutServiceCall(request, expectedErrorCode);
    }

    @Test
    void createObjection_whenMultipleFieldsAreInvalid_returnsMultipleErrors() throws Exception {
        ObjectNode request = baseValidRequest();
        request.put("partner_contact_email", "");
        request.put("partner_case_reference", "");
        request.putNull("partner_objection_workstream");

        postCreateObjection(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("MISSING_REQUIRED_PARAMETER, MISSING_WORKSTREAM"));
        verifyNoInteractions(strikeOffPartnerObjectionService);
    }

    @Test
    void createObjection_whenValidationFails_hasNoSideEffects() throws Exception {
        ObjectNode request = baseValidRequest();
        request.put("partner_contact_email", "bad");

        assertBadRequestWithoutServiceCall(request, EMAIL_INCORRECT_FORMAT);
    }

    @Test
    void createObjection_whenValidationSucceeds_triggersService() throws Exception {
        postCreateObjection(baseValidRequest())
                .andExpect(status().isCreated());

        verify(strikeOffPartnerObjectionService).createObjection(eq(COMPANY_NUMBER), any());
    }

    @Test
    void createObjection_whenCaseReferenceIsBlank_returnsMissingRequiredParameter() throws Exception {
        ObjectNode request = baseValidRequest();
        request.put("partner_case_reference", "");

        assertBadRequestWithoutServiceCall(request, MISSING_REQUIRED_PARAMETER);
    }

    @Test
    void createObjection_whenCaseReferenceIsNull_returnsMissingRequiredParameter() throws Exception {
        ObjectNode request = baseValidRequest();
        request.putNull("partner_case_reference");

        assertBadRequestWithoutServiceCall(request, MISSING_REQUIRED_PARAMETER);
    }

    @Test
    void createObjection_whenCompanyNameIsBlank_returnsMissingRequiredParameter() throws Exception {
        ObjectNode request = baseValidRequest();
        request.put("submission_company_name", "");

        assertBadRequestWithoutServiceCall(request, MISSING_REQUIRED_PARAMETER);
    }

    @Test
    void createObjection_whenCompanyNameIsNull_returnsMissingRequiredParameter() throws Exception {
        ObjectNode request = baseValidRequest();
        request.putNull("submission_company_name");

        assertBadRequestWithoutServiceCall(request, MISSING_REQUIRED_PARAMETER);
    }

    @ParameterizedTest
    @MethodSource("wrongTypeCases")
    void createObjection_whenFieldTypeIsWrong_returnsMissingRequiredParameter(String fieldName, JsonNode wrongTypeValue) throws Exception {
        ObjectNode request = baseValidRequest();
        request.set(fieldName, wrongTypeValue);

        assertBadRequestWithoutServiceCall(request, MISSING_REQUIRED_PARAMETER);
    }

    @Test
    void createObjection_whenJsonIsMalformed_returnsMissingRequiredParameter() throws Exception {
        mockMvc.perform(post(CREATE_OBJECTION_URL)
                        .contentType(APPLICATION_JSON)
                        .header("X-Request-Id", "test-request-id")
                        .header("ERIC-Identity-Type", "key")
                        .header("CHS_API_KEY", "test-api-key")
                        .content("{\"partner_contact_email\":\"valid@email.com\","))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value(MISSING_REQUIRED_PARAMETER));

        verifyNoInteractions(strikeOffPartnerObjectionService);
    }

    @Test
    void createObjection_whenEmailHasWhitespace_trimsAndAcceptsValue() throws Exception {
        ObjectNode request = baseValidRequest();
        request.put("partner_contact_email", " test@test.com ");

        postCreateObjection(request)
                .andExpect(status().isCreated());

        ArgumentCaptor<CreateObjectionRequest> requestCaptor = ArgumentCaptor.forClass(CreateObjectionRequest.class);
        verify(strikeOffPartnerObjectionService).createObjection(eq(COMPANY_NUMBER), requestCaptor.capture());
        assertEquals("test@test.com", requestCaptor.getValue().getPartnerContactEmail());
    }

    @Test
    void createObjection_whenCaseReferenceHasWhitespace_trimsAndAcceptsValue() throws Exception {
        ObjectNode request = baseValidRequest();
        request.put("partner_case_reference", " CASE123 ");

        postCreateObjection(request)
                .andExpect(status().isCreated());

        ArgumentCaptor<CreateObjectionRequest> requestCaptor = ArgumentCaptor.forClass(CreateObjectionRequest.class);
        verify(strikeOffPartnerObjectionService).createObjection(eq(COMPANY_NUMBER), requestCaptor.capture());
        assertEquals("CASE123", requestCaptor.getValue().getPartnerCaseReference());
    }

    @Test
    void createObjection_whenCompanyNameHasWhitespace_trimsAndAcceptsValue() throws Exception {
        ObjectNode request = baseValidRequest();
        request.put("submission_company_name", " Valid Company Ltd ");

        postCreateObjection(request)
                .andExpect(status().isCreated());

        ArgumentCaptor<CreateObjectionRequest> requestCaptor = ArgumentCaptor.forClass(CreateObjectionRequest.class);
        verify(strikeOffPartnerObjectionService).createObjection(eq(COMPANY_NUMBER), requestCaptor.capture());
        assertEquals("Valid Company Ltd", requestCaptor.getValue().getSubmissionCompanyName());
    }

    @Test
    void createObjection_whenServiceThrowsException_returns500() throws Exception {
        when(strikeOffPartnerObjectionService.createObjection(eq(COMPANY_NUMBER), any()))
                .thenThrow(new RuntimeException("Internal service error"));

        postCreateObjection(baseValidRequest())
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error_code").value("internal_server_error"))
                .andExpect(jsonPath("$.message").value("Internal Server Error"));
    }

    @Test
    void createObjection_whenServiceThrowsResponseStatusException_preservesStatusAndReason() throws Exception {
        when(strikeOffPartnerObjectionService.createObjection(eq(COMPANY_NUMBER), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Duplicate objection"));

        postCreateObjection(baseValidRequest())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error_code").value("conflict"))
                .andExpect(jsonPath("$.message").value("Duplicate objection"));
    }

    @Test
    void createObjection_whenResponseStatusExceptionHasNoReason_usesFallbackMessage() throws Exception {
        when(strikeOffPartnerObjectionService.createObjection(eq(COMPANY_NUMBER), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

        postCreateObjection(baseValidRequest())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error_code").value("not_found"))
                .andExpect(jsonPath("$.message").value("Request failed"));
    }

    @Test
    void createObjection_whenServiceThrowsErrorResponseException_preservesStatus() throws Exception {
        when(strikeOffPartnerObjectionService.createObjection(eq(COMPANY_NUMBER), any()))
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
    void createObjection_whenResponseStatusCodeIsNonStandard_usesGenericErrorCode() throws Exception {
        when(strikeOffPartnerObjectionService.createObjection(eq(COMPANY_NUMBER), any()))
                .thenThrow(new ResponseStatusException(HttpStatusCode.valueOf(499)));

        postCreateObjection(baseValidRequest())
                .andExpect(status().is(499))
                .andExpect(jsonPath("$.error_code").value("error"))
                .andExpect(jsonPath("$.message").value("Request failed"));
    }

    @ParameterizedTest
    @MethodSource("gatewayErrorCases")
    void createObjection_whenServiceThrowsGatewayError_returnsCorrectStatus(
            HttpStatus status, String expectedErrorCode) throws Exception {
        when(strikeOffPartnerObjectionService.createObjection(eq(COMPANY_NUMBER), any()))
                .thenThrow(new ResponseStatusException(status, status.getReasonPhrase()));

        postCreateObjection(baseValidRequest())
                .andExpect(status().is(status.value()))
                .andExpect(jsonPath("$.error_code").value(expectedErrorCode));
    }

    static Stream<Arguments> gatewayErrorCases() {
        return Stream.of(
                Arguments.of(HttpStatus.BAD_GATEWAY,          "bad_gateway"),
                Arguments.of(HttpStatus.SERVICE_UNAVAILABLE,  "service_unavailable"),
                Arguments.of(HttpStatus.GATEWAY_TIMEOUT,      "gateway_timeout"),
                Arguments.of(HttpStatus.INTERNAL_SERVER_ERROR,      "internal_server_error")
        );
    }


    private ResultActions postCreateObjection(JsonNode payload) throws Exception {
        return mockMvc.perform(post(CREATE_OBJECTION_URL)
                .contentType(APPLICATION_JSON)
                .header("X-Request-Id", "test-request-id")
                .header("ERIC-Identity-Type", "key")
                .header("CHS_API_KEY", "test-api-key")
                .content(objectMapper.writeValueAsString(payload)));
    }

    private ResultActions postCreateObjectionWithoutBody() throws Exception {
        return mockMvc.perform(post(CREATE_OBJECTION_URL)
                .contentType(APPLICATION_JSON)
                .header("X-Request-Id", "test-request-id")
                .header("ERIC-Identity-Type", "key")
                .header("CHS_API_KEY", "test-api-key"));
    }

    private ResultActions performGetObjection(String companyNumber) throws Exception {
        return performGetObjection(companyNumber, OBJECTION_ID);
    }

    private ResultActions performGetObjection(String companyNumber, String objectionId) throws Exception {
        return mockMvc.perform(get(String.format(GET_OBJECTION_URL, companyNumber, objectionId))
                .contentType(APPLICATION_JSON)
                .header("X-Request-Id", "test-request-id")
                .header("ERIC-Identity-Type", "key")
                .header("CHS_API_KEY", "test-api-key"));
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
                        MISSING_WORKSTREAM)
        );
    }

    private void assertBadRequestWithoutServiceCall(JsonNode payload, String expectedErrorCode) throws Exception {
        postCreateObjection(payload)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value(expectedErrorCode));
        verifyNoInteractions(strikeOffPartnerObjectionService);
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
        response.setCompanyNumber(COMPANY_NUMBER);
        response.setSubmissionCompanyName("Valid Company Ltd");
        response.setObjectionId("objection-123");
        response.setPartnerCaseReference("CASE123");
        response.setPartnerObjectionWorkstream("debt-management");
        response.setPartnerObjectionReason(PartnerObjectionReason.OTHER);
        response.setPartnerContactEmail("valid@email.com");
        response.setProcessingStatus(ObjectionProcessingStatus.OBJECTION_SUBMITTED);
        response.setLinks(new BaseObjectionResponseLinks()
                .self("/company/12345678/strike-off-partner-objections/objection-123"));
        response.setKind("strike-off-partner-objection#objection");
        response.setCreatedAt(OffsetDateTime.parse("2026-06-03T12:00:00Z"));
        response.setEtag("etag-1");
        response.setProcessingStatusChangedAt(OffsetDateTime.parse("2026-06-03T13:00:00Z"));
        response.setInitialExpirationOn(OffsetDateTime.parse("2026-12-03T12:00:00Z"));
        response.setFailureReason(FailureReason.COMPANY_HAS_BEEN_DISSOLVED);
        return response;
    }

}
