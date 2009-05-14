/*
 * Copyright (c) 2009 LabKey Corporation
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

import org.apache.commons.collections15.MultiMap;
import org.apache.commons.collections15.multimap.MultiHashMap;
import org.apache.xmlbeans.XmlOptions;
import org.labkey.api.util.VirtualFile;
import org.labkey.study.model.Cohort;
import org.labkey.study.model.Participant;
import org.labkey.study.model.Study;
import org.labkey.study.model.StudyManager;
import org.labkey.study.xml.CohortType;
import org.labkey.study.xml.StudyDocument;
import org.labkey.study.xml.CohortsDocument;

import java.util.Collection;
import java.io.PrintWriter;

/**
 * User: adam
 * Date: Apr 23, 2009
 * Time: 11:29:36 AM
 */
public class CohortWriter implements Writer<Study>
{
    private static final String COHORTS_FILENAME = "cohorts.xml";

    public void write(Study study, ExportContext ctx, VirtualFile fs) throws Exception
    {
        StudyDocument.Study studyXml = ctx.getStudyXml();
        StudyDocument.Study.Cohorts cohortsXml = studyXml.addNewCohorts();

        if (study.isManualCohortAssignment())
        {
            cohortsXml.setType(CohortType.MANUAL);
            cohortsXml.setFile(COHORTS_FILENAME);

            Cohort[] cohorts = study.getCohorts(ctx.getUser());
            MultiMap<Integer, String> participantsInEachCohort = new MultiHashMap<Integer, String>(cohorts.length);

            for (Participant participant : StudyManager.getInstance().getParticipants(study))
            {
                Integer id = participant.getCohortId();

                if (null != id)
                    participantsInEachCohort.put(id, participant.getParticipantId());
            }

            PrintWriter pw = fs.getPrintWriter(COHORTS_FILENAME);

            CohortsDocument cohortFileXml = CohortsDocument.Factory.newInstance();
            CohortsDocument.Cohorts cohortAssignmentXml = cohortFileXml.addNewCohorts();

            for (Cohort cohort : cohorts)
            {
                CohortsDocument.Cohorts.Cohort cohortXml = cohortAssignmentXml.addNewCohort();
                cohortXml.setLabel(cohort.getLabel());
                Collection<String> ids = participantsInEachCohort.get(cohort.getRowId());

                if (null != ids)
                    cohortXml.setIdArray(ids.toArray(new String[ids.size()]));
            }

            XmlOptions options = new XmlOptions();
            options.setSavePrettyPrint();
            options.setUseDefaultNamespace();
            cohortFileXml.save(pw, options);
            pw.close();
        }
        else
        {
            cohortsXml.setType(CohortType.AUTOMATIC);
            cohortsXml.setDataSetId(study.getParticipantCohortDataSetId().intValue());
            cohortsXml.setDataSetProperty(study.getParticipantCohortProperty());
        }
    }
}
