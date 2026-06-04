package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.mapping.Document;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.ObjectionDocument;

class ObjectionDocumentRepositorySchemaTest {

	@Test
	void objectionDocumentUsesExpectedCollectionAndFieldMappings() throws Exception {
		Document documentAnnotation = ObjectionDocument.class.getAnnotation(Document.class);
		assertNotNull(documentAnnotation);
		assertEquals("objections", documentAnnotation.collection());

		assertEquals("company_number", getFieldName("companyNumber"));
		assertEquals("submission_company_name", getFieldName("submissionCompanyName"));
		assertEquals("partner_case_reference", getFieldName("partnerCaseReference"));
		assertEquals("partner_objection_workstream", getFieldName("partnerObjectionWorkstream"));
		assertEquals("partner_objection_reason", getFieldName("partnerObjectionReason"));
		assertEquals("partner_contact_email", getFieldName("partnerContactEmail"));
		assertEquals("objection_id", getFieldName("objectionId"));
		assertEquals("processing_status", getFieldName("processingStatus"));
		assertEquals("created_at", getFieldName("createdAt"));
	}

	private String getFieldName(String javaFieldName) throws Exception {
		Field field = ObjectionDocument.class.getDeclaredField(javaFieldName);
		org.springframework.data.mongodb.core.mapping.Field mongoField =
				field.getAnnotation(org.springframework.data.mongodb.core.mapping.Field.class);
		assertNotNull(mongoField);
		return mongoField.value();
	}
}

