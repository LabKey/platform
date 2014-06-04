/*
 * Copyright (c) 2014 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.query.ValidationException;
import org.labkey.api.view.ViewContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by klum on 1/3/14.
 */
public interface ModuleRunUploadContext<ProviderType extends AssayProvider> extends AssayRunUploadContext<ProviderType>
{

    Map<ExpData, String> getOutputDatas();

    Map<ExpMaterial, String> getInputMaterials();

    Map<ExpMaterial, String> getOutputMaterials();

    // helper to import result data
    void importResultData(ExpRun run, Map<ExpData, String> inputDatas, Map<ExpData, String> outputDatas, List<ExpData> insertedDatas) throws ExperimentException, ValidationException;

    // allow the context to add data and material during run creation
    void addDataAndMaterials(Map<ExpData, String> inputDatas, Map<ExpData, String> outputDatas, Map<ExpMaterial, String> inputMaterials, Map<ExpMaterial, String> outputMaterials);

    public abstract class Factory<ProviderType extends AssayProvider, FACTORY extends Factory<ProviderType, FACTORY>> extends AssayRunUploadContext.Factory<ProviderType, FACTORY>
    {
        // required fields, but must be checked at creation time since the AssayProvider.createRunUploadFactory() doesn't include the other arguments
        protected JSONObject _jsonObject;
        protected List<Map<String, Object>> _rawData;

        // TODO: Move outputDatas, inputMaterials, outputMaterials to the base class AssayRunUploadContext.Factory
        // optional fields
        protected Map<ExpData, String> _outputDatas = new HashMap<>();
        protected Map<ExpMaterial, String> _inputMaterials = new HashMap<>();
        protected Map<ExpMaterial, String> _outputMaterials = new HashMap<>();

        public Factory(@NotNull ExpProtocol protocol, @NotNull ProviderType provider, @NotNull ViewContext context, @NotNull JSONObject jsonObject, @NotNull List<Map<String, Object>> rawData)
        {
            super(protocol, provider, context);
            _jsonObject = jsonObject;
            _rawData = rawData;
        }

        public Factory(@NotNull ExpProtocol protocol, @NotNull ProviderType provider, @NotNull ViewContext context)
        {
            super(protocol, provider, context);
        }

        public FACTORY setJsonObject(JSONObject jsonObject)
        {
            this._jsonObject = jsonObject;
            return self();
        }

        public FACTORY setRawData(List<Map<String, Object>> rawData)
        {
            _rawData = rawData;
            return self();
        }

        // TODO: Move to AssayRunUploadContext.Factory
        public FACTORY setOutputDatas(Map<ExpData, String> outputDatas)
        {
            this._outputDatas = outputDatas;
            return self();
        }

        // TODO: Move to AssayRunUploadContext.Factory
        public FACTORY setInputMaterials(Map<ExpMaterial, String> inputMaterials)
        {
            this._inputMaterials = inputMaterials;
            return self();
        }

        // TODO: Move to AssayRunUploadContext.Factory
        public FACTORY setOutputMaterials(Map<ExpMaterial, String> outputMaterials)
        {
            this._outputMaterials = outputMaterials;
            return self();
        }

    }
}
