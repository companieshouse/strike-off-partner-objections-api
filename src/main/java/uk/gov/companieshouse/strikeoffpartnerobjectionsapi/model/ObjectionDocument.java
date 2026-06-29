package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model;

import java.time.Instant;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = "objections")
@CompoundIndex(name = "company_number_objection_id_idx", def = "{'company_number': 1, 'objection_id': 1}", unique = true)
public class ObjectionDocument extends PartnerRequestDocument {

    @Field("company_number")
	private String companyNumber;

	@Field("partner_objection_reason")
	private String partnerObjectionReason;

	@Field("objection_id")
	@Indexed(unique=true)
	private String objectionId;

	@Field("processing_status")
	private String processingStatus;

	@Field("processing_status_changed_at")
	private Instant processingStatusChangedAt;


	@Field("initial_expiration_on")
	private Instant initialExpirationOn;

	@Field("failure_reason")
	private String failureReason;

	public String getCompanyNumber() {
		return companyNumber;
	}

	public void setCompanyNumber(String companyNumber) {
		this.companyNumber = companyNumber;
	}

	public String getPartnerObjectionReason() {
		return partnerObjectionReason;
	}

	public void setPartnerObjectionReason(String partnerObjectionReason) {
		this.partnerObjectionReason = partnerObjectionReason;
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


}

