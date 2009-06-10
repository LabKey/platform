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

import org.apache.log4j.Logger;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.VirtualFile;
import org.labkey.study.model.StudyImpl;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * User: adam
 * Date: Apr 14, 2009
 * Time: 7:29:32 PM
 */
public class StudyWriter implements Writer<StudyImpl>
{
    private static final Logger LOG = Logger.getLogger(StudyWriter.class);

    private final Set<String> _dataTypes;

    public StudyWriter()
    {
        _dataTypes = null;   // All data types
    }

    public StudyWriter(String... dataTypes)
    {
        _dataTypes = PageFlowUtil.set(dataTypes);
    }

    public String getSelectionText()
    {
        return null;
    }

    public void write(StudyImpl study, ExportContext ctx, VirtualFile fs) throws Exception
    {
        LOG.info("Exporting study to " + fs.getLocation());

        for (Writer<StudyImpl> writer : getWriters())
        {
            if (null == _dataTypes || null == writer.getSelectionText() || _dataTypes.contains(writer.getSelectionText()))
                writer.write(study, ctx, fs);
        }

        LOG.info("Done exporting study to " + fs.getLocation());
    }

    public static List<Writer<StudyImpl>> getWriters()
    {
        // New up the writers every time since these classes can be stateful
        return Arrays.asList(
            new VisitMapWriter(),
            new CohortWriter(),
            new QcStateWriter(),
            new DatasetWriter(),
            new SpecimenArchiveWriter(),
            new QueryWriter(),
            new CustomViewWriter(),
            new ReportWriter(),
            new StudyXmlWriter()  // Note: Needs to be last of the study writers since it writes out the study.xml file (to which other writers contribute)
        );
    }
}
