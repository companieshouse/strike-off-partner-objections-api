package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.companieshouse.api.objections.model.BaseObjectionResponse;
import uk.gov.companieshouse.api.objections.model.CreateObjectionRequest;
import uk.gov.companieshouse.api.objections.model.ObjectionProcessingStatus;
import uk.gov.companieshouse.api.objections.model.UpdateObjectionStatusRequest;
import uk.gov.companieshouse.strikeoff.partner.objections.StrikeOffPartnerObjections;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.ObjectionNotFoundException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.ObjectionPersistenceException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.KafkaPublishException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.kafka.ObjectionKafkaProducer;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.mapper.ObjectionRequestMapper;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.mapper.ObjectionResponseMapper;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.ObjectionDocument;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.repository.ObjectionRepository;

import static java.lang.String.format;
import static uk.gov.companieshouse.api.objections.model.ObjectionProcessingStatus.OBJECTION_ACCEPTED;
import static uk.gov.companieshouse.api.objections.model.ObjectionProcessingStatus.OBJECTION_PROCESSING;
import static uk.gov.companieshouse.api.objections.model.ObjectionProcessingStatus.OBJECTION_REJECTED;
import static uk.gov.companieshouse.api.objections.model.ObjectionProcessingStatus.OBJECTION_SUBMITTED;
import static uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils.StrikeoffPartnerObjectionsUtils.LOGGER;
import static uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils.StrikeoffPartnerObjectionsUtils.PARTNER_ORGANISATION;

@Service
public class StrikeOffPartnerObjectionService {

    private final ObjectionRepository objectionRepository;
    private final ObjectionRequestMapper objectionRequestMapper;
    private final ObjectionResponseMapper objectionResponseMapper;
    private final ObjectionKafkaProducer objectionKafkaProducer;
    private final CompanyValidator companyValidator;

    public StrikeOffPartnerObjectionService(
            ObjectionRepository objectionRepository,
            ObjectionRequestMapper objectionRequestMapper,
            ObjectionResponseMapper objectionResponseMapper,
            ObjectionKafkaProducer objectionKafkaProducer,
            CompanyValidator companyValidator) {
        this.objectionRepository = objectionRepository;
        this.objectionRequestMapper = objectionRequestMapper;
        this.objectionResponseMapper = objectionResponseMapper;
        this.objectionKafkaProducer = objectionKafkaProducer;
        this.companyValidator = companyValidator;
    }

    public BaseObjectionResponse createObjection(final String companyNumber,
                                                 final CreateObjectionRequest createObjectionRequest) {

        // Validate company before persistence and publishing.
        // This validator is intentionally exception-driven: it returns nothing on success
        // and throws a CompanyValidationException on failure to stop processing.
        companyValidator.validateCompany(companyNumber, createObjectionRequest.getSubmissionCompanyName());

        final String objectionId = UUID.randomUUID().toString();

        LOGGER.info(format("Creating objection: companyNumber=%s, partnerOrganisation=%s, objectionId=%s",
                companyNumber, PARTNER_ORGANISATION, objectionId));

        ObjectionDocument document = objectionRequestMapper.toObjectionDocument(
                createObjectionRequest,
                companyNumber,
                PARTNER_ORGANISATION,
                objectionId
        );
        EventTracker.markPending(document);

        try {
            ObjectionDocument persistedObjection = objectionRepository.insert(document);
            LOGGER.info(format("Objection created successfully: objectionId=%s, companyNumber=%s",
                    persistedObjection.getObjectionId(), persistedObjection.getCompanyNumber()));

            publishAndSaveObjection(persistedObjection);

            return objectionResponseMapper.toObjectionApiResponse(persistedObjection);
        } catch (DataAccessException ex) {
            throw new ObjectionPersistenceException("Failed to persist objection", ex);
        }
    }

    private void publishAndSaveObjection(ObjectionDocument persistedObjection) {
        try {
            StrikeOffPartnerObjections publishedEvent = objectionKafkaProducer.publishObjectionEvent(persistedObjection);
            EventTracker.markPublished(persistedObjection, publishedEvent.getEventId());
            LOGGER.info(format("Objection event published successfully: objectionId=%s",
                    persistedObjection.getObjectionId()));
        } catch (KafkaPublishException ex) {
            EventTracker.markFailed(persistedObjection, ex.getEventId(), ex.getMessage());
            throw ex;
        } finally {
            saveObjectionEventStatus(persistedObjection);
        }
    }

    private void saveObjectionEventStatus(ObjectionDocument persistedObjection) {
        try {
            objectionRepository.save(persistedObjection);
        } catch (DataAccessException saveEx) {
            LOGGER.error(format("Failed to update objection event status: objectionId=%s",
                    persistedObjection.getObjectionId()), saveEx);
        }
    }

    public BaseObjectionResponse getObjection(final String companyNumber,
                                                 final String objectionId) throws ObjectionNotFoundException {

        LOGGER.info(format("Attempting to fetch objection with ID=%s and company number=%s",
                objectionId, companyNumber));

        ObjectionDocument document = objectionRepository.findByCompanyNumberAndObjectionId(companyNumber, objectionId);
        if (document == null) throw new ObjectionNotFoundException(
                format("Objection not found for company number=%s, objectionId=%s", companyNumber, objectionId));
        
        LOGGER.info(format("Objection found successfully: objectionId=%s, companyNumber=%s",
                document.getObjectionId(), document.getCompanyNumber()));
        
        return objectionResponseMapper.toObjectionApiResponse(document);
    }


    public void updateObjectionProcessingStatus(
            final String companyNumber,
            final String objectionId,
            final UpdateObjectionStatusRequest updateStatusRequest) throws ObjectionNotFoundException {

        LOGGER.info(format("Attempting to update objection processing status: objectionId=%s, companyNumber=%s",
                objectionId, companyNumber));

        ObjectionDocument existingDocument = objectionRepository.findByCompanyNumberAndObjectionId(companyNumber, objectionId);
        if (existingDocument == null) {
            throw new ObjectionNotFoundException(
                    format("Objection not found for company number=%s, objectionId=%s", companyNumber, objectionId));
        }

        String requestedStatusValue = updateStatusRequest.getProcessingStatus() == null
                ? null
                : updateStatusRequest.getProcessingStatus().getValue().trim();
        ObjectionProcessingStatus requestedStatus = parseRequestedStatus(requestedStatusValue);

        ObjectionProcessingStatus currentStatus = parseCurrentStatus(existingDocument.getProcessingStatus());
        if (currentStatus == requestedStatus) {
            return;
        }

        if (!isAllowedTransition(currentStatus, requestedStatus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    format("Invalid status transition from %s to %s",
                            currentStatus.getValue(), requestedStatus.getValue()));
        }

        existingDocument.setProcessingStatus(requestedStatus.getValue());
        existingDocument.setProcessingStatusChangedAt(Instant.now());
        existingDocument.setEtag(objectionRequestMapper.getEtag());

        try {
            ObjectionDocument updatedObjection = objectionRepository.save(existingDocument);
            LOGGER.info(format("Objection processing status updated successfully: objectionId=%s, companyNumber=%s",
                    updatedObjection.getObjectionId(), updatedObjection.getCompanyNumber()));
        } catch (DataAccessException ex) {
            throw new ObjectionPersistenceException("Failed to persist updated objection processing status", ex);
        }
    }

    private ObjectionProcessingStatus parseRequestedStatus(String requestedStatusValue) {
        try {
            return ObjectionProcessingStatus.fromValue(requestedStatusValue);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    format("Unsupported status=%s", requestedStatusValue), ex);
        }
    }

    private ObjectionProcessingStatus parseCurrentStatus(String currentStatusValue) {
        try {
            return ObjectionProcessingStatus.fromValue(currentStatusValue);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    format("Invalid current processing status=%s", currentStatusValue), ex);
        }
    }

    private boolean isAllowedTransition(ObjectionProcessingStatus currentStatus,
                                        ObjectionProcessingStatus requestedStatus) {
        return switch (currentStatus) {
            case OBJECTION_SUBMITTED -> requestedStatus == OBJECTION_PROCESSING;
            case OBJECTION_PROCESSING -> requestedStatus == OBJECTION_ACCEPTED || requestedStatus == OBJECTION_REJECTED;
            case OBJECTION_ACCEPTED, OBJECTION_REJECTED -> false;
        };
    }
}

