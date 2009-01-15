/*
 * Copyright (c) 2009 LabKey Corporation
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
package org.labkey.study.controllers.assay.actions;

import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.ACL;
import org.labkey.api.action.SimpleApiJsonForm;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiVersion;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.exp.api.*;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.query.ValidationException;
import org.springframework.validation.BindException;
import org.json.JSONObject;
import org.json.JSONException;
import org.json.JSONArray;
import org.apache.commons.beanutils.ConvertUtils;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * User: jeckels
 * Date: Jan 14, 2009
 */
@RequiresPermission(ACL.PERM_INSERT)
@ApiVersion(9.1)
public class SaveAssayBatchAction extends AbstractAssayAPIAction<SimpleApiJsonForm>
{
    public ApiResponse executeAction(ExpProtocol protocol, AssayProvider provider, SimpleApiJsonForm form, BindException errors) throws Exception
    {
        JSONObject rootJsonObject = form.getJsonObject();

        JSONObject batchJsonObject = rootJsonObject.getJSONObject(BATCH);
        if (batchJsonObject == null)
        {
            throw new IllegalArgumentException("No batch object found");
        }

        ExpExperiment batch;
        List<ExpRun> runs = new ArrayList<ExpRun>();

        try
        {
            ExperimentService.get().beginTransaction();
            batch = handleBatch(batchJsonObject, protocol, provider);

            if (rootJsonObject.has(RUNS))
            {
                JSONArray runsArray = rootJsonObject.getJSONArray(RUNS);
                for (int i = 0; i < runsArray.length(); i++)
                {
                    JSONObject runJsonObject = runsArray.getJSONObject(i);
                    ExpRun run = handleRun(runJsonObject, protocol, provider, batch);
                    runs.add(run);
                }
            }
            List<ExpRun> existingRuns = Arrays.asList(batch.getRuns());

            // Make sure that all the runs are considered part of the batch
            List<ExpRun> runsToAdd = new ArrayList<ExpRun>(runs);
            runsToAdd.removeAll(existingRuns);
            batch.addRuns(getViewContext().getUser(), runsToAdd.toArray(new ExpRun[runsToAdd.size()]));

            // Remove any runs that are no longer part of the batch
            List<ExpRun> runsToRemove = new ArrayList<ExpRun>(existingRuns);
            runsToRemove.removeAll(runs);
            for (ExpRun runToRemove : runsToRemove)
            {
                batch.removeRun(getViewContext().getUser(), runToRemove);
            }

            ExperimentService.get().commitTransaction();
        }
        finally
        {
            ExperimentService.get().closeTransaction();
        }

        return serializeResult(provider, protocol, batch);
    }

    private ExpRun handleRun(JSONObject runJsonObject, ExpProtocol protocol, AssayProvider provider, ExpExperiment batch) throws JSONException, ValidationException
    {
        String name = runJsonObject.has(NAME) ? runJsonObject.getString(NAME) : null;
        ExpRun run;
        if (runJsonObject.has(ID))
        {
            int runId = runJsonObject.getInt(ID);
            run = ExperimentService.get().getExpRun(runId);
            if (run == null)
            {
                throw new NotFoundException("Could not find assay run " + runId);
            }
            if (!batch.getContainer().equals(getViewContext().getContainer()))
            {
                throw new NotFoundException("Could not find assay run " + runId + " in folder " + getViewContext().getContainer());
            }
        }
        else
        {
            run = provider.createExperimentRun(name, getViewContext().getContainer(), protocol);
        }
        if (name != null)
        {
            run.setName(name);
        }
        run.save(getViewContext().getUser());
        PropertyDescriptor[] pds = provider.getUploadSetColumns(protocol);
        if (runJsonObject.has(PROPERTIES))
        {
            saveProperties(run, pds, runJsonObject.getJSONObject(PROPERTIES));
        }

        return run;
    }

    private ExpExperiment handleBatch(JSONObject batchJsonObject, ExpProtocol protocol, AssayProvider provider) throws JSONException, ValidationException
    {
        ExpExperiment batch;
        if (batchJsonObject.has(ID))
        {
            batch = lookupBatch(batchJsonObject.getInt(ID));
        }
        else
        {
            batch = AssayService.get().createStandardBatch(getViewContext().getContainer(),
                    batchJsonObject.has(NAME) ? batchJsonObject.getString(NAME) : null);
        }
        if (batchJsonObject.has(NAME))
        {
            batch.setName(batchJsonObject.getString(NAME));
        }
        batch.save(getViewContext().getUser());

        PropertyDescriptor[] pds = provider.getUploadSetColumns(protocol);
        if (batchJsonObject.has(PROPERTIES))
        {
            saveProperties(batch, pds, batchJsonObject.getJSONObject(PROPERTIES));
        }

        return batch;
    }

    private void saveProperties(ExpObject object, PropertyDescriptor[] pds, JSONObject propertiesJsonObject) throws ValidationException, JSONException
    {
        for (PropertyDescriptor pd : pds)
        {
            if (propertiesJsonObject.has(pd.getName()))
            {
                Class javaType = pd.getPropertyType().getJavaType();
                object.setProperty(getViewContext().getUser(), pd, ConvertUtils.lookup(javaType).convert(javaType, propertiesJsonObject.get(pd.getName())));
            }
            else
            {
                object.setProperty(getViewContext().getUser(), pd, null);
            }
        }
    }
}

