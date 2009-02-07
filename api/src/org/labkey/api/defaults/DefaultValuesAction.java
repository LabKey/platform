package org.labkey.api.defaults;

import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.view.ViewForm;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ActionURL;
import org.labkey.api.action.FormViewAction;
import org.springframework.validation.Errors;

public abstract class DefaultValuesAction extends FormViewAction<DefaultValuesAction.DomainIdForm>
{
    protected Domain getDomain(DomainIdForm domainIdForm)
    {
        Domain domain = PropertyService.get().getDomain(domainIdForm.getDomainId());
        if (domain == null)
            HttpView.throwNotFound();
        return domain;
    }

    public static class DomainIdForm extends ViewForm
    {
        private int _domainId;
        private String _returnUrl;

        public int getDomainId()
        {
            return _domainId;
        }

        public void setDomainId(int domainId)
        {
            _domainId = domainId;
        }

        public String getReturnUrl()
        {
            return _returnUrl;
        }

        public void setReturnUrl(String returnUrl)
        {
            _returnUrl = returnUrl;
        }
    }

    public void validateCommand(DomainIdForm target, Errors errors)
    {
    }

    public ActionURL getSuccessURL(DomainIdForm domainIdForm)
    {
        return new ActionURL(domainIdForm.getReturnUrl());
    }
}