package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
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

    @Test
    void getAllWithdrawalsReturnsNotImplemented() {
        ResponseEntity<WithdrawAllObjectionsResponse> response =
                strikeOffPartnerWithdrawalsController.getAllWithdrawals("12345678", "withdrawal-123");

        assertEquals(HttpStatus.NOT_IMPLEMENTED, response.getStatusCode());
        assertNull(response.getBody());
    }

    @Test
    void withdrawAllObjectionsReturnsCreatedAndDelegatesToService() {
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
    void withdrawAllObjectionsWithMissingFieldsReturnsBadRequestErrorResponse() throws Exception {
        mockMvc().perform(post(WITHDRAWALS_PATH)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "submission_company_name": "ACME LTD"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("bad_request"))
                .andExpect(jsonPath("$.message").value("Invalid request: missing or invalid required fields"));
    }

    @Test
    void withdrawAllObjectionsWhenServiceThrowsRuntimeExceptionReturnsInternalServerErrorResponse() throws Exception {
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
    void withdrawAllObjectionsWhenServiceThrowsResponseStatusExceptionReturnsMappedErrorResponse() throws Exception {
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
    void withdrawAllObjectionsWhenServiceThrowsUnauthorizedReturnsUnauthorizedErrorResponse() throws Exception {
        assertResponseStatusExceptionResponse(HttpStatus.UNAUTHORIZED, "Unauthorized access", "unauthorized");
    }

    @Test
    void withdrawAllObjectionsWhenServiceThrowsForbiddenReturnsForbiddenErrorResponse() throws Exception {
        assertResponseStatusExceptionResponse(HttpStatus.FORBIDDEN, "Forbidden action", "forbidden");
    }

    @Test
    void withdrawAllObjectionsWhenServiceThrowsBadGatewayReturnsBadGatewayErrorResponse() throws Exception {
        assertResponseStatusExceptionResponse(HttpStatus.BAD_GATEWAY, "Upstream bad gateway", "bad_gateway");
    }

    @Test
    void withdrawAllObjectionsWhenServiceThrowsServiceUnavailableReturnsServiceUnavailableErrorResponse() throws Exception {
        assertResponseStatusExceptionResponse(HttpStatus.SERVICE_UNAVAILABLE,
                "Dependency unavailable",
                "service_unavailable");
    }

    @Test
    void withdrawAllObjectionsWhenServiceThrowsGatewayTimeoutReturnsGatewayTimeoutErrorResponse() throws Exception {
        assertResponseStatusExceptionResponse(HttpStatus.GATEWAY_TIMEOUT, "Upstream timeout", "gateway_timeout");
    }

    @Test
    void withdrawAllObjectionsWhenServiceThrowsInternalServerErrorStatusReturnsInternalServerErrorResponse() throws Exception {
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

