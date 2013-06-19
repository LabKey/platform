/*
 * Copyright (c) 2013 LabKey Corporation
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
