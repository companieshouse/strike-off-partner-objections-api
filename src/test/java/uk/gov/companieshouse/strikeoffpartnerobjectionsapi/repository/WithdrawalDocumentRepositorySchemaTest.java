package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.WithdrawalDocument;

@Tag("unit-test")
class WithdrawalDocumentRepositorySchemaTest {

    @Test
    void withdrawalDocument_usesExpectedCollectionName_whenAnnotated() {
        Document documentAnnotation = WithdrawalDocument.class.getAnnotation(Document.class);
        assertNotNull(documentAnnotation);
        assertEquals("withdrawals", documentAnnotation.collection());
    }

    @Test
    void withdrawalDocument_hasExpectedFieldMappings_whenAnnotated() throws Exception {
        assertEquals("company_number", getFieldName("companyNumber"));
        assertEquals("submission_company_name", getFieldName("submissionCompanyName"));
        assertEquals("withdrawal_id", getFieldName("withdrawalId"));
        assertEquals("partner_organisation", getFieldName("partnerOrganisation"));
        assertEquals("partner_contact_email", getFieldName("partnerContactEmail"));
        assertEquals("partner_case_reference", getFieldName("partnerCaseReference"));
        assertEquals("partner_objection_workstream", getFieldName("partnerObjectionWorkstream"));
        assertEquals("processing_status", getFieldName("processingStatus"));
        assertEquals("created_at", getFieldName("createdAt"));
    }

    @Test
    void withdrawalId_hasUniqueIndex_whenAnnotated() throws Exception {
        Field field = WithdrawalDocument.class.getDeclaredField("withdrawalId");
        Indexed indexed = field.getAnnotation(Indexed.class);
        assertNotNull(indexed, "withdrawalId should have @Indexed annotation");
        assertTrue(indexed.unique(), "withdrawalId index should be unique");
    }

    @Test
    void companyNumber_hasIndex_whenAnnotated() throws Exception {
        Field field = WithdrawalDocument.class.getDeclaredField("companyNumber");
        Indexed indexed = field.getAnnotation(Indexed.class);
        assertNotNull(indexed, "companyNumber should have @Indexed annotation");
    }

    @Test
    void processingStatus_hasIndex_whenAnnotated() throws Exception {
        Field field = WithdrawalDocument.class.getDeclaredField("processingStatus");
        Indexed indexed = field.getAnnotation(Indexed.class);
        assertNotNull(indexed, "processingStatus should have @Indexed annotation");
    }

    private String getFieldName(String javaFieldName) throws Exception {
        Field field = WithdrawalDocument.class.getDeclaredField(javaFieldName);
        org.springframework.data.mongodb.core.mapping.Field mongoField =
                field.getAnnotation(org.springframework.data.mongodb.core.mapping.Field.class);
        assertNotNull(mongoField, "Field " + javaFieldName + " should have @Field annotation");
        return mongoField.value();
    }
}

