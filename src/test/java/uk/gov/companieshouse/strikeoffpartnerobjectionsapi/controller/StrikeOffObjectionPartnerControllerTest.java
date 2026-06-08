package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.companieshouse.api.objections.model.BaseObjectionResponse;
import uk.gov.companieshouse.api.objections.model.BaseObjectionResponseLinks;
import uk.gov.companieshouse.api.objections.model.ObjectionProcessingStatus;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service.StrikeOffObjectionPartnerService;

import java.time.OffsetDateTime;


import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class StrikeOffObjectionPartnerControllerTest {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private StrikeOffObjectionPartnerService strikeOffObjectionPartnerService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void createObjectionReturnsCreatedAndResponseBody() throws Exception {
        BaseObjectionResponse response = new BaseObjectionResponse();
        response.setObjectionId("objection-123");
        response.setProcessingStatus(ObjectionProcessingStatus.OBJECTION_SUBMITTED);
        response.setLinks(new BaseObjectionResponseLinks()
                .self("/company/12345678/strike-off-partner-objections/objection-123"));
        response.setCreatedAt(OffsetDateTime.parse("2026-06-03T12:00:00Z"));
        response.setEtag("etag-1");

        when(strikeOffObjectionPartnerService.createObjection(eq("12345678"), any()))
                .thenReturn(response);

        mockMvc.perform(post("/company/12345678/strike-off-partner-objections")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  \"submission_company_name\": \"ACME LTD\",
                                  \"partner_case_reference\": \"CASE-123\",
                                  \"partner_objection_workstream\": \"individuals-and-small-business-compliance\",
                                  \"partner_contact_email\": \"case.owner@example.com\",
                                  \"partner_objection_reason\": \"compliance-issue-outstanding\"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.objection_id").value("objection-123"))
                .andExpect(jsonPath("$.processing_status").value("objection-submitted"))
                .andExpect(jsonPath("$.links.self").value(
                        "/company/12345678/strike-off-partner-objections/objection-123"))
                .andExpect(jsonPath("$.created_at").value("2026-06-03T12:00:00Z"))
                .andExpect(jsonPath("$.etag").value("etag-1"));

        verify(strikeOffObjectionPartnerService).createObjection(eq("12345678"), any());
    }

    @Test
    void createObjectionWithMissingFieldsReturns400BadRequest() throws Exception {
        mockMvc.perform(post("/company/12345678/strike-off-partner-objections")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  \"submission_company_name\": \"ACME LTD\"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("bad_request"))
                .andExpect(jsonPath("$.message").value("Invalid request: missing or invalid required fields"));
    }

    @Test
    void createObjectionWithEmptyFieldsReturns400BadRequest() throws Exception {
        mockMvc.perform(post("/company/12345678/strike-off-partner-objections")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  \"submission_company_name\": \"\",
                                  \"partner_case_reference\": \"\",
                                  \"partner_objection_workstream\": \"\",
                                  \"partner_contact_email\": \"\",
                                  \"partner_objection_reason\": \"\"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("bad_request"))
                .andExpect(jsonPath("$.message").value("Invalid request: missing or invalid required fields"));
    }

    @Test
    void createObjectionWhenServiceThrowsExceptionReturns500() throws Exception {
        when(strikeOffObjectionPartnerService.createObjection(eq("12345678"), any()))
                .thenThrow(new RuntimeException("Internal service error"));

        mockMvc.perform(post("/company/12345678/strike-off-partner-objections")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  \"submission_company_name\": \"ACME LTD\",
                                  \"partner_case_reference\": \"CASE-123\",
                                  \"partner_objection_workstream\": \"individuals-and-small-business-compliance\",
                                  \"partner_contact_email\": \"case.owner@example.com\",
                                  \"partner_objection_reason\": \"compliance-issue-outstanding\"
                                }
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error_code").value("internal_server_error"))
                .andExpect(jsonPath("$.message").value("Internal Server Error"));
    }

    @Test
    void createObjectionWhenDatabaseUnavailableReturns500() throws Exception {
        when(strikeOffObjectionPartnerService.createObjection(eq("12345678"), any()))
                .thenThrow(new RuntimeException("Database connection failed"));

        mockMvc.perform(post("/company/12345678/strike-off-partner-objections")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  \"submission_company_name\": \"ACME LTD\",
                                  \"partner_case_reference\": \"CASE-123\",
                                  \"partner_objection_workstream\": \"individuals-and-small-business-compliance\",
                                  \"partner_contact_email\": \"case.owner@example.com\",
                                  \"partner_objection_reason\": \"compliance-issue-outstanding\"
                                }
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error_code").value("internal_server_error"))
                .andExpect(jsonPath("$.message").value("Internal Server Error"));
    }

    @Test
    void createObjectionWhenServiceThrowsResponseStatusExceptionPreservesStatusAndReason() throws Exception {
        when(strikeOffObjectionPartnerService.createObjection(eq("12345678"), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Duplicate objection"));

        mockMvc.perform(post("/company/12345678/strike-off-partner-objections")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  \"submission_company_name\": \"ACME LTD\",
                                  \"partner_case_reference\": \"CASE-123\",
                                  \"partner_objection_workstream\": \"individuals-and-small-business-compliance\",
                                  \"partner_contact_email\": \"case.owner@example.com\",
                                  \"partner_objection_reason\": \"compliance-issue-outstanding\"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error_code").value("conflict"))
                .andExpect(jsonPath("$.message").value("Duplicate objection"));
    }
}
