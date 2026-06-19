package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.WithdrawalDocument;

import java.util.Optional;

public interface WithdrawalRepository extends MongoRepository<WithdrawalDocument, String> {

    Optional<WithdrawalDocument> findByWithdrawalId(String withdrawalId);

    Optional<WithdrawalDocument> findByCompanyNumberAndWithdrawalId(String companyNumber, String withdrawalId);
}
