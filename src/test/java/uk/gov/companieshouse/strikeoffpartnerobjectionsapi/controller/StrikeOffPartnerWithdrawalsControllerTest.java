package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.companieshouse.api.objections.model.PartnerObjectionWorkstream;
import uk.gov.companieshouse.api.objections.model.WithdrawAllObjections201Response;
import uk.gov.companieshouse.api.objections.model.WithdrawAllObjectionsRequest;
import uk.gov.companieshouse.api.objections.model.WithdrawAllObjectionsResponse;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.errorhandler.GlobalExceptionHandler;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service.StrikeOffPartnerWithdrawalsService;

@ExtendWith(MockitoExtension.class)
class StrikeOffPartnerWithdrawalsControllerTest {

    private static final String WITHDRAWALS_PATH = "/company/12345678/strike-off-partner-objections-withdrawals";
    private static final String GET_WITHDRAWAL_PATH = "/company/12345678/strike-off-partner-objections-withdrawals/withdrawal-123";
    private static final String VALID_WITHDRAWAL_REQUEST = """
            {
              "submission_company_name": "ACME LTD",
              "partner_case_reference": "CASE-123",
              "partner_objection_workstream": "individuals-and-small-business-compliance",
              "partner_contact_email": "case.owner@example.com"
            }
            """;

    @Mock
    private StrikeOffPartnerWithdrawalsService strikeOffPartnerWithdrawalsService;

    @InjectMocks
    private StrikeOffPartnerWithdrawalsController strikeOffPartnerWithdrawalsController;

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(strikeOffPartnerWithdrawalsController)
                .setControllerAdvice(new GlobalExceptionHandler())
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

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(response, result.getBody());
        verify(strikeOffPartnerWithdrawalsService).getWithdrawal("12345678", "withdrawal-123");
    }

    @Test
    void getAllWithdrawals_returnsNotFoundError_whenWithdrawalNotFound() throws Exception {
        when(strikeOffPartnerWithdrawalsService.getWithdrawal("12345678", "withdrawal-123"))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Withdrawal not found: withdrawalId=withdrawal-123 for company=12345678"));

        mockMvc().perform(get(GET_WITHDRAWAL_PATH))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error_code").value("not_found"))
                .andExpect(jsonPath("$.message").value("Withdrawal not found: withdrawalId=withdrawal-123 for company=12345678"));
    }

    @Test
    void getAllWithdrawals_returnsNotFoundError_whenCompanyNumberDoesNotMatch() throws Exception {
        when(strikeOffPartnerWithdrawalsService.getWithdrawal("12345678", "withdrawal-123"))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Withdrawal not found: withdrawalId=withdrawal-123 for company=12345678"));

        mockMvc().perform(get(GET_WITHDRAWAL_PATH))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error_code").value("not_found"));
    }

    @Test
    void getAllWithdrawals_returnsUnauthorizedErrorResponse_whenAuthenticationFails() throws Exception {
        when(strikeOffPartnerWithdrawalsService.getWithdrawal("12345678", "withdrawal-123"))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized access"));

        mockMvc().perform(get(GET_WITHDRAWAL_PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value("unauthorized"))
                .andExpect(jsonPath("$.message").value("Unauthorized access"));
    }

    @Test
    void getAllWithdrawals_returnsForbiddenErrorResponse_whenAuthorizationFails() throws Exception {
        when(strikeOffPartnerWithdrawalsService.getWithdrawal("12345678", "withdrawal-123"))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden access"));

        mockMvc().perform(get(GET_WITHDRAWAL_PATH))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("forbidden"))
                .andExpect(jsonPath("$.message").value("Forbidden access"));
    }

    @Test
    void getAllWithdrawals_returnsInternalServerErrorResponse_whenServiceThrowsRuntimeException() throws Exception {
        when(strikeOffPartnerWithdrawalsService.getWithdrawal("12345678", "withdrawal-123"))
                .thenThrow(new RuntimeException("Unexpected error"));

        mockMvc().perform(get(GET_WITHDRAWAL_PATH))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error_code").value("internal_server_error"))
                .andExpect(jsonPath("$.message").value("Internal Server Error"));
    }

    @Test
    void getAllWithdrawals_returnsBadGatewayErrorResponse_whenServiceThrowsBadGatewayException() throws Exception {
        when(strikeOffPartnerWithdrawalsService.getWithdrawal("12345678", "withdrawal-123"))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Upstream bad gateway"));

        mockMvc().perform(get(GET_WITHDRAWAL_PATH))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error_code").value("bad_gateway"))
                .andExpect(jsonPath("$.message").value("Upstream bad gateway"));
    }

    @Test
    void getAllWithdrawals_returnsServiceUnavailableErrorResponse_whenServiceUnavailable() throws Exception {
        when(strikeOffPartnerWithdrawalsService.getWithdrawal("12345678", "withdrawal-123"))
                .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Service unavailable"));

        mockMvc().perform(get(GET_WITHDRAWAL_PATH))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error_code").value("service_unavailable"))
                .andExpect(jsonPath("$.message").value("Service unavailable"));
    }

    @Test
    void getAllWithdrawals_returnsGatewayTimeoutErrorResponse_whenGatewayTimeout() throws Exception {
        when(strikeOffPartnerWithdrawalsService.getWithdrawal("12345678", "withdrawal-123"))
                .thenThrow(new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "Gateway timeout"));

        mockMvc().perform(get(GET_WITHDRAWAL_PATH))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.error_code").value("gateway_timeout"))
                .andExpect(jsonPath("$.message").value("Gateway timeout"));
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

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
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
                .andExpect(jsonPath("$.error_code").value("MISSING_REQUIRED_PARAMETER, MISSING_WORKSTREAM"))
                .andExpect(jsonPath("$.message").value("Invalid Message"));
    }

    @Test
    void withdrawAllObjections_returnsInternalServerErrorResponse_whenServiceThrowsRuntimeException() throws Exception {
        when(strikeOffPartnerWithdrawalsService.withdrawAllObjections(org.mockito.ArgumentMatchers.eq("12345678"),
                org.mockito.ArgumentMatchers.any()))
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
        when(strikeOffPartnerWithdrawalsService.withdrawAllObjections(org.mockito.ArgumentMatchers.eq("12345678"),
                org.mockito.ArgumentMatchers.any()))
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
        when(strikeOffPartnerWithdrawalsService.withdrawAllObjections(org.mockito.ArgumentMatchers.eq("12345678"),
                org.mockito.ArgumentMatchers.any()))
                .thenThrow(new ResponseStatusException(status, reason));

        mockMvc().perform(post(WITHDRAWALS_PATH)
                        .contentType(APPLICATION_JSON)
                        .content(VALID_WITHDRAWAL_REQUEST))
                .andExpect(status().is(status.value()))
                .andExpect(jsonPath("$.error_code").value(expectedErrorCode))
                .andExpect(jsonPath("$.message").value(reason));
    }
}
