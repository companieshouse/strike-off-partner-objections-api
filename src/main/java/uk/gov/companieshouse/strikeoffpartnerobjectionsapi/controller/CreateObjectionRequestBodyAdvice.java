package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.controller;

import org.jspecify.annotations.NullMarked;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import java.util.function.Consumer;
import uk.gov.companieshouse.api.objections.model.CreateObjectionRequest;
import uk.gov.companieshouse.api.objections.model.WithdrawAllObjectionsRequest;

@NullMarked
@ControllerAdvice
public class CreateObjectionRequestBodyAdvice extends RequestBodyAdviceAdapter {

    @Override
    public boolean supports(MethodParameter methodParameter,
                            java.lang.reflect.Type targetType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        Class<?> parameterType = methodParameter.getParameterType();
        return CreateObjectionRequest.class.isAssignableFrom(parameterType)
                || WithdrawAllObjectionsRequest.class.isAssignableFrom(parameterType);
    }

    @Override
    public Object afterBodyRead(Object body,
                                HttpInputMessage inputMessage,
                                MethodParameter parameter,
                                java.lang.reflect.Type targetType,
                                Class<? extends HttpMessageConverter<?>> converterType) {
        if (body instanceof CreateObjectionRequest request) {
            trimObjectionRequestFields(request);
        } else if (body instanceof WithdrawAllObjectionsRequest request) {
            trimWithdrawalRequestFields(request);
        }
        return body;
    }

    private static void trimObjectionRequestFields(CreateObjectionRequest request) {
        trimRequestField(request.getPartnerContactEmail(), request::setPartnerContactEmail);
        trimRequestField(request.getPartnerCaseReference(), request::setPartnerCaseReference);
        trimRequestField(request.getSubmissionCompanyName(), request::setSubmissionCompanyName);
    }

    private static void trimWithdrawalRequestFields(WithdrawAllObjectionsRequest request) {
        trimRequestField(request.getPartnerContactEmail(), request::setPartnerContactEmail);
        trimRequestField(request.getPartnerCaseReference(), request::setPartnerCaseReference);
        trimRequestField(request.getSubmissionCompanyName(), request::setSubmissionCompanyName);
    }

    private static void trimRequestField(String value, Consumer<String> setter) {
        setter.accept(StringUtils.trim(value));
    }

}
