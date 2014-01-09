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
