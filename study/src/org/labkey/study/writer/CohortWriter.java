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

import org.labkey.study.model.Study;
import org.labkey.study.xml.StudyDocument;
import org.labkey.study.xml.CohortType;
import org.labkey.api.util.VirtualFile;

/**
 * User: adam
 * Date: Apr 23, 2009
 * Time: 11:29:36 AM
 */
public class CohortWriter implements Writer<Study>
{
    public void write(Study study, ExportContext ctx, VirtualFile fs) throws Exception
    {
        // TODO: Support manual cohorts

        StudyDocument.Study studyXml = ctx.getStudyXml();
        StudyDocument.Study.Cohorts cohorts = studyXml.addNewCohorts();
        cohorts.setType(CohortType.AUTOMATIC);
        cohorts.setDataSetId(study.getParticipantCohortDataSetId().intValue());
        cohorts.setDataSetProperty(study.getParticipantCohortProperty());
    }
}
