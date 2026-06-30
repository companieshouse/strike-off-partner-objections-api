package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import uk.gov.companieshouse.api.objections.model.BaseObjectionResponse;
import uk.gov.companieshouse.api.objections.model.CreateObjectionRequest;
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
import static uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils.StrikeoffPartnerObjectionsUtils.LOGGER;
import static uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils.StrikeoffPartnerObjectionsUtils.PARTNER_ORGANISATION;

@Service
public class StrikeOffPartnerObjectionService {

    private final ObjectionRepository objectionRepository;
    private final ObjectionRequestMapper objectionRequestMapper;
    private final ObjectionResponseMapper objectionResponseMapper;
    private final CompanyProfileService companyProfileService;
    private final ObjectionKafkaProducer objectionKafkaProducer;

    public StrikeOffPartnerObjectionService(
            ObjectionRepository objectionRepository,
            ObjectionRequestMapper objectionRequestMapper,
            ObjectionResponseMapper objectionResponseMapper,
            CompanyProfileService companyProfileService,
            ObjectionKafkaProducer objectionKafkaProducer) {
        this.objectionRepository = objectionRepository;
        this.objectionRequestMapper = objectionRequestMapper;
        this.objectionResponseMapper = objectionResponseMapper;
        this.companyProfileService = companyProfileService;
        this.objectionKafkaProducer = objectionKafkaProducer;
    }
    public BaseObjectionResponse createObjection(final String companyNumber,
                                                 final CreateObjectionRequest createObjectionRequest) {

        companyProfileService.getCompanyProfile(companyNumber);
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
        if (document == null) throw new ObjectionNotFoundException(format("Objection not found for company number=%s, objectionId=%s", companyNumber, objectionId));
        LOGGER.info(format("Objection found successfully: objectionId=%s, companyNumber=%s", document.getObjectionId(), document.getCompanyNumber()));
        return objectionResponseMapper.toObjectionApiResponse(document);
    }
}

