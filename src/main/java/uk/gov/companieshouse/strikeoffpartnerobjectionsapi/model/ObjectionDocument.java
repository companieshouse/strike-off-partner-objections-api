package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter
@Setter
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

	private ObjectionLinks links;

	private String kind;
}

