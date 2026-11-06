package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.validation.Validator;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.companieshouse.api.objections.model.UpdateWithdrawalStatusRequest;
import uk.gov.companieshouse.api.objections.model.WithdrawAllObjectionsRequest;
import uk.gov.companieshouse.api.objections.model.WithdrawalProcessingStatus;
import uk.gov.companieshouse.strikeoff.partner.objections.StrikeOffPartnerObjections;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.config.MongoDbIntegration;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.KafkaPublishException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.HmrcCallbackFailedException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.kafka.WithdrawalKafkaProducer;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.mapper.WithdrawalMapper;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.mapper.WithdrawalMapperImpl;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.CallbackResult;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.EventStatus;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.WithdrawalDocument;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.enums.CallbackStatus;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.repository.ObjectionRepository;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.repository.WithdrawalRepository;

@Tag("integration-test")
@ExtendWith(MockitoExtension.class)
@SpringJUnitConfig(StrikeOffPartnerObjectionEventStatusIntegrationTest.MongoTestConfiguration.class)
class WithdrawalStatusRaceIntegrationTest extends MongoDbIntegration {

    private static final String COMPANY_NUMBER = "01234567";
    private static final String WITHDRAWAL_ID = "withdrawal-race";
    private static final String PARTNER = "hmrc";
    private static final String EVENT_ID = "event-id";
    private static final String FAILURE_REASON = "publish failed";
    private static final Instant CHANGED_AT = Instant.parse("2026-01-01T12:00:00Z");
    private static final String NEW_ETAG = "new-etag";

    @Autowired
    private WithdrawalRepository repository;
    @Mock
    private ObjectionRepository objections;
    @Mock
    private WithdrawalKafkaProducer producer;
    @Mock
    private CompanyValidator companyValidator;
    @Mock
    private EmailValidator emailValidator;
    @Mock
    private Validator validator;
    @Mock
    private HmrcCallbackService callbacks;
    private final WithdrawalMapper mapper = new WithdrawalMapperImpl();
    private StrikeOffPartnerWithdrawalsService service;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        service = new StrikeOffPartnerWithdrawalsService(
                repository, objections, mapper, producer, companyValidator, validator, emailValidator, callbacks);
    }

    @ParameterizedTest
    @EnumSource(value = EventStatus.class, names = {"PUBLISHED", "FAILED"})
    void publication_preservesNewerProcessingAndCallbackState(EventStatus status) {
        when(objections.existsByCompanyNumberAndPartnerOrganisation(COMPANY_NUMBER, PARTNER)).thenReturn(true);
        KafkaPublishException failure = new KafkaPublishException(FAILURE_REASON, EVENT_ID,
                new IllegalStateException("Kafka unavailable"));
        when(producer.publishWithdrawalEvent(any())).thenAnswer(invocation -> {
            WithdrawalDocument stale = invocation.getArgument(0);
            repository.updateProcessingStatus(COMPANY_NUMBER, stale.getWithdrawalId(), stale.getProcessingStatus(),
                    WithdrawalProcessingStatus.WITHDRAWAL_PROCESSING.getValue(), CHANGED_AT, NEW_ETAG);
            repository.updateCallbackStatus(COMPANY_NUMBER, stale.getWithdrawalId(),
                    CallbackStatus.FAILED, CHANGED_AT, "callback-id", "callback failed");
            if (status == EventStatus.FAILED) {
                throw failure;
            }
            StrikeOffPartnerObjections event = new StrikeOffPartnerObjections();
            event.setEventId(EVENT_ID);
            return event;
        });
        WithdrawAllObjectionsRequest request = new WithdrawAllObjectionsRequest();
        if (status == EventStatus.FAILED) {
            assertThatThrownBy(() -> service.withdrawAllObjections(COMPANY_NUMBER, request, PARTNER)).isSameAs(failure);
        } else {
            service.withdrawAllObjections(COMPANY_NUMBER, request, PARTNER);
        }
        WithdrawalDocument persisted = repository.findAll().getFirst();
        assertThat(persisted.getProcessingStatus())
                .isEqualTo(WithdrawalProcessingStatus.WITHDRAWAL_PROCESSING.getValue());
        assertThat(persisted.getProcessingStatusChangedAt()).isEqualTo(CHANGED_AT);
        assertThat(persisted.getEtag()).isEqualTo(NEW_ETAG);
        assertThat(persisted.getCallbackStatus()).isEqualTo(CallbackStatus.FAILED);
        assertThat(persisted.getCallbackStatusChangedAt()).isEqualTo(CHANGED_AT);
        assertThat(persisted.getCallbackCorrelationId()).isEqualTo("callback-id");
        assertThat(persisted.getCallbackFailureReason()).isEqualTo("callback failed");
        assertEventFields(persisted, status);
    }

    @ParameterizedTest
    @EnumSource(value = EventStatus.class, names = {"PUBLISHED", "FAILED"})
    void processingUpdate_preservesEventUpdateAfterRead(EventStatus status) {
        insertWithdrawal();
        UpdateWithdrawalStatusRequest request = processingRequest(WithdrawalProcessingStatus.WITHDRAWAL_PROCESSING);
        when(validator.validate(request)).thenAnswer(invocation -> {
            persistEvent(status);
            repository.updateCallbackStatus(COMPANY_NUMBER, WITHDRAWAL_ID,
                    CallbackStatus.FAILED, CHANGED_AT, "callback-id", "callback failed");
            return Set.of();
        });

        service.updateWithdrawalProcessingStatus(COMPANY_NUMBER, WITHDRAWAL_ID, request);

        WithdrawalDocument persisted = findWithdrawal();
        assertThat(persisted.getProcessingStatus()).isEqualTo(request.getProcessingStatus().getValue());
        assertThat(persisted.getProcessingStatusChangedAt()).isNotNull();
        assertEventFields(persisted, status);
        assertThat(persisted.getCallbackStatus()).isEqualTo(CallbackStatus.FAILED);
        verifyNoInteractions(callbacks);
    }

    @Test
    void processingUpdate_whenConcurrentUpdateWins_returnsConflictWithoutCallback() {
        insertWithdrawal();
        UpdateWithdrawalStatusRequest request = processingRequest(WithdrawalProcessingStatus.WITHDRAWAL_PROCESSING);
        when(validator.validate(request)).thenAnswer(invocation -> {
            repository.updateProcessingStatus(COMPANY_NUMBER, WITHDRAWAL_ID,
                    WithdrawalProcessingStatus.WITHDRAWAL_REQUESTED.getValue(),
                    WithdrawalProcessingStatus.WITHDRAWAL_PROCESSING.getValue(), CHANGED_AT, NEW_ETAG);
            return Set.of();
        });

        assertThatThrownBy(() -> service.updateWithdrawalProcessingStatus(COMPANY_NUMBER, WITHDRAWAL_ID, request))
                .isInstanceOf(ResponseStatusException.class).hasFieldOrPropertyWithValue("statusCode", HttpStatus.CONFLICT);
        assertThat(findWithdrawal().getEtag()).isEqualTo(NEW_ETAG);
        assertThat(findWithdrawal().getProcessingStatusChangedAt()).isEqualTo(CHANGED_AT);
        verifyNoInteractions(callbacks);
    }

    @Test
    void processingUpdate_whenDeletedAfterRead_doesNotRecreateDocument() {
        insertWithdrawal();
        UpdateWithdrawalStatusRequest request = processingRequest(WithdrawalProcessingStatus.WITHDRAWAL_PROCESSING);
        when(validator.validate(request)).thenAnswer(invocation -> {
            repository.deleteAll();
            return Set.of();
        });

        assertThatThrownBy(() -> service.updateWithdrawalProcessingStatus(COMPANY_NUMBER, WITHDRAWAL_ID, request))
                .isInstanceOf(ResponseStatusException.class).hasFieldOrPropertyWithValue("statusCode", HttpStatus.CONFLICT);
        assertThat(repository.findAll()).isEmpty();
        verifyNoInteractions(callbacks);
    }

    @ParameterizedTest
    @EnumSource(value = CallbackStatus.class, names = {"SUCCESS", "FAILED"})
    void synchronousCallbackPreservesProcessingAndEventUpdates(CallbackStatus status) {
        insertWithdrawal();
        service.updateWithdrawalProcessingStatus(COMPANY_NUMBER, WITHDRAWAL_ID,
                processingRequest(WithdrawalProcessingStatus.WITHDRAWAL_PROCESSING));

        CallbackResult result = status == CallbackStatus.SUCCESS
                ? CallbackResult.success("callback-id") : CallbackResult.failure("callback failed");
        when(callbacks.sendWithdrawalOutcomeCallback(eq(WITHDRAWAL_ID), eq(COMPANY_NUMBER), anyString()))
                .thenAnswer(invocation -> {
                    repository.updateProcessingStatus(COMPANY_NUMBER, WITHDRAWAL_ID,
                            WithdrawalProcessingStatus.WITHDRAWAL_ACCEPTED.getValue(),
                            WithdrawalProcessingStatus.WITHDRAWAL_REQUESTED.getValue(), CHANGED_AT, NEW_ETAG);
                    persistEvent(EventStatus.PUBLISHED);
                    return result;
                });

        UpdateWithdrawalStatusRequest terminalRequest =
                processingRequest(WithdrawalProcessingStatus.WITHDRAWAL_ACCEPTED);
        if (status == CallbackStatus.FAILED) {
            assertThatThrownBy(() -> service.updateWithdrawalProcessingStatus(COMPANY_NUMBER, WITHDRAWAL_ID,
                    terminalRequest))
                    .isInstanceOf(HmrcCallbackFailedException.class);
        } else {
            service.updateWithdrawalProcessingStatus(COMPANY_NUMBER, WITHDRAWAL_ID, terminalRequest);
        }

        WithdrawalDocument persisted = findWithdrawal();
        assertThat(persisted.getProcessingStatus())
                .isEqualTo(WithdrawalProcessingStatus.WITHDRAWAL_REQUESTED.getValue());
        assertThat(persisted.getProcessingStatusChangedAt()).isEqualTo(CHANGED_AT);
        assertThat(persisted.getEtag()).isEqualTo(NEW_ETAG);
        assertEventFields(persisted, EventStatus.PUBLISHED);
        assertThat(persisted.getCallbackStatus()).isEqualTo(status);
        assertThat(persisted.getCallbackCorrelationId()).isEqualTo(status == CallbackStatus.SUCCESS ? "callback-id" : null);
        assertThat(persisted.getCallbackFailureReason()).isEqualTo(status == CallbackStatus.FAILED ? "callback failed" : null);
    }

    private void insertWithdrawal() {
        WithdrawalDocument document = mapper.toWithdrawalDocument(
                new WithdrawAllObjectionsRequest(), COMPANY_NUMBER, PARTNER, WITHDRAWAL_ID, "original-etag");
        document.setEventStatus(EventStatus.PENDING);
        repository.insert(document);
    }

    private void persistEvent(EventStatus status) {
        repository.updateEventStatus(COMPANY_NUMBER, WITHDRAWAL_ID, status, CHANGED_AT, EVENT_ID,
                status == EventStatus.FAILED ? FAILURE_REASON : null);
    }

    private void assertEventFields(WithdrawalDocument document, EventStatus status) {
        assertThat(document.getEventStatus()).isEqualTo(status);
        assertThat(document.getEventStatusChangedAt()).isNotNull();
        assertThat(document.getEventCorrelationId()).isEqualTo(EVENT_ID);
        assertThat(document.getEventFailureReason()).isEqualTo(status == EventStatus.FAILED ? FAILURE_REASON : null);
    }

    private WithdrawalDocument findWithdrawal() {
        return repository.findByCompanyNumberAndWithdrawalId(COMPANY_NUMBER, WITHDRAWAL_ID).orElseThrow();
    }

    private UpdateWithdrawalStatusRequest processingRequest(WithdrawalProcessingStatus status) {
        UpdateWithdrawalStatusRequest request = new UpdateWithdrawalStatusRequest();
        request.setProcessingStatus(status);
        return request;
    }
}
