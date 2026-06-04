package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import uk.gov.companieshouse.api.objections.model.BaseObjectionResponse;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.ObjectionPersistenceException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service.ObjectionService;

@ExtendWith(MockitoExtension.class)
class StrikeOffPartnerObjectionsControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ObjectionService objectionService;

    @BeforeEach
    void setUp() {
        StrikeOffPartnerObjectionsController controller =
                new StrikeOffPartnerObjectionsController(objectionService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void createObjectionReturnsCreatedWhenPersisted() throws Exception {
        BaseObjectionResponse response = new BaseObjectionResponse();
        response.setCompanyNumber("01234567");
        response.setObjectionId("obj-123");

        when(objectionService.createObjection(eq("01234567"), any())).thenReturn(response);

        mockMvc.perform(post("/company/01234567/strike-off-partner-objections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "submission_company_name": "Acme Limited",
                                  "partner_case_reference": "CASE-123",
                                  "partner_objection_workstream": "debt-management",
                                  "partner_contact_email": "test@example.com",
                                  "partner_objection_reason": "other"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.company_number").value("01234567"))
                .andExpect(jsonPath("$.objection_id").value("obj-123"));
    }

    @Test
    void createObjectionReturnsServerErrorWhenPersistenceFails() throws Exception {
        when(objectionService.createObjection(eq("01234567"), any()))
                .thenThrow(new ObjectionPersistenceException("mongo failed", new RuntimeException()));

        mockMvc.perform(post("/company/01234567/strike-off-partner-objections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "submission_company_name": "Acme Limited",
                                  "partner_case_reference": "CASE-123",
                                  "partner_objection_workstream": "debt-management",
                                  "partner_contact_email": "test@example.com",
                                  "partner_objection_reason": "other"
                                }
                                """))
                .andExpect(status().is5xxServerError());
    }
}
