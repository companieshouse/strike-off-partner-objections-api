package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.controller;

import static org.apache.commons.lang3.StringUtils.trim;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import java.util.function.Consumer;
import uk.gov.companieshouse.api.objections.model.CreateObjectionRequest;
import uk.gov.companieshouse.api.objections.model.WithdrawAllObjectionsRequest;

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

    private void trimObjectionRequestFields(CreateObjectionRequest request) {
        trimRequestFields(request.getPartnerContactEmail(),
                request.getPartnerCaseReference(),
                request.getSubmissionCompanyName(),
                request::setPartnerContactEmail,
                request::setPartnerCaseReference,
                request::setSubmissionCompanyName);
    }

    private void trimWithdrawalRequestFields(WithdrawAllObjectionsRequest request) {
        trimRequestFields(request.getPartnerContactEmail(),
                request.getPartnerCaseReference(),
                request.getSubmissionCompanyName(),
                request::setPartnerContactEmail,
                request::setPartnerCaseReference,
                request::setSubmissionCompanyName);
    }

    private void trimRequestFields(String partnerContactEmail,
                                   String partnerCaseReference,
                                   String submissionCompanyName,
                                   Consumer<String> emailSetter,
                                   Consumer<String> caseReferenceSetter,
                                   Consumer<String> companyNameSetter) {
        emailSetter.accept(trim(partnerContactEmail));
        caseReferenceSetter.accept(trim(partnerCaseReference));
        companyNameSetter.accept(trim(submissionCompanyName));
    }

}
