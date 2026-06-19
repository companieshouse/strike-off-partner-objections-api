package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
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
                .andExpect(jsonPath("$.error_code").value("MISSING_REQUIRED_PARAMETER, MISSING_WORKSTREAM"))
                .andExpect(jsonPath("$.message").value("Invalid Message"));
    }
}
