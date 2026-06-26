package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uk.gov.companieshouse.api.objections.model.BaseObjectionResponse;
import uk.gov.companieshouse.api.objections.model.FailureReason;
import uk.gov.companieshouse.api.objections.model.ObjectionProcessingStatus;
import uk.gov.companieshouse.api.objections.model.PartnerObjectionReason;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.ObjectionDocument;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.PartnerLinks;

@Tag("unit-test")
class ObjectionResponseMapperTest {

    private final ObjectionResponseMapper mapper = new ObjectionResponseMapperImpl();

    private static final String OBJECTION_ID = "obj-123";
    private static final String ETAG = "etag-abc";
    private static final String DEBT_MANAGEMENT_WORKSTREAM = "debt-management";
    private static final Instant CREATED_AT = Instant.parse("2026-06-11T10:00:00Z");
    private static final Instant STATUS_CHANGED_AT = Instant.parse("2026-06-11T11:00:00Z");
    private static final Instant EXPIRATION_ON = Instant.parse("2026-11-11T10:00:00Z");

    @Test
    void toObjectionApiResponseMapsAllFields() {
        ObjectionDocument document = buildDocument();

        BaseObjectionResponse response = mapper.toObjectionApiResponse(document);

        assertThat(response.getObjectionId()).isEqualTo(OBJECTION_ID);
        assertThat(response.getEtag()).isEqualTo(ETAG);
        assertThat(response.getProcessingStatus()).isEqualTo(ObjectionProcessingStatus.OBJECTION_SUBMITTED);
        assertThat(response.getPartnerObjectionWorkstream()).isEqualTo(DEBT_MANAGEMENT_WORKSTREAM);
        assertThat(response.getPartnerObjectionReason()).isEqualTo(PartnerObjectionReason.OTHER);
        assertThat(response.getFailureReason()).isEqualTo(FailureReason.COMPANY_HAS_BEEN_DISSOLVED);
        assertThat(response.getProcessingStatusChangedAt()).isEqualTo(OffsetDateTime.ofInstant(STATUS_CHANGED_AT, ZoneOffset.UTC));
        assertThat(response.getInitialExpirationOn()).isEqualTo(OffsetDateTime.ofInstant(EXPIRATION_ON, ZoneOffset.UTC));
    }

    @Test
    void toObjectionApiResponseMapsLinks() {
        ObjectionDocument document = new ObjectionDocument();
        PartnerLinks links = new PartnerLinks();
        links.setSelf("/company/01234567/strike-off-partner-objections/obj-123");
        links.setCompanyProfile("/company/01234567");
        document.setLinks(links);

        BaseObjectionResponse response = mapper.toObjectionApiResponse(document);

        assertThat(response.getLinks()).isNotNull();
        assertThat(response.getLinks().getSelf()).isEqualTo("/company/01234567/strike-off-partner-objections/obj-123");
        assertThat(response.getLinks().getCompanyProfile()).isEqualTo("/company/01234567");
    }

    @Test
    void toObjectionApiResponseWhenLinksNullReturnsNullLinks() {
        ObjectionDocument document = new ObjectionDocument();
        document.setLinks(null);

        BaseObjectionResponse response = mapper.toObjectionApiResponse(document);

        assertThat(response.getLinks()).isNull();
    }

    @Test
    void toObjectionApiResponseWhenNullInstantsReturnsNullDateFields() {
        ObjectionDocument document = new ObjectionDocument();

        BaseObjectionResponse response = mapper.toObjectionApiResponse(document);

        assertThat(response.getCreatedAt()).isNull();
        assertThat(response.getProcessingStatusChangedAt()).isNull();
        assertThat(response.getInitialExpirationOn()).isNull();
    }

    @Test
    void toObjectionApiResponseWhenNullEnumsReturnsNullEnumFields() {
        ObjectionDocument document = new ObjectionDocument();

        BaseObjectionResponse response = mapper.toObjectionApiResponse(document);

        assertThat(response.getProcessingStatus()).isNull();
        assertThat(response.getPartnerObjectionWorkstream()).isNull();
        assertThat(response.getPartnerObjectionReason()).isNull();
        assertThat(response.getFailureReason()).isNull();
    }

    @Test
    void toObjectionApiResponseWhenNullDocumentReturnsNull() {
        BaseObjectionResponse response = mapper.toObjectionApiResponse(null);

        assertThat(response).isNull();
    }

    @Test
    void processingStatusChangedAtIsConvertedToUtcOffsetDateTime() {
        ObjectionDocument document = new ObjectionDocument();
        document.setProcessingStatusChangedAt(CREATED_AT);

        BaseObjectionResponse response = mapper.toObjectionApiResponse(document);

        assertThat(response.getProcessingStatusChangedAt()).isNotNull();
        assertThat(response.getProcessingStatusChangedAt().getOffset()).isEqualTo(ZoneOffset.UTC);
    }

    private ObjectionDocument buildDocument() {
        ObjectionDocument document = new ObjectionDocument();
        document.setObjectionId(OBJECTION_ID);
        document.setEtag(ETAG);
        document.setProcessingStatus(ObjectionProcessingStatus.OBJECTION_SUBMITTED.getValue());
        document.setPartnerObjectionWorkstream(DEBT_MANAGEMENT_WORKSTREAM);
        document.setPartnerObjectionReason(PartnerObjectionReason.OTHER.getValue());
        document.setFailureReason(FailureReason.COMPANY_HAS_BEEN_DISSOLVED.getValue());
        document.setProcessingStatusChangedAt(STATUS_CHANGED_AT);
        document.setInitialExpirationOn(EXPIRATION_ON);

        PartnerLinks links = new PartnerLinks();
        links.setSelf("/company/01234567/strike-off-partner-objections/" + OBJECTION_ID);
        links.setCompanyProfile("/company/01234567");
        document.setLinks(links);

        return document;
    }
}