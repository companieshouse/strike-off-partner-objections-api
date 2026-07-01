package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import org.assertj.core.api.AssertionsForClassTypes;
import org.assertj.core.api.AssertionsForInterfaceTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uk.gov.companieshouse.api.objections.model.BaseObjectionResponse;
import uk.gov.companieshouse.api.objections.model.CreateObjectionRequest;
import uk.gov.companieshouse.api.objections.model.ObjectionProcessingStatus;
import uk.gov.companieshouse.api.objections.model.PartnerObjectionReason;
import uk.gov.companieshouse.strikeoff.partner.objections.EventType;
import uk.gov.companieshouse.strikeoff.partner.objections.StrikeOffPartnerObjections;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.config.BaseTestIntegration;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.ObjectionDocument;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.repository.ObjectionRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils.StrikeoffPartnerObjectionsUtils.PARTNER_ORGANISATION;

@SpringBootTest
@Tag("integration-test")
class StrikeOffObjectionPartnerIntegrationTest extends BaseTestIntegration {
    private static final String COMPANY_NUMBER = "01234567";

    private static final String DEBT_MANAGEMENT_WORKSTREAM = "debt-management";

    @Autowired
    private StrikeOffPartnerObjectionService strikeOffPartnerObjectionService;

    @Autowired
    private ObjectionRepository objectionRepository;

    @BeforeEach
    void setUp() {
        objectionRepository.deleteAll();
        // Drain any leftover messages from previous tests
        testConsumer.poll(Duration.ofMillis(100));
    }

    @Test
    void createObjectionPersistsDocumentInMongo() {
        CreateObjectionRequest request = buildValidRequest();

        Instant before = Instant.now();
        BaseObjectionResponse response = strikeOffPartnerObjectionService.createObjection(COMPANY_NUMBER, request);
        Instant after = Instant.now();

        List<ObjectionDocument> savedDocs = objectionRepository.findAll();
        assertThat(savedDocs).hasSize(1);

        ObjectionDocument saved = savedDocs.getFirst();
        assertSavedDocument(saved, before, after);

        assertResponse(response, saved);
    }

    @Test
    void getObjectionFetchesDocumentInMongo() {
        CreateObjectionRequest request = buildValidRequest();

        BaseObjectionResponse created = strikeOffPartnerObjectionService.createObjection(COMPANY_NUMBER, request);

        BaseObjectionResponse fetched = strikeOffPartnerObjectionService.getObjection(
                COMPANY_NUMBER,
                created.getObjectionId());

        List<ObjectionDocument> savedDocs = objectionRepository.findAll();
        assertThat(savedDocs).hasSize(1);
        ObjectionDocument saved = savedDocs.getFirst();

        assertResponse(fetched, saved);
        assertThat(fetched.getCompanyNumber()).isEqualTo(COMPANY_NUMBER);
        assertThat(fetched.getSubmissionCompanyName()).isEqualTo(request.getSubmissionCompanyName());
        assertThat(fetched.getPartnerCaseReference()).isEqualTo(request.getPartnerCaseReference());
        assertThat(fetched.getPartnerContactEmail()).isEqualTo(request.getPartnerContactEmail());
    }

    @Test
    void createObjectionPublishesObjectionEventToKafkaTopicWhenRequestIsValid() {
        CreateObjectionRequest request = buildValidRequest();

        var response = strikeOffPartnerObjectionService.createObjection(COMPANY_NUMBER, request);

        List<StrikeOffPartnerObjections> events = pollKafkaForEvents(List.of(response.getObjectionId()));

        AssertionsForInterfaceTypes.assertThat(events).hasSize(1);

        StrikeOffPartnerObjections event = events.getFirst();
        AssertionsForInterfaceTypes.assertThat(event.getEventType()).isEqualTo(EventType.OBJECTION);
        AssertionsForClassTypes.assertThat(event.getPartnerOrganisation()).isEqualTo(PARTNER_ORGANISATION);
        AssertionsForClassTypes.assertThat(event.getSource()).isEqualTo("strike-off-partner-objections-api");
        AssertionsForClassTypes.assertThat(event.getEventId()).isNotBlank();
        AssertionsForClassTypes.assertThat(event.getEventTime()).isNotBlank();
        AssertionsForClassTypes.assertThat(event.getStrikeOffEventId()).isNotBlank();
    }

    @Test
    void createObjectionPublishesOneKafkaEventPerCall() {
        var request = buildValidRequest();

        var response1 = strikeOffPartnerObjectionService.createObjection(COMPANY_NUMBER, request);
        var response2 = strikeOffPartnerObjectionService.createObjection(COMPANY_NUMBER, request);

        List<StrikeOffPartnerObjections> events = pollKafkaForEvents(List.of(response1.getObjectionId(), response2.getObjectionId()));

        AssertionsForInterfaceTypes.assertThat(events).hasSize(2);
        // Each event should have a unique eventId
        List<String> eventIds = events.stream()
                .map(StrikeOffPartnerObjections::getEventId)
                .toList();
        AssertionsForInterfaceTypes.assertThat(eventIds).doesNotHaveDuplicates();
    }

    @Test
    void createObjectionKafkaEventContainsObjectionIdMatchingPersistedDocument() {
        var request = buildValidRequest();

        var response = strikeOffPartnerObjectionService.createObjection(COMPANY_NUMBER, request);

        List<StrikeOffPartnerObjections> events = pollKafkaForEvents(List.of(response.getObjectionId()));

        AssertionsForInterfaceTypes.assertThat(events).hasSize(1);
        AssertionsForClassTypes.assertThat(events.getFirst().getStrikeOffEventId())
                .isEqualTo(response.getObjectionId());
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

    private static void assertSavedDocument(ObjectionDocument saved, Instant before, Instant after) {
        assertThat(saved.getCompanyNumber()).isEqualTo(COMPANY_NUMBER);
        assertThat(saved.getPartnerOrganisation()).isEqualTo("hmrc");
        assertThat(saved.getSubmissionCompanyName()).isEqualTo("Acme Limited");
        assertThat(saved.getPartnerCaseReference()).isEqualTo("CASE-123");
        assertThat(saved.getPartnerContactEmail()).isEqualTo("test@example.com");
        assertThat(saved.getPartnerObjectionWorkstream()).isEqualTo(DEBT_MANAGEMENT_WORKSTREAM);
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
        request.setPartnerObjectionWorkstream(DEBT_MANAGEMENT_WORKSTREAM);
        request.setPartnerObjectionReason(PartnerObjectionReason.OTHER);
        return request;
    }
}
