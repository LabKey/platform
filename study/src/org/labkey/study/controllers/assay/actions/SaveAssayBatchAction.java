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

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiVersion;
import org.labkey.api.action.SimpleApiJsonForm;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.api.*;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.qc.DataValidator;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.ACL;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.study.assay.AbstractAssayProvider;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.view.ViewContext;
import org.labkey.study.assay.ModuleRunUploadContext;
import org.labkey.study.assay.TsvDataHandler;
import org.labkey.study.assay.ModuleAssayProvider;
import org.springframework.validation.BindException;

import java.sql.SQLException;
import java.util.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.io.File;

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
        if (runJsonObject.has(ExperimentJSONConverter.COMMENT))
        {
            run.setComments(runJsonObject.getString(ExperimentJSONConverter.COMMENT));
        }

        handleStandardProperties(runJsonObject, run, provider.getRunDomain(protocol).getProperties());

        if (runJsonObject.has(DATA_ROWS) ||
                runJsonObject.has(ExperimentJSONConverter.DATA_INPUTS) ||
                runJsonObject.has(ExperimentJSONConverter.MATERIAL_INPUTS))
        {
            JSONArray dataRows;
            JSONArray dataInputs;
            JSONArray materialInputs;
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

            if (!runJsonObject.has(ExperimentJSONConverter.MATERIAL_INPUTS))
            {
                materialInputs = serializeRun(run, provider, protocol).getJSONArray(ExperimentJSONConverter.MATERIAL_INPUTS);
            }
            else
            {
                materialInputs = runJsonObject.getJSONArray(ExperimentJSONConverter.MATERIAL_INPUTS);
            }

            rewriteProtocolApplications(protocol, provider, run, dataInputs, dataRows, materialInputs, runJsonObject, pipeRoot);
        }

        return run;
    }

    private void rewriteProtocolApplications(ExpProtocol protocol, AssayProvider provider, ExpRun run, JSONArray inputDataArray, JSONArray dataArray, JSONArray inputMaterialArray, JSONObject runJsonObject, PipeRoot pipelineRoot) throws ExperimentException, ValidationException
    {
        ViewContext context = getViewContext();

        // First, clear out any old data analysis results
        for (ExpData data : run.getOutputDatas(provider.getDataType()))
        {
            data.delete(context.getUser());
        }

        Map<ExpData, String> inputData = new HashMap<ExpData, String>();
        for (int i = 0; i < inputDataArray.length(); i++)
        {
            JSONObject dataObject = inputDataArray.getJSONObject(i);
            inputData.put(handleData(dataObject, pipelineRoot), "Data");
        }

        Map<ExpMaterial, String> inputMaterial = new HashMap<ExpMaterial, String>();
        for (int i=0; i < inputMaterialArray.length(); i++)
        {
            JSONObject materialObject = inputMaterialArray.getJSONObject(i);
            ExpMaterial material = handleMaterial(materialObject);
            if (material != null)
                inputMaterial.put(material, null);
        }

        // Delete the contents of the run
        run.deleteProtocolApplications(context.getUser());

        // Recreate the run
        ExpData newData = AbstractAssayProvider.createData(run.getContainer(), null, "Analysis Results", provider.getDataType());
        newData.save(getViewContext().getUser());

        List<Map<String, Object>> rawData = dataArray.toMapList();

        run = ExperimentService.get().insertSimpleExperimentRun(run,
            inputMaterial,
            inputData,
            Collections.<ExpMaterial, String>emptyMap(),
            Collections.singletonMap(newData, "Data"),
            Collections.<ExpData, String>emptyMap(),                
            new ViewBackgroundInfo(context.getContainer(),
                    context.getUser(), context.getActionURL()), LOG, false);

        // programmatic qc validation
        DataValidator dataValidator = provider.getDataValidator();
        if (dataValidator != null)
            dataValidator.validate(new ModuleRunUploadContext(getViewContext(), protocol.getRowId(), runJsonObject, rawData), run);

        TsvDataHandler dataHandler = new TsvDataHandler();
        dataHandler.setAllowEmptyData(true);
        dataHandler.importRows(newData, getViewContext().getUser(), run, protocol, provider, rawData);
    }

    private ExpData handleData(JSONObject dataObject, PipeRoot pipelineRoot) throws ValidationException
    {
        ExpData data;
        if (dataObject.has(ExperimentJSONConverter.ID))
        {
            int dataId = dataObject.getInt(ExperimentJSONConverter.ID);
            data = ExperimentService.get().getExpData(dataId);

            if (data == null)
            {
                throw new NotFoundException("Could not find data with id " + dataId);
            }
            if (!data.getContainer().equals(getViewContext().getContainer()))
            {
                throw new NotFoundException("Data with row id " + dataId + " is not in folder " + getViewContext().getContainer());
            }
        }
        else
        {
            //create a new data object if there's a pipeline path property
            if (dataObject.has(ExperimentJSONConverter.PIPELINE_PATH))
            {
                String pipelinePath = dataObject.getString(ExperimentJSONConverter.PIPELINE_PATH);
                URI uri;
                try
                {
                    File file = new File(pipelineRoot.getRootPath(), pipelinePath);
                    uri = new URI("file://" + file.getAbsolutePath());
                }
                catch (URISyntaxException e)
                {
                    throw new ValidationException(e.getMessage());

                }

                String name = dataObject.optString(ExperimentJSONConverter.NAME, pipelinePath);
                data = ExperimentService.get().createData(getViewContext().getContainer(), ModuleAssayProvider.RAW_DATA_TYPE, name);
                data.setDataFileURI(uri);
                data.save(getViewContext().getUser());
            }
            else
                throw new IllegalArgumentException("Data input must have an id proeprty or a pipelinePath property.");
        }

        saveProperties(data, new DomainProperty[0], dataObject);
        return data;
    }

    private ExpMaterial handleMaterial(JSONObject materialObject) throws ValidationException
    {
        if (materialObject.has(ExperimentJSONConverter.ROW_ID))
        {
            // Unlike with runs and batches, we require that the materials are already created
            int materialRowId = materialObject.getInt(ExperimentJSONConverter.ROW_ID);
            ExpMaterial material = ExperimentService.get().getExpMaterial(materialRowId);
            if (material == null)
            {
                throw new NotFoundException("Could not find material with row id: " + materialRowId);
            }
            if (!material.getContainer().equals(getViewContext().getContainer()))
            {
                throw new NotFoundException("Material with row id " + materialRowId + " is not in folder " + getViewContext().getContainer());
            }
            saveProperties(material, new DomainProperty[0], materialObject);
            return material;
        }
        return null;
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
                    batchJsonObject.has(ExperimentJSONConverter.NAME) ? batchJsonObject.getString(ExperimentJSONConverter.NAME) : null, protocol);
        }

        if (batchJsonObject.has(ExperimentJSONConverter.COMMENT))
        {
            batch.setComments(batchJsonObject.getString(ExperimentJSONConverter.COMMENT));
        }
        handleStandardProperties(batchJsonObject, batch, provider.getBatchDomain(protocol).getProperties());

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
        for (Map.Entry<DomainProperty, Object> entry : ExperimentJSONConverter.convertProperties(propertiesJsonObject, dps).entrySet())
        {
            object.setProperty(getViewContext().getUser(), entry.getKey().getPropertyDescriptor(), entry.getValue());
        }
    }
}

