/*
 * Copyright (c) 2009-2016 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.defaults;

import org.labkey.api.action.FormViewAction;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NotFoundException;
import org.springframework.validation.Errors;

public abstract class DefaultValuesAction<FormType extends DomainIdForm> extends FormViewAction<FormType>
{
    public DefaultValuesAction()
    {
        this(DomainIdForm.class);
    }

    public DefaultValuesAction(Class formClass)
    {
        super(formClass);
    }

    protected Domain getDomain(FormType domainIdForm)
    {
        Domain domain = PropertyService.get().getDomain(domainIdForm.getDomainId());
        if (domain == null)
        {
            throw new NotFoundException();
        }
        return domain;
    }

    @Override
    public void validateCommand(FormType target, Errors errors)
    {
    }

    @Override
    public ActionURL getSuccessURL(FormType domainIdForm)
    {
        return domainIdForm.getReturnActionURL();
    }
}