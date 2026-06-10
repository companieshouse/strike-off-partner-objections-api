package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.gov.companieshouse.api.objections.model.PartnerObjectionWorkstream;
import uk.gov.companieshouse.api.objections.model.WithdrawAllObjections201Response;
import uk.gov.companieshouse.api.objections.model.WithdrawAllObjectionsRequest;
import uk.gov.companieshouse.api.objections.model.WithdrawalRequestedStatus;

class StrikeOffPartnerWithdrawalsServiceTest {

    private static final String COMPANY_NUMBER = "12345678";
    private final StrikeOffPartnerWithdrawalsService strikeOffPartnerWithdrawalsService =
            new StrikeOffPartnerWithdrawalsService();

    @Test
    void withdrawAllObjectionsReturnsPopulatedWithdrawalResponse() {
        WithdrawAllObjectionsRequest request = new WithdrawAllObjectionsRequest();
        request.setSubmissionCompanyName("ACME LTD");
        request.setPartnerCaseReference("CASE-001");
        request.setPartnerContactEmail("owner@example.com");
        request.setPartnerObjectionWorkstream(PartnerObjectionWorkstream.values()[0]);

        WithdrawAllObjections201Response response =
                strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request);

        assertEquals(COMPANY_NUMBER, response.getCompanyNumber());
        assertEquals("ACME LTD", response.getSubmissionCompanyName());
        assertEquals("CASE-001", response.getPartnerCaseReference());
        assertEquals("owner@example.com", response.getPartnerContactEmail());
        assertEquals(PartnerObjectionWorkstream.values()[0], response.getPartnerObjectionWorkstream());
        assertEquals(WithdrawalRequestedStatus.WITHDRAWAL_REQUESTED, response.getProcessingStatus());
        assertEquals("strike-off-partner-objection#withdrawal", response.getKind());

        assertNotNull(response.getWithdrawalId());
        assertDoesNotThrow(() -> UUID.fromString(response.getWithdrawalId()));
        assertNotNull(response.getCreatedAt());
        assertNotNull(response.getEtag());
        assertNotNull(response.getLinks());
        assertNotNull(response.getLinks().getSelf());
        assertTrue(response.getLinks().getSelf().contains(
                "/company/" + COMPANY_NUMBER + "/strike-off-partner-objections-withdrawals"));
    }
}