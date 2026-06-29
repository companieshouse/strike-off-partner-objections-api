package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model;

import java.time.Instant;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = "withdrawals")
public class WithdrawalDocument extends PartnerRequestDocument {

    @Field("company_number")
    @Indexed
    private String companyNumber;

    @Field("withdrawal_id")
    @Indexed(unique = true)
    private String withdrawalId;

    @Field("processing_status")
    @Indexed
    private String processingStatus;

    @Field("event_status")
    @Indexed
    private String eventStatus;

    @Field("event_status_changed_at")
    private Instant eventStatusChangedAt;

    @Field("event_correlation_id")
    @Indexed
    private String eventCorrelationId;

    @Field("event_failure_reason")
    private String eventFailureReason;

    public String getCompanyNumber() {
        return companyNumber;
    }

    public void setCompanyNumber(String companyNumber) {
        this.companyNumber = companyNumber;
    }

    public String getWithdrawalId() {
        return withdrawalId;
    }

    public void setWithdrawalId(String withdrawalId) {
        this.withdrawalId = withdrawalId;
    }

    public String getProcessingStatus() {
        return processingStatus;
    }

    public void setProcessingStatus(String processingStatus) {
        this.processingStatus = processingStatus;
    }

    public String getEventStatus() {
        return eventStatus;
    }

    public void setEventStatus(String eventStatus) {
        this.eventStatus = eventStatus;
    }

    public Instant getEventStatusChangedAt() {
        return eventStatusChangedAt;
    }

    public void setEventStatusChangedAt(Instant eventStatusChangedAt) {
        this.eventStatusChangedAt = eventStatusChangedAt;
    }

    public String getEventCorrelationId() {
        return eventCorrelationId;
    }

    public void setEventCorrelationId(String eventCorrelationId) {
        this.eventCorrelationId = eventCorrelationId;
    }

    public String getEventFailureReason() {
        return eventFailureReason;
    }

    public void setEventFailureReason(String eventFailureReason) {
        this.eventFailureReason = eventFailureReason;
    }


}

