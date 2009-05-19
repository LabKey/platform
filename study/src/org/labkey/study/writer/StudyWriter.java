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
import org.labkey.api.util.VirtualFile;
import org.apache.log4j.Logger;

import java.util.Arrays;
import java.util.List;

/**
 * User: adam
 * Date: Apr 14, 2009
 * Time: 7:29:32 PM
 */
public class StudyWriter implements Writer<Study>
{
    private static final Logger LOG = Logger.getLogger(StudyWriter.class);

    private static final List<Writer<Study>> WRITERS = Arrays.asList(
        new VisitMapWriter(),
        new CohortWriter(),
        new QcStateWriter(),
        new DataSetWriter(),
        new SpecimenArchiveWriter(),
        new StudyXmlWriter(),          // Note: Needs to be last of the study writers since it writes out the study.xml file (to which other writers contribute)

        new ReportWriter(),
        new QueryWriter());

    public String getSelectionText()
    {
        return null;
    }

    public void write(Study study, ExportContext ctx, VirtualFile fs) throws Exception
    {
        LOG.info("Exporting study to " + fs.getLocation());

        for (Writer<Study> writer : WRITERS)
        {
            if (null != writer.getSelectionText())
                LOG.info("Writing " + writer.getSelectionText());

            writer.write(study, ctx, fs);
        }

        LOG.info("Done exporting study to " + fs.getLocation());
    }
}
