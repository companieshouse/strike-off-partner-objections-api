package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.companieshouse.api.objections.model.BaseObjectionResponse;
import uk.gov.companieshouse.api.objections.model.CreateObjectionRequest;
import uk.gov.companieshouse.api.objections.model.ObjectionProcessingStatus;
import uk.gov.companieshouse.api.objections.model.PartnerObjectionReason;
import uk.gov.companieshouse.api.objections.model.PartnerObjectionWorkstream;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.ObjectionPersistenceException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.ObjectionDocument;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.repository.ObjectionRepository;

@ExtendWith(MockitoExtension.class)
class ObjectionServiceTest {

	@Mock
	private ObjectionRepository objectionRepository;

	@Captor
	private ArgumentCaptor<ObjectionDocument> objectionCaptor;

	private ObjectionService objectionService;

	@BeforeEach
	void setUp() {
		objectionService = new ObjectionService(objectionRepository);
	}

	@Test
	void createObjectionPersistsExpectedDocumentAndReturnsResponse() {
		CreateObjectionRequest request = new CreateObjectionRequest();
		request.setSubmissionCompanyName("Acme Limited");
		request.setPartnerCaseReference("CASE-123");
		request.setPartnerObjectionWorkstream(
				PartnerObjectionWorkstream.DEBT_MANAGEMENT);
		request.setPartnerObjectionReason(PartnerObjectionReason.OTHER);
		request.setPartnerContactEmail("test@example.com");

		when(objectionRepository.save(any(ObjectionDocument.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		BaseObjectionResponse response = objectionService.createObjection("01234567", request);

		verify(objectionRepository).save(objectionCaptor.capture());
		ObjectionDocument saved = objectionCaptor.getValue();

		assertEquals("01234567", saved.getCompanyNumber());
		assertEquals("Acme Limited", saved.getSubmissionCompanyName());
		assertEquals("CASE-123", saved.getPartnerCaseReference());
		assertEquals("debt-management", saved.getPartnerObjectionWorkstream());
		assertEquals("other", saved.getPartnerObjectionReason());
		assertEquals("test@example.com", saved.getPartnerContactEmail());
		assertNotNull(saved.getObjectionId());
		assertEquals("objection-submitted", saved.getProcessingStatus());
		assertNotNull(saved.getCreatedAt());
		assertEquals(ZoneOffset.UTC, saved.getCreatedAt().getOffset());
		assertNotNull(saved.getEtag());
		assertEquals("strike-off-partner-objection#objection", saved.getKind());
		assertNotNull(saved.getLinks());
		assertEquals(
				String.format("/company/01234567/strike-off-partner-objections/%s",
						saved.getObjectionId()),
				saved.getLinks().getSelf());
		assertEquals("/company/01234567", saved.getLinks().getCompanyProfile());

		assertEquals(saved.getObjectionId(), response.getObjectionId());
		assertEquals(ObjectionProcessingStatus.OBJECTION_SUBMITTED,
				response.getProcessingStatus());
		assertEquals(saved.getEtag(), response.getEtag());
	}

	@Test
	void createObjectionThrowsWhenPersistenceFails() {
		when(objectionRepository.save(any(ObjectionDocument.class)))
				.thenThrow(new RuntimeException("mongo down"));

		CreateObjectionRequest request = new CreateObjectionRequest();
		request.setSubmissionCompanyName("Acme Limited");
		request.setPartnerCaseReference("CASE-123");
		request.setPartnerObjectionWorkstream(
				PartnerObjectionWorkstream.DEBT_MANAGEMENT);
		request.setPartnerObjectionReason(PartnerObjectionReason.OTHER);
		request.setPartnerContactEmail("test@example.com");

		assertThrows(ObjectionPersistenceException.class,
				() -> objectionService.createObjection("01234567", request));
	}
}

