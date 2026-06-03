package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.dto.BaseObjectionResponse;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.dto.CreateObjectionRequest;

class StrikeOffObjectionServiceTest {

    private final StrikeOffObjectionService strikeOffObjectionService = new StrikeOffObjectionService();

    @Test
    void createObjectionReturnsPopulatedBaseObjectionResponse() {
        CreateObjectionRequest request = new CreateObjectionRequest();
        request.setSubmissionCompanyName("ACME LTD");
        request.setPartnerCaseReference("CASE-001");
        request.setPartnerObjectionWorkstream("DS01");
        request.setPartnerContactEmail("owner@example.com");
        request.setPartnerObjectionReason("Supporting documents provided");

        BaseObjectionResponse response = strikeOffObjectionService.createObjection("12345678", request);

        assertNotNull(response.getObjectionId());
        assertEquals("PENDING", response.getProcessingStatus());
        assertNotNull(response.getLinks());
        assertTrue(response.getLinks().get("self").contains("/company/12345678/strike-off-partner-objections/"));
        assertNotNull(response.getCreatedAt());
        assertNotNull(response.getEtag());
    }
}

