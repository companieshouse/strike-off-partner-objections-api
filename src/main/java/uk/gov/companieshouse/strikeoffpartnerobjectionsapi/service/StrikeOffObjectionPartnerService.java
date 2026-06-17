package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import uk.gov.companieshouse.api.model.company.CompanyProfileApi;
import uk.gov.companieshouse.api.objections.model.BaseObjectionResponse;
import uk.gov.companieshouse.api.objections.model.CreateObjectionRequest;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.ObjectionNotFoundException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.ObjectionPersistenceException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.mapper.ObjectionRequestMapper;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.mapper.ObjectionResponseMapper;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.ObjectionDocument;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.repository.ObjectionRepository;

import static java.lang.String.format;
import static uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils.StrikeoffPartnerObjectionsUtils.LOGGER;
import static uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils.StrikeoffPartnerObjectionsUtils.PARTNER_ORGANISATION;

@Service
public class StrikeOffObjectionPartnerService {

    private final ObjectionRepository objectionRepository;
    private final ObjectionRequestMapper objectionRequestMapper;
    private final ObjectionResponseMapper objectionResponseMapper;
    private final CompanyProfileService companyProfileService;

    public StrikeOffObjectionPartnerService(ObjectionRepository objectionRepository,
                                            ObjectionRequestMapper objectionRequestMapper,
                                            ObjectionResponseMapper objectionResponseMapper,
                                            CompanyProfileService companyProfileService) {
        this.objectionRepository = objectionRepository;
        this.objectionRequestMapper = objectionRequestMapper;
        this.objectionResponseMapper = objectionResponseMapper;
        this.companyProfileService = companyProfileService;
    }

    public BaseObjectionResponse createObjection(final String companyNumber,
                                                 final CreateObjectionRequest createObjectionRequest) {

        final String objectionId = UUID.randomUUID().toString();

        LOGGER.info(format("Creating objection: companyNumber=%s, partnerOrganisation=%s, objectionId=%s",
                companyNumber, PARTNER_ORGANISATION, objectionId));

        CompanyProfileApi companyProfile = companyProfileService.getCompanyProfile(companyNumber);
        LOGGER.info(format("Retrieved company profile: companyNumber=%s, companyName=%s, companyStatus=%s",
                companyNumber, companyProfile.getCompanyName(), companyProfile.getCompanyStatus()));

        ObjectionDocument document = objectionRequestMapper.toObjectionDocument(
                createObjectionRequest,
                companyNumber,
                PARTNER_ORGANISATION,
                objectionId
        );
        try {
            ObjectionDocument saved = objectionRepository.insert(document);
            LOGGER.info(format("Objection created successfully: objectionId=%s, companyNumber=%s", saved.getObjectionId(), saved.getCompanyNumber()));
            return objectionResponseMapper.toObjectionApiResponse(saved);
        } catch (DataAccessException ex) {
            throw new ObjectionPersistenceException("Failed to persist objection", ex);
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
