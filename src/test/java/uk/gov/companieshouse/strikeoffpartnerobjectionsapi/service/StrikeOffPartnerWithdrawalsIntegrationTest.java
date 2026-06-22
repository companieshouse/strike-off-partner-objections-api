package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import consumer.deserialization.AvroDeserializer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.assertj.core.api.AssertionsForClassTypes;
import org.assertj.core.api.AssertionsForInterfaceTypes;
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
import uk.gov.companieshouse.strikeoff.partner.objections.EventType;
import uk.gov.companieshouse.strikeoff.partner.objections.StrikeOffPartnerObjections;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.config.BaseTestIntegration;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.WithdrawalDocument;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.repository.WithdrawalRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Tag("integration-test")
class StrikeOffPartnerWithdrawalsIntegrationTest extends BaseTestIntegration {

    private static final String COMPANY_NUMBER = "01234567";
    private static final String SECOND_COMPANY_NUMBER = "87654321";

    @Autowired
    private StrikeOffPartnerWithdrawalsService strikeOffPartnerWithdrawalsService;

    @Autowired
    private WithdrawalRepository withdrawalRepository;

    @Autowired
    private KafkaConsumer<String, byte[]> testConsumer;

    @BeforeEach
    void setUp() {
        withdrawalRepository.deleteAll();
        // Drain any leftover messages from previous tests
        testConsumer.poll(Duration.ofMillis(100));
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
    void getWithdrawal_returnsMappedResponseWithAllFields_whenRetrieved() {
        WithdrawAllObjectionsRequest request = buildRequest();
        WithdrawAllObjections201Response createResponse =
                strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request);

        WithdrawAllObjectionsResponse retrieveResponse =
                strikeOffPartnerWithdrawalsService.getWithdrawal(COMPANY_NUMBER, createResponse.getWithdrawalId());

        // Verify all fields have correct values and links
        assertThat(retrieveResponse.getCompanyNumber()).isEqualTo(COMPANY_NUMBER);
        assertThat(retrieveResponse.getSubmissionCompanyName()).isEqualTo("Acme Limited");
        assertThat(retrieveResponse.getWithdrawalId()).isEqualTo(createResponse.getWithdrawalId());
        assertThat(retrieveResponse.getPartnerContactEmail()).isEqualTo("test@example.com");
        assertThat(retrieveResponse.getPartnerCaseReference()).isEqualTo("CASE-123");
        assertThat(retrieveResponse.getPartnerObjectionWorkstream()).isEqualTo(PartnerObjectionWorkstream.DEBT_MANAGEMENT);
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


    @Test
    void withdrawAllObjectionsPublishesWithdrawalEventToKafkaTopicWhenRequestIsValid() {
        WithdrawAllObjectionsRequest request = buildRequest();

        var response = strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request);

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
    void withdrawAllObjectionsPublishesOneKafkaEventPerCall() {
        WithdrawAllObjectionsRequest request = buildRequest();

        var response1 = strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request);
        var response2 = strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request);

        List<StrikeOffPartnerObjections> events = pollKafkaForEvents(List.of(response1.getWithdrawalId(), response2.getWithdrawalId()));

        AssertionsForInterfaceTypes.assertThat(events).hasSize(2);
        // Each event should have a unique eventId
        List<String> eventIds = events.stream()
                .map(StrikeOffPartnerObjections::getEventId)
                .toList();
        AssertionsForInterfaceTypes.assertThat(eventIds).doesNotHaveDuplicates();
    }

    @Test
    void withdrawAllObjectionsKafkaEventContainsWithdrawalIdMatchingPersistedDocument() {
        WithdrawAllObjectionsRequest request = buildRequest();

        var response = strikeOffPartnerWithdrawalsService.withdrawAllObjections(COMPANY_NUMBER, request);

        List<StrikeOffPartnerObjections> events = pollKafkaForEvents(List.of(response.getWithdrawalId()));

        AssertionsForInterfaceTypes.assertThat(events).hasSize(1);
        AssertionsForClassTypes.assertThat(events.getFirst().getStrikeOffEventId())
                .isEqualTo(response.getWithdrawalId());
    }

    /**
     * Polls until {@code expectedCount} records arrive or 10 seconds elapse.
     * Uses AvroDeserializer to decode the raw byte[] payload.
     */
    private List<StrikeOffPartnerObjections> pollKafkaForEvents(List<String> expectedWithdrawalIds) {
        try (AvroDeserializer<StrikeOffPartnerObjections> deserializer =
                new AvroDeserializer<>(StrikeOffPartnerObjections.class)) {

            List<StrikeOffPartnerObjections> collected = new ArrayList<>();

            await().atMost(10, SECONDS).until(() -> {
                ConsumerRecords<String, byte[]> records =
                        testConsumer.poll(Duration.ofMillis(500));

                records.forEach(r -> collected.add(deserializer.deserialize(r.topic(), r.value())));

                return collected.size() >= expectedWithdrawalIds.size();
            });

            return collected.stream().filter(e -> expectedWithdrawalIds.contains(e.getStrikeOffEventId())).toList();
        }
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

