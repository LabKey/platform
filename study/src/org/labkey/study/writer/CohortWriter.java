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

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.model.CohortImpl;
import org.labkey.study.model.Participant;
import org.labkey.study.model.ParticipantMapper;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.xml.CohortType;
import org.labkey.study.xml.CohortsDocument;
import org.labkey.study.xml.StudyDocument;
import org.labkey.study.xml.CohortMode;

import java.util.Collection;
import java.util.List;

/**
 * User: adam
 * Date: Apr 23, 2009
 * Time: 11:29:36 AM
 */
public class CohortWriter implements InternalStudyWriter
{
    private static final String COHORTS_FILENAME = "cohorts.xml";

    public String getDataType()
    {
        return StudyArchiveDataTypes.COHORT_SETTINGS;
    }

    public void write(StudyImpl study, StudyExportContext ctx, VirtualFile vf) throws Exception
    {
        if (!ctx.isDataTypeSelected(getDataType()))
            return;

        StudyDocument.Study studyXml = ctx.getXml();
        StudyDocument.Study.Cohorts cohortsXml = studyXml.addNewCohorts();

        //
        // export cohorts.xml for both manual and automatic mode to include
        // the enrolled bit
        //
        if (study.isManualCohortAssignment())
        {
            cohortsXml.setType(CohortType.MANUAL);
            cohortsXml.setMode(CohortMode.SIMPLE);
        }
        else
        {
            cohortsXml.setType(CohortType.AUTOMATIC);
            cohortsXml.setMode(study.isAdvancedCohorts() ? CohortMode.ADVANCED : CohortMode.SIMPLE);

            Integer datasetId = study.getParticipantCohortDatasetId();
            String datasetProperty = study.getParticipantCohortProperty();

            if (null != datasetId && null != datasetProperty)
            {
                cohortsXml.setDatasetId(study.getParticipantCohortDatasetId());
                cohortsXml.setDatasetProperty(study.getParticipantCohortProperty());
            }
        }

        cohortsXml.setFile(COHORTS_FILENAME);

        List<CohortImpl> cohorts = study.getCohorts(ctx.getUser());
        MultiValuedMap<Integer, String> participantsInEachCohort = new ArrayListValuedHashMap<>(cohorts.size());
        ParticipantMapper participantMapper = ctx.getParticipantMapper();

        for (Participant participant : StudyManager.getInstance().getParticipants(study))
        {
            Integer id = participant.getCurrentCohortId();

            if (null != id)
                participantsInEachCohort.put(id, participantMapper.getMappedParticipantId(participant.getParticipantId()));
        }

        CohortsDocument cohortFileXml = CohortsDocument.Factory.newInstance();
        CohortsDocument.Cohorts cohortAssignmentXml = cohortFileXml.addNewCohorts();

        for (CohortImpl cohort : cohorts)
        {
            CohortsDocument.Cohorts.Cohort cohortXml = cohortAssignmentXml.addNewCohort();
            cohortXml.setLabel(cohort.getLabel());
            cohortXml.setEnrolled(cohort.isEnrolled());

            if (cohort.getSubjectCount() != null)
                cohortXml.setSubjectCount(cohort.getSubjectCount());
            if (cohort.getDescription() != null)
                cohortXml.setDescription(cohort.getDescription());

            // only add the list of participants in the manual assignment case
            if (study.isManualCohortAssignment())
            {
                Collection<String> ids = participantsInEachCohort.get(cohort.getRowId());
                if (null != ids)
                    cohortXml.setIdArray(ids.toArray(new String[ids.size()]));
            }
        }

        vf.saveXmlBean(COHORTS_FILENAME, cohortFileXml);
    }
}
