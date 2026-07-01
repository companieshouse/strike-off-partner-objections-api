package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.mapping.Document;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.ObjectionDocument;

@Tag("unit-test")
class ObjectionDocumentRepositorySchemaTest {

	@Test
	void objectionDocument_whenAnnotated_usesExpectedCollectionAndFieldMappings() throws Exception {
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
		Field field = findField(javaFieldName);
		org.springframework.data.mongodb.core.mapping.Field mongoField =
				field.getAnnotation(org.springframework.data.mongodb.core.mapping.Field.class);
		assertNotNull(mongoField);
		return mongoField.value();
	}

	private Field findField(String fieldName) throws NoSuchFieldException {
		Class<?> current = ObjectionDocument.class;
		while (current != null) {
			try {
				return current.getDeclaredField(fieldName);
			} catch (NoSuchFieldException ignored) {
				current = current.getSuperclass();
			}
		}
		throw new NoSuchFieldException(fieldName);
	}
}

