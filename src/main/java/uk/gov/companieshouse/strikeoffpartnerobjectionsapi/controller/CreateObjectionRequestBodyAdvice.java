package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.controller;

import static org.apache.commons.lang3.StringUtils.trim;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import java.util.Set;
import java.util.function.Consumer;
import uk.gov.companieshouse.api.objections.model.CreateObjectionRequest;
import uk.gov.companieshouse.api.objections.model.WithdrawAllObjectionsRequest;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.CompanyValidationException;

@NullMarked
@ControllerAdvice
public class CreateObjectionRequestBodyAdvice extends RequestBodyAdviceAdapter {

    private static final String INVALID_WORKSTREAM = "INVALID_WORKSTREAM";
    private static final int MAX_WORKSTREAM_LENGTH = 100;
    private static final Set<String> VALID_WORKSTREAMS = Set.of(
            "individuals-and-small-business-compliance",
            "wealthy-and-mid-sized-business-compliance",
            "debt-management"
    );

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
        validateWorkstream(request.getPartnerObjectionWorkstream());
    }

    private void trimWithdrawalRequestFields(WithdrawAllObjectionsRequest request) {
        trimRequestFields(request.getPartnerContactEmail(),
                request.getPartnerCaseReference(),
                request.getSubmissionCompanyName(),
                request::setPartnerContactEmail,
                request::setPartnerCaseReference,
                request::setSubmissionCompanyName);
        validateWorkstream(request.getPartnerObjectionWorkstream());
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

    private void validateWorkstream(@Nullable String workstream) {
        if (workstream == null) {
            return;
        }
        if (workstream.isBlank() || workstream.length() > MAX_WORKSTREAM_LENGTH || !VALID_WORKSTREAMS.contains(workstream)) {
            throw new CompanyValidationException(
                    "Invalid partner_objection_workstream: " + workstream, INVALID_WORKSTREAM);
        }
    }

}
