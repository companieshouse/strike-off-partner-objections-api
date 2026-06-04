package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Service;
import uk.gov.companieshouse.api.objections.model.BaseObjectionResponse;
import uk.gov.companieshouse.api.objections.model.BaseObjectionResponseLinks;
import uk.gov.companieshouse.api.objections.model.CreateObjectionRequest;
import uk.gov.companieshouse.api.objections.model.ObjectionProcessingStatus;
import uk.gov.companieshouse.api.objections.model.PartnerObjectionReason;
import uk.gov.companieshouse.api.objections.model.PartnerObjectionWorkstream;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.ObjectionPersistenceException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.ObjectionDocument;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.ObjectionLinks;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.repository.ObjectionRepository;

@Service
public class ObjectionService {

	static final String OBJECTION_KIND = "strike-off-partner-objection#objection";

	private final ObjectionRepository objectionRepository;

	public ObjectionService(ObjectionRepository objectionRepository) {
		this.objectionRepository = objectionRepository;
	}

	public BaseObjectionResponse createObjection(String companyNumber,
												 CreateObjectionRequest request) {
		ObjectionDocument document = toDocument(companyNumber, request);

		try {
			ObjectionDocument saved = objectionRepository.save(document);
			return toResponse(saved);
		} catch (RuntimeException ex) {
			throw new ObjectionPersistenceException("Failed to persist objection", ex);
		}
	}

	private ObjectionDocument toDocument(String companyNumber, CreateObjectionRequest request) {
		String objectionId = UUID.randomUUID().toString();

		ObjectionDocument document = new ObjectionDocument();
		document.setId(objectionId);
		document.setCompanyNumber(companyNumber);
		document.setSubmissionCompanyName(request.getSubmissionCompanyName());
		document.setPartnerCaseReference(request.getPartnerCaseReference());
		document.setPartnerObjectionWorkstream(request.getPartnerObjectionWorkstream().getValue());
		document.setPartnerObjectionReason(request.getPartnerObjectionReason().getValue());
		document.setPartnerContactEmail(request.getPartnerContactEmail());
		document.setObjectionId(objectionId);
		document.setProcessingStatus(ObjectionProcessingStatus.OBJECTION_SUBMITTED.getValue());
		document.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
		document.setEtag(UUID.randomUUID().toString());
		document.setKind(OBJECTION_KIND);
		document.setLinks(buildLinks(companyNumber, objectionId));

		return document;
	}

	private ObjectionLinks buildLinks(String companyNumber, String objectionId) {
		ObjectionLinks links = new ObjectionLinks();
		links.setSelf(String.format("/company/%s/strike-off-partner-objections/%s", companyNumber,
				objectionId));
		links.setCompanyProfile(String.format("/company/%s", companyNumber));
		return links;
	}

	private BaseObjectionResponse toResponse(ObjectionDocument document) {
		BaseObjectionResponseLinks links = new BaseObjectionResponseLinks();
		links.setSelf(document.getLinks().getSelf());
		links.setCompanyProfile(document.getLinks().getCompanyProfile());

		BaseObjectionResponse response = new BaseObjectionResponse();
		response.setCompanyNumber(document.getCompanyNumber());
		response.setSubmissionCompanyName(document.getSubmissionCompanyName());
		response.setObjectionId(document.getObjectionId());
		response.setPartnerCaseReference(document.getPartnerCaseReference());
		response.setPartnerObjectionWorkstream(
				PartnerObjectionWorkstream.fromValue(document.getPartnerObjectionWorkstream()));
		response.setPartnerObjectionReason(
				PartnerObjectionReason.fromValue(document.getPartnerObjectionReason()));
		response.setPartnerContactEmail(document.getPartnerContactEmail());
		response.setProcessingStatus(
				ObjectionProcessingStatus.fromValue(document.getProcessingStatus()));
		response.setLinks(links);
		response.setKind(document.getKind());
		response.setCreatedAt(document.getCreatedAt());
		response.setEtag(document.getEtag());

		return response;
	}
}

