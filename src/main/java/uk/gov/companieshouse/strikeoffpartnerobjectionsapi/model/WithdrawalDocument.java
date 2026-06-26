package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model;

import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = "withdrawals")
public class WithdrawalDocument extends PartnerRequestDocument {

    @Field("company_number")
    @Indexed
    private String companyNumber;

    @Field("withdrawal_id")
    @Indexed(unique = true)
    private String withdrawalId;

    @Field("processing_status")
    @Indexed
    private String processingStatus;

    public String getCompanyNumber() {
        return companyNumber;
    }

    public void setCompanyNumber(String companyNumber) {
        this.companyNumber = companyNumber;
    }

    public String getWithdrawalId() {
        return withdrawalId;
    }

    public void setWithdrawalId(String withdrawalId) {
        this.withdrawalId = withdrawalId;
    }

    public String getProcessingStatus() {
        return processingStatus;
    }

    public void setProcessingStatus(String processingStatus) {
        this.processingStatus = processingStatus;
    }


}

