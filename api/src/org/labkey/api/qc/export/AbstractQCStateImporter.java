package org.labkey.api.qc.export;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.qc.DataState;
import org.labkey.api.qc.QCStateManager;
import org.labkey.study.xml.qcStates.StudyqcDocument;

import java.util.HashMap;
import java.util.Map;

public abstract class AbstractQCStateImporter
{
    public static void importQCStates(ImportContext<?> ctx, StudyqcDocument doc, QCStateImportExportHelper helper)
    {
        StudyqcDocument.Studyqc qcXml = doc.getStudyqc();
        StudyqcDocument.Studyqc.Qcstates states = qcXml.getQcstates();

        // Remember all of the states that existed before we started importing
        Map<String, DataState> prexistingStates = getExistingQCStates(ctx);

        if (states != null)
        {
            for (StudyqcDocument.Studyqc.Qcstates.Qcstate xmlState : states.getQcstateArray())
            {
                if (StringUtils.isBlank(xmlState.getName()))
                {
                    ctx.getLogger().warn("Ignoring QC state with blank name");
                }
                else
                {
                    // Check if it exists, and remove it from the map if it already does
                    DataState state = prexistingStates.remove(xmlState.getName());
                    if (state == null)
                    {
                        // Insert a new record
                        state = new DataState();
                        state.setContainer(ctx.getContainer());

                        state.setLabel(xmlState.getName());
                        state.setDescription(xmlState.getDescription());
                        state.setPublicData(xmlState.getPublic());

                        helper.insertQCState(ctx.getUser(), state);
                    }
                    else
                    {
                        // Update the existing QCState row in-place
                        state.setDescription(xmlState.getDescription());
                        state.setPublicData(xmlState.getPublic());
                        helper.updateQCState(ctx.getUser(), state);
                    }
                }
            }
        }

        // Clean up orphaned states if they don't seem to be used anymore
        for (DataState orphanedState : prexistingStates.values())
        {
            if (!helper.isQCStateInUse(ctx.getContainer(), orphanedState))
                QCStateManager.getInstance().deleteState(orphanedState);
            else
                ctx.getLogger().info("Retaining existing QCState because it is still in use, even though it's missing from the new list: " + orphanedState.getLabel());
        }

        Map<String, DataState> finalStates = getExistingQCStates(ctx);

        // make the default qc state assignments for dataset inserts/updates
        String pipelineDefault = qcXml.getPipelineImportDefault();
        if (finalStates.containsKey(pipelineDefault))
            helper.setDefaultPipelineQCState(ctx.getContainer(), ctx.getUser(), finalStates.get(pipelineDefault).getRowId());

        String assayDefault = qcXml.getAssayDataDefault();
        if (finalStates.containsKey(assayDefault))
            helper.setDefaultPublishedDataQCState(ctx.getContainer(), ctx.getUser(), finalStates.get(assayDefault).getRowId());

        String datasetDefault = qcXml.getInsertUpdateDefault();
        if (finalStates.containsKey(datasetDefault))
            helper.setDefaultDirectEntryQCState(ctx.getContainer(), ctx.getUser(), finalStates.get(datasetDefault).getRowId());

        helper.setShowPrivateDataByDefault(ctx.getContainer(), ctx.getUser(), qcXml.getShowPrivateDataByDefault());
        helper.setBlankQCStatePublic(ctx.getContainer(), ctx.getUser(), qcXml.getBlankQCStatePublic());
    }

    @NotNull
    private static Map<String, DataState> getExistingQCStates(ImportContext<?> ctx)
    {
        Map<String, DataState> prexistingStates = new HashMap<>();
        for (DataState s : QCStateManager.getInstance().getStates(ctx.getContainer()))
        {
            prexistingStates.put(s.getLabel(), s);
        }
        return prexistingStates;
    }
}
