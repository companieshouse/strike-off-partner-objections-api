package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import uk.gov.companieshouse.api.objections.model.BaseObjectionResponse;
import uk.gov.companieshouse.api.objections.model.BaseObjectionResponseLinks;
import uk.gov.companieshouse.api.objections.model.FailureReason;
import uk.gov.companieshouse.api.objections.model.ObjectionProcessingStatus;
import uk.gov.companieshouse.api.objections.model.PartnerObjectionReason;
import uk.gov.companieshouse.api.objections.model.PartnerObjectionWorkstream;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.ObjectionDocument;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.ObjectionLinks;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Mapper(componentModel = "spring")
public interface ObjectionResponseMapper {

    @Mapping(target = "partnerObjectionWorkstream", source = "partnerObjectionWorkstream", qualifiedByName = "toWorkstream")
    @Mapping(target = "partnerObjectionReason", source = "partnerObjectionReason", qualifiedByName = "toReason")
    @Mapping(target = "processingStatus", source = "processingStatus", qualifiedByName = "toStatus")
    @Mapping(target = "links", source = "links")
    @Mapping(target = "objectionId", source = "objectionId")
    @Mapping(target = "etag", source = "etag")
    @Mapping(target = "failureReason", source = "failureReason", qualifiedByName = "toFailureReason")
    @Mapping(target = "createdAt", source = "createdAt", qualifiedByName = "toOffsetDateTime")
    @Mapping(target = "processingStatusChangedAt", source = "processingStatusChangedAt", qualifiedByName = "toOffsetDateTime")
    @Mapping(target = "initialExpirationOn", source = "initialExpirationOn", qualifiedByName = "toOffsetDateTime")
    BaseObjectionResponse toObjectionApiResponse(ObjectionDocument document);

    default BaseObjectionResponseLinks map(ObjectionLinks links) {
        if (links == null) {
            return null;
        }
        BaseObjectionResponseLinks responseLinks = new BaseObjectionResponseLinks();
        responseLinks.setSelf(links.getSelf());
        responseLinks.setCompanyProfile(links.getCompanyProfile());
        return responseLinks;
    }

    @Named("toWorkstream")
    default PartnerObjectionWorkstream toWorkstream(String value) {
        return value == null ? null : PartnerObjectionWorkstream.fromValue(value);
    }

    @Named("toReason")
    default PartnerObjectionReason toReason(String value) {
        return value == null ? null : PartnerObjectionReason.fromValue(value);
    }

    @Named("toStatus")
    default ObjectionProcessingStatus toStatus(String value) {
        return value == null ? null : ObjectionProcessingStatus.fromValue(value);
    }

    @Named("toFailureReason")
    default FailureReason toFailureReason(String value) {
        return value == null ? null : FailureReason.fromValue(value);
    }

    @Named("toOffsetDateTime")
    default OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}