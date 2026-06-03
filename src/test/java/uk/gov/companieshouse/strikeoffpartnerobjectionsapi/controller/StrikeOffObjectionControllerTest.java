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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.dto.BaseObjectionResponse;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service.StrikeOffObjectionService;

import java.time.Instant;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class StrikeOffObjectionControllerTest {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private StrikeOffObjectionService strikeOffObjectionService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void createObjectionReturnsCreatedAndResponseBody() throws Exception {
        when(strikeOffObjectionService.createObjection(eq("12345678"), any()))
                .thenReturn(new BaseObjectionResponse(
                        "objection-123",
                        "PENDING",
                        Map.of("self", "/company/12345678/strike-off-partner-objections/objection-123"),
                        Instant.parse("2026-06-03T12:00:00Z"),
                        "etag-1"));

        mockMvc.perform(post("/strike-off-partner-objections-api/company/12345678/strike-off-partner-objections")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  \"submission_company_name\": \"ACME LTD\",
                                  \"partner_case_reference\": \"CASE-123\",
                                  \"partner_objection_workstream\": \"DS01\",
                                  \"partner_contact_email\": \"case.owner@example.com\",
                                  \"partner_objection_reason\": \"Evidence supplied by partner\"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.objection_id").value("objection-123"))
                .andExpect(jsonPath("$.processing_status").value("PENDING"))
                .andExpect(jsonPath("$.links.self").value(
                        "/company/12345678/strike-off-partner-objections/objection-123"))
                .andExpect(jsonPath("$.created_at").value("2026-06-03T12:00:00Z"))
                .andExpect(jsonPath("$.etag").value("etag-1"));

        verify(strikeOffObjectionService).createObjection(eq("12345678"), any());
    }

    @Test
    void createObjectionWithMissingFieldsReturns400BadRequest() throws Exception {
        mockMvc.perform(post("/strike-off-partner-objections-api/company/12345678/strike-off-partner-objections")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  \"submission_company_name\": \"ACME LTD\"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid request: missing or invalid required fields"));
    }

    @Test
    void createObjectionWithEmptyFieldsReturns400BadRequest() throws Exception {
        mockMvc.perform(post("/strike-off-partner-objections-api/company/12345678/strike-off-partner-objections")
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
                .andExpect(jsonPath("$.error").value("Invalid request: missing or invalid required fields"));
    }

    @Test
    void createObjectionWhenServiceThrowsExceptionReturns500() throws Exception {
        when(strikeOffObjectionService.createObjection(eq("12345678"), any()))
                .thenThrow(new RuntimeException("Internal service error"));

        mockMvc.perform(post("/strike-off-partner-objections-api/company/12345678/strike-off-partner-objections")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  \"submission_company_name\": \"ACME LTD\",
                                  \"partner_case_reference\": \"CASE-123\",
                                  \"partner_objection_workstream\": \"DS01\",
                                  \"partner_contact_email\": \"case.owner@example.com\",
                                  \"partner_objection_reason\": \"Evidence supplied by partner\"
                                }
                                """))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void createObjectionWhenDatabaseUnavailableReturns500() throws Exception {
        when(strikeOffObjectionService.createObjection(eq("12345678"), any()))
                .thenThrow(new RuntimeException("Database connection failed"));

        mockMvc.perform(post("/strike-off-partner-objections-api/company/12345678/strike-off-partner-objections")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  \"submission_company_name\": \"ACME LTD\",
                                  \"partner_case_reference\": \"CASE-123\",
                                  \"partner_objection_workstream\": \"DS01\",
                                  \"partner_contact_email\": \"case.owner@example.com\",
                                  \"partner_objection_reason\": \"Evidence supplied by partner\"
                                }
                                """))
                .andExpect(status().isInternalServerError());
    }
}

