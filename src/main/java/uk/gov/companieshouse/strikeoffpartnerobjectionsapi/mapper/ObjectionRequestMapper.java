package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import uk.gov.companieshouse.api.objections.model.CreateObjectionRequest;
import uk.gov.companieshouse.api.objections.model.PartnerObjectionReason;
import uk.gov.companieshouse.api.objections.model.PartnerObjectionWorkstream;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.ObjectionDocument;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.ObjectionLinks;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static uk.gov.companieshouse.api.objections.model.ObjectionProcessingStatus.OBJECTION_SUBMITTED;
import static uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils.StrikeoffPartnerObjectionsUtils.OBJECTION_KIND;

@Mapper(componentModel = "spring")
public interface ObjectionRequestMapper {

    @Mapping(target = "objectionId", source = "objectionId")
    @Mapping(target = "processingStatus", expression = "java(getStatus())")
    @Mapping(target = "etag", source = "etag")
    @Mapping(target = "kind", constant = OBJECTION_KIND)
    @Mapping(target = "links", expression = "java(buildLinks(companyNumber, objectionId))")
    @Mapping(target = "companyNumber", source = "companyNumber")
    @Mapping(target = "partnerOrganisation", source = "partnerOrganisation")

    @Mapping(target = "submissionCompanyName", source = "request.submissionCompanyName")
    @Mapping(target = "partnerCaseReference", source = "request.partnerCaseReference")
    @Mapping(target = "partnerObjectionWorkstream", source = "request.partnerObjectionWorkstream", qualifiedByName = "workstreamToString")
    @Mapping(target = "partnerObjectionReason", source = "request.partnerObjectionReason", qualifiedByName = "reasonToString")
    @Mapping(target = "partnerContactEmail", source = "request.partnerContactEmail")
    @Mapping(target = "createdAt", expression = "java(createTime())")
    ObjectionDocument toObjectionDocument(
            CreateObjectionRequest request,
            String companyNumber,
            String partnerOrganisation,
            String objectionId,
            String etag
    );

    default String getStatus() {
        return OBJECTION_SUBMITTED.getValue();
    }

    @Named("workstreamToString")
    default String workstreamToString(PartnerObjectionWorkstream value) {
        return value == null ? null : value.getValue();
    }

    @Named("reasonToString")
    default String reasonToString(PartnerObjectionReason value) {
        return value == null ? null : value.getValue();
    }

    default OffsetDateTime createTime() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    default ObjectionLinks buildLinks(String companyNumber, String objectionId) {
        ObjectionLinks links = new ObjectionLinks();
        links.setSelf(String.format("/company/%s/strike-off-partner-objections/%s", companyNumber, objectionId));
        links.setCompanyProfile(String.format("/company/%s", companyNumber));
        return links;
    }
}

