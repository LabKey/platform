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
package org.labkey.study.importer;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.study.StudyImportException;
import org.labkey.study.model.CohortImpl;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.VisitImpl;
import org.labkey.study.visitmanager.VisitManager;
import org.labkey.study.xml.StudyDocument;

import java.io.File;
import java.sql.SQLException;
import java.util.List;

/**
 * User: adam
 * Date: May 21, 2009
 * Time: 3:22:31 PM
 */
public class VisitCohortAssigner implements InternalStudyImporter
{
    public String getDescription()
    {
        return "visit map cohort assignments";
    }

    // Parses the whole visit map again to retrieve the cohort assigments; should cache info from the first parsing
    // somewhere in the ImportContext
    public void process(StudyImpl study, ImportContext ctx, File root) throws SQLException, StudyImportException
    {
        StudyDocument.Study.Visits visitsXml = ctx.getStudyXml().getVisits();

        if (null != visitsXml)
        {
            File visitMap = ctx.getStudyFile(root, root, visitsXml.getFile());

            StudyManager studyManager = StudyManager.getInstance();
            VisitManager visitManager = studyManager.getVisitManager(study);
            Container c = ctx.getContainer();
            User user = ctx.getUser();

            VisitMapImporter.Format vmFormat = VisitMapImporter.Format.getFormat(visitMap);
            String contents = PageFlowUtil.getFileContentsAsString(visitMap);
            List<VisitMapRecord> records = vmFormat.getReader().getRecords(contents);

            for (VisitMapRecord record : records)
            {
                VisitImpl visit = visitManager.findVisitBySequence(record.getSequenceNumMin());

                String oldCohortLabel = null != visit.getCohort() ? visit.getCohort().getLabel() : null;

                if (!PageFlowUtil.nullSafeEquals(oldCohortLabel, record.getCohort()))
                {
                    CohortImpl cohort = studyManager.getCohortByLabel(c, user, record.getCohort());
                    VisitImpl mutable = visit.createMutable();
                    mutable.setCohortId(cohort.getRowId());
                    StudyManager.getInstance().updateVisit(ctx.getUser(), mutable);
                }
            }
        }
    }
}
