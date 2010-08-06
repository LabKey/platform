/*
 * Copyright (c) 2009-2010 LabKey Corporation
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
import org.labkey.api.exp.*;
import org.labkey.api.exp.api.*;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.xar.LsidUtils;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.qc.DataValidator;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.permissions.*;
import org.labkey.api.study.assay.AbstractAssayProvider;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.URIUtil;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.view.ViewContext;
import org.labkey.api.data.Container;
import org.labkey.study.assay.ModuleRunUploadContext;
import org.labkey.study.assay.TsvAssayProvider;
import org.labkey.study.assay.TsvDataHandler;
import org.springframework.validation.BindException;

import java.sql.SQLException;
import java.util.*;
import java.net.URI;
import java.io.File;

/**
 * User: jeckels
 * Date: Jan 14, 2009
 */
@RequiresPermissionClass(InsertPermission.class)
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

        if (!(provider instanceof TsvAssayProvider))
        {
            throw new IllegalArgumentException("SaveAssayBatch is not supported for assay provider: " + provider);
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
            JSONArray dataOutputs;
            JSONArray materialOutputs;

            JSONObject serializedRun = null;
            if (!runJsonObject.has(DATA_ROWS))
            {
                // Client didn't post the rows so reuse the values that are currently attached to the run
                // Inefficient but easy
                serializedRun = serializeRun(run, provider, protocol);
                dataRows = serializedRun.getJSONArray(DATA_ROWS);
            }
            else
            {
                dataRows = runJsonObject.getJSONArray(DATA_ROWS);
            }

            if (!runJsonObject.has(ExperimentJSONConverter.DATA_INPUTS))
            {
                if (serializedRun == null)
                    serializedRun = serializeRun(run, provider, protocol);
                dataInputs = serializedRun.getJSONArray(ExperimentJSONConverter.DATA_INPUTS);
            }
            else
            {
                dataInputs = runJsonObject.getJSONArray(ExperimentJSONConverter.DATA_INPUTS);
            }

            if (!runJsonObject.has(ExperimentJSONConverter.MATERIAL_INPUTS))
            {
                if (serializedRun == null)
                    serializedRun = serializeRun(run, provider, protocol);
                materialInputs = serializedRun.getJSONArray(ExperimentJSONConverter.MATERIAL_INPUTS);
            }
            else
            {
                materialInputs = runJsonObject.getJSONArray(ExperimentJSONConverter.MATERIAL_INPUTS);
            }

            if (!runJsonObject.has(ExperimentJSONConverter.DATA_OUTPUTS))
            {
                if (serializedRun == null)
                    serializedRun = serializeRun(run, provider, protocol);
                dataOutputs = serializedRun.getJSONArray(ExperimentJSONConverter.DATA_OUTPUTS);
            }
            else
            {
                dataOutputs = runJsonObject.getJSONArray(ExperimentJSONConverter.DATA_OUTPUTS);
            }

            if (!runJsonObject.has(ExperimentJSONConverter.MATERIAL_OUTPUTS))
            {
                if (serializedRun == null)
                    serializedRun = serializeRun(run, provider, protocol);
                materialOutputs = serializedRun.getJSONArray(ExperimentJSONConverter.MATERIAL_OUTPUTS);
            }
            else
            {
                materialOutputs = runJsonObject.getJSONArray(ExperimentJSONConverter.MATERIAL_OUTPUTS);
            }

            rewriteProtocolApplications(protocol, provider, run, dataInputs, dataRows, materialInputs, runJsonObject, pipeRoot, dataOutputs, materialOutputs);
        }

        return run;
    }

    private void rewriteProtocolApplications(ExpProtocol protocol, AssayProvider provider, ExpRun run,
                                             JSONArray inputDataArray, JSONArray dataArray,
                                             JSONArray inputMaterialArray, JSONObject runJsonObject, PipeRoot pipelineRoot,
                                             JSONArray outputDataArray, JSONArray outputMaterialArray)
            throws ExperimentException, ValidationException
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
            inputData.put(handleData(dataObject, pipelineRoot), dataObject.optString(ExperimentJSONConverter.ROLE, "Data"));
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
        Map<ExpData, String> outputData = new HashMap<ExpData, String>();
        ExpData newData = AbstractAssayProvider.createData(run.getContainer(), null, "Analysis Results", provider.getDataType());
        newData.save(getViewContext().getUser());
        outputData.put(newData, "Data");

        //other data outputs
        for (int i=0; i < outputDataArray.length(); i++)
        {
            JSONObject dataObject = outputDataArray.getJSONObject(i);
            outputData.put(handleData(dataObject, pipelineRoot), dataObject.optString(ExperimentJSONConverter.ROLE, "Data"));
        }

        Map<ExpMaterial, String> outputMaterial = new HashMap<ExpMaterial, String>();
        for (int i=0; i < outputMaterialArray.length(); i++)
        {
            JSONObject materialObject = outputMaterialArray.getJSONObject(i);
            ExpMaterial material = handleMaterial(materialObject);
            if (material != null)
                outputMaterial.put(material, materialObject.optString(ExperimentJSONConverter.ROLE, "Material"));
        }

        List<Map<String, Object>> rawData = dataArray.toMapList();

        run = ExperimentService.get().insertSimpleExperimentRun(run,
            inputMaterial,
            inputData,
            outputMaterial,
            outputData,
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
        ExperimentService.Interface expSvc = ExperimentService.get();
        Container container = getViewContext().getContainer();
        ExpData data;
        if (dataObject.has(ExperimentJSONConverter.ID))
        {
            int dataId = dataObject.getInt(ExperimentJSONConverter.ID);
            data = expSvc.getExpData(dataId);

            if (data == null)
            {
                throw new NotFoundException("Could not find data with id " + dataId);
            }
            if (!data.getContainer().equals(getViewContext().getContainer()))
            {
                throw new NotFoundException("Data with row id " + dataId + " is not in folder " + getViewContext().getContainer());
            }
        }
        else if (dataObject.has(ExperimentJSONConverter.PIPELINE_PATH) && dataObject.getString(ExperimentJSONConverter.PIPELINE_PATH) != null)
        {
            String pipelinePath = dataObject.getString(ExperimentJSONConverter.PIPELINE_PATH);

            //check to see if this is already an ExpData
            File file = pipelineRoot.resolvePath(pipelinePath);
            URI uri = file.toURI();
            data = expSvc.getExpDataByURL(uri.toString(), container);

            if (null == data)
            {
                if (!NetworkDrive.exists(file))
                {
                    throw new IllegalArgumentException("No file with relative pipeline path '" + pipelinePath + "' was found");
                }

                //create a new one
                String name = dataObject.optString(ExperimentJSONConverter.NAME, pipelinePath);
                DataType type = AbstractAssayProvider.RELATED_FILE_DATA_TYPE;
                String lsid = expSvc.generateLSID(container, type, name);
                data = expSvc.createData(container, name, lsid);
                data.setDataFileURI(uri);
                data.save(getViewContext().getUser());
            }
        }
        else if (dataObject.has(ExperimentJSONConverter.DATA_FILE_URL) && dataObject.getString(ExperimentJSONConverter.DATA_FILE_URL) != null)
        {
            String dataFileURL = dataObject.getString(ExperimentJSONConverter.DATA_FILE_URL);
            //check to see if this is already an ExpData
            data = expSvc.getExpDataByURL(dataFileURL, container);

            if (null == data)
            {
                throw new IllegalArgumentException("Could not find a file for dataFileURL " + dataFileURL);
            }
        }
        else if (dataObject.has(ExperimentJSONConverter.ABSOLUTE_PATH) && dataObject.get(ExperimentJSONConverter.ABSOLUTE_PATH) != null)
        {
            String absolutePath = dataObject.getString(ExperimentJSONConverter.ABSOLUTE_PATH);
            File f = new File(absolutePath);
            if (!pipelineRoot.isUnderRoot(f))
            {
                throw new IllegalArgumentException("File with path " + absolutePath + " is not under the pipeline root for this folder");
            }
            //check to see if this is already an ExpData
            data = expSvc.getExpDataByURL(f, container);

            if (null == data)
            {
                if (!NetworkDrive.exists(f))
                {
                    throw new IllegalArgumentException("No file with path '" + absolutePath + "' was found");
                }
                String name = dataObject.optString(ExperimentJSONConverter.NAME, f.getName());
                DataType type = AbstractAssayProvider.RELATED_FILE_DATA_TYPE;
                String lsid = expSvc.generateLSID(container, type, name);
                data = expSvc.createData(container, name, lsid);
                data.setDataFileURI(f.toURI());
                data.save(getViewContext().getUser());
            }
        }
        else
            throw new IllegalArgumentException("Data input must have an id, pipelinePath, dataFileURL, or absolutePath property.");

        saveProperties(data, new DomainProperty[0], dataObject);
        return data;
    }

    private ExpMaterial handleMaterial(JSONObject materialObject) throws ValidationException
    {
        ExpSampleSet sampleSet = null;
        ExpMaterial material = null;
        if (materialObject.has(ExperimentJSONConverter.ID))
        {
            int materialRowId = materialObject.getInt(ExperimentJSONConverter.ID);
            material = ExperimentService.get().getExpMaterial(materialRowId);
            if (material == null)
                throw new NotFoundException("Could not find material with row id '" + materialRowId + "'");
            sampleSet = material.getSampleSet();
        }
        else if (materialObject.has(ExperimentJSONConverter.LSID))
        {
            String materialLsid = materialObject.getString(ExperimentJSONConverter.LSID);
            material = ExperimentService.get().getExpMaterial(materialLsid);
            if (material == null)
                throw new NotFoundException("Could not find material with LSID '" + materialLsid + "'");
            sampleSet = material.getSampleSet();
        }

        if (material == null)
        {
            // Get SampleSet by id or name
            if (materialObject.has(ExperimentJSONConverter.SAMPLE_SET))
            {
                JSONObject sampleSetJson = materialObject.getJSONObject(ExperimentJSONConverter.SAMPLE_SET);
                if (sampleSetJson.has(ExperimentJSONConverter.ID))
                {
                    int sampleSetRowId = sampleSetJson.getInt(ExperimentJSONConverter.ID);
                    sampleSet = ExperimentService.get().getSampleSet(sampleSetRowId);
                    if (sampleSet == null)
                        throw new NotFoundException("A sample set with row id '" + sampleSetRowId + "' doesn't exist.");
                }
                else if (sampleSetJson.has(ExperimentJSONConverter.NAME))
                {
                    String sampleSetName = sampleSetJson.getString(ExperimentJSONConverter.NAME);
                    // XXX: may need to search Project and Shared contains for sample set
//                String sampleSetLsid = ExperimentService.get().getSampleSetLsid(sampleSetName, getViewContext().getContainer()).toString();
//                sampleSet = ExperimentService.get().getSampleSet(sampleSetLsid);
                    sampleSet = ExperimentService.get().getSampleSet(getViewContext().getContainer(), sampleSetName);
                    if (sampleSet == null)
                        throw new NotFoundException("A sample set named '" + sampleSetName + "' doesn't exist.");
                }
            }

            // Get material name or construct name from SampleSet id columns
            String materialName = null;
            if (materialObject.has(ExperimentJSONConverter.NAME))
            {
                materialName = materialObject.getString(ExperimentJSONConverter.NAME);
            }
            else if (sampleSet != null && !sampleSet.hasNameAsIdCol() && materialObject.has(ExperimentJSONConverter.PROPERTIES))
            {
                JSONObject properties = materialObject.getJSONObject(ExperimentJSONConverter.PROPERTIES);
                StringBuilder sb = new StringBuilder();
                for (DomainProperty dp : sampleSet.getIdCols())
                {
                    // XXX: may need to support all possible forms of the property name: label, import aliases, mv
                    String val = properties.getString(dp.getName());
                    if (val == null)
                        throw new IllegalArgumentException("Can't create new sample for sample set '" + sampleSet.getName() + "'; missing required id column '" + dp.getName() + "'");
                    if (sb.length() > 0)
                        sb.append("-");
                    sb.append(val);
                }

                if (sb.length() == 0)
                    throw new IllegalArgumentException("Failed to construct name for material from sample set '" + sampleSet.getName() + "' id columns");
                materialName = sb.toString();
            }

            if (materialName != null && materialName.length() > 0)
            {
                if (sampleSet != null)
                    material = sampleSet.getSample(materialName);
                else
                {
                    List<? extends ExpMaterial> materials = ExperimentService.get().getExpMaterialsByName(materialName, getViewContext().getContainer(), getViewContext().getUser());
                    if (materials != null)
                    {
                        if (materials.size() > 1)
                            throw new NotFoundException("More than one material matches name '" + materialName + "'.  Provide name and sampleSet to disambiguate the desired material.");
                        if (materials.size() == 1)
                            material = materials.get(0);
                    }
                }

                if (material == null)
                    material = createMaterial(sampleSet, materialName);
            }
        }

        if (material == null)
            return null;

        if (!material.getContainer().equals(getViewContext().getContainer()))
            throw new NotFoundException("Material with row id " + material.getRowId() + " is not in folder " + getViewContext().getContainer());

        if (materialObject.has(ExperimentJSONConverter.PROPERTIES))
        {
            DomainProperty[] dps = sampleSet != null ? sampleSet.getPropertiesForType() : new DomainProperty[0];
            saveProperties(material, dps, materialObject.getJSONObject(ExperimentJSONConverter.PROPERTIES));
        }
        
        return material;
    }

    // XXX: doesn't handle SampleSet parent property magic
    // XXX: doesn't assert the materialName is the concat of sampleSet id columns
    private ExpMaterial createMaterial(ExpSampleSet sampleSet, String materialName)
    {
        ExpMaterial material;
        String materialLsid;
        if (sampleSet != null)
        {
            Lsid lsid = new Lsid(sampleSet.getMaterialLSIDPrefix() + "test");
            lsid.setObjectId(materialName);
            materialLsid = lsid.toString();
        }
        else
        {
            XarContext context = new XarContext("DeriveSamples", getViewContext().getContainer(), getViewContext().getUser());
            try
            {
                materialLsid = LsidUtils.resolveLsidFromTemplate("${FolderLSIDBase}:" + materialName, context, "Material");
            }
            catch (XarFormatException e)
            {
                // Shouldn't happen - our template is safe
                throw new RuntimeException(e);
            }
        }

        ExpMaterial other = ExperimentService.get().getExpMaterial(materialLsid);
        if (other != null)
            throw new IllegalArgumentException("Sample with name '" + materialName + "' already exists.");

        material = ExperimentService.get().createExpMaterial(getViewContext().getContainer(), materialLsid, materialName);
        if (sampleSet != null)
            material.setCpasType(sampleSet.getLSID());
        material.save(getViewContext().getUser());
        return material;
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

