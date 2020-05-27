/*
 * Copyright (c) 2019 LabKey Corporation
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
package org.labkey.api.assay.actions;

import org.labkey.api.action.SpringActionController;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.study.assay.ParticipantVisitResolverType;
import org.labkey.api.assay.plate.PlateBasedAssayProvider;
import org.labkey.api.assay.plate.PlateSamplePropertyHelper;
import org.labkey.api.view.InsertView;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.validation.ObjectError;

import javax.servlet.ServletException;
import java.sql.SQLException;
import java.util.Map;

/**
 * User: klum
 * Date: 10/9/12
 */

@RequiresPermission(InsertPermission.class)
public abstract class PlateBasedUploadWizardAction <FormType extends PlateUploadFormImpl<ProviderType>, ProviderType extends PlateBasedAssayProvider> extends UploadWizardAction<FormType, ProviderType>
{
    @Override
    protected InsertView createRunInsertView(FormType form, boolean errorReshow, BindException errors) throws ExperimentException
    {
        ProviderType provider = form.getProvider();
        InsertView parent = super.createRunInsertView(form, errorReshow, errors);
        ParticipantVisitResolverType resolverType = getSelectedParticipantVisitResolverType(provider, form);
        PlateSamplePropertyHelper helper = provider.getSamplePropertyHelper(form, resolverType);
        try
        {
            helper.addSampleColumns(parent, form.getUser(), form, errorReshow);
        }
        catch (ExperimentException e)
        {
            errors.addError(new ObjectError("main", null, null, e.toString()));
        }
        return parent;
    }

    @Override
    protected RunStepHandler getRunStepHandler()
    {
        return new PlateBasedRunStepHandler();
    }

    protected class PlateBasedRunStepHandler extends RunStepHandler
    {
        protected Map<String, Map<DomainProperty, String>> _postedSampleProperties = null;

        @Override
        public void validateStep(FormType form, Errors errors)
        {
            super.validateStep(form, errors);
            try
            {
                ProviderType provider = form.getProvider();
                PlateSamplePropertyHelper helper = provider.getSamplePropertyHelper(form, getSelectedParticipantVisitResolverType(provider, form));

                _postedSampleProperties = helper.getPostedPropertyValues(form.getRequest());
                for (Map.Entry<String, Map<DomainProperty, String>> entry : _postedSampleProperties.entrySet())
                {
                    // if samplePropsValid flips to false, we want to leave it false (via the "&&" below).  We don't
                    // short-circuit the loop because we want to run through all samples every time, so all errors can be reported.
                    validatePostedProperties(getViewContext(), entry.getValue(), errors);
                }
            }
            catch (ExperimentException e)
            {
                errors.reject(SpringActionController.ERROR_MSG, e.getMessage());
            }
        }

        @Override
        public boolean executeStep(FormType form, BindException errors) throws ServletException, SQLException, ExperimentException
        {
            form.setSampleProperties(_postedSampleProperties);
            for (Map.Entry<String, Map<DomainProperty, String>> entry : _postedSampleProperties.entrySet())
            {
                try
                {
                    form.saveDefaultValues(entry.getValue(), entry.getKey());
                }
                catch (org.labkey.api.exp.ExperimentException e)
                {
                    errors.addError(new ObjectError("main", null, null, e.toString()));
                }
            }
            return super.executeStep(form, errors);
        }
    }
}
