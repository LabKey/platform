package org.labkey.study.writer;

import org.labkey.study.model.Study;
import org.labkey.study.xml.StudyDocument;
import org.labkey.api.util.VirtualFile;

/**
 * User: adam
 * Date: Apr 23, 2009
 * Time: 11:43:38 AM
 */
public class QcStateWriter implements Writer<Study>
{
    public void write(Study study, ExportContext ctx, VirtualFile fs) throws Exception
    {
        // TODO: Write other QC properties
        StudyDocument.Study.QcStates qcStatesXml = ctx.getStudyXml().addNewQcStates();
        qcStatesXml.setShowPrivateDataByDefault(study.isShowPrivateDataByDefault());
    }
}
