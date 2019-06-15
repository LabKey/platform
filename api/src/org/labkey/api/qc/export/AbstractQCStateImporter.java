package org.labkey.api.qc.export;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.qc.QCState;
import org.labkey.api.qc.QCStateManager;
import org.labkey.study.xml.qcStates.StudyqcDocument;

import java.util.HashMap;
import java.util.Map;

public abstract class AbstractQCStateImporter
{
    public static void importQCStates(ImportContext ctx, StudyqcDocument doc, QCStateImportExportHelper helper)
    {
        StudyqcDocument.Studyqc qcXml = doc.getStudyqc();
        StudyqcDocument.Studyqc.Qcstates states = qcXml.getQcstates();
        Map<String, Integer> stateMap = new HashMap<>();

        for (QCState existingState : QCStateManager.getInstance().getQCStates(ctx.getContainer()))
        {
            // replace any existing states unless they are currently in use
            if (!helper.isQCStateInUse(ctx.getContainer(),existingState))
                QCStateManager.getInstance().deleteQCState(existingState);
            else
                stateMap.put(existingState.getLabel(), existingState.getRowId());
        }

        if (states != null)
        {
            for (StudyqcDocument.Studyqc.Qcstates.Qcstate state : states.getQcstateArray())
            {
                if (!stateMap.containsKey(state.getName()))
                {
                    if (!StringUtils.isBlank(state.getName()))
                    {
                        QCState newState = new QCState();
                        newState.setContainer(ctx.getContainer());

                        newState.setLabel(state.getName());
                        newState.setDescription(state.getDescription());
                        newState.setPublicData(state.getPublic());

                        newState = helper.insertQCState(ctx.getUser(), newState);
                        stateMap.put(newState.getLabel(), newState.getRowId());
                    }
                    else
                        ctx.getLogger().warn("Ignoring QC state with blank name");
                }
            }
        }

        // make the default qc state assignments for dataset inserts/updates
        String pipelineDefault = qcXml.getPipelineImportDefault();
        if (stateMap.containsKey(pipelineDefault))
            helper.setDefaultPipelineQCState(ctx.getContainer(), ctx.getUser(), stateMap.get(pipelineDefault));

        String assayDefault = qcXml.getAssayDataDefault();
        if (stateMap.containsKey(assayDefault))
            helper.setDefaultAssayQCState(ctx.getContainer(), ctx.getUser(), stateMap.get(assayDefault));

        String datasetDefault = qcXml.getInsertUpdateDefault();
        if (stateMap.containsKey(datasetDefault))
            helper.setDefaultDirectEntryQCState(ctx.getContainer(), ctx.getUser(), stateMap.get(datasetDefault));

        helper.setShowPrivateDataByDefault(ctx.getContainer(), ctx.getUser(), qcXml.getShowPrivateDataByDefault());
        helper.setBlankQCStatePublic(ctx.getContainer(), ctx.getUser(), qcXml.getBlankQCStatePublic());
    }
}
