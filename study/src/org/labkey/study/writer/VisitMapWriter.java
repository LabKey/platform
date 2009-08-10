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

import org.labkey.api.util.VirtualFile;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.importer.StudyImporter;

import java.io.IOException;

/**
 * User: adam
 * Date: Apr 15, 2009
 * Time: 10:52:38 AM
 */
public class VisitMapWriter implements Writer<StudyImpl>
{
    public String getSelectionText()
    {
        return "Visit Map";
    }

    public void write(StudyImpl study, ExportContext ctx, VirtualFile fs) throws IOException, StudyImporter.StudyImportException
    {
        if (ctx.useOldFormats())
        {
            DataFaxVisitMapWriter writer = new DataFaxVisitMapWriter();
            writer.write(study.getVisits(), ctx, fs);
        }
        else
        {
            XmlVisitMapWriter writer = new XmlVisitMapWriter();
            writer.write(study.getVisits(), ctx, fs);
        }
    }
}
