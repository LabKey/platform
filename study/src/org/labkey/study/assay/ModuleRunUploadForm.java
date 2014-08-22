/*
 * Copyright (c) 2009-2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.study.assay;

import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentJSONConverter;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.ValidationException;
import org.labkey.api.study.actions.AssayRunUploadForm;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayRunUploadContext;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.assay.ModuleRunUploadContext;
import org.labkey.api.study.assay.TsvDataHandler;
import org.labkey.api.view.ViewContext;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This is a utility class to help with validation/transformation of uploaded
 * data for module based assays.
 *
 * User: klum
 * Date: Apr 29, 2009
 */
public class ModuleRunUploadForm extends AssayRunUploadForm<TsvAssayProvider> implements ModuleRunUploadContext<TsvAssayProvider>
{
    // required fields
    private final JSONObject _runJsonObject;
    private final List<Map<String, Object>> _uploadedData;

    // optional fields
    private Map<Object, String> inputDatas;
    private Map<ExpData, String> outputDatas;
    private Map<ExpMaterial, String> inputMaterials;
    private Map<ExpMaterial, String> outputMaterials;

    private ModuleRunUploadForm(@NotNull ViewContext context, int protocolId, @NotNull JSONObject jsonObject, @NotNull List<Map<String, Object>> uploadedData)
    {
        _runJsonObject = jsonObject;
        _uploadedData = uploadedData;

        setViewContext(context);
        setRowId(protocolId);
    }

    @Override
    public Map<DomainProperty, String> getRunProperties() throws ExperimentException
    {
        if (_runProperties == null)
        {
            AssayProvider provider = AssayService.get().getProvider(getProtocol());
            _runProperties = new HashMap<>();

            if (_runJsonObject.has(ExperimentJSONConverter.PROPERTIES))
            {
                for (Map.Entry<DomainProperty, Object> entry : ExperimentJSONConverter.convertProperties(_runJsonObject.getJSONObject(ExperimentJSONConverter.PROPERTIES),
                        provider.getRunDomain(getProtocol()).getProperties(), getContainer(), false).entrySet())
                {
                    Object o = entry.getValue();
                    _runProperties.put(entry.getKey(), o == null ? null : String.valueOf(o));
                }
            }
        }
        return Collections.unmodifiableMap(_runProperties);
    }

    @Override
    public Map<DomainProperty, String> getBatchProperties()
    {
        if (_uploadSetProperties == null)
        {
            AssayProvider provider = AssayService.get().getProvider(getProtocol());
            _uploadSetProperties = new HashMap<>();

            if (_runJsonObject.has(ExperimentJSONConverter.PROPERTIES))
            {
                for (Map.Entry<DomainProperty, Object> entry : ExperimentJSONConverter.convertProperties(_runJsonObject.getJSONObject(ExperimentJSONConverter.PROPERTIES),
                        provider.getBatchDomain(getProtocol()).getProperties(), getContainer(), false).entrySet())
                {
                    Object o = entry.getValue();
                    _uploadSetProperties.put(entry.getKey(), o == null ? null : String.valueOf(o));
                }
            }
        }
        return Collections.unmodifiableMap(_uploadSetProperties);
    }

    public List<Map<String, Object>> getRawData()
    {
        return _uploadedData;
    }

    @NotNull
    @Override
    public Map<Object, String> getInputDatas()
    {
        return inputDatas;
    }

    @Override
    public Map<ExpData, String> getOutputDatas()
    {
        return outputDatas;
    }

    @Override
    public Map<ExpMaterial, String> getInputMaterials()
    {
        return inputMaterials;
    }

    @Override
    public Map<ExpMaterial, String> getOutputMaterials()
    {
        return outputMaterials;
    }

    @Override
    public void importResultData(ExpRun run, Map<ExpData, String> inputDatas, Map<ExpData, String> outputDatas, List<ExpData> insertedDatas) throws ExperimentException, ValidationException
    {
        insertedDatas.addAll(outputDatas.keySet());
        List<Map<String, Object>> rawData = getRawData();

        for (ExpData insertedData : insertedDatas)
        {
            TsvDataHandler dataHandler = new TsvDataHandler();
            dataHandler.setAllowEmptyData(true);
            dataHandler.importRows(insertedData, getUser(), run, getProtocol(), getProvider(), rawData);
        }
    }

    @Override
    public void addDataAndMaterials(Map<ExpData, String> inputDatas, Map<ExpData, String> outputDatas, Map<ExpMaterial, String> inputMaterials, Map<ExpMaterial, String> outputMaterials)
    {
        // NOTE: Adding inputs from this AssayRunUploadContext is handled by DefaultAssayRunCreator now. Eventually outputDatas and materials will also be handled there as well
        //if (!getInputDatas().isEmpty())
        //    inputDatas.putAll(getInputDatas());

        if (!getOutputDatas().isEmpty())
            outputDatas.putAll(getOutputDatas());

        if (!getInputMaterials().isEmpty())
            inputMaterials.putAll(getInputMaterials());

        if (!getOutputMaterials().isEmpty())
            outputMaterials.putAll(getOutputMaterials());
    }

    public static class Factory extends ModuleRunUploadContext.Factory<TsvAssayProvider, Factory>
    {
        public Factory(@NotNull ExpProtocol protocol, @NotNull TsvAssayProvider provider, @NotNull ViewContext context, @NotNull JSONObject jsonObject, @NotNull List<Map<String, Object>> rawData)
        {
            super(protocol, provider, context, jsonObject, rawData);
        }

        public Factory(@NotNull ExpProtocol protocol, @NotNull TsvAssayProvider provider, @NotNull ViewContext context)
        {
            super(protocol, provider, context);
        }

        @Override
        public ModuleRunUploadForm.Factory self()
        {
            return this;
        }

        @Override
        public AssayRunUploadContext<TsvAssayProvider> create()
        {
            if (_jsonObject == null)
                throw new IllegalStateException("jsonObject required");

            if (_rawData == null)
                throw new IllegalStateException("rawData required");

            ModuleRunUploadForm form = new ModuleRunUploadForm(_context, _protocol.getRowId(), _jsonObject, _rawData);
            form.setName(_name);
            form.setComments(_comments);

            if (this._inputDatas == null)
                form.inputDatas = Collections.emptyMap();
            else
                form.inputDatas = Collections.unmodifiableMap(this._inputDatas);

            if (this._inputMaterials == null)
                form.inputMaterials = Collections.emptyMap();
            else
                form.inputMaterials = Collections.unmodifiableMap(this._inputMaterials);

            if (this._outputDatas == null)
                form.outputDatas = Collections.emptyMap();
            else
                form.outputDatas = Collections.unmodifiableMap(this._outputDatas);

            if (this._outputMaterials == null)
                form.outputMaterials = Collections.emptyMap();
            else
                form.outputMaterials = Collections.unmodifiableMap(this._outputMaterials);

            return form;
        }
    }
}
