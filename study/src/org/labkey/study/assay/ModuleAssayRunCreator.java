package org.labkey.study.assay;

import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.XarContext;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.qc.TransformResult;
import org.labkey.api.query.ValidationException;
import org.labkey.api.study.assay.AssayRunUploadContext;
import org.labkey.api.study.assay.DefaultAssayRunCreator;
import org.labkey.api.study.assay.ParticipantVisitResolverType;
import org.labkey.api.study.assay.TsvDataHandler;
import org.labkey.api.view.ViewBackgroundInfo;

import java.util.List;
import java.util.Map;

/**
 * Created by klum on 12/31/13.
 */
public class ModuleAssayRunCreator extends DefaultAssayRunCreator<ModuleAssayProvider>
{
    public ModuleAssayRunCreator(ModuleAssayProvider provider)
    {
        super(provider);
    }

    @Override
    protected void importStandardResultData(AssayRunUploadContext<ModuleAssayProvider> context, ExpRun run, Map<ExpData, String> inputDatas, Map<ExpData, String> outputDatas, ViewBackgroundInfo info, XarContext xarContext, TransformResult transformResult, List<ExpData> insertedDatas) throws ExperimentException, ValidationException
    {
        if (context instanceof ModuleRunUploadForm)
        {
            insertedDatas.addAll(outputDatas.keySet());
            List<Map<String, Object>> rawData = ((ModuleRunUploadForm)context).getRawData();

            for (ExpData insertedData : insertedDatas)
            {
                TsvDataHandler dataHandler = new TsvDataHandler();
                dataHandler.setAllowEmptyData(true);
                dataHandler.importRows(insertedData, context.getUser(), run, context.getProtocol(), context.getProvider(), rawData);
            }
        }
    }

    @Override
    protected void addInputDatas(AssayRunUploadContext<ModuleAssayProvider> context, Map<ExpData, String> inputDatas, ParticipantVisitResolverType resolverType) throws ExperimentException
    {
        if (context instanceof ModuleRunUploadForm)
        {
            Map<ExpData, String> moduleInputDatas = ((ModuleRunUploadForm)context).getInputDatas();
            if (!moduleInputDatas.isEmpty())
                inputDatas.putAll(moduleInputDatas);
        }
    }

    @Override
    protected void addOutputDatas(AssayRunUploadContext<ModuleAssayProvider> context, Map<ExpData, String> outputDatas, ParticipantVisitResolverType resolverType) throws ExperimentException
    {
        if (context instanceof ModuleRunUploadForm)
        {
            Map<ExpData, String> moduleOutputDatas = ((ModuleRunUploadForm)context).getOutputDatas();
            if (!moduleOutputDatas.isEmpty())
                outputDatas.putAll(moduleOutputDatas);
        }
    }

    @Override
    protected void addInputMaterials(AssayRunUploadContext<ModuleAssayProvider> context, Map<ExpMaterial, String> inputMaterials, ParticipantVisitResolverType resolverType) throws ExperimentException
    {
        if (context instanceof ModuleRunUploadForm)
        {
            Map<ExpMaterial, String> moduleInputMaterials = ((ModuleRunUploadForm)context).getInputMaterials();
            if (!moduleInputMaterials.isEmpty())
                inputMaterials.putAll(moduleInputMaterials);
        }
    }

    @Override
    protected void addOutputMaterials(AssayRunUploadContext<ModuleAssayProvider> context, Map<ExpMaterial, String> outputMaterials, ParticipantVisitResolverType resolverType) throws ExperimentException
    {
        if (context instanceof ModuleRunUploadForm)
        {
            Map<ExpMaterial, String> moduleOutputMaterials = ((ModuleRunUploadForm)context).getOutputMaterials();
            if (!moduleOutputMaterials.isEmpty())
                outputMaterials.putAll(moduleOutputMaterials);
        }
    }
}
