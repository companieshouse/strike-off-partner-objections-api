package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.mapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import uk.gov.companieshouse.api.objections.model.CallbackResourceKind;
import uk.gov.companieshouse.api.objections.model.WithdrawAllObjectionsRequest;
import uk.gov.companieshouse.api.objections.model.WithdrawAllObjectionsResponse;
import uk.gov.companieshouse.api.objections.model.WithdrawAllObjectionsResponseLinks;
import uk.gov.companieshouse.api.objections.model.WithdrawalRequestedStatus;
import uk.gov.companieshouse.api.objections.model.WithdrawalProcessingStatus;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.WithdrawalDocument;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.WithdrawalLinks;

@Mapper(componentModel = "spring")
public interface WithdrawalMapper {

    @Mapping(target = "withdrawalId", source = "withdrawalId")
    @Mapping(target = "companyNumber", source = "companyNumber")
    @Mapping(target = "partnerOrganisation", source = "partnerOrganisation")
    @Mapping(target = "etag", source = "etag")
    @Mapping(target = "submissionCompanyName", source = "request.submissionCompanyName")
    @Mapping(target = "partnerCaseReference", source = "request.partnerCaseReference")
    @Mapping(target = "partnerContactEmail", source = "request.partnerContactEmail")
    @Mapping(target = "partnerObjectionWorkstream", source = "request.partnerObjectionWorkstream")
    @Mapping(target = "processingStatus", expression = "java(getStatus())")
    @Mapping(target = "kind", expression = "java(getWithdrawalKind())")
    @Mapping(target = "links", expression = "java(buildLinks(companyNumber, withdrawalId))")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    WithdrawalDocument toWithdrawalDocument(
            WithdrawAllObjectionsRequest request,
            String companyNumber,
            String partnerOrganisation,
            String withdrawalId,
            String etag
    );


    @Named("toWithdrawalProcessingStatus")
    default WithdrawalProcessingStatus toWithdrawalProcessingStatus(String value) {
        return value == null ? null : WithdrawalProcessingStatus.fromValue(value);
    }

    @Named("toOffsetDateTime")
    default OffsetDateTime toOffsetDateTime(Instant value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }

    @Named("toResponseLinks")
    default WithdrawAllObjectionsResponseLinks toResponseLinks(WithdrawalLinks links) {
        if (links == null) {
            return null;
        }
        return new WithdrawAllObjectionsResponseLinks()
                .self(links.getSelf())
                .companyProfile(links.getCompanyProfile());
    }

    /**
     * Processing status set on all newly created withdrawals.
     */
    default String getStatus() {
        return WithdrawalRequestedStatus.WITHDRAWAL_REQUESTED.getValue();
    }

    /**
     * KIND is constant for all withdrawals created by this API.
     */
    default String getWithdrawalKind() {
        return CallbackResourceKind.STRIKE_OFF_PARTNER_OBJECTION_WITHDRAWAL.getValue();
    }

    default WithdrawalLinks buildLinks(String companyNumber, String withdrawalId) {
        WithdrawalLinks links = new WithdrawalLinks();
        links.setSelf(String.format("/company/%s/strike-off-partner-objections-withdrawals/%s",
                companyNumber, withdrawalId));
        links.setCompanyProfile(String.format("/company/%s", companyNumber));
        return links;
    }

    @Mapping(target = "processingStatus", source = "processingStatus", qualifiedByName = "toWithdrawalProcessingStatus")
    @Mapping(target = "partnerObjectionWorkstream", source = "partnerObjectionWorkstream")
    @Mapping(target = "createdAt", source = "createdAt", qualifiedByName = "toOffsetDateTime")
    @Mapping(target = "links", source = "links", qualifiedByName = "toResponseLinks")
    @Mapping(target = "failureReason", ignore = true)
    @Mapping(target = "processingStatusChangedAt", ignore = true)
    WithdrawAllObjectionsResponse toWithdrawAllObjectionsResponse(WithdrawalDocument document);
}
