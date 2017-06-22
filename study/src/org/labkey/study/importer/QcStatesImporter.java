/*
 * Copyright (c) 2009-2017 LabKey Corporation
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
package org.labkey.study.importer;

import org.apache.commons.lang3.StringUtils;
import org.apache.xmlbeans.XmlObject;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.ImportException;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.model.QCState;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.writer.StudyArchiveDataTypes;
import org.labkey.study.xml.StudyDocument;
import org.labkey.study.xml.qcStates.StudyqcDocument;
import org.springframework.validation.BindException;

import java.util.HashMap;
import java.util.Map;

/**
 * User: adam
 * Date: May 16, 2009
 * Time: 9:36:25 PM
 */
public class QcStatesImporter implements InternalStudyImporter
{
    @Override
    public String getDescription()
    {
        return "QC States Importer";
    }

    public String getDataType()
    {
        return StudyArchiveDataTypes.QC_STATE_SETTINGS;
    }

    @Override
    public void process(StudyImportContext ctx, VirtualFile root, BindException errors) throws Exception
    {
        if (!ctx.isDataTypeSelected(getDataType()))
            return;

        if (isValidForImportArchive(ctx, root))
        {
            StudyImpl study = ctx.getStudy();
            StudyDocument.Study.QcStates qcStates = ctx.getXml().getQcStates();

            ctx.getLogger().info("Loading QC states");
            StudyController.ManageQCStatesForm qcForm = new StudyController.ManageQCStatesForm();
            StudyqcDocument doc = getSettingsFile(ctx, root);

            // if the import provides a study qc document (new in 13.3), parse it for the qc states, else
            // revert back to the previous behavior where we just set the default data visibility
            if (doc != null)
            {
                StudyqcDocument.Studyqc qcXml = doc.getStudyqc();
                StudyqcDocument.Studyqc.Qcstates states = qcXml.getQcstates();
                Map<String, Integer> stateMap = new HashMap<>();

                for (QCState existingState : StudyManager.getInstance().getQCStates(ctx.getContainer()))
                {
                    // replace any existing states unless they are currently in use
                    if (!StudyManager.getInstance().isQCStateInUse(existingState))
                        StudyManager.getInstance().deleteQCState(existingState);
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

                                newState = StudyManager.getInstance().insertQCState(ctx.getUser(), newState);
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
                    qcForm.setDefaultPipelineQCState(stateMap.get(pipelineDefault));

                String assayDefault = qcXml.getAssayDataDefault();
                if (stateMap.containsKey(assayDefault))
                    qcForm.setDefaultAssayQCState(stateMap.get(assayDefault));

                String datasetDefault = qcXml.getInsertUpdateDefault();
                if (stateMap.containsKey(datasetDefault))
                    qcForm.setDefaultDirectEntryQCState(stateMap.get(datasetDefault));

                qcForm.setShowPrivateDataByDefault(qcXml.getShowPrivateDataByDefault());
                qcForm.setBlankQCStatePublic(qcXml.getBlankQCStatePublic());

                StudyController.updateQcState(study, ctx.getUser(), qcForm);
            }
            else
            {
                qcForm.setShowPrivateDataByDefault(qcStates.getShowPrivateDataByDefault());
                // Preserve these default settings as they may have been previously configured in the app
                qcForm.setDefaultPipelineQCState(study.getDefaultPipelineQCState());
                qcForm.setDefaultAssayQCState(study.getDefaultAssayQCState());
                qcForm.setDefaultDirectEntryQCState(study.getDefaultDirectEntryQCState());
                qcForm.setBlankQCStatePublic(study.isBlankQCStatePublic());
                StudyController.updateQcState(study, ctx.getUser(), qcForm);
            }

            ctx.getLogger().info("Done importing QC states");
        }
    }

    @Override
    public boolean isValidForImportArchive(StudyImportContext ctx, VirtualFile root) throws ImportException
    {
        return ctx.getXml() != null && ctx.getXml().getQcStates() != null;
    }

    @Nullable
    private StudyqcDocument getSettingsFile(StudyImportContext ctx, VirtualFile root) throws Exception
    {
        StudyDocument.Study.QcStates qcXml  = ctx.getXml().getQcStates();

        if (qcXml != null)
        {
            String fileName = qcXml.getFile();

            if (fileName != null)
            {
                XmlObject doc = root.getXmlBean(fileName);
                if (doc instanceof StudyqcDocument)
                    return (StudyqcDocument)doc;
            }
        }
        return null;
    }
}
