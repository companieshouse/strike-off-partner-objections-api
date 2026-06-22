package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import uk.gov.companieshouse.api.objections.model.CallbackResourceKind;
import uk.gov.companieshouse.api.objections.model.CreateObjectionRequest;
import uk.gov.companieshouse.api.objections.model.PartnerObjectionReason;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.ObjectionDocument;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.ObjectionLinks;

import static uk.gov.companieshouse.GenerateEtagUtil.generateEtag;
import static uk.gov.companieshouse.api.objections.model.ObjectionProcessingStatus.OBJECTION_SUBMITTED;

@Mapper(componentModel = "spring")
public interface ObjectionRequestMapper {

    @Mapping(target = "objectionId", source = "objectionId")
    @Mapping(target = "processingStatus", expression = "java(getStatus())")
    @Mapping(target = "etag", expression = "java(getEtag())")
    @Mapping(target = "kind", expression = "java(getObjectionKind())")
    @Mapping(target = "links", expression = "java(buildLinks(companyNumber, objectionId))")
    @Mapping(target = "companyNumber", source = "companyNumber")
    @Mapping(target = "partnerOrganisation", source = "partnerOrganisation")
    @Mapping(target = "submissionCompanyName", source = "request.submissionCompanyName")
    @Mapping(target = "partnerCaseReference", source = "request.partnerCaseReference")
    @Mapping(target = "partnerObjectionWorkstream", source = "request.partnerObjectionWorkstream")
    @Mapping(target = "partnerObjectionReason", source = "request.partnerObjectionReason", qualifiedByName = "reasonToString")
    @Mapping(target = "partnerContactEmail", source = "request.partnerContactEmail")
    ObjectionDocument toObjectionDocument(
            CreateObjectionRequest request,
            String companyNumber,
            String partnerOrganisation,
            String objectionId
    );

    default String getStatus() {
        return OBJECTION_SUBMITTED.getValue();
    }


    /**
     * KIND is constant for all objections created by this API, so it is set as a constant in the mapping annotation.
     */
    default String getObjectionKind() {
        return CallbackResourceKind.STRIKE_OFF_PARTNER_OBJECTION_OBJECTION.getValue();
    }


    @Named("reasonToString")
    default String reasonToString(PartnerObjectionReason value) {
        return value == null ? null : value.getValue();
    }

    default ObjectionLinks buildLinks(String companyNumber, String objectionId) {
        ObjectionLinks links = new ObjectionLinks();
        links.setSelf(String.format("/company/%s/strike-off-partner-objections/%s", companyNumber, objectionId));
        links.setCompanyProfile(String.format("/company/%s", companyNumber));
        return links;
    }

    /**
     *
     * Generates etag for Objection record
     */
    default String getEtag() {
        return generateEtag();
    }

}

