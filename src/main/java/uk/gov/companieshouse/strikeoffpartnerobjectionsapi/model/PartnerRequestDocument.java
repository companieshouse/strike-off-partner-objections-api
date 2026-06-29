package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model;

import java.time.Instant;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Field;

public abstract class PartnerRequestDocument {

    @Id
    private String id;

    @Field("submission_company_name")
    private String submissionCompanyName;

    @Field("partner_organisation")
    private String partnerOrganisation;

    @Field("partner_contact_email")
    private String partnerContactEmail;

    @Field("partner_case_reference")
    private String partnerCaseReference;

    @Field("partner_objection_workstream")
    private String partnerObjectionWorkstream;

    @CreatedDate
    @Field("created_at")
    private Instant createdAt;

    private String etag;

    private PartnerLinks links;

    private String kind;

    @Field("event_status")
    private String eventStatus;

    @Field("event_status_changed_at")
    private Instant eventStatusChangedAt;

    @Field("event_correlation_id")
    @Indexed
    private String eventCorrelationId;

    @Field("event_failure_reason")
    private String eventFailureReason;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSubmissionCompanyName() {
        return submissionCompanyName;
    }

    public void setSubmissionCompanyName(String submissionCompanyName) {
        this.submissionCompanyName = submissionCompanyName;
    }

    public String getPartnerOrganisation() {
        return partnerOrganisation;
    }

    public void setPartnerOrganisation(String partnerOrganisation) {
        this.partnerOrganisation = partnerOrganisation;
    }

    public String getPartnerContactEmail() {
        return partnerContactEmail;
    }

    public void setPartnerContactEmail(String partnerContactEmail) {
        this.partnerContactEmail = partnerContactEmail;
    }

    public String getPartnerCaseReference() {
        return partnerCaseReference;
    }

    public void setPartnerCaseReference(String partnerCaseReference) {
        this.partnerCaseReference = partnerCaseReference;
    }

    public String getPartnerObjectionWorkstream() {
        return partnerObjectionWorkstream;
    }

    public void setPartnerObjectionWorkstream(String partnerObjectionWorkstream) {
        this.partnerObjectionWorkstream = partnerObjectionWorkstream;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getEtag() {
        return etag;
    }

    public void setEtag(String etag) {
        this.etag = etag;
    }

    public PartnerLinks getLinks() {
        return links;
    }

    public void setLinks(PartnerLinks links) {
        this.links = links;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
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

