package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.mapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import uk.gov.companieshouse.api.objections.model.PartnerObjectionWorkstream;
import uk.gov.companieshouse.api.objections.model.WithdrawAllObjections201Response;
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
    @Mapping(target = "partnerObjectionWorkstream", source = "request.partnerObjectionWorkstream", qualifiedByName = "mapWorkstreamToString")
    @Mapping(target = "processingStatus", expression = "java(getInitialWithdrawalStatus())")
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

    @Mapping(target = "processingStatus", source = "processingStatus", qualifiedByName = "mapStringToWithdrawalRequestedStatus")
    @Mapping(target = "partnerObjectionWorkstream", source = "partnerObjectionWorkstream", qualifiedByName = "mapStringToWorkstream")
    @Mapping(target = "createdAt", source = "createdAt", qualifiedByName = "mapInstantToOffsetDateTime")
    @Mapping(target = "links", source = "links", qualifiedByName = "mapWithdrawalLinksToResponseLinks")
    @Mapping(target = "failureReason", ignore = true)
    @Mapping(target = "processingStatusChangedAt", ignore = true)
    WithdrawAllObjections201Response toWithdrawAllObjections201Response(WithdrawalDocument document);

    @Named("mapWorkstreamToString")
    default String mapWorkstreamToString(PartnerObjectionWorkstream value) {
        return value == null ? null : value.getValue();
    }

    @Named("mapStringToWorkstream")
    default PartnerObjectionWorkstream mapStringToWorkstream(String value) {
        return value == null ? null : PartnerObjectionWorkstream.fromValue(value);
    }

    @Named("mapStringToWithdrawalRequestedStatus")
    default WithdrawalRequestedStatus mapStringToWithdrawalRequestedStatus(String value) {
        return value == null ? null : WithdrawalRequestedStatus.fromValue(value);
    }

    @Named("mapStringToWithdrawalProcessingStatus")
    default WithdrawalProcessingStatus mapStringToWithdrawalProcessingStatus(String value) {
        return value == null ? null : WithdrawalProcessingStatus.fromValue(value);
    }

    @Named("mapInstantToOffsetDateTime")
    default OffsetDateTime mapInstantToOffsetDateTime(Instant value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }

    @Named("mapWithdrawalLinksToResponseLinks")
    default WithdrawAllObjectionsResponseLinks mapWithdrawalLinksToResponseLinks(WithdrawalLinks links) {
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
    default String getInitialWithdrawalStatus() {
        return WithdrawalRequestedStatus.WITHDRAWAL_REQUESTED.getValue();
    }

    /**
     * KIND is constant for all withdrawals created by this API.
     */
    default String getWithdrawalKind() {
        return "strike-off-partner-objection#withdrawal";
    }

    default WithdrawalLinks buildLinks(String companyNumber, String withdrawalId) {
        WithdrawalLinks links = new WithdrawalLinks();
        links.setSelf(String.format("/company/%s/strike-off-partner-objections-withdrawals/%s",
                companyNumber, withdrawalId));
        links.setCompanyProfile(String.format("/company/%s", companyNumber));
        return links;
    }

    @Mapping(target = "processingStatus", source = "processingStatus", qualifiedByName = "mapStringToWithdrawalProcessingStatus")
    @Mapping(target = "partnerObjectionWorkstream", source = "partnerObjectionWorkstream", qualifiedByName = "mapStringToWorkstream")
    @Mapping(target = "createdAt", source = "createdAt", qualifiedByName = "mapInstantToOffsetDateTime")
    @Mapping(target = "links", source = "links", qualifiedByName = "mapWithdrawalLinksToResponseLinks")
    @Mapping(target = "failureReason", ignore = true)
    @Mapping(target = "processingStatusChangedAt", ignore = true)
    WithdrawAllObjectionsResponse toWithdrawAllObjectionsResponse(WithdrawalDocument document);
}
