package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import org.assertj.core.api.AssertionsForClassTypes;
import org.assertj.core.api.AssertionsForInterfaceTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.companieshouse.api.model.company.CompanyProfileApi;
import uk.gov.companieshouse.api.objections.model.UpdateWithdrawalStatusRequest;
import uk.gov.companieshouse.api.objections.model.WithdrawAllObjectionsRequest;
import uk.gov.companieshouse.api.objections.model.WithdrawAllObjectionsResponse;
import uk.gov.companieshouse.api.objections.model.WithdrawalProcessingStatus;
import uk.gov.companieshouse.strikeoff.partner.objections.EventType;
import uk.gov.companieshouse.strikeoff.partner.objections.StrikeOffPartnerObjections;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.config.BaseTestIntegration;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.CompanyValidationException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.WithdrawalNotFoundException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.ObjectionDocument;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.WithdrawalDocument;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.repository.ObjectionRepository;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.repository.WithdrawalRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils.StrikeoffPartnerObjectionsUtils.PARTNER_ORGANISATION;

@SpringBootTest
@Tag("integration-test")
class StrikeOffPartnerWithdrawalsIntegrationTest extends BaseTestIntegration {

    private static final String COMPANY_NUMBER = "01234567";
    private static final String SECOND_COMPANY_NUMBER = "87654321";
    private static final String THIRD_COMPANY_NUMBER = "11223344";
    private static final String OTHER_PARTNER_ORGANISATION = "home-office";
    private static final String DEBT_MANAGEMENT_WORKSTREAM = "debt-management";

    @Autowired
    private StrikeOffPartnerWithdrawalsService strikeOffPartnerWithdrawalsService;

    @Autowired
    private WithdrawalRepository withdrawalRepository;

    @Autowired
    private ObjectionRepository objectionRepository;


    @BeforeEach
    void setUp() throws Exception {
        withdrawalRepository.deleteAll();
        objectionRepository.deleteAll();
        // Drain any leftover messages from previous tests
        testConsumer.poll(Duration.ofMillis(100));

        // Default stubs used by tests that are not explicitly exercising validation failures.
        when(internalApiClient.company().get("/company/" + COMPANY_NUMBER).execute().getData())
                .thenReturn(buildValidCompanyProfile());
        when(internalApiClient.company().get("/company/" + SECOND_COMPANY_NUMBER).execute().getData())
                .thenReturn(buildValidCompanyProfile());
        when(internalApiClient.company().get("/company/" + THIRD_COMPANY_NUMBER).execute().getData())
                .thenReturn(buildValidCompanyProfile());

        seedObjection(COMPANY_NUMBER, PARTNER_ORGANISATION);
        seedObjection(SECOND_COMPANY_NUMBER, PARTNER_ORGANISATION);
        seedObjection(THIRD_COMPANY_NUMBER, OTHER_PARTNER_ORGANISATION);
    }

    // ===== Company Validation Tests =====

    @Test
    void withdrawAllObjections_whenCompanyProfileReturnsValidCompany_acceptsWithdrawal() throws Exception {
        CompanyProfileApi validCompany = buildValidCompanyProfile();
        when(internalApiClient.company().get("/company/" + COMPANY_NUMBER).execute().getData())
                .thenReturn(validCompany);

        WithdrawAllObjectionsRequest request = buildRequest();

        WithdrawAllObjectionsResponse response =
                strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request, PARTNER_ORGANISATION);

        assertThat(response).isNotNull();
        assertThat(response.getWithdrawalId()).isNotBlank();
        assertThat(response.getCompanyNumber()).isEqualTo(COMPANY_NUMBER);
    }

    @Test
    void withdrawAllObjections_whenOnlyOtherPartnerObjectionsExist_throwsCompanyValidationException() {
        objectionRepository.deleteAll();
        seedObjection(THIRD_COMPANY_NUMBER, OTHER_PARTNER_ORGANISATION);
        WithdrawAllObjectionsRequest request = buildRequest();

        assertThatThrownBy(() ->
                strikeOffPartnerWithdrawalsService.withdrawAllObjections(THIRD_COMPANY_NUMBER, request, PARTNER_ORGANISATION))
                .isInstanceOf(CompanyValidationException.class)
                .hasMessageContaining(THIRD_COMPANY_NUMBER)
                .hasMessageContaining(PARTNER_ORGANISATION);

        assertThat(withdrawalRepository.findAll()).isEmpty();
    }


    // ===== GET Withdrawal Tests =====

    @Test
    void getWithdrawal_whenWithdrawalIsFound_retrievesWithdrawalFromMongo() {
        WithdrawAllObjectionsRequest request = buildRequest();
        WithdrawAllObjectionsResponse createResponse =
                strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request, PARTNER_ORGANISATION);

        WithdrawAllObjectionsResponse retrieveResponse =
                strikeOffPartnerWithdrawalsService.getWithdrawal(COMPANY_NUMBER, createResponse.getWithdrawalId(), PARTNER_ORGANISATION);

        assertThat(retrieveResponse).isNotNull();
        assertThat(retrieveResponse.getWithdrawalId()).isEqualTo(createResponse.getWithdrawalId());
        assertThat(retrieveResponse.getCompanyNumber()).isEqualTo(COMPANY_NUMBER);
    }

    @Test
    void getWithdrawal_whenRetrieved_returnsMappedResponseWithAllFields() {
        WithdrawAllObjectionsRequest request = buildRequest();
        WithdrawAllObjectionsResponse createResponse =
                strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request, PARTNER_ORGANISATION);

        WithdrawAllObjectionsResponse retrieveResponse =
                strikeOffPartnerWithdrawalsService.getWithdrawal(COMPANY_NUMBER, createResponse.getWithdrawalId(), PARTNER_ORGANISATION);

        // Verify all fields have correct values and links
        assertThat(retrieveResponse.getCompanyNumber()).isEqualTo(COMPANY_NUMBER);
        assertThat(retrieveResponse.getSubmissionCompanyName()).isEqualTo("Acme Limited");
        assertThat(retrieveResponse.getWithdrawalId()).isEqualTo(createResponse.getWithdrawalId());
        assertThat(retrieveResponse.getPartnerContactEmail()).isEqualTo("test@example.com");
        assertThat(retrieveResponse.getPartnerCaseReference()).isEqualTo("CASE-123");
        assertThat(retrieveResponse.getPartnerObjectionWorkstream()).isEqualTo(DEBT_MANAGEMENT_WORKSTREAM);
        assertThat(retrieveResponse.getProcessingStatus()).hasToString("withdrawal-requested");
        assertThat(retrieveResponse.getCreatedAt()).isNotNull();
        assertThat(retrieveResponse.getEtag()).isNotBlank();
        assertThat(retrieveResponse.getKind()).isEqualTo("strike-off-partner-objection#withdrawal");
        assertThat(retrieveResponse.getLinks()).isNotNull();
        assertThat(retrieveResponse.getLinks().getSelf())
                .isEqualTo("/company/" + COMPANY_NUMBER + "/strike-off-partner-objections-withdrawals/" + createResponse.getWithdrawalId());
        assertThat(retrieveResponse.getLinks().getCompanyProfile())
                .isEqualTo("/company/" + COMPANY_NUMBER);
    }

    @Test
    void getWithdrawal_whenWithdrawalDoesNotExist_throwsNotFoundException() {
        assertThatThrownBy(() ->
                strikeOffPartnerWithdrawalsService.getWithdrawal(COMPANY_NUMBER, "non-existent-id", PARTNER_ORGANISATION))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);
    }

    @Test
    void getWithdrawal_whenCompanyNumberDoesNotMatch_throwsNotFoundException() {
        WithdrawAllObjectionsRequest request = buildRequest();
        WithdrawAllObjectionsResponse createResponse =
                strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request, PARTNER_ORGANISATION);
        String withdrawalId = createResponse.getWithdrawalId();

        assertThatThrownBy(() ->
                strikeOffPartnerWithdrawalsService.getWithdrawal(
                        SECOND_COMPANY_NUMBER, withdrawalId, PARTNER_ORGANISATION))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);
    }

    @Test
    void getWithdrawal_whenPartnerOrganisationDoesNotMatch_throwsForbidden() {
        WithdrawAllObjectionsRequest request = buildRequest();
        WithdrawAllObjectionsResponse createResponse =
                strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request, PARTNER_ORGANISATION);
        String withdrawalId = createResponse.getWithdrawalId();

        assertThatThrownBy(() ->
                strikeOffPartnerWithdrawalsService.getWithdrawal(
                        COMPANY_NUMBER, withdrawalId, "different-organisation"))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.FORBIDDEN);
    }

    @Test
    void getWithdrawal_whenMultipleWithdrawalsExist_retrievesCorrectWithdrawal() {
        // Create first withdrawal for company A
        WithdrawAllObjectionsRequest request1 = buildRequest();
        WithdrawAllObjectionsResponse createResponse1 =
                strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request1, PARTNER_ORGANISATION);

        // Create second withdrawal for company B
        WithdrawAllObjectionsRequest request2 = buildRequest();
        strikeOffPartnerWithdrawalsService.withdrawAllObjections(SECOND_COMPANY_NUMBER, request2, PARTNER_ORGANISATION);

        // Retrieve first withdrawal
        WithdrawAllObjectionsResponse retrieveResponse1 =
                strikeOffPartnerWithdrawalsService.getWithdrawal(COMPANY_NUMBER, createResponse1.getWithdrawalId(), PARTNER_ORGANISATION);

        // Verify we get the correct withdrawal
        assertThat(retrieveResponse1.getCompanyNumber()).isEqualTo(COMPANY_NUMBER);
        assertThat(retrieveResponse1.getWithdrawalId()).isEqualTo(createResponse1.getWithdrawalId());
    }

    @Test
    void updateWithdrawalProcessingStatus_whenValidStatus_updatesPersistedDocument() {
        WithdrawAllObjectionsResponse created =
                strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, buildRequest(), PARTNER_ORGANISATION);
        UpdateWithdrawalStatusRequest request = new UpdateWithdrawalStatusRequest();
        request.setProcessingStatus(WithdrawalProcessingStatus.WITHDRAWAL_PROCESSING);

        strikeOffPartnerWithdrawalsService.updateWithdrawalProcessingStatus(
                COMPANY_NUMBER, created.getWithdrawalId(), request);

        WithdrawalDocument updated = withdrawalRepository
                .findByCompanyNumberAndWithdrawalId(COMPANY_NUMBER, created.getWithdrawalId())
                .orElseThrow();
        assertThat(updated.getProcessingStatus()).isEqualTo("withdrawal-processing");
    }

    @Test
    void updateWithdrawalProcessingStatus_whenWithdrawalMissing_throwsNotFound() {
        UpdateWithdrawalStatusRequest request = new UpdateWithdrawalStatusRequest();
        request.setProcessingStatus(WithdrawalProcessingStatus.WITHDRAWAL_PROCESSING);

        assertThatThrownBy(() -> strikeOffPartnerWithdrawalsService.updateWithdrawalProcessingStatus(
                COMPANY_NUMBER, "missing-withdrawal-id", request))
                .isInstanceOf(WithdrawalNotFoundException.class);
    }

    @Test
    void updateWithdrawalProcessingStatus_whenStatusMissing_throwsBadRequest() {
        WithdrawAllObjectionsResponse created =
                strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, buildRequest(), PARTNER_ORGANISATION);
        String withdrawalId = created.getWithdrawalId();
        UpdateWithdrawalStatusRequest request = new UpdateWithdrawalStatusRequest();

        assertThatThrownBy(() -> strikeOffPartnerWithdrawalsService.updateWithdrawalProcessingStatus(
                COMPANY_NUMBER, withdrawalId, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);
    }


    // ===== POST Withdrawal Tests (Existing Tests) =====

    @Test
    void withdrawAllObjections_whenRequestIsValid_persistsDocumentInMongo() {
        WithdrawAllObjectionsRequest request = buildRequest();

        Instant before = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        WithdrawAllObjectionsResponse response =
                strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request, PARTNER_ORGANISATION);
        Instant after = Instant.now();

        List<WithdrawalDocument> savedDocs = withdrawalRepository.findAll();
        assertThat(savedDocs).hasSize(1);

        WithdrawalDocument saved = savedDocs.getFirst();

        // Verify expected metadata
        assertThat(saved.getId()).isNotBlank();
        assertThat(saved.getWithdrawalId()).isNotBlank();
        assertThat(saved.getEtag()).isNotBlank();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getCreatedAt()).isAfterOrEqualTo(before);
        assertThat(saved.getCreatedAt()).isBeforeOrEqualTo(after);

        // Verify expected field values from request
        assertThat(saved.getCompanyNumber()).isEqualTo(COMPANY_NUMBER);
        assertThat(saved.getSubmissionCompanyName()).isEqualTo("Acme Limited");
        assertThat(saved.getPartnerCaseReference()).isEqualTo("CASE-123");
        assertThat(saved.getPartnerContactEmail()).isEqualTo("test@example.com");
        assertThat(saved.getPartnerObjectionWorkstream())
                .isEqualTo(DEBT_MANAGEMENT_WORKSTREAM);
        assertThat(saved.getPartnerOrganisation()).isEqualTo("hmrc");

        // Verify expected status values
        assertThat(saved.getProcessingStatus()).isEqualTo("withdrawal-requested");
        assertThat(saved.getKind()).isEqualTo("strike-off-partner-objection#withdrawal");

        // Verify links
        assertThat(saved.getLinks()).isNotNull();
        assertThat(saved.getLinks().getCompanyProfile()).isEqualTo("/company/" + COMPANY_NUMBER);
        assertThat(saved.getLinks().getSelf()).startsWith(
                "/company/" + COMPANY_NUMBER + "/strike-off-partner-objections-withdrawals/");
        assertThat(saved.getLinks().getSelf()).contains(saved.getWithdrawalId());

        // Verify the response is mapped from persisted data
        assertThat(response.getWithdrawalId()).isEqualTo(saved.getWithdrawalId());
        assertThat(response.getCompanyNumber()).isEqualTo(COMPANY_NUMBER);
        assertThat(response.getProcessingStatus()).isEqualTo(WithdrawalProcessingStatus.WITHDRAWAL_REQUESTED);
    }

    @Test
    void findByWithdrawalId_whenWithdrawalIsPersisted_returnsWithdrawalDocument() {
        WithdrawAllObjectionsRequest request = buildRequest();
        WithdrawAllObjectionsResponse response =
                strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request, PARTNER_ORGANISATION);

        Optional<WithdrawalDocument> found =
                withdrawalRepository.findByWithdrawalId(response.getWithdrawalId());

        assertThat(found).isPresent();
        assertThat(found.get().getCompanyNumber()).isEqualTo(COMPANY_NUMBER);
        assertThat(found.get().getWithdrawalId()).isEqualTo(response.getWithdrawalId());
    }

    @Test
    void findByCompanyNumberAndWithdrawalId_whenWithdrawalIsPersisted_returnsWithdrawalDocument() {
        WithdrawAllObjectionsRequest request = buildRequest();
        WithdrawAllObjectionsResponse response =
                strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request, PARTNER_ORGANISATION);

        Optional<WithdrawalDocument> found =
                withdrawalRepository.findByCompanyNumberAndWithdrawalId(COMPANY_NUMBER, response.getWithdrawalId());

        assertThat(found).isPresent();
        assertThat(found.get().getCompanyNumber()).isEqualTo(COMPANY_NUMBER);
        assertThat(found.get().getWithdrawalId()).isEqualTo(response.getWithdrawalId());
    }


    @Test
    void withdrawAllObjections_whenCalledMultipleTimes_persistsWithUniqueWithdrawalId() {
        WithdrawAllObjectionsRequest request = buildRequest();

        strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request, PARTNER_ORGANISATION);
        strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request, PARTNER_ORGANISATION);

        List<WithdrawalDocument> savedDocs = withdrawalRepository.findAll();
        assertThat(savedDocs).hasSize(2);

        String id1 = savedDocs.get(0).getWithdrawalId();
        String id2 = savedDocs.get(1).getWithdrawalId();
        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    void withdrawAllObjections_whenRequestIsValid_returnsMappedResponseFromPersistedDocument() {
        WithdrawAllObjectionsRequest request = buildRequest();

        WithdrawAllObjectionsResponse response =
                strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request, PARTNER_ORGANISATION);

        assertThat(response.getCompanyNumber()).isEqualTo(COMPANY_NUMBER);
        assertThat(response.getSubmissionCompanyName()).isEqualTo("Acme Limited");
        assertThat(response.getPartnerCaseReference()).isEqualTo("CASE-123");
        assertThat(response.getPartnerContactEmail()).isEqualTo("test@example.com");
        assertThat(response.getPartnerObjectionWorkstream()).isEqualTo(DEBT_MANAGEMENT_WORKSTREAM);
        assertThat(response.getProcessingStatus()).isEqualTo(WithdrawalProcessingStatus.WITHDRAWAL_REQUESTED);
        assertThat(response.getKind()).isEqualTo("strike-off-partner-objection#withdrawal");
        assertThat(response.getWithdrawalId()).isNotBlank();
        assertThat(response.getEtag()).isNotBlank();
        assertThat(response.getCreatedAt()).isNotNull();
        assertThat(response.getLinks()).isNotNull();
        assertThat(response.getLinks().getSelf()).isEqualTo(
                "/company/" + COMPANY_NUMBER + "/strike-off-partner-objections-withdrawals/" + response.getWithdrawalId());
        assertThat(response.getLinks().getCompanyProfile()).isEqualTo("/company/" + COMPANY_NUMBER);
    }


    @Test
    void withdrawAllObjections_whenRequestIsValid_publishesWithdrawalEventToKafkaTopic() {
        WithdrawAllObjectionsRequest request = buildRequest();

        var response = strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request, PARTNER_ORGANISATION);

        List<StrikeOffPartnerObjections> events = pollKafkaForEvents(List.of(response.getWithdrawalId()));

        AssertionsForInterfaceTypes.assertThat(events).hasSize(1);

        StrikeOffPartnerObjections event = events.getFirst();
        AssertionsForInterfaceTypes.assertThat(event.getEventType()).isEqualTo(EventType.WITHDRAWAL);
        AssertionsForClassTypes.assertThat(event.getPartnerOrganisation()).isEqualTo("hmrc");
        AssertionsForClassTypes.assertThat(event.getSource()).isEqualTo("strike-off-partner-objections-api");
        AssertionsForClassTypes.assertThat(event.getEventId()).isNotBlank();
        AssertionsForClassTypes.assertThat(event.getEventTime()).isNotBlank();
        AssertionsForClassTypes.assertThat(event.getStrikeOffEventId()).isNotBlank(); // maps to withdrawalId
    }

    @Test
    void withdrawAllObjections_whenCalledMultipleTimes_publishesOneKafkaEventPerCall() {
        WithdrawAllObjectionsRequest request = buildRequest();

        var response1 = strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request, PARTNER_ORGANISATION);
        var response2 = strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request, PARTNER_ORGANISATION);

        List<StrikeOffPartnerObjections> events = pollKafkaForEvents(List.of(response1.getWithdrawalId(), response2.getWithdrawalId()));

        AssertionsForInterfaceTypes.assertThat(events).hasSize(2);
        // Each event should have a unique eventId
        List<String> eventIds = events.stream()
                .map(StrikeOffPartnerObjections::getEventId)
                .toList();
        AssertionsForInterfaceTypes.assertThat(eventIds).doesNotHaveDuplicates();
    }

    @Test
    void withdrawAllObjections_whenSuccessful_kafkaEventContainsWithdrawalIdMatchingPersistedDocument() {
        WithdrawAllObjectionsRequest request = buildRequest();

        var response = strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request, PARTNER_ORGANISATION);

        List<StrikeOffPartnerObjections> events = pollKafkaForEvents(List.of(response.getWithdrawalId()));

        AssertionsForInterfaceTypes.assertThat(events).hasSize(1);
        AssertionsForClassTypes.assertThat(events.getFirst().getStrikeOffEventId())
                .isEqualTo(response.getWithdrawalId());
    }


    private WithdrawAllObjectionsRequest buildRequest() {
        WithdrawAllObjectionsRequest request = new WithdrawAllObjectionsRequest();
        request.setSubmissionCompanyName("Acme Limited");
        request.setPartnerCaseReference("CASE-123");
        request.setPartnerContactEmail("test@example.com");
        request.setPartnerObjectionWorkstream(DEBT_MANAGEMENT_WORKSTREAM);
        return request;
    }

    private CompanyProfileApi buildValidCompanyProfile() {
        CompanyProfileApi companyProfile = new CompanyProfileApi();
        companyProfile.setCompanyName("Acme Limited");
        companyProfile.setType("llp");
        companyProfile.setCompanyStatus("active-proposal-to-strike-off");
        return companyProfile;
    }

    private void seedObjection(String companyNumber, String partnerOrganisation) {
        ObjectionDocument objection = new ObjectionDocument();
        objection.setCompanyNumber(companyNumber);
        objection.setObjectionId(UUID.randomUUID().toString());
        objection.setSubmissionCompanyName("Acme Limited");
        objection.setPartnerOrganisation(partnerOrganisation);
        objection.setPartnerContactEmail("test@example.com");
        objection.setPartnerCaseReference("CASE-123");
        objection.setPartnerObjectionWorkstream(DEBT_MANAGEMENT_WORKSTREAM);
        objection.setPartnerObjectionReason("debt-management");
        objectionRepository.insert(objection);
    }
}
