package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.repository;

import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.ObjectionDocument;

public interface ObjectionRepository extends MongoRepository<ObjectionDocument, String> {
    Optional<ObjectionDocument> findByCompanyNumberAndObjectionId(String companyNumber, String objectionId);

    boolean existsByCompanyNumberAndPartnerOrganisation(String companyNumber, String partnerOrganisation);
}

