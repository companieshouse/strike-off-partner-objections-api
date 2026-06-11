package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import uk.gov.companieshouse.api.objections.model.BaseObjectionResponse;
import uk.gov.companieshouse.api.objections.model.CreateObjectionRequest;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.ObjectionPersistenceException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.mapper.ObjectionRequestMapper;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.ObjectionDocument;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.repository.ObjectionRepository;

@Tag("unit-test")
@ExtendWith(MockitoExtension.class)
class StrikeOffObjectionPartnerServiceTest {

    @Mock
    private ObjectionRepository objectionRepository;

    @Mock
    private ObjectionRequestMapper objectionRequestMapper;

    private StrikeOffObjectionPartnerService strikeOffObjectionPartnerService;

    @BeforeEach
    void setUp() {
        strikeOffObjectionPartnerService = new StrikeOffObjectionPartnerService(
                objectionRepository,
                objectionRequestMapper
        );
    }
    @Test
    void createObjectionSuccessReturnsMappedResponse() {
        CreateObjectionRequest requestDto = new CreateObjectionRequest();
        ObjectionDocument mappedDocument = new ObjectionDocument();
        ObjectionDocument savedDocument = new ObjectionDocument();
        String companyNumber = "12345";

        when(objectionRequestMapper.toObjectionDocument(
                eq(requestDto), eq(companyNumber), anyString(), anyString(), anyString()))
                .thenReturn(mappedDocument);
        when(objectionRepository.insert(mappedDocument)).thenReturn(savedDocument);

        BaseObjectionResponse result = strikeOffObjectionPartnerService.createObjection(companyNumber, requestDto);

        assertThat(result).isNotNull();
        verify(objectionRequestMapper).toObjectionDocument(
                eq(requestDto), eq(companyNumber), anyString(), anyString(), anyString());
        verify(objectionRepository).insert(mappedDocument);
    }

    @Test
    void createObjectionWhenRepositoryInsertFailsThrowsException() {
        CreateObjectionRequest requestDto = new CreateObjectionRequest();
        String companyNumber = "12345";
        ObjectionDocument mappedDocument = new ObjectionDocument();
        DataAccessResourceFailureException cause =
                new DataAccessResourceFailureException("mongo insert failed");


        when(objectionRequestMapper.toObjectionDocument(
                eq(requestDto), eq(companyNumber), anyString(), anyString(), anyString()))
                .thenReturn(mappedDocument);
        when(objectionRepository.insert(mappedDocument)).thenThrow(cause);

        assertThatThrownBy(() -> strikeOffObjectionPartnerService.createObjection(companyNumber, requestDto))
                .isInstanceOf(ObjectionPersistenceException.class)
                .hasMessage("Failed to persist objection")
                .hasCause(cause);

        verify(objectionRequestMapper).toObjectionDocument(
                eq(requestDto), eq(companyNumber), anyString(), anyString(), anyString());
        verify(objectionRepository).insert(mappedDocument);
    }
}