package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import uk.gov.companieshouse.api.objections.model.PartnerObjectionWorkstream;
import uk.gov.companieshouse.api.objections.model.WithdrawAllObjections201Response;
import uk.gov.companieshouse.api.objections.model.WithdrawAllObjectionsRequest;
import uk.gov.companieshouse.api.objections.model.WithdrawalRequestedStatus;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.config.MongoDbTestContainerConfiguration;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.WithdrawalDocument;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.repository.WithdrawalRepository;

@SpringBootTest
@Import({MongoDbTestContainerConfiguration.class})
@Tag("integration-test")
class StrikeOffPartnerWithdrawalsIntegrationTest {

    private static final String COMPANY_NUMBER = "01234567";

    @Autowired
    private StrikeOffPartnerWithdrawalsService strikeOffPartnerWithdrawalsService;

    @Autowired
    private WithdrawalRepository withdrawalRepository;

    @BeforeEach
    void setUp() {
        withdrawalRepository.deleteAll();
    }

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

