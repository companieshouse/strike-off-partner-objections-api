package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uk.gov.companieshouse.api.objections.model.BaseObjectionResponse;
import uk.gov.companieshouse.api.objections.model.CreateObjectionRequest;
import uk.gov.companieshouse.api.objections.model.ObjectionProcessingStatus;
import uk.gov.companieshouse.api.objections.model.PartnerObjectionReason;
import uk.gov.companieshouse.api.objections.model.PartnerObjectionWorkstream;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.config.MongoDbIntegration;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.ObjectionDocument;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.repository.ObjectionRepository;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@Tag("integration-test")
class StrikeOffObjectionPartnerIntegrationTest extends MongoDbIntegration {

    @Autowired
    private StrikeOffObjectionPartnerService strikeOffObjectionPartnerService;

    @Autowired
    private ObjectionRepository objectionRepository;

    @BeforeEach
    void setUp() {
        objectionRepository.deleteAll();
    }

    @Test
    void createObjectionPersistsDocumentInMongo() {
        String companyNumber = "01234567";
        CreateObjectionRequest request = buildValidRequest();

        Instant before = Instant.now();
        BaseObjectionResponse response = strikeOffObjectionPartnerService.createObjection(companyNumber, request);
        Instant after = Instant.now();

        List<ObjectionDocument> savedDocs = objectionRepository.findAll();
        assertThat(savedDocs).hasSize(1);

        ObjectionDocument saved = savedDocs.getFirst();
        assertSavedDocument(saved, companyNumber, before, after);

        assertResponse(response, saved);
    }

    @Test
    void getObjectionFetchesDocumentInMongo() {
        String companyNumber = "01234567";
        CreateObjectionRequest request = buildValidRequest();

        BaseObjectionResponse created = strikeOffObjectionPartnerService.createObjection(companyNumber, request);

        BaseObjectionResponse fetched = strikeOffObjectionPartnerService.getObjection(
                companyNumber,
                created.getObjectionId());

        List<ObjectionDocument> savedDocs = objectionRepository.findAll();
        assertThat(savedDocs).hasSize(1);
        ObjectionDocument saved = savedDocs.getFirst();

        assertResponse(fetched, saved);
        assertThat(fetched.getCompanyNumber()).isEqualTo(companyNumber);
        assertThat(fetched.getSubmissionCompanyName()).isEqualTo(request.getSubmissionCompanyName());
        assertThat(fetched.getPartnerCaseReference()).isEqualTo(request.getPartnerCaseReference());
        assertThat(fetched.getPartnerContactEmail()).isEqualTo(request.getPartnerContactEmail());
    }

    private static void assertResponse(BaseObjectionResponse response, ObjectionDocument saved) {
        assertThat(response).isNotNull();
        assertThat(response.getObjectionId()).isEqualTo(saved.getObjectionId());
        assertThat(response.getEtag()).isEqualTo(saved.getEtag());
        assertThat(response.getProcessingStatus()).isEqualTo(ObjectionProcessingStatus.OBJECTION_SUBMITTED);
        assertThat(response.getLinks()).isNotNull();
        assertThat(response.getLinks().getSelf()).isEqualTo(saved.getLinks().getSelf());
        assertThat(response.getLinks().getCompanyProfile()).isEqualTo(saved.getLinks().getCompanyProfile());
        assertThat(response.getCreatedAt()).isNotNull();
    }

    private static void assertSavedDocument(ObjectionDocument saved, String companyNumber, Instant before, Instant after) {
        assertThat(saved.getCompanyNumber()).isEqualTo(companyNumber);
        assertThat(saved.getPartnerOrganisation()).isEqualTo("hmrc");
        assertThat(saved.getSubmissionCompanyName()).isEqualTo("Acme Limited");
        assertThat(saved.getPartnerCaseReference()).isEqualTo("CASE-123");
        assertThat(saved.getPartnerContactEmail()).isEqualTo("test@example.com");
        assertThat(saved.getPartnerObjectionWorkstream()).isEqualTo(PartnerObjectionWorkstream.DEBT_MANAGEMENT.getValue());
        assertThat(saved.getPartnerObjectionReason()).isEqualTo(PartnerObjectionReason.OTHER.getValue());
        assertThat(saved.getId()).isNotBlank();
        assertThat(saved.getEtag()).isNotBlank();
        assertThat(saved.getProcessingStatus()).isEqualTo(ObjectionProcessingStatus.OBJECTION_SUBMITTED.getValue());
        assertThat(saved.getKind()).isEqualTo("strike-off-partner-objection#objection");
        assertThat(saved.getLinks()).isNotNull();
        assertThat(saved.getLinks().getCompanyProfile()).isEqualTo("/company/01234567");
        assertThat(saved.getLinks().getSelf()).startsWith("/company/01234567/strike-off-partner-objections/");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getCreatedAt()).isAfterOrEqualTo(before);
        assertThat(saved.getCreatedAt()).isBeforeOrEqualTo(after);
    }

    private CreateObjectionRequest buildValidRequest() {
        CreateObjectionRequest request = new CreateObjectionRequest();
        request.setSubmissionCompanyName("Acme Limited");
        request.setPartnerCaseReference("CASE-123");
        request.setPartnerContactEmail("test@example.com");
        request.setPartnerObjectionWorkstream(PartnerObjectionWorkstream.DEBT_MANAGEMENT);
        request.setPartnerObjectionReason(PartnerObjectionReason.OTHER);
        return request;
    }
}
