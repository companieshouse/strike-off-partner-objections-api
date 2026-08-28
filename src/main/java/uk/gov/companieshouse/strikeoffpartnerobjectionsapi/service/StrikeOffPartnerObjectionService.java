package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
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
import static uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils.StrikeoffPartnerObjectionsUtils.LOGGER;

@Service
public class StrikeOffPartnerObjectionService {

    private final ObjectionRepository objectionRepository;
    private final ObjectionRequestMapper objectionRequestMapper;
    private final ObjectionResponseMapper objectionResponseMapper;
    private final ObjectionKafkaProducer objectionKafkaProducer;
    private final CompanyValidator companyValidator;

    @Autowired
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

    public BaseObjectionResponse createObjection(String companyNumber,
                                                 CreateObjectionRequest createObjectionRequest,
                                                 String partnerOrganisation) {

        // Validate company before persistence and publishing.
        // This validator is intentionally exception-driven: it returns nothing on success
        // and throws a CompanyValidationException on failure to stop processing.
        companyValidator.validateCompany(companyNumber, createObjectionRequest.getSubmissionCompanyName());

        String objectionId = UUID.randomUUID().toString();

        LOGGER.info(format("Creating objection: companyNumber=%s, partnerOrganisation=%s, objectionId=%s",
                companyNumber, partnerOrganisation, objectionId));

        ObjectionDocument document = objectionRequestMapper.toObjectionDocument(
                createObjectionRequest,
                companyNumber,
                partnerOrganisation,
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

    public BaseObjectionResponse getObjection(String companyNumber,
                                              String objectionId,
                                              String partnerOrganisation) throws ObjectionNotFoundException {

        LOGGER.info(format("Attempting to fetch objection with ID=%s and company number=%s",
                objectionId, companyNumber));

        ObjectionDocument document = objectionRepository.findByCompanyNumberAndObjectionId(companyNumber, objectionId)
                .orElseThrow(() -> new ObjectionNotFoundException(
                        format("Objection not found for company number=%s, objectionId=%s", companyNumber, objectionId)));

        if (!partnerOrganisation.equals(document.getPartnerOrganisation())) {
            LOGGER.error(format("Organisation mismatch: caller=%s, document=%s, objectionId=%s",
                    partnerOrganisation, document.getPartnerOrganisation(), objectionId));
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Access denied: objection belongs to a different organisation");
        }

        LOGGER.info(format("Objection found successfully: objectionId=%s, companyNumber=%s",
                document.getObjectionId(), document.getCompanyNumber()));

        return objectionResponseMapper.toObjectionApiResponse(document);
    }

    public void updateObjectionProcessingStatus(
            String companyNumber,
            String objectionId,
            UpdateObjectionStatusRequest updateStatusRequest) throws ObjectionNotFoundException {

        LOGGER.info(format("Attempting to update objection processing status: objectionId=%s, companyNumber=%s",
                objectionId, companyNumber));

        ObjectionDocument existingDocument = objectionRepository.findByCompanyNumberAndObjectionId(companyNumber, objectionId)
                .orElseThrow(() -> new ObjectionNotFoundException(
                        format("Objection not found for company number=%s, objectionId=%s", companyNumber, objectionId)));

        String requestedStatusValue = updateStatusRequest.getProcessingStatus().getValue().trim();
        ObjectionProcessingStatus requestedStatus = parseRequestedStatus(requestedStatusValue);

        ObjectionProcessingStatus currentStatus = parseCurrentStatus(
                existingDocument.getProcessingStatus(),
                companyNumber,
                objectionId);
        if (currentStatus == requestedStatus) {
            LOGGER.debug(format("Objection processing status unchanged: objectionId=%s, companyNumber=%s, status=%s",
                    objectionId, companyNumber, currentStatus.getValue()));
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

    private static ObjectionProcessingStatus parseRequestedStatus(String requestedStatusValue) {
        try {
            return ObjectionProcessingStatus.fromValue(requestedStatusValue);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    format("Unsupported status=%s", requestedStatusValue), ex);
        }
    }

    private static ObjectionProcessingStatus parseCurrentStatus(String currentStatusValue,
                                                                String companyNumber,
                                                                String objectionId) {
        try {
            return ObjectionProcessingStatus.fromValue(currentStatusValue);
        } catch (IllegalArgumentException | NullPointerException ex) {
            LOGGER.error(format(
                    "Invalid persisted objection processing status: companyNumber=%s, objectionId=%s, currentStatus=%s",
                    companyNumber,
                    objectionId,
                    currentStatusValue), ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to process objection status update");
        }
    }

    private static boolean isAllowedTransition(ObjectionProcessingStatus currentStatus,
                                               ObjectionProcessingStatus requestedStatus) {
        return switch (currentStatus) {
            case OBJECTION_SUBMITTED -> requestedStatus == OBJECTION_PROCESSING;
            case OBJECTION_PROCESSING -> requestedStatus == OBJECTION_ACCEPTED || requestedStatus == OBJECTION_REJECTED;
            case OBJECTION_ACCEPTED, OBJECTION_REJECTED -> false;
        };
    }
}
