/*
 * Copyright (c) 2009-2016 LabKey Corporation
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
package org.labkey.study.writer;

import org.labkey.api.data.Container;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.model.QCState;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.xml.StudyDocument;
import org.labkey.study.xml.qcStates.StudyqcDocument;

import java.util.List;

/**
 * User: adam
 * Date: Apr 23, 2009
 * Time: 11:43:38 AM
 */
public class QcStateWriter implements InternalStudyWriter
{
    private static final String DEFAULT_SETTINGS_FILE = "quality_control_states.xml";

    public String getDataType()
    {
        return StudyArchiveDataTypes.QC_STATE_SETTINGS;
    }

    public void write(StudyImpl study, StudyExportContext ctx, VirtualFile vf) throws Exception
    {
        List<QCState> qcStates = StudyManager.getInstance().getQCStates(ctx.getContainer());

        if (!qcStates.isEmpty())
        {
            StudyDocument.Study.QcStates qcStatesXml = ctx.getXml().addNewQcStates();
            StudyqcDocument doc = StudyqcDocument.Factory.newInstance();

            StudyqcDocument.Studyqc qcXml = doc.addNewStudyqc();
            qcXml.setShowPrivateDataByDefault(study.isShowPrivateDataByDefault());
            qcXml.setBlankQCStatePublic(study.isBlankQCStatePublic());

            // set the default states for each import type
            QCState pipelineImportState = getQCStateFromRowId(ctx.getContainer(), study.getDefaultPipelineQCState());
            if (pipelineImportState != null)
                qcXml.setPipelineImportDefault(pipelineImportState.getLabel());

            QCState assayCopyState = getQCStateFromRowId(ctx.getContainer(), study.getDefaultAssayQCState());
            if (assayCopyState != null)
                qcXml.setAssayDataDefault(assayCopyState.getLabel());

            QCState datasetInsertState = getQCStateFromRowId(ctx.getContainer(), study.getDefaultDirectEntryQCState());
            if (datasetInsertState != null)
                qcXml.setInsertUpdateDefault(datasetInsertState.getLabel());

            // now save each of the individual states
            StudyqcDocument.Studyqc.Qcstates states = qcXml.addNewQcstates();
            for (QCState qc : qcStates)
            {
                StudyqcDocument.Studyqc.Qcstates.Qcstate state = states.addNewQcstate();

                state.setName(qc.getLabel());
                state.setDescription(qc.getDescription());
                state.setPublic(qc.isPublicData());
            }
            qcStatesXml.setFile(DEFAULT_SETTINGS_FILE);
            vf.saveXmlBean(DEFAULT_SETTINGS_FILE, doc);
        }
        else if (!study.isShowPrivateDataByDefault() || study.isBlankQCStatePublic())
        {
            // else if there are no qc states, but either of these other states are not the default, then write them out
            StudyDocument.Study.QcStates qcStatesXml = ctx.getXml().addNewQcStates();
            StudyqcDocument doc = StudyqcDocument.Factory.newInstance();

            StudyqcDocument.Studyqc qcXml = doc.addNewStudyqc();
            qcXml.setShowPrivateDataByDefault(study.isShowPrivateDataByDefault());
            qcXml.setBlankQCStatePublic(study.isBlankQCStatePublic());

            qcStatesXml.setFile(DEFAULT_SETTINGS_FILE);
            vf.saveXmlBean(DEFAULT_SETTINGS_FILE, doc);
        }
    }

    private QCState getQCStateFromRowId(Container container, Integer rowId)
    {
        if (rowId != null)
            return StudyManager.getInstance().getQCStateForRowId(container, rowId);

        return null;
    }
}
