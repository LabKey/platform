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
