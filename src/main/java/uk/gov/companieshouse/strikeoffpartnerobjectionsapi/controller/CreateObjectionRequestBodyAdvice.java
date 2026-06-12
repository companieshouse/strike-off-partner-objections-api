package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.controller;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import uk.gov.companieshouse.api.objections.model.CreateObjectionRequest;

@ControllerAdvice
public class CreateObjectionRequestBodyAdvice extends RequestBodyAdviceAdapter {

    @Override
    public boolean supports(MethodParameter methodParameter,
                            java.lang.reflect.Type targetType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return CreateObjectionRequest.class.isAssignableFrom(methodParameter.getParameterType());
    }

    @Override
    public Object afterBodyRead(Object body,
                                HttpInputMessage inputMessage,
                                MethodParameter parameter,
                                java.lang.reflect.Type targetType,
                                Class<? extends HttpMessageConverter<?>> converterType) {
        if (body instanceof CreateObjectionRequest request) {
            request.setPartnerContactEmail(trimToNullSafe(request.getPartnerContactEmail()));
            request.setPartnerCaseReference(trimToNullSafe(request.getPartnerCaseReference()));
            request.setSubmissionCompanyName(trimToNullSafe(request.getSubmissionCompanyName()));
        }
        return body;
    }

    private String trimToNullSafe(String value) {
        return value == null ? null : value.trim();
    }
}


