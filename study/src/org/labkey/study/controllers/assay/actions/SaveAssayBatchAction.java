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
import org.labkey.api.study.assay.AbstractAssayProvider;
import org.labkey.api.exp.api.*;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.view.ViewContext;
import org.labkey.api.query.ValidationException;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.study.assay.TsvDataHandler;
import org.springframework.validation.BindException;
import org.json.JSONObject;
import org.json.JSONException;
import org.json.JSONArray;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.log4j.Logger;

import java.util.*;
import java.sql.SQLException;

/**
 * User: jeckels
 * Date: Jan 14, 2009
 */
@RequiresPermission(ACL.PERM_INSERT)
@ApiVersion(9.1)
public class SaveAssayBatchAction extends AbstractAssayAPIAction<SimpleApiJsonForm>
{
    private static final Logger LOG = Logger.getLogger(SaveAssayBatchAction.class);

    public ApiResponse executeAction(ExpProtocol protocol, AssayProvider provider, SimpleApiJsonForm form, BindException errors) throws Exception
    {
        JSONObject rootJsonObject = form.getJsonObject();

        JSONObject batchJsonObject = rootJsonObject.getJSONObject(BATCH);
        if (batchJsonObject == null)
        {
            throw new IllegalArgumentException("No batch object found");
        }

        ExpExperiment batch;

        try
        {
            ExperimentService.get().beginTransaction();
            batch = handleBatch(batchJsonObject, protocol, provider);

            ExperimentService.get().commitTransaction();
        }
        finally
        {
            ExperimentService.get().closeTransaction();
        }

        return serializeResult(provider, protocol, batch);
    }

    private ExpRun handleRun(JSONObject runJsonObject, ExpProtocol protocol, AssayProvider provider, ExpExperiment batch) throws JSONException, ValidationException, ExperimentException, SQLException
    {
        String name = runJsonObject.has(ExperimentJSONConverter.NAME) ? runJsonObject.getString(ExperimentJSONConverter.NAME) : null;
        ExpRun run;
        if (runJsonObject.has(ExperimentJSONConverter.ID))
        {
            int runId = runJsonObject.getInt(ExperimentJSONConverter.ID);
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
        PipeRoot pipeRoot = PipelineService.get().findPipelineRoot(getViewContext().getContainer());
        if (pipeRoot == null)
        {
            throw new NotFoundException("Pipeline root is not configured for folder " + getViewContext().getContainer());
        }
        run.setFilePathRoot(pipeRoot.getRootPath());
        handleStandardProperties(runJsonObject, run, provider.getRunDataDomain(protocol).getProperties());

        if (runJsonObject.has(DATA_ROWS) || runJsonObject.has(ExperimentJSONConverter.DATA_INPUTS))
        {
            JSONArray dataRows;
            JSONArray dataInputs;
            if (!runJsonObject.has(DATA_ROWS))
            {
                // Client didn't post the rows so reuse the values that are currently attached to the run
                // Ineffecient but easy
                dataRows = serializeRun(run, provider, protocol).getJSONArray(DATA_ROWS);
            }
            else
            {
                dataRows = runJsonObject.getJSONArray(DATA_ROWS);
            }

            if (!runJsonObject.has(ExperimentJSONConverter.DATA_INPUTS))
            {
                // Client didn't post the inputs so reuse the values that are currently attached to the run
                // Ineffecient but easy
                dataInputs = serializeRun(run, provider, protocol).getJSONArray(ExperimentJSONConverter.DATA_INPUTS);
            }
            else
            {
                dataInputs = runJsonObject.getJSONArray(ExperimentJSONConverter.DATA_INPUTS);
            }

            rewriteProtocolApplications(protocol, provider, run, dataInputs, dataRows);
        }

        return run;
    }

    private void rewriteProtocolApplications(ExpProtocol protocol, AssayProvider provider, ExpRun run, JSONArray inputArray, JSONArray dataArray) throws ExperimentException, ValidationException
    {
        ViewContext context = getViewContext();

        // First, clear out any old data analysis results
        for (ExpData data : run.getOutputDatas(provider.getDataType()))
        {
            data.delete(context.getUser());
        }

        Map<ExpData, String> inputData = new HashMap<ExpData, String>();
        for (int i = 0; i < inputArray.length(); i++)
        {
            JSONObject dataObject = inputArray.getJSONObject(i);
            inputData.put(handleData(dataObject), "Data");
        }

        // Delete the contents of the run
        run.deleteProtocolApplications(context.getUser());

        // Recreate the run
        ExpData newData = AbstractAssayProvider.createData(run.getContainer(), null, "Analysis Results", provider.getDataType());
        newData.save(getViewContext().getUser());

        List<Map<String, Object>> rawData = dataArray.toMapList();

        run = ExperimentService.get().insertSimpleExperimentRun(run,
            Collections.<ExpMaterial, String>emptyMap(),
            inputData,
            Collections.<ExpMaterial, String>emptyMap(),
            Collections.singletonMap(newData, "Data"),
            new ViewBackgroundInfo(context.getContainer(),
                    context.getUser(), context.getActionURL()), LOG, false);

        TsvDataHandler dataHandler = new TsvDataHandler();
        dataHandler.importRows(newData, getViewContext().getUser(), run, protocol, provider, rawData);
    }

    private ExpData handleData(JSONObject dataObject) throws ValidationException
    {
        // Unlike with runs and batches, we require that the datas are already created
        int dataId = dataObject.getInt(ExperimentJSONConverter.ID);
        ExpData data = ExperimentService.get().getExpData(dataId);
        if (data == null)
        {
            throw new NotFoundException("Could not find data with id " + dataId);
        }
        if (!data.getContainer().equals(getViewContext().getContainer()))
        {
            throw new NotFoundException("Data with row id " + dataId + " is not in folder " + getViewContext().getContainer());
        }
        saveProperties(data, new DomainProperty[0], dataObject);
        return data;
    }

    private ExpExperiment handleBatch(JSONObject batchJsonObject, ExpProtocol protocol, AssayProvider provider) throws Exception
    {
        ExpExperiment batch;
        if (batchJsonObject.has(ExperimentJSONConverter.ID))
        {
            batch = lookupBatch(batchJsonObject.getInt(ExperimentJSONConverter.ID));
        }
        else
        {
            batch = AssayService.get().createStandardBatch(getViewContext().getContainer(),
                    batchJsonObject.has(ExperimentJSONConverter.NAME) ? batchJsonObject.getString(ExperimentJSONConverter.NAME) : null);
        }

        handleStandardProperties(batchJsonObject, batch, provider.getUploadSetDomain(protocol).getProperties());

        List<ExpRun> runs = new ArrayList<ExpRun>();
        if (batchJsonObject.has(RUNS))
        {
            JSONArray runsArray = batchJsonObject.getJSONArray(RUNS);
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

        return batch;
    }

    private void handleStandardProperties(JSONObject jsonObject, ExpObject object, DomainProperty[] dps) throws ValidationException, SQLException
    {
        if (jsonObject.has(ExperimentJSONConverter.NAME))
        {
            object.setName(jsonObject.getString(ExperimentJSONConverter.NAME));
        }

        object.save(getViewContext().getUser());
        OntologyManager.ensureObject(object.getContainer(), object.getLSID());

        if (jsonObject.has(ExperimentJSONConverter.PROPERTIES))
        {
            saveProperties(object, dps, jsonObject.getJSONObject(ExperimentJSONConverter.PROPERTIES));
        }
    }

    private void saveProperties(ExpObject object, DomainProperty[] dps, JSONObject propertiesJsonObject) throws ValidationException, JSONException
    {
        for (DomainProperty dp : dps)
        {
            if (propertiesJsonObject.has(dp.getName()))
            {
                Class javaType = dp.getPropertyDescriptor().getPropertyType().getJavaType();
                object.setProperty(getViewContext().getUser(), dp.getPropertyDescriptor(), ConvertUtils.lookup(javaType).convert(javaType, propertiesJsonObject.get(dp.getName())));
            }
            else
            {
                object.setProperty(getViewContext().getUser(), dp.getPropertyDescriptor(), null);
            }
        }
    }
}

