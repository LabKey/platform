/*
 * Copyright (c) 2018-2019 LabKey Corporation
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
package org.labkey.api.exp.api;

import org.apache.commons.beanutils.ConversionException;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.labkey.api.action.ApiUsageException;
import org.labkey.api.data.Container;
import org.labkey.api.data.ExpDataFileConverter;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.XarContext;
import org.labkey.api.exp.XarFormatException;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.xar.LsidUtils;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.assay.AssayService;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.view.ViewContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DefaultExperimentSaveHandler implements ExperimentSaveHandler
{
    protected static final Logger LOG = Logger.getLogger(DefaultExperimentSaveHandler.class);

    @Override
    public void beforeSave(ViewContext context, JSONObject rootJson, ExpProtocol protocol)
    {
    }

    @Override
    public void afterSave(ViewContext context, List<? extends ExpExperiment> batches, ExpProtocol protocol) throws Exception
    {
    }

    public static ExpExperiment lookupBatch(Container c, int batchId)
    {
        ExpExperiment batch = ExperimentService.get().getExpExperiment(batchId);
        if (batch == null)
        {
            throw new NotFoundException("Could not find assay batch " + batchId);
        }
        if (!batch.getContainer().equals(c))
        {
            throw new NotFoundException("Could not find assay batch " + batchId + " in folder " + c);
        }
        return batch;
    }

    protected List<? extends DomainProperty> getBatchDomainProperties(ExpProtocol protocol)
    {
        // TODO, figure out handling for ad-hoc properties
        return Collections.emptyList();
    }

    protected List<? extends DomainProperty> getRunDomainProperties(ExpProtocol protocol)
    {
        // TODO, figure out handling for ad-hoc properties
        return Collections.emptyList();
    }

    protected ExpRun createRun(String name, Container container, ExpProtocol protocol)
    {
        ExpRun run = ExperimentService.get().createExperimentRun(container, name);
        run.setProtocol(protocol);
        PipeRoot pipeRoot = PipelineService.get().findPipelineRoot(container);
        if (pipeRoot == null)
        {
            throw new NotFoundException("Pipeline root is not configured for folder " + container);
        }
        run.setFilePathRoot(pipeRoot.getRootPath());

        return run;
    }

    @Override
    public ExpExperiment handleBatch(ViewContext context, JSONObject batchJsonObject, ExpProtocol protocol) throws Exception
    {
        boolean makeNameUnique = false;
        ExpExperiment batch;
        if (batchJsonObject.has(ExperimentJSONConverter.ID))
        {
            batch = lookupBatch(context.getContainer(), batchJsonObject.getInt(ExperimentJSONConverter.ID));
        }
        else
        {
            String batchName;
            if (batchJsonObject.has(ExperimentJSONConverter.NAME))
            {
                batchName = batchJsonObject.getString(ExperimentJSONConverter.NAME);
            }
            else
            {
                batchName = null;
                makeNameUnique = true;
            }
            batch = AssayService.get().createStandardBatch(context.getContainer(), batchName, protocol);
        }

        if (batchJsonObject.has(ExperimentJSONConverter.COMMENT))
        {
            batch.setComments(batchJsonObject.getString(ExperimentJSONConverter.COMMENT));
        }
        handleStandardProperties(context, batchJsonObject, batch, getBatchDomainProperties(protocol));

        List<ExpRun> runs = new ArrayList<>();
        if (batchJsonObject.has(AssayJSONConverter.RUNS))
        {
            JSONArray runsArray = batchJsonObject.getJSONArray(AssayJSONConverter.RUNS);
            for (int i = 0; i < runsArray.length(); i++)
            {
                JSONObject runJsonObject = runsArray.getJSONObject(i);
                ExpRun run = handleRun(context, runJsonObject, protocol, batch);
                runs.add(run);
            }
        }
        List<? extends ExpRun> existingRuns = batch.getRuns();

        // Make sure that all the runs are considered part of the batch
        List<ExpRun> runsToAdd = new ArrayList<>(runs);
        runsToAdd.removeAll(existingRuns);
        batch.addRuns(context.getUser(), runsToAdd.toArray(new ExpRun[runsToAdd.size()]));
        assert checkRunsInBatch(batch, runsToAdd) : "Runs should be in current batch: " + batch;

        // Remove any runs that are no longer part of the batch
        List<ExpRun> runsToRemove = new ArrayList<>(existingRuns);
        runsToRemove.removeAll(runs);
        for (ExpRun runToRemove : runsToRemove)
        {
            batch.removeRun(context.getUser(), runToRemove);
            assert !batch.equals(runToRemove.getBatch()) : "Run's batch should not be the current batch";
        }

        if (makeNameUnique)
        {
            batch = AssayService.get().ensureUniqueBatchName(batch, protocol, context.getUser());
        }
        return batch;
    }

    private boolean checkRunsInBatch(ExpExperiment batch, Collection<ExpRun> runs)
    {
        for (ExpRun run : runs)
        {
            if (!batch.equals(run.getBatch()))
            {
                LOG.warn("Run '" + run + "' is not in batch '" + batch + "'");
                return false;
            }
        }
        return true;
    }

    /**
     * Serialize the dataRows object from the posted run JSON object
     */
    protected JSONArray serializeRunData(ViewContext context, ExpRun run, JSONObject runJson, ExpProtocol protocol, ExpExperiment batch)
    {
        if (runJson.has(AssayJSONConverter.DATA_ROWS) && runJson.getJSONArray(AssayJSONConverter.DATA_ROWS).length() > 0)
        {
            throw new UnsupportedOperationException("Run data is not supported for runs which are not associated with an Assay.");
        }
        return new JSONArray();
    }

    /**
     * Serialize the dataInputs object from the posted run JSON object
     */
    protected JSONArray serializeDataInputs(ViewContext context, ExpRun run, JSONObject runJson, ExpProtocol protocol, ExpExperiment batch)
    {
        if (runJson.has(ExperimentJSONConverter.DATA_INPUTS))
        {
            return runJson.getJSONArray(ExperimentJSONConverter.DATA_INPUTS);
        }
        return new JSONArray();
    }

    /**
     * Serialize the dataOutputs object from the posted run JSON object
     */
    protected JSONArray serializeDataOutputs(ViewContext context, ExpRun run, JSONObject runJson, ExpProtocol protocol, ExpExperiment batch)
    {
        if (runJson.has(ExperimentJSONConverter.DATA_OUTPUTS))
        {
            return runJson.getJSONArray(ExperimentJSONConverter.DATA_OUTPUTS);
        }
        return new JSONArray();
    }

    /**
     * Serialize the materialInputs object from the posted run JSON object
     */
    protected JSONArray serializeMaterialInputs(ViewContext context, ExpRun run, JSONObject runJson, ExpProtocol protocol, ExpExperiment batch)
    {
        if (runJson.has(ExperimentJSONConverter.MATERIAL_INPUTS))
        {
            return runJson.getJSONArray(ExperimentJSONConverter.MATERIAL_INPUTS);
        }
        return new JSONArray();
    }

    /**
     * Serialize the materialOutputs object from the posted run JSON object
     */
    protected JSONArray serializeMaterialOutputs(ViewContext context, ExpRun run, JSONObject runJson, ExpProtocol protocol, ExpExperiment batch)
    {
        if (runJson.has(ExperimentJSONConverter.MATERIAL_OUTPUTS))
        {
            return runJson.getJSONArray(ExperimentJSONConverter.MATERIAL_OUTPUTS);
        }
        return new JSONArray();
    }

    @Override
    public ExpRun handleRun(ViewContext context, JSONObject runJsonObject, ExpProtocol protocol, ExpExperiment batch) throws JSONException, ValidationException, ExperimentException
    {
        String name = runJsonObject.has(ExperimentJSONConverter.NAME) ? runJsonObject.getString(ExperimentJSONConverter.NAME) : null;
        ExpRun run;

        if (runJsonObject.has(ExperimentJSONConverter.ID))
        {
            int runId = runJsonObject.getInt(ExperimentJSONConverter.ID);
            run = ExperimentService.get().getExpRun(runId);
            if (run == null)
            {
                throw new NotFoundException("Could not find experiment run " + runId);
            }
            if (!batch.getContainer().equals(context.getContainer()))
            {
                throw new NotFoundException("Could not find experiment run " + runId + " in folder " + context.getContainer());
            }
        }
        else
        {
            run = createRun(name, context.getContainer(), protocol);
        }

        if (runJsonObject.has(ExperimentJSONConverter.COMMENT))
        {
            run.setComments(runJsonObject.getString(ExperimentJSONConverter.COMMENT));
        }

        try
        {
            handleStandardProperties(context, runJsonObject, run, getRunDomainProperties(protocol));

            if (runJsonObject.has(AssayJSONConverter.DATA_ROWS) ||
                    runJsonObject.has(ExperimentJSONConverter.DATA_INPUTS) ||
                    runJsonObject.has(ExperimentJSONConverter.MATERIAL_INPUTS) ||
                    runJsonObject.has(ExperimentJSONConverter.DATA_OUTPUTS))
            {
                JSONArray dataRows = serializeRunData(context, run, runJsonObject, protocol, batch);
                JSONArray dataInputs = serializeDataInputs(context, run, runJsonObject, protocol, batch);
                JSONArray materialInputs = serializeMaterialInputs(context, run, runJsonObject, protocol, batch);
                JSONArray dataOutputs = serializeDataOutputs(context, run, runJsonObject, protocol, batch);
                JSONArray materialOutputs = serializeMaterialOutputs(context, run, runJsonObject, protocol, batch);

                handleProtocolApplications(context, protocol, batch, run, dataInputs, dataRows, materialInputs, runJsonObject, dataOutputs, materialOutputs);
                ExperimentService.get().onRunDataCreated(protocol, run, context.getContainer(), context.getUser());
            }
        }
        catch (BatchValidationException e)
        {
            throw new ExperimentException(e);
        }
        ExperimentService.get().queueSyncRunEdges(run);

        return run;
    }

    @Override
    public void handleProperties(ViewContext context, ExpObject object, List<? extends DomainProperty> dps, JSONObject propertiesJsonObject) throws ValidationException, JSONException
    {
        for (Map.Entry<PropertyDescriptor, Object> entry : ExperimentJSONConverter.convertProperties(propertiesJsonObject, dps, context.getContainer(), true).entrySet())
        {
            object.setProperty(context.getUser(), entry.getKey(), entry.getValue());
        }
    }

    private void handleStandardProperties(ViewContext context, JSONObject jsonObject, ExpObject object, List<? extends DomainProperty> dps) throws ValidationException, BatchValidationException
    {
        if (jsonObject.has(ExperimentJSONConverter.NAME))
        {
            object.setName(jsonObject.getString(ExperimentJSONConverter.NAME));
        }

        object.save(context.getUser());
        OntologyManager.ensureObject(object.getContainer(), object.getLSID());

        if (jsonObject.has(ExperimentJSONConverter.PROPERTIES))
        {
            try
            {
                handleProperties(context, object, dps, jsonObject.getJSONObject(ExperimentJSONConverter.PROPERTIES));
            }
            catch (ConversionException e)
            {
                throw new ApiUsageException(e);
            }
        }
    }

    protected Map<ExpData, String> getInputData(ViewContext context, JSONArray inputDataArray) throws ValidationException
    {
        Map<ExpData, String> inputData = new HashMap<>();
        for (int i = 0; i < inputDataArray.length(); i++)
        {
            JSONObject dataObject = inputDataArray.getJSONObject(i);
            inputData.put(handleData(context, dataObject), dataObject.optString(ExperimentJSONConverter.ROLE, ExpDataRunInput.DEFAULT_ROLE));
        }

        return inputData;
    }

    protected Map<ExpMaterial, String> getInputMaterial(ViewContext context, JSONArray inputMaterialArray) throws ValidationException
    {
        Map<ExpMaterial, String> inputMaterial = new HashMap<>();
        for (int i=0; i < inputMaterialArray.length(); i++)
        {
            JSONObject materialObject = inputMaterialArray.getJSONObject(i);
            ExpMaterial material = handleMaterial(context, materialObject);
            if (material != null)
                inputMaterial.put(material, materialObject.optString(ExperimentJSONConverter.ROLE, ExpMaterialRunInput.DEFAULT_ROLE));
        }

        return inputMaterial;
    }

    protected void clearOutputDatas(ViewContext context, ExpRun run)
    {
        for (ExpData data : run.getOutputDatas(null))
        {
            if (data.getDataFileUrl() == null)
            {
                data.delete(context.getUser(), false);
            }
        }
    }

    /**
     * Enables the implementor to decide how to save the passed in ExpRun.  The default implementation deletes the
     * run and recreates it.  A custom implementation could choose to enforce that only certain aspects of the
     * protocol application change or choose not add its own data for saving.
     * Called from DefaultAssaySaveHandler.handleRun.
     */
    protected void handleProtocolApplications(ViewContext context, ExpProtocol protocol, ExpExperiment batch, ExpRun run, JSONArray inputDataArray,
                                           JSONArray dataArray, JSONArray inputMaterialArray, JSONObject runJsonObject, JSONArray outputDataArray,
                                           JSONArray outputMaterialArray) throws ExperimentException, ValidationException
    {
        // First, clear out any old data analysis results
        clearOutputDatas(context, run);

        Map<ExpData, String> inputData = getInputData(context, inputDataArray);
        Map<ExpMaterial, String> inputMaterial = getInputMaterial(context, inputMaterialArray);

        run.deleteProtocolApplications(context.getUser());

        // Recreate the run
        Map<ExpData, String> outputData = new HashMap<>();

        //other data outputs
        for (int i=0; i < outputDataArray.length(); i++)
        {
            JSONObject dataObject = outputDataArray.getJSONObject(i);
            outputData.put(handleData(context, dataObject), dataObject.optString(ExperimentJSONConverter.ROLE, ExpDataRunInput.DEFAULT_ROLE));
        }

        Map<ExpMaterial, String> outputMaterial = new HashMap<>();
        for (int i=0; i < outputMaterialArray.length(); i++)
        {
            JSONObject materialObject = outputMaterialArray.getJSONObject(i);
            ExpMaterial material = handleMaterial(context, materialObject);
            if (material != null)
                outputMaterial.put(material, materialObject.optString(ExperimentJSONConverter.ROLE, "Material"));
        }
        saveExperimentRun(context, protocol, batch, run, runJsonObject, dataArray, inputData, outputData, inputMaterial, outputMaterial);
    }

    protected ExpExperiment saveExperimentRun(ViewContext context, ExpProtocol protocol, ExpExperiment batch, ExpRun run,
                                              JSONObject runJson,
                                              JSONArray dataArray,
                                              Map<ExpData, String> inputData,
                                              Map<ExpData, String> outputData,
                                              Map<ExpMaterial, String> inputMaterial,
                                              Map<ExpMaterial, String> outputMaterial) throws ExperimentException, ValidationException
    {
        ViewBackgroundInfo info = new ViewBackgroundInfo(context.getContainer(), context.getUser(), context.getActionURL());
        ExperimentService.get().saveSimpleExperimentRun(run, inputMaterial,inputData, outputMaterial, outputData, Collections.emptyMap(), info, null, false);

        return batch;
    }

    @Override
    public ExpMaterial handleMaterial(ViewContext context, JSONObject materialObject) throws ValidationException
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
            // Get SampleSet by id, name, or lsid
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
                    sampleSet = ExperimentService.get().getSampleSet(context.getContainer(), context.getUser(), sampleSetName);
                    if (sampleSet == null)
                        throw new NotFoundException("A sample set named '" + sampleSetName + "' doesn't exist.");
                }
                else if (sampleSetJson.has(ExperimentJSONConverter.LSID))
                {
                    String lsid = sampleSetJson.getString(ExperimentJSONConverter.LSID);
                    sampleSet = ExperimentService.get().getSampleSet(lsid);
                    if (sampleSet == null)
                        throw new NotFoundException("A sample set with LSID '" + lsid + "' doesn't exist.");
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

                try
                {
                    materialName = sampleSet.createSampleName(properties);
                }
                catch (ExperimentException e)
                {
                    throw new ValidationException(e.getMessage());
                }
            }

            if (materialName != null && materialName.length() > 0)
            {
                if (sampleSet != null)
                    material = sampleSet.getSample(materialName);
                else
                {
                    List<? extends ExpMaterial> materials = ExperimentService.get().getExpMaterialsByName(materialName, context.getContainer(), context.getUser());
                    if (materials != null)
                    {
                        if (materials.size() > 1)
                            throw new NotFoundException("More than one material matches name '" + materialName + "'.  Provide name and sampleSet to disambiguate the desired material.");
                        if (materials.size() == 1)
                            material = materials.get(0);
                    }
                }

                if (material == null)
                    material = createMaterial(context, sampleSet, materialName);
            }
        }

        if (material == null)
            return null;

        if (!material.getContainer().hasPermission(context.getUser(), ReadPermission.class))
            throw new UnauthorizedException("User does not have permissions to reference Material with row id " + material.getRowId());

        if (materialObject.has(ExperimentJSONConverter.PROPERTIES))
        {
            JSONObject materialProperties = materialObject.getJSONObject(ExperimentJSONConverter.PROPERTIES);
            // Treat an empty properties collection as if there were no property map at all.
            // To delete a property, include a property map with that property and set its value to null.
            if (materialProperties.size() > 0)
            {
                List<? extends DomainProperty> dps = sampleSet != null ? sampleSet.getType().getProperties() : Collections.emptyList();
                handleProperties(context, material, dps, materialProperties);
            }
        }

        return material;
    }

    @Override
    public ExpData handleData(ViewContext context, JSONObject dataObject) throws ValidationException
    {
        return ExpDataFileConverter.resolveExpData(dataObject, context.getContainer(), context.getUser(), Collections.emptyList());
    }

    // XXX: doesn't handle SampleSet parent property magic
    // XXX: doesn't assert the materialName is the concat of sampleSet id columns
    private ExpMaterial createMaterial(ViewContext viewContext, ExpSampleSet sampleSet, String materialName)
    {
        ExpMaterial material;
        String materialLsid;
        if (sampleSet != null)
        {
            Lsid.LsidBuilder lsid = new Lsid.LsidBuilder(sampleSet.getMaterialLSIDPrefix() + "test");
            lsid.setObjectId(materialName);
            materialLsid = lsid.toString();
        }
        else
        {
            XarContext context = new XarContext("DeriveSamples", viewContext.getContainer(), viewContext.getUser());
            try
            {
                materialLsid = LsidUtils.resolveLsidFromTemplate("${FolderLSIDBase}:" + materialName, context, ExpMaterial.DEFAULT_CPAS_TYPE);
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

        material = ExperimentService.get().createExpMaterial(viewContext.getContainer(), materialLsid, materialName);
        if (sampleSet != null)
            material.setCpasType(sampleSet.getLSID());
        material.save(viewContext.getUser());
        return material;
    }
}
