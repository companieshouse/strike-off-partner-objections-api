package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.mock.http.MockHttpInputMessage;
import uk.gov.companieshouse.api.objections.model.CreateObjectionRequest;
import uk.gov.companieshouse.api.objections.model.WithdrawAllObjectionsRequest;

@Tag("unit-test")
class CreateObjectionRequestBodyAdviceTest {

    private final CreateObjectionRequestBodyAdvice advice = new CreateObjectionRequestBodyAdvice();

    @Test
    void supports_whenParameterTypeIsCreateObjectionRequest_returnsTrue() {
        MethodParameter parameter = mock(MethodParameter.class);
        doReturn(CreateObjectionRequest.class).when(parameter).getParameterType();

        assertTrue(advice.supports(
                parameter,
                CreateObjectionRequest.class,
                StringHttpMessageConverter.class));
    }

    @Test
    void supports_whenParameterTypeIsWithdrawAllObjectionsRequest_returnsTrue() {
        MethodParameter parameter = mock(MethodParameter.class);
        doReturn(WithdrawAllObjectionsRequest.class).when(parameter).getParameterType();

        assertTrue(advice.supports(
                parameter,
                WithdrawAllObjectionsRequest.class,
                StringHttpMessageConverter.class));
    }

    @Test
    void supports_whenParameterTypeIsOtherType_returnsFalse() {
        MethodParameter parameter = mock(MethodParameter.class);
        doReturn(String.class).when(parameter).getParameterType();

        assertFalse(advice.supports(
                parameter,
                String.class,
                StringHttpMessageConverter.class));
    }

    @Test
    void afterBodyRead_whenBodyIsCreateObjectionRequest_trimsSupportedFields() {
        CreateObjectionRequest request = new CreateObjectionRequest();
        request.setPartnerContactEmail(" owner@example.com ");
        request.setPartnerCaseReference(" CASE-123 ");
        request.setSubmissionCompanyName(" ACME LTD ");

        Object result = advice.afterBodyRead(
                request,
                new MockHttpInputMessage(new byte[0]),
                mock(MethodParameter.class),
                CreateObjectionRequest.class,
                StringHttpMessageConverter.class);

        assertSame(request, result);
        assertEquals("owner@example.com", request.getPartnerContactEmail());
        assertEquals("CASE-123", request.getPartnerCaseReference());
        assertEquals("ACME LTD", request.getSubmissionCompanyName());
    }

    @Test
    void afterBodyRead_whenCreateObjectionRequestContainsWhitespaceOnlyValues_trimsToEmptyString() {
        CreateObjectionRequest request = new CreateObjectionRequest();
        request.setPartnerContactEmail("   ");
        request.setPartnerCaseReference("\t");
        request.setSubmissionCompanyName("\n");

        advice.afterBodyRead(
                request,
                new MockHttpInputMessage(new byte[0]),
                mock(MethodParameter.class),
                CreateObjectionRequest.class,
                StringHttpMessageConverter.class);

        assertEquals("", request.getPartnerContactEmail());
        assertEquals("", request.getPartnerCaseReference());
        assertEquals("", request.getSubmissionCompanyName());
    }

    @Test
    void afterBodyRead_whenCreateObjectionRequestContainsNullValues_leavesNullValuesAsNull() {
        CreateObjectionRequest request = new CreateObjectionRequest();

        advice.afterBodyRead(
                request,
                new MockHttpInputMessage(new byte[0]),
                mock(MethodParameter.class),
                CreateObjectionRequest.class,
                StringHttpMessageConverter.class);

        assertNull(request.getPartnerContactEmail());
        assertNull(request.getPartnerCaseReference());
        assertNull(request.getSubmissionCompanyName());
    }

    @Test
    void afterBodyRead_whenBodyIsWithdrawAllObjectionsRequest_trimsSupportedFields() {
        WithdrawAllObjectionsRequest request = new WithdrawAllObjectionsRequest();
        request.setPartnerContactEmail(" owner@example.com ");
        request.setPartnerCaseReference(" CASE-123 ");
        request.setSubmissionCompanyName(" ACME LTD ");

        Object result = advice.afterBodyRead(
                request,
                new MockHttpInputMessage(new byte[0]),
                mock(MethodParameter.class),
                WithdrawAllObjectionsRequest.class,
                StringHttpMessageConverter.class);

        assertSame(request, result);
        assertEquals("owner@example.com", request.getPartnerContactEmail());
        assertEquals("CASE-123", request.getPartnerCaseReference());
        assertEquals("ACME LTD", request.getSubmissionCompanyName());
    }

    @Test
    void afterBodyRead_whenWithdrawAllObjectionsRequestContainsNullValues_leavesNullValuesAsNull() {
        WithdrawAllObjectionsRequest request = new WithdrawAllObjectionsRequest();

        advice.afterBodyRead(
                request,
                new MockHttpInputMessage(new byte[0]),
                mock(MethodParameter.class),
                WithdrawAllObjectionsRequest.class,
                StringHttpMessageConverter.class);

        assertNull(request.getPartnerContactEmail());
        assertNull(request.getPartnerCaseReference());
        assertNull(request.getSubmissionCompanyName());
    }

    @Test
    void afterBodyRead_whenBodyIsNonCreateRequest_returnsUnchanged() {
        String body = "raw-body";

        Object result = advice.afterBodyRead(
                body,
                new MockHttpInputMessage(new byte[0]),
                mock(MethodParameter.class),
                String.class,
                StringHttpMessageConverter.class);

        assertSame(body, result);
    }
}





