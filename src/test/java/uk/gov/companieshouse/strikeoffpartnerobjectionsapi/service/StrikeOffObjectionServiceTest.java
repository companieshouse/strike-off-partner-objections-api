package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uk.gov.companieshouse.api.objections.model.BaseObjectionResponse;
import uk.gov.companieshouse.api.objections.model.CreateObjectionRequest;
import uk.gov.companieshouse.api.objections.model.ObjectionProcessingStatus;
import uk.gov.companieshouse.api.objections.model.PartnerObjectionReason;
import uk.gov.companieshouse.api.objections.model.PartnerObjectionWorkstream;


class StrikeOffObjectionServiceTest {

    private static final String COMPANY_NUMBER = "12345678";
    private final StrikeOffObjectionService strikeOffObjectionService = new StrikeOffObjectionService();

    @Test
    void createObjectionReturnsPopulatedBaseObjectionResponse() {
        CreateObjectionRequest request = new CreateObjectionRequest();
        request.setSubmissionCompanyName("ACME LTD");
        request.setPartnerCaseReference("CASE-001");
        request.setPartnerObjectionWorkstream(PartnerObjectionWorkstream.values()[0]);
        request.setPartnerContactEmail("owner@example.com");
        request.setPartnerObjectionReason(PartnerObjectionReason.values()[0]);

        BaseObjectionResponse response = strikeOffObjectionService.createObjection(COMPANY_NUMBER, request);

        assertNotNull(response.getObjectionId());
        assertEquals(ObjectionProcessingStatus.OBJECTION_SUBMITTED, response.getProcessingStatus());
        assertNotNull(response.getLinks());
        assertTrue(response.getLinks().getSelf().contains("/company/" + COMPANY_NUMBER + "/strike-off-partner-objections/"));
        assertNotNull(response.getCreatedAt());
        assertNotNull(response.getEtag());
    }
}
