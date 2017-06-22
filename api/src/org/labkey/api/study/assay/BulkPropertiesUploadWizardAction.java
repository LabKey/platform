/*
 * Copyright (c) 2009-2017 LabKey Corporation
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
package org.labkey.api.study.assay;

import org.labkey.api.action.NullSafeBindException;
import org.labkey.api.data.DbScope;
import org.labkey.api.study.actions.UploadWizardAction;
import org.labkey.api.study.actions.BulkPropertiesUploadForm;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.action.LabKeyError;
import org.labkey.api.query.ValidationException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.validation.BindException;

import javax.servlet.ServletException;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Collections;
import java.io.File;

/**
 * User: jeckels
 * Date: May 31, 2009
 */
public class BulkPropertiesUploadWizardAction<FormType extends BulkPropertiesUploadForm<ProviderType>, ProviderType extends AssayProvider>
        extends UploadWizardAction<FormType, ProviderType>
{
    public BulkPropertiesUploadWizardAction(Class<? extends FormType> formClass)
    {
        super(formClass);
    }

    @Override
    protected boolean showBatchStep(FormType runForm, Domain uploadDomain) throws ServletException
    {
        return true;
    }

    @Override
    protected StepHandler<FormType> getBatchStepHandler()
    {
        return new BulkPropertiesBatchStepHandler();
    }

    private class BulkPropertiesBatchStepHandler extends BatchStepHandler
    {
        @Override
        public ModelAndView handleStep(FormType form, BindException errors) throws ServletException, ExperimentException
        {
            if (form.isBulkUploadAttempted())
            {
                BindException batchErrors = new NullSafeBindException(form, "form");
                // Collect the errors in a separate list because if otherwise we fail, the superclass will add them a
                // second time during its reshow logic
                try
                {
                    if (validatePostedProperties(getViewContext(), form.getBatchProperties(), batchErrors))
                    {
                        List<ExpRun> runs = insertRuns(form, errors);
                        if (batchErrors.getErrorCount() == 0 && errors.getErrorCount() == 0 && !runs.isEmpty())
                        {
                            return afterRunCreation(form, runs.get(0), errors);
                        }
                    }
                }
                catch (ExperimentException e)
                {
                    errors.addError(new LabKeyError(e));
                }
            }

            return super.handleStep(form, errors);
        }

        private List<ExpRun> insertRuns(FormType form, BindException errors)
        {
            try
            {
                PipelineDataCollector<BulkPropertiesUploadForm<ProviderType>> collector = form.getSelectedDataCollector();
                RunStepHandler handler = getRunStepHandler();
                List<ExpRun> runs = new ArrayList<>();

                // Hold on to a copy of the original file list so that we can reset the selection state if one of them fails
                List<Map<String, File>> allFiles =
                        new ArrayList<>(collector.getFileQueue(form));
                boolean success = false;
                try (DbScope.Transaction transaction = ExperimentService.get().ensureTransaction())
                {
                    AssayDataCollector.AdditionalUploadType additionalStatus;
                    do
                    {
                        additionalStatus = collector.getAdditionalUploadType(form);
                        form.getUploadedData();
                        form.getBulkProperties();
                        validatePostedProperties(getViewContext(), form.getRunProperties(), errors);
                        if (errors.getErrorCount() > 0)
                        {
                            // Intentionally don't commit - we hit some errors
                            return Collections.emptyList();
                        }
                        runs.add(handler.saveExperimentRun(form));
                        form.clearUploadedData();
                    }
                    while (additionalStatus == AssayDataCollector.AdditionalUploadType.AlreadyUploaded);
                    success = true;
                    transaction.commit();
                    return runs;
                }
                finally
                {
                    if (!success)
                    {
                        // Something went wrong, restore the full list of files
                        PipelineDataCollector.setFileCollection(getViewContext().getRequest().getSession(true), getContainer(), form.getProtocol(), allFiles);
                        collector.uploadFailed(form, allFiles);
                    }
                }
            }
            catch (ExperimentException e)
            {
                errors.addError(new LabKeyError(e));
            }
            catch (ValidationException e)
            {
                errors.addError(new LabKeyError(e));
            }
            return Collections.emptyList();
        }
    }

}
