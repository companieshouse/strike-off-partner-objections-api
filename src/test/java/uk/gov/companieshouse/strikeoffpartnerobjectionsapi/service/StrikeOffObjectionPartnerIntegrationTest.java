package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import org.assertj.core.api.AssertionsForClassTypes;
import org.assertj.core.api.AssertionsForInterfaceTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uk.gov.companieshouse.api.model.company.CompanyProfileApi;
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
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils.StrikeoffPartnerObjectionsUtils.PARTNER_ORGANISATION;

@SpringBootTest
@Tag("integration-test")
class StrikeOffObjectionPartnerIntegrationTest extends BaseTestIntegration {

    private static final String COMPANY_NUMBER = "01234567";
    private static final String SECOND_COMPANY_NUMBER = "87654321";
    private static final String DEBT_MANAGEMENT_WORKSTREAM = "debt-management";

    @Autowired
    private StrikeOffPartnerObjectionService strikeOffPartnerObjectionService;

    @Autowired
    private ObjectionRepository objectionRepository;

    @BeforeEach
    void setUp() throws Exception {
        objectionRepository.deleteAll();
        // Drain any leftover messages from previous tests
        testConsumer.poll(Duration.ofMillis(100));

        // Default stubs used by tests that are not explicitly exercising validation failures.
        when(internalApiClient.company().get("/company/" + COMPANY_NUMBER).execute().getData())
                .thenReturn(buildValidCompanyProfile());
        when(internalApiClient.company().get("/company/" + SECOND_COMPANY_NUMBER).execute().getData())
                .thenReturn(buildValidCompanyProfile());
    }

    @Test
    void createObjections_whenCompanyProfileReturnsValidCompany_acceptsObjection() throws Exception {
        CompanyProfileApi validCompany = buildValidCompanyProfile();
        when(internalApiClient.company().get("/company/" + COMPANY_NUMBER).execute().getData())
                .thenReturn(validCompany);

        CreateObjectionRequest request = buildValidRequest();

        BaseObjectionResponse response =
                strikeOffPartnerObjectionService.createObjection(COMPANY_NUMBER, request, PARTNER_ORGANISATION);

        assertThat(response).isNotNull();
        assertThat(response.getObjectionId()).isNotBlank();
        assertThat(response.getCompanyNumber()).isEqualTo(COMPANY_NUMBER);
    }

    @Test
    void createObjection_whenRequestIsValid_persistsDocumentInMongo() {
        CreateObjectionRequest request = buildValidRequest();

        Instant before = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        BaseObjectionResponse response = strikeOffPartnerObjectionService.createObjection(COMPANY_NUMBER, request, PARTNER_ORGANISATION);
        Instant after = Instant.now();

        List<ObjectionDocument> savedDocs = objectionRepository.findAll();
        assertThat(savedDocs).hasSize(1);

        ObjectionDocument saved = savedDocs.getFirst();
        assertSavedDocument(saved, before, after);

        assertResponse(response, saved);
    }

    @Test
    void getObjection_whenRequestIsValid_fetchesDocumentInMongo() {
        CreateObjectionRequest request = buildValidRequest();

        BaseObjectionResponse created = strikeOffPartnerObjectionService.createObjection(COMPANY_NUMBER, request, PARTNER_ORGANISATION);

        BaseObjectionResponse fetched = strikeOffPartnerObjectionService.getObjection(
                COMPANY_NUMBER,
                created.getObjectionId(),
                PARTNER_ORGANISATION);

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
    void createObjection_whenRequestIsValid_publishesObjectionEventToKafkaTopic() {
        CreateObjectionRequest request = buildValidRequest();

        var response = strikeOffPartnerObjectionService.createObjection(COMPANY_NUMBER, request, PARTNER_ORGANISATION);

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
    void createObjection_whenCalledMultipleTimes_publishesOneKafkaEventPerCall() {
        var request = buildValidRequest();

        var response1 = strikeOffPartnerObjectionService.createObjection(COMPANY_NUMBER, request, PARTNER_ORGANISATION);
        var response2 = strikeOffPartnerObjectionService.createObjection(COMPANY_NUMBER, request, PARTNER_ORGANISATION);

        List<StrikeOffPartnerObjections> events = pollKafkaForEvents(List.of(response1.getObjectionId(), response2.getObjectionId()));

        AssertionsForInterfaceTypes.assertThat(events).hasSize(2);
        // Each event should have a unique eventId
        List<String> eventIds = events.stream()
                .map(StrikeOffPartnerObjections::getEventId)
                .toList();
        AssertionsForInterfaceTypes.assertThat(eventIds).doesNotHaveDuplicates();
    }

    @Test
    void createObjection_whenSuccessful_kafkaEventContainsObjectionIdMatchingPersistedDocument() {
        var request = buildValidRequest();

        var response = strikeOffPartnerObjectionService.createObjection(COMPANY_NUMBER, request, PARTNER_ORGANISATION);

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
        Instant beforeAtMillisPrecision = before.truncatedTo(ChronoUnit.MILLIS);
        assertThat(saved.getCompanyNumber()).isEqualTo(COMPANY_NUMBER);
        assertThat(saved.getPartnerOrganisation()).isEqualTo(PARTNER_ORGANISATION);
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
        assertThat(saved.getCreatedAt()).isAfterOrEqualTo(beforeAtMillisPrecision);
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

    private CompanyProfileApi buildValidCompanyProfile() {
        CompanyProfileApi companyProfile = new CompanyProfileApi();
        companyProfile.setCompanyName("Acme Limited");
        companyProfile.setType("llp");
        companyProfile.setCompanyStatus("active-proposal-to-strike-off");
        return companyProfile;
    }
}
