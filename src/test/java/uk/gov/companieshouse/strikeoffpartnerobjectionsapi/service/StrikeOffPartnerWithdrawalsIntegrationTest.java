package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.companieshouse.api.objections.model.PartnerObjectionWorkstream;
import uk.gov.companieshouse.api.objections.model.WithdrawAllObjections201Response;
import uk.gov.companieshouse.api.objections.model.WithdrawAllObjectionsRequest;
import uk.gov.companieshouse.api.objections.model.WithdrawAllObjectionsResponse;
import uk.gov.companieshouse.api.objections.model.WithdrawalRequestedStatus;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.config.MongoDbIntegration;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.WithdrawalDocument;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.repository.WithdrawalRepository;

@SpringBootTest
@Tag("integration-test")
class StrikeOffPartnerWithdrawalsIntegrationTest extends MongoDbIntegration {

    private static final String COMPANY_NUMBER = "01234567";
    private static final String SECOND_COMPANY_NUMBER = "87654321";

    @Autowired
    private StrikeOffPartnerWithdrawalsService strikeOffPartnerWithdrawalsService;

    @Autowired
    private WithdrawalRepository withdrawalRepository;

    @BeforeEach
    void setUp() {
        withdrawalRepository.deleteAll();
    }

    // ===== GET Withdrawal Tests =====

    @Test
    void getWithdrawal_retrievesWithdrawalFromMongo_whenWithdrawalFound() {
        WithdrawAllObjectionsRequest request = buildRequest();
        WithdrawAllObjections201Response createResponse =
                strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request);

        WithdrawAllObjectionsResponse retrieveResponse =
                strikeOffPartnerWithdrawalsService.getWithdrawal(COMPANY_NUMBER, createResponse.getWithdrawalId());

        assertThat(retrieveResponse).isNotNull();
        assertThat(retrieveResponse.getWithdrawalId()).isEqualTo(createResponse.getWithdrawalId());
        assertThat(retrieveResponse.getCompanyNumber()).isEqualTo(COMPANY_NUMBER);
    }

    @Test
    void getWithdrawal_returnsCorrectCompanyNumber_whenRetrieved() {
        WithdrawAllObjectionsRequest request = buildRequest();
        WithdrawAllObjections201Response createResponse =
                strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request);

        WithdrawAllObjectionsResponse retrieveResponse =
                strikeOffPartnerWithdrawalsService.getWithdrawal(COMPANY_NUMBER, createResponse.getWithdrawalId());

        assertThat(retrieveResponse.getCompanyNumber()).isEqualTo(COMPANY_NUMBER);
    }

    @Test
    void getWithdrawal_returnsWithdrawalWithAllRequiredFields_whenRetrieved() {
        WithdrawAllObjectionsRequest request = buildRequest();
        WithdrawAllObjections201Response createResponse =
                strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request);

        WithdrawAllObjectionsResponse retrieveResponse =
                strikeOffPartnerWithdrawalsService.getWithdrawal(COMPANY_NUMBER, createResponse.getWithdrawalId());

        // Verify all required fields
        assertThat(retrieveResponse.getCompanyNumber()).isNotBlank();
        assertThat(retrieveResponse.getSubmissionCompanyName()).isNotBlank();
        assertThat(retrieveResponse.getWithdrawalId()).isNotBlank();
        assertThat(retrieveResponse.getPartnerContactEmail()).isNotBlank();
        assertThat(retrieveResponse.getPartnerCaseReference()).isNotBlank();
        assertThat(retrieveResponse.getPartnerObjectionWorkstream()).isNotNull();
        assertThat(retrieveResponse.getProcessingStatus()).isNotNull();
        assertThat(retrieveResponse.getCreatedAt()).isNotNull();
        assertThat(retrieveResponse.getEtag()).isNotBlank();
        assertThat(retrieveResponse.getKind()).isNotBlank();
        assertThat(retrieveResponse.getLinks()).isNotNull();
    }

    @Test
    void getWithdrawal_returnsCorrectProcessingStatus_whenRetrieved() {
        WithdrawAllObjectionsRequest request = buildRequest();
        WithdrawAllObjections201Response createResponse =
                strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request);

        WithdrawAllObjectionsResponse retrieveResponse =
                strikeOffPartnerWithdrawalsService.getWithdrawal(COMPANY_NUMBER, createResponse.getWithdrawalId());

        assertThat(retrieveResponse.getProcessingStatus()).isNotNull();
        assertThat(retrieveResponse.getProcessingStatus()).hasToString("withdrawal-requested");
    }

    @Test
    void getWithdrawal_throwsNotFoundException_whenWithdrawalDoesNotExist() {
        assertThatThrownBy(() ->
                strikeOffPartnerWithdrawalsService.getWithdrawal(COMPANY_NUMBER, "non-existent-id"))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);
    }

    @Test
    void getWithdrawal_throwsNotFoundException_whenCompanyNumberDoesNotMatch() {
        WithdrawAllObjectionsRequest request = buildRequest();
        WithdrawAllObjections201Response createResponse =
                strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request);
        String withdrawalId = createResponse.getWithdrawalId();

        assertThatThrownBy(() ->
                strikeOffPartnerWithdrawalsService.getWithdrawal(
                        SECOND_COMPANY_NUMBER, withdrawalId))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);
    }

    @Test
    void getWithdrawal_retrievesCorrectWithdrawalAcrossMultiple_whenMultipleExist() {
        // Create first withdrawal for company A
        WithdrawAllObjectionsRequest request1 = buildRequest();
        WithdrawAllObjections201Response createResponse1 =
                strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request1);

        // Create second withdrawal for company B
        WithdrawAllObjectionsRequest request2 = buildRequest();
        strikeOffPartnerWithdrawalsService.withdrawAllObjections(SECOND_COMPANY_NUMBER, request2);

        // Retrieve first withdrawal
        WithdrawAllObjectionsResponse retrieveResponse1 =
                strikeOffPartnerWithdrawalsService.getWithdrawal(COMPANY_NUMBER, createResponse1.getWithdrawalId());

        // Verify we get the correct withdrawal
        assertThat(retrieveResponse1.getCompanyNumber()).isEqualTo(COMPANY_NUMBER);
        assertThat(retrieveResponse1.getWithdrawalId()).isEqualTo(createResponse1.getWithdrawalId());
    }

    @Test
    void getWithdrawal_mapsAllFieldsCorrectly_fromStoredDocument() {
        WithdrawAllObjectionsRequest request = buildRequest();
        WithdrawAllObjections201Response createResponse =
                strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request);

        WithdrawAllObjectionsResponse retrieveResponse =
                strikeOffPartnerWithdrawalsService.getWithdrawal(COMPANY_NUMBER, createResponse.getWithdrawalId());

        // Verify mapped values match the request
        assertThat(retrieveResponse.getSubmissionCompanyName()).isEqualTo("Acme Limited");
        assertThat(retrieveResponse.getPartnerCaseReference()).isEqualTo("CASE-123");
        assertThat(retrieveResponse.getPartnerContactEmail()).isEqualTo("test@example.com");
        assertThat(retrieveResponse.getPartnerObjectionWorkstream()).isEqualTo(PartnerObjectionWorkstream.DEBT_MANAGEMENT);
    }

    @Test
    void getWithdrawal_returnsCorrectLinks_whenRetrieved() {
        WithdrawAllObjectionsRequest request = buildRequest();
        WithdrawAllObjections201Response createResponse =
                strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request);

        WithdrawAllObjectionsResponse retrieveResponse =
                strikeOffPartnerWithdrawalsService.getWithdrawal(COMPANY_NUMBER, createResponse.getWithdrawalId());

        assertThat(retrieveResponse.getLinks()).isNotNull();
        assertThat(retrieveResponse.getLinks().getSelf())
                .isEqualTo("/company/" + COMPANY_NUMBER + "/strike-off-partner-objections-withdrawals/" + createResponse.getWithdrawalId());
        assertThat(retrieveResponse.getLinks().getCompanyProfile())
                .isEqualTo("/company/" + COMPANY_NUMBER);
    }

    // ===== POST Withdrawal Tests (Existing Tests) =====

    @Test
    void withdrawAllObjections_persistsDocumentInMongo_whenRequestIsValid() {
        WithdrawAllObjectionsRequest request = buildRequest();

        Instant before = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        WithdrawAllObjections201Response response =
                strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request);
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
                .isEqualTo(PartnerObjectionWorkstream.DEBT_MANAGEMENT.getValue());
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
        assertThat(response.getProcessingStatus()).isEqualTo(WithdrawalRequestedStatus.WITHDRAWAL_REQUESTED);
    }

    @Test
    void withdrawalDocument_canBeRetrievedByWithdrawalId_afterWithdrawalIsPersisted() {
        WithdrawAllObjectionsRequest request = buildRequest();
        WithdrawAllObjections201Response response =
                strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request);

        Optional<WithdrawalDocument> found =
                withdrawalRepository.findByWithdrawalId(response.getWithdrawalId());

        assertThat(found).isPresent();
        assertThat(found.get().getCompanyNumber()).isEqualTo(COMPANY_NUMBER);
        assertThat(found.get().getWithdrawalId()).isEqualTo(response.getWithdrawalId());
    }

    @Test
    void withdrawalDocument_canBeRetrievedByCompanyNumberAndWithdrawalId_afterWithdrawalIsPersisted() {
        WithdrawAllObjectionsRequest request = buildRequest();
        WithdrawAllObjections201Response response =
                strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request);

        Optional<WithdrawalDocument> found =
                withdrawalRepository.findByCompanyNumberAndWithdrawalId(COMPANY_NUMBER, response.getWithdrawalId());

        assertThat(found).isPresent();
        assertThat(found.get().getCompanyNumber()).isEqualTo(COMPANY_NUMBER);
        assertThat(found.get().getWithdrawalId()).isEqualTo(response.getWithdrawalId());
    }

    @Test
    void withdrawalDocument_containsAllRequiredFields_whenPersisted() {
        WithdrawAllObjectionsRequest request = buildRequest();
        strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request);

        WithdrawalDocument saved = withdrawalRepository.findAll().getFirst();

        assertThat(saved.getCompanyNumber()).isNotBlank();
        assertThat(saved.getSubmissionCompanyName()).isNotBlank();
        assertThat(saved.getWithdrawalId()).isNotBlank();
        assertThat(saved.getPartnerOrganisation()).isNotBlank();
        assertThat(saved.getPartnerContactEmail()).isNotBlank();
        assertThat(saved.getPartnerCaseReference()).isNotBlank();
        assertThat(saved.getPartnerObjectionWorkstream()).isNotBlank();
        assertThat(saved.getProcessingStatus()).isNotBlank();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getEtag()).isNotBlank();
        assertThat(saved.getLinks()).isNotNull();
        assertThat(saved.getKind()).isNotBlank();
    }

    @Test
    void withdrawAllObjections_persistsWithUniqueWithdrawalId_onEachCall() {
        WithdrawAllObjectionsRequest request = buildRequest();

        strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request);
        strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request);

        List<WithdrawalDocument> savedDocs = withdrawalRepository.findAll();
        assertThat(savedDocs).hasSize(2);

        String id1 = savedDocs.get(0).getWithdrawalId();
        String id2 = savedDocs.get(1).getWithdrawalId();
        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    void withdrawAllObjections_returnsMappedResponseFromPersistedDocument_whenRequestIsValid() {
        WithdrawAllObjectionsRequest request = buildRequest();

        WithdrawAllObjections201Response response =
                strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request);

        assertThat(response.getCompanyNumber()).isEqualTo(COMPANY_NUMBER);
        assertThat(response.getSubmissionCompanyName()).isEqualTo("Acme Limited");
        assertThat(response.getPartnerCaseReference()).isEqualTo("CASE-123");
        assertThat(response.getPartnerContactEmail()).isEqualTo("test@example.com");
        assertThat(response.getPartnerObjectionWorkstream()).isEqualTo(PartnerObjectionWorkstream.DEBT_MANAGEMENT);
        assertThat(response.getProcessingStatus()).isEqualTo(WithdrawalRequestedStatus.WITHDRAWAL_REQUESTED);
        assertThat(response.getKind()).isEqualTo("strike-off-partner-objection#withdrawal");
        assertThat(response.getWithdrawalId()).isNotBlank();
        assertThat(response.getEtag()).isNotBlank();
        assertThat(response.getCreatedAt()).isNotNull();
        assertThat(response.getLinks()).isNotNull();
        assertThat(response.getLinks().getSelf()).isEqualTo(
                "/company/" + COMPANY_NUMBER + "/strike-off-partner-objections-withdrawals/" + response.getWithdrawalId());
        assertThat(response.getLinks().getCompanyProfile()).isEqualTo("/company/" + COMPANY_NUMBER);
    }

    private WithdrawAllObjectionsRequest buildRequest() {
        WithdrawAllObjectionsRequest request = new WithdrawAllObjectionsRequest();
        request.setSubmissionCompanyName("Acme Limited");
        request.setPartnerCaseReference("CASE-123");
        request.setPartnerContactEmail("test@example.com");
        request.setPartnerObjectionWorkstream(PartnerObjectionWorkstream.DEBT_MANAGEMENT);
        return request;
    }
}

