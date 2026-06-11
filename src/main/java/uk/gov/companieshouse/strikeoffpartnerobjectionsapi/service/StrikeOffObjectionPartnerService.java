package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import uk.gov.companieshouse.api.objections.model.BaseObjectionResponse;
import uk.gov.companieshouse.api.objections.model.CreateObjectionRequest;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.ObjectionPersistenceException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.mapper.ObjectionRequestMapper;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.ObjectionDocument;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.repository.ObjectionRepository;

import static java.lang.String.format;
import static uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils.StrikeoffPartnerObjectionsUtils.LOGGER;

@Service
public class StrikeOffObjectionPartnerService {

    private final ObjectionRepository objectionRepository;
    private final ObjectionRequestMapper objectionRequestMapper;
    public StrikeOffObjectionPartnerService(ObjectionRepository objectionRepository, ObjectionRequestMapper objectionRequestMapper) {
        this.objectionRepository = objectionRepository;
        this.objectionRequestMapper = objectionRequestMapper;
    }

    public BaseObjectionResponse createObjection(final String companyNumber,
                                                 final CreateObjectionRequest createObjectionRequest) {
        //TODO: partnerOrganisation will be retrieved from API key // NOSONAR
        final String partnerOrganisation = "hmrc";
        final String objectionId = UUID.randomUUID().toString();
        final String etag = UUID.randomUUID().toString();
        LOGGER.info(format("Creating objection: companyNumber=%s, partnerOrganisation=%s, objectionId=%s",
                companyNumber, partnerOrganisation, objectionId));

        ObjectionDocument document = objectionRequestMapper.toObjectionDocument(
                createObjectionRequest,
                companyNumber,
                partnerOrganisation,
                objectionId,
                etag
        );
        try {
            ObjectionDocument saved = objectionRepository.insert(document);
            LOGGER.info(format("Objection created successfully: objectionId=%s, companyNumber=%s", saved.getObjectionId(), saved.getCompanyNumber()));
            //TODO: RESPONSE MAPPING // NOSONAR
            return new BaseObjectionResponse();
        } catch (DataAccessException ex) {
            throw new ObjectionPersistenceException("Failed to persist objection", ex);
        }
    }
}
