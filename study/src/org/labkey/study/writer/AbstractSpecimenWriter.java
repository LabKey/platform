package org.labkey.study.writer;

import org.labkey.study.xml.StudyDocument;

/**
 * User: kevink
 * Date: 6/13/13
 */
abstract class AbstractSpecimenWriter implements InternalStudyWriter
{
    protected static final String DEFAULT_DIRECTORY = "specimens";

    protected StudyDocument.Study.Specimens ensureSpecimensElement(StudyExportContext ctx) throws Exception
    {
        StudyDocument.Study studyXml = ctx.getXml();
        StudyDocument.Study.Specimens specimensXml = null;
        if (studyXml.isSetSpecimens())
        {
            specimensXml = studyXml.getSpecimens();
            assert DEFAULT_DIRECTORY.equals(specimensXml.getDir());
        }
        else
        {
            specimensXml = studyXml.addNewSpecimens();
            specimensXml.setDir(DEFAULT_DIRECTORY);
        }

        return specimensXml;
    }
}
