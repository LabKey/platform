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

package org.labkey.api.study.assay;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.ExpDataFileConverter;
import org.labkey.api.data.MvUtil;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.MvColumn;
import org.labkey.api.exp.MvFieldWrapper;
import org.labkey.api.exp.api.AssayJSONConverter;
import org.labkey.api.exp.api.DefaultExperimentSaveHandler;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpDataRunInput;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentJSONConverter;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.ValidationException;
import org.labkey.api.view.ViewContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * User: jeckels
 * Date: Jul 12, 2007
 */
public class DefaultAssaySaveHandler extends DefaultExperimentSaveHandler implements AssaySaveHandler
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
    protected List<? extends DomainProperty> getBatchDomainProperties(ExpProtocol protocol)
    {
        return _provider.getBatchDomain(protocol).getProperties();
    }

    @Override
    protected List<? extends DomainProperty> getRunDomainProperties(ExpProtocol protocol)
    {
        return _provider.getRunDomain(protocol).getProperties();
    }

    protected ExpRun createRun(String name, Container container, ExpProtocol protocol)
    {
        return AssayService.get().createExperimentRun(name, container, protocol, null);
    }

    @Override
    protected JSONArray serializeRunData(ViewContext context, ExpRun run, JSONObject runJson, ExpProtocol protocol, ExpExperiment batch)
    {
        JSONArray dataRows;
        if (!runJson.has(AssayJSONConverter.DATA_ROWS))
        {
            // Client didn't post the rows so reuse the values that are currently attached to the run
            // Inefficient but easy
            JSONObject serializedRun = AssayJSONConverter.serializeRun(run, _provider, protocol, context.getUser());
            dataRows = serializedRun.getJSONArray(AssayJSONConverter.DATA_ROWS);
        }
        else
        {
            dataRows = runJson.getJSONArray(AssayJSONConverter.DATA_ROWS);
        }
        return dataRows;
    }

    @Override
    protected JSONArray serializeDataInputs(ViewContext context, ExpRun run, JSONObject runJson, ExpProtocol protocol, ExpExperiment batch)
    {
        JSONArray dataInputs;
        if (!runJson.has(ExperimentJSONConverter.DATA_INPUTS))
        {
            JSONObject serializedRun = AssayJSONConverter.serializeRun(run, _provider, protocol, context.getUser());
            dataInputs = serializedRun.getJSONArray(ExperimentJSONConverter.DATA_INPUTS);
        }
        else
        {
            dataInputs = runJson.getJSONArray(ExperimentJSONConverter.DATA_INPUTS);
        }
        return dataInputs;
    }

    @Override
    protected JSONArray serializeMaterialInputs(ViewContext context, ExpRun run, JSONObject runJson, ExpProtocol protocol, ExpExperiment batch)
    {
        JSONArray materialInputs;
        if (!runJson.has(ExperimentJSONConverter.MATERIAL_INPUTS))
        {
            JSONObject serializedRun = AssayJSONConverter.serializeRun(run, _provider, protocol, context.getUser());
            materialInputs = serializedRun.getJSONArray(ExperimentJSONConverter.MATERIAL_INPUTS);
        }
        else
        {
            materialInputs = runJson.getJSONArray(ExperimentJSONConverter.MATERIAL_INPUTS);
        }
        return materialInputs;
    }

    @Override
    protected JSONArray serializeDataOutputs(ViewContext context, ExpRun run, JSONObject runJson, ExpProtocol protocol, ExpExperiment batch)
    {
        JSONArray dataOutputs;
        if (!runJson.has(ExperimentJSONConverter.DATA_OUTPUTS))
        {
            JSONObject serializedRun = AssayJSONConverter.serializeRun(run, _provider, protocol, context.getUser());
            dataOutputs = serializedRun.getJSONArray(ExperimentJSONConverter.DATA_OUTPUTS);
        }
        else
        {
            dataOutputs = runJson.getJSONArray(ExperimentJSONConverter.DATA_OUTPUTS);
        }
        return dataOutputs;
    }

    @Override
    protected JSONArray serializeMaterialOutputs(ViewContext context, ExpRun run, JSONObject runJson, ExpProtocol protocol, ExpExperiment batch)
    {
        JSONArray materialOutputs;
        if (!runJson.has(ExperimentJSONConverter.MATERIAL_OUTPUTS))
        {
            JSONObject serializedRun = AssayJSONConverter.serializeRun(run, _provider, protocol, context.getUser());
            materialOutputs = serializedRun.getJSONArray(ExperimentJSONConverter.MATERIAL_OUTPUTS);
        }
        else
        {
            materialOutputs = runJson.getJSONArray(ExperimentJSONConverter.MATERIAL_OUTPUTS);
        }
        return materialOutputs;
    }

    protected void clearOutputDatas(ViewContext context, ExpRun run)
    {
        for (ExpData data : run.getOutputDatas(_provider.getDataType()))
        {
            if (data.getDataFileUrl() == null)
            {
                data.delete(context.getUser(), false);
            }
        }
    }

    @Override
    protected ExpExperiment saveExperimentRun(ViewContext context, ExpProtocol protocol, ExpExperiment batch, ExpRun run,
                                              JSONObject runJson, JSONArray dataArray, Map<ExpData, String> inputData,
                                              Map<ExpData, String> outputData, Map<ExpMaterial, String> inputMaterial,
                                              Map<ExpMaterial, String> outputMaterial) throws ExperimentException, ValidationException
    {
        List<Map<String, Object>> dataRows = convertRunData(dataArray, run.getContainer(), protocol);
        ExpData tsvData = DefaultAssayRunCreator.generateResultData(context.getUser(), run.getContainer(), getProvider(), dataRows, (Map)outputData);

        // CONSIDER: Is this block still needed?
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

        if (tsvData != null && dataRows != null)
        {
            AssayRunUploadContext uploadContext = createRunUploadContext(context, protocol, runJson, dataRows,
                    inputData, outputData, inputMaterial, outputMaterial);

            return saveAssayRun(uploadContext, batch, run);
        }
        return null;
    }

    /**
     * Handle any mv indicator columns plus any additional conversion on the results domain data before run creation.
     */
    private List<Map<String, Object>> convertRunData(JSONArray dataArray, Container container, ExpProtocol protocol)
    {
        Domain domain = _provider.getResultsDomain(protocol);
        Map<String, DomainProperty> propertyMap = domain.getProperties().stream()
                .collect(Collectors.toMap(DomainProperty::getName, e -> e));

        List<Map<String, Object>> dataRows = dataArray.toMapList();
        for (Map<String, Object> row : dataRows)
        {
            for (Map.Entry<String, Object> entry : row.entrySet())
            {
                DomainProperty prop = propertyMap.get(entry.getKey());
                if (prop != null && prop.isMvEnabled())
                {
                    String mvIndicatorName = entry.getKey() + MvColumn.MV_INDICATOR_SUFFIX;
                    if (row.containsKey(mvIndicatorName))
                    {
                        MvFieldWrapper mvFieldWrapper = new MvFieldWrapper(MvUtil.getMvIndicators(container), entry.getValue(), String.valueOf(row.get(mvIndicatorName)));
                        row.put(entry.getKey(), mvFieldWrapper);
                    }
                }
            }
        }
        return dataRows;
    }

    @Nullable
    protected AssayRunUploadContext createRunUploadContext(ViewContext context, ExpProtocol protocol, JSONObject runJsonObject, List<Map<String, Object>> dataRows,
                                                           Map<ExpData, String> inputData, Map<ExpData, String> outputData,
                                                           Map<ExpMaterial, String> inputMaterial, Map<ExpMaterial, String> outputMaterial)
    {
        if (dataRows != null)
        {
            AssayRunUploadContext.Factory<? extends AssayProvider, ? extends AssayRunUploadContext.Factory> factory = createRunUploadContext(protocol, context);

            if (runJsonObject != null && runJsonObject.has(ExperimentJSONConverter.PROPERTIES))
            {
                Map<String, Object> runProperties = runJsonObject.getJSONObject(ExperimentJSONConverter.PROPERTIES);
                factory.setRunProperties(runProperties);
                // BUGBUG?: The original ModuleRunUploadForm sets batch properties from the runJsonObject ?!! maybe we shouldn't do that
                factory.setBatchProperties(runProperties);
            }
            factory.setUploadedData(Collections.emptyMap());
            factory.setRawData(dataRows);
            factory.setInputDatas(inputData);
            factory.setOutputDatas(outputData);
            factory.setInputMaterials(inputMaterial);
            factory.setOutputMaterials(outputMaterial);

            return factory.create();
        }
        return null;
    }

    @NotNull
    protected AssayRunUploadContext.Factory<? extends AssayProvider, ? extends AssayRunUploadContext.Factory> createRunUploadContext(ExpProtocol protocol, ViewContext context)
    {
        AssayProvider provider = getProvider();
        return provider.createRunUploadFactory(protocol, context);
    }

    protected ExpExperiment saveAssayRun(AssayRunUploadContext context, ExpExperiment batch, ExpRun run) throws ExperimentException, ValidationException
    {
        AssayRunCreator runCreator = getProvider().getRunCreator();
        return runCreator.saveExperimentRun(context, batch, run, false);
    }

    @Override
    public ExpData handleData(ViewContext context, JSONObject dataObject) throws ValidationException
    {
        List<AssayDataType> knownTypes = new ArrayList<>();
        if (getProvider().getDataType() != null)
        {
            knownTypes.add(getProvider().getDataType());
        }
        knownTypes.addAll(getProvider().getRelatedDataTypes());
        ExpData data = ExpDataFileConverter.resolveExpData(dataObject, context.getContainer(), context.getUser(), knownTypes);

        handleProperties(context, data, Collections.emptyList(), dataObject);
        return data;
    }
}
