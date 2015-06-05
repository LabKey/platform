/*
 * Copyright (c) 2013-2015 LabKey Corporation
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

import org.apache.commons.beanutils.ConversionException;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.labkey.api.action.ApiUsageException;
import org.labkey.api.data.Container;
import org.labkey.api.data.ExpDataFileConverter;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.XarContext;
import org.labkey.api.exp.XarFormatException;
import org.labkey.api.exp.api.AssayJSONConverter;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpDataRunInput;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpMaterialRunInput;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.exp.api.ExperimentJSONConverter;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.xar.LsidUtils;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.ViewContext;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: jeckels
 * Date: Jul 12, 2007
 */
public class DefaultAssaySaveHandler implements AssaySaveHandler
{
    protected static final Logger LOG = Logger.getLogger(DefaultAssaySaveHandler.class);
    protected AssayProvider _provider;

    public AssayProvider getProvider()
    {
        return _provider;
    }

    public void setProvider(AssayProvider provider)
    {
        _provider = provider;
    }

    @Override
    public void beforeSave(ViewContext context, JSONObject rootJson, ExpProtocol protocol) throws Exception
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
        handleStandardProperties(context, batchJsonObject, batch, _provider.getBatchDomain(protocol).getProperties());

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

    @Override
    public ExpRun handleRun(ViewContext context, JSONObject runJsonObject, ExpProtocol protocol, ExpExperiment batch) throws JSONException, ValidationException, ExperimentException, SQLException
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
            if (!batch.getContainer().equals(context.getContainer()))
            {
                throw new NotFoundException("Could not find assay run " + runId + " in folder " + context.getContainer());
            }
        }
        else
        {
            run = AssayService.get().createExperimentRun(name, context.getContainer(), protocol, null);
        }

        if (runJsonObject.has(ExperimentJSONConverter.COMMENT))
        {
            run.setComments(runJsonObject.getString(ExperimentJSONConverter.COMMENT));
        }

        handleStandardProperties(context, runJsonObject, run, _provider.getRunDomain(protocol).getProperties());

        if (runJsonObject.has(AssayJSONConverter.DATA_ROWS) ||
                runJsonObject.has(ExperimentJSONConverter.DATA_INPUTS) ||
                runJsonObject.has(ExperimentJSONConverter.MATERIAL_INPUTS))
        {
            JSONArray dataRows;
            JSONArray dataInputs;
            JSONArray materialInputs;
            JSONArray dataOutputs;
            JSONArray materialOutputs;

            JSONObject serializedRun = null;
            if (!runJsonObject.has(AssayJSONConverter.DATA_ROWS))
            {
                // Client didn't post the rows so reuse the values that are currently attached to the run
                // Inefficient but easy
                serializedRun = AssayJSONConverter.serializeRun(run, _provider, protocol, context.getUser());
                dataRows = serializedRun.getJSONArray(AssayJSONConverter.DATA_ROWS);
            }
            else
            {
                dataRows = runJsonObject.getJSONArray(AssayJSONConverter.DATA_ROWS);
            }

            if (!runJsonObject.has(ExperimentJSONConverter.DATA_INPUTS))
            {
                if (serializedRun == null)
                    serializedRun = AssayJSONConverter.serializeRun(run, _provider, protocol, context.getUser());
                dataInputs = serializedRun.getJSONArray(ExperimentJSONConverter.DATA_INPUTS);
            }
            else
            {
                dataInputs = runJsonObject.getJSONArray(ExperimentJSONConverter.DATA_INPUTS);
            }

            if (!runJsonObject.has(ExperimentJSONConverter.MATERIAL_INPUTS))
            {
                if (serializedRun == null)
                    serializedRun = AssayJSONConverter.serializeRun(run, _provider, protocol, context.getUser());
                materialInputs = serializedRun.getJSONArray(ExperimentJSONConverter.MATERIAL_INPUTS);
            }
            else
            {
                materialInputs = runJsonObject.getJSONArray(ExperimentJSONConverter.MATERIAL_INPUTS);
            }

            if (!runJsonObject.has(ExperimentJSONConverter.DATA_OUTPUTS))
            {
                if (serializedRun == null)
                    serializedRun = AssayJSONConverter.serializeRun(run, _provider, protocol, context.getUser());
                dataOutputs = serializedRun.getJSONArray(ExperimentJSONConverter.DATA_OUTPUTS);
            }
            else
            {
                dataOutputs = runJsonObject.getJSONArray(ExperimentJSONConverter.DATA_OUTPUTS);
            }

            if (!runJsonObject.has(ExperimentJSONConverter.MATERIAL_OUTPUTS))
            {
                if (serializedRun == null)
                    serializedRun = AssayJSONConverter.serializeRun(run, _provider, protocol, context.getUser());
                materialOutputs = serializedRun.getJSONArray(ExperimentJSONConverter.MATERIAL_OUTPUTS);
            }
            else
            {
                materialOutputs = runJsonObject.getJSONArray(ExperimentJSONConverter.MATERIAL_OUTPUTS);
            }

            handleProtocolApplications(context, protocol, batch, run, dataInputs, dataRows, materialInputs, runJsonObject, dataOutputs, materialOutputs);
            AssayPublishService.get().autoCopyResults(protocol, run, context.getUser(), context.getContainer());
        }

        return run;
    }


    @Override
    public void handleProperties(ViewContext context, ExpObject object, List<? extends DomainProperty> dps, JSONObject propertiesJsonObject) throws ValidationException, JSONException
    {
        for (Map.Entry<DomainProperty, Object> entry : ExperimentJSONConverter.convertProperties(propertiesJsonObject, dps, context.getContainer(), true).entrySet())
        {
            object.setProperty(context.getUser(), entry.getKey().getPropertyDescriptor(), entry.getValue());
        }
    }

    private void handleStandardProperties(ViewContext context, JSONObject jsonObject, ExpObject object, List<? extends DomainProperty> dps) throws ValidationException, SQLException
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

    protected ExpData generateResultData(ViewContext context, ExpRun run, JSONArray dataArray, Map<ExpData, String> outputData)
    {
        ExpData newData = null;

        // Don't create an empty result data file if there are other outputs from this run, or if the user didn't
        // include any data rows
        if (dataArray.length() > 0 && outputData.isEmpty())
        {
            DataType dataType = getProvider().getDataType();
            if (dataType == null)
                dataType = AbstractAssayProvider.RELATED_FILE_DATA_TYPE;

            newData = DefaultAssayRunCreator.createData(run.getContainer(), null, "Analysis Results", dataType, true);
            newData.save(context.getUser());
            outputData.put(newData, ExpDataRunInput.DEFAULT_ROLE);
        }

        return newData;
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
        for (ExpData data : run.getOutputDatas(_provider.getDataType()))
        {
            if (data.getDataFileUrl() == null)
            {
                data.delete(context.getUser());
            }
        }
    }

    @Override
    public void handleProtocolApplications(ViewContext context, ExpProtocol protocol, ExpExperiment batch, ExpRun run, JSONArray inputDataArray,
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

        ExpData newData = generateResultData(context, run, dataArray, outputData);

        Map<ExpMaterial, String> outputMaterial = new HashMap<>();
        for (int i=0; i < outputMaterialArray.length(); i++)
        {
            JSONObject materialObject = outputMaterialArray.getJSONObject(i);
            ExpMaterial material = handleMaterial(context, materialObject);
            if (material != null)
                outputMaterial.put(material, materialObject.optString(ExperimentJSONConverter.ROLE, "Material"));
        }

        ExpData tsvData = newData;
        // Try to find a data object to attach our data rows to
        if (tsvData == null && !outputData.isEmpty())
        {
            tsvData = outputData.keySet().iterator().next();
            for (Map.Entry<ExpData, String> entry : outputData.entrySet())
            {
                // Prefer the generic "Data" role if we have one
                if (ExpDataRunInput.DEFAULT_ROLE.equals(entry.getValue()))
                {
                    tsvData = entry.getKey();
                    break;
                }
            }
        }

        if (tsvData != null && dataArray != null)
        {
            AssayRunUploadContext uploadContext = createRunUploadContext(context, protocol, runJsonObject, dataArray,
                    inputData, outputData, inputMaterial, outputMaterial);

            saveExperimentRun(uploadContext, batch, run);
        }
    }

    @Nullable
    protected AssayRunUploadContext createRunUploadContext(ViewContext context, ExpProtocol protocol, JSONObject runJsonObject, JSONArray runDataArray,
                                                           Map<ExpData, String> inputData, Map<ExpData, String> outputData,
                                                           Map<ExpMaterial, String> inputMaterial, Map<ExpMaterial, String> outputMaterial)
    {
        if (runDataArray != null)
        {
            AssayRunUploadContext.Factory factory = createRunUploadContext(protocol, context);
            factory.setInputDatas(inputData);

            if (factory instanceof ModuleRunUploadContext.Factory)
            {
                ModuleRunUploadContext.Factory moduleContextFactory = (ModuleRunUploadContext.Factory)factory;
                moduleContextFactory.setJsonObject(runJsonObject);
                moduleContextFactory.setRawData(runDataArray.toMapList());

                // TODO: Move the .setOutputDatas and materials to the AssayRunUploadContext.Factory
                moduleContextFactory.setOutputDatas(outputData);
                moduleContextFactory.setInputMaterials(inputMaterial);
                moduleContextFactory.setOutputMaterials(outputMaterial);
            }

            return factory.create();
        }
        return null;
    }

    @NotNull
    protected AssayRunUploadContext.Factory createRunUploadContext(ExpProtocol protocol, ViewContext context)
    {
        AssayProvider provider = getProvider();
        return provider.createRunUploadFactory(protocol, context);
    }

    protected ExpExperiment saveExperimentRun(AssayRunUploadContext context, ExpExperiment batch, ExpRun run) throws ExperimentException, ValidationException
    {
        AssayRunCreator runCreator = getProvider().getRunCreator();
        return runCreator.saveExperimentRun(context, batch, run, false);
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
                    sampleSet = ExperimentService.get().getSampleSet(context.getContainer(), sampleSetName);
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
                List<? extends DomainProperty> dps = sampleSet != null ? sampleSet.getType().getProperties() : Collections.<DomainProperty>emptyList();
                handleProperties(context, material, dps, materialProperties);
            }
        }

        return material;
    }

    @Override
    public ExpData handleData(ViewContext context, JSONObject dataObject) throws ValidationException
    {
        ExpData data = ExpDataFileConverter.resolveExpData(dataObject, context.getContainer(), context.getUser());

        handleProperties(context, data, Collections.<DomainProperty>emptyList(), dataObject);
        return data;
    }

    // XXX: doesn't handle SampleSet parent property magic
    // XXX: doesn't assert the materialName is the concat of sampleSet id columns
    private ExpMaterial createMaterial(ViewContext viewContext, ExpSampleSet sampleSet, String materialName)
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
            XarContext context = new XarContext("DeriveSamples", viewContext.getContainer(), viewContext.getUser());
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

        material = ExperimentService.get().createExpMaterial(viewContext.getContainer(), materialLsid, materialName);
        if (sampleSet != null)
            material.setCpasType(sampleSet.getLSID());
        material.save(viewContext.getUser());
        return material;
    }
}
