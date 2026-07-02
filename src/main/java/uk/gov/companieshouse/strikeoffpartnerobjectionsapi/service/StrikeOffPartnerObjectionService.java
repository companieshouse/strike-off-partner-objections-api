package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import uk.gov.companieshouse.api.error.ApiErrorResponseException;
import uk.gov.companieshouse.api.model.company.CompanyProfileApi;
import uk.gov.companieshouse.api.objections.model.BaseObjectionResponse;
import uk.gov.companieshouse.api.objections.model.CreateObjectionRequest;
import uk.gov.companieshouse.strikeoff.partner.objections.StrikeOffPartnerObjections;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.ObjectionNotFoundException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.ObjectionPersistenceException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.CompanyValidationException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.KafkaPublishException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.ServiceException;
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

    static final String COMPANY_NUMBER_NOT_EXIST = "COMPANY_NUMBER_NOT_EXIST";
    static final String SUBMISSION_COMPANY_NAME_MISMATCH = "SUBMISSION_COMPANY_NAME_MISMATCH";
    static final String INVALID_COMPANY_TYPE = "INVALID_COMPANY_TYPE";
    static final String INVALID_COMPANY_STATUS = "INVALID_COMPANY_STATUS";
    private static final String ACTIVE_PROPOSAL_TO_STRIKE_OFF = "active-proposal-to-strike-off";
    private static final Set<String> ELIGIBLE_COMPANY_TYPES = Set.of("ltd", "plc");

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

    public boolean validateRequest(String companyNumber) {
        //validation logic as parts of TRACS-64
        companyProfileService.getCompanyProfile(companyNumber);
        return true;
    }


    public BaseObjectionResponse createObjection(final String companyNumber,
                                                 final CreateObjectionRequest createObjectionRequest) {

        validateCompany(createObjectionRequest, companyNumber);

        // Validate company exists / is accessible before creating objection
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

    public void validateCompany(CreateObjectionRequest createObjectionRequest, String companyNumber) {
        CompanyProfileApi company = getCompanyProfileOrThrow(companyNumber);

        validateOrThrow(company != null, COMPANY_NUMBER_NOT_EXIST);
        validateOrThrow(isCompanyTypeEligible(company.getType()), INVALID_COMPANY_TYPE);
        validateOrThrow(hasActiveStrikeOffProposal(company), INVALID_COMPANY_STATUS);
        validateOrThrow(companyNameMatches(createObjectionRequest.getSubmissionCompanyName(), company.getCompanyName()),
                SUBMISSION_COMPANY_NAME_MISMATCH);
    }

    private void validateOrThrow(boolean condition, String errorCode) {
        if (!condition) {
            throw new CompanyValidationException(errorCode);
        }
    }

    private CompanyProfileApi getCompanyProfileOrThrow(String companyNumber) {
        try {
            return companyProfileService.getCompanyProfile(companyNumber);
        } catch (ServiceException ex) {
            if (isCompanyNotFound(ex)) {
                throw new CompanyValidationException(COMPANY_NUMBER_NOT_EXIST);
            }
            throw ex;
        }
    }

    private boolean isCompanyNotFound(ServiceException ex) {
        return ex.getCause() instanceof ApiErrorResponseException apiError
                && apiError.getStatusCode() == 404;
    }

    private boolean isCompanyTypeEligible(String companyType) {
        return companyType != null && ELIGIBLE_COMPANY_TYPES.contains(companyType.toLowerCase(Locale.ROOT));
    }

    private boolean hasActiveStrikeOffProposal(CompanyProfileApi company) {
        return ACTIVE_PROPOSAL_TO_STRIKE_OFF.equalsIgnoreCase(company.getCompanyStatusDetail());
    }

    private boolean companyNameMatches(String submissionCompanyName, String profileCompanyName) {
        return submissionCompanyName != null
                && profileCompanyName != null
                && submissionCompanyName.trim().equalsIgnoreCase(profileCompanyName.trim());
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

