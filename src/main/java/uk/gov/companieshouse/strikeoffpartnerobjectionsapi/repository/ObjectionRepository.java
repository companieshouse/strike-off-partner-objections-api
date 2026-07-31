package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.ObjectionDocument;

public interface ObjectionRepository extends MongoRepository<ObjectionDocument, String> {
    ObjectionDocument findByCompanyNumberAndObjectionId(String companyNumber, String objectionId);

    boolean existsByCompanyNumberAndPartnerOrganisation(String companyNumber, String partnerOrganisation);
}

