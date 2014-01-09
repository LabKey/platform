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

import org.labkey.api.data.views.ProviderType;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.query.ValidationException;

import java.util.List;
import java.util.Map;

/**
 * Created by klum on 1/3/14.
 */
public interface ModuleRunUploadContext<ProviderType extends AssayProvider> extends AssayRunUploadContext<ProviderType>
{
    Map<ExpData, String> getInputDatas();
    void setInputDatas(Map<ExpData, String> inputDatas);

    Map<ExpData, String> getOutputDatas();
    void setOutputDatas(Map<ExpData, String> outputDatas);

    Map<ExpMaterial, String> getInputMaterials();
    void setInputMaterials(Map<ExpMaterial, String> inputMaterials);

    Map<ExpMaterial, String> getOutputMaterials();
    void setOutputMaterials(Map<ExpMaterial, String> outputMaterials);

    // helper to import result data
    void importResultData(ExpRun run, Map<ExpData, String> inputDatas, Map<ExpData, String> outputDatas, List<ExpData> insertedDatas) throws ExperimentException, ValidationException;

    // allow the context to add data and material during run creation
    void addDataAndMaterials(Map<ExpData, String> inputDatas, Map<ExpData, String> outputDatas, Map<ExpMaterial, String> inputMaterials, Map<ExpMaterial, String> outputMaterials);
}
