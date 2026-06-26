package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model;

import java.time.Instant;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = "objections")
@CompoundIndex(name = "company_number_objection_id_idx", def = "{'company_number': 1, 'objection_id': 1}", unique = true)
public class ObjectionDocument {

	@Id
	private String id;

    @Field("company_number")
	private String companyNumber;

	@Field("partner_organisation")
	private String partnerOrganisation;

	@Field("submission_company_name")
	private String submissionCompanyName;

	@Field("partner_case_reference")
	private String partnerCaseReference;

	@Field("partner_objection_workstream")
	private String partnerObjectionWorkstream;

	@Field("partner_objection_reason")
	private String partnerObjectionReason;

	@Field("partner_contact_email")
	private String partnerContactEmail;

	@Field("objection_id")
	@Indexed(unique=true)
	private String objectionId;

	@Field("processing_status")
	private String processingStatus;

	@CreatedDate
	@Field("created_at")
	private Instant createdAt;

	@Field("processing_status_changed_at")
	private Instant processingStatusChangedAt;

	@Field("initial_expiration_on")
	private Instant initialExpirationOn;

	@Field("failure_reason")
	private String failureReason;

	private String etag;

	private PartnerLinks links;

	private String kind;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getCompanyNumber() {
		return companyNumber;
	}

	public void setCompanyNumber(String companyNumber) {
		this.companyNumber = companyNumber;
	}

	public String getPartnerOrganisation() {
		return partnerOrganisation;
	}

	public void setPartnerOrganisation(String partnerOrganisation) {
		this.partnerOrganisation = partnerOrganisation;
	}

	public String getSubmissionCompanyName() {
		return submissionCompanyName;
	}

	public void setSubmissionCompanyName(String submissionCompanyName) {
		this.submissionCompanyName = submissionCompanyName;
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

	public String getPartnerObjectionReason() {
		return partnerObjectionReason;
	}

	public void setPartnerObjectionReason(String partnerObjectionReason) {
		this.partnerObjectionReason = partnerObjectionReason;
	}

	public String getPartnerContactEmail() {
		return partnerContactEmail;
	}

	public void setPartnerContactEmail(String partnerContactEmail) {
		this.partnerContactEmail = partnerContactEmail;
	}

	public String getObjectionId() {
		return objectionId;
	}

	public void setObjectionId(String objectionId) {
		this.objectionId = objectionId;
	}

	public String getProcessingStatus() {
		return processingStatus;
	}

	public void setProcessingStatus(String processingStatus) {
		this.processingStatus = processingStatus;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public Instant getProcessingStatusChangedAt() {
		return processingStatusChangedAt;
	}

	public void setProcessingStatusChangedAt(Instant processingStatusChangedAt) {
		this.processingStatusChangedAt = processingStatusChangedAt;
	}

	public Instant getInitialExpirationOn() {
		return initialExpirationOn;
	}

	public void setInitialExpirationOn(Instant initialExpirationOn) {
		this.initialExpirationOn = initialExpirationOn;
	}

	public String getFailureReason() {
		return failureReason;
	}

	public void setFailureReason(String failureReason) {
		this.failureReason = failureReason;
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
}

