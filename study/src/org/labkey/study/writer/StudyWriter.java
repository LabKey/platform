/*
 * Copyright (c) 2009-2017 LabKey Corporation
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
import org.labkey.api.writer.PrintWriters;
import org.labkey.api.writer.VirtualFile;
import org.labkey.api.writer.Writer;
import org.labkey.study.model.StudyImpl;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Set;

/**
 * User: adam
 * Date: Apr 14, 2009
 * Time: 7:29:32 PM
 */
public class StudyWriter implements Writer<StudyImpl, StudyExportContext>
{
    private static final Logger LOG = Logger.getLogger(StudyWriter.class);

    public String getDataType()
    {
        return null;
    }

    public void write(StudyImpl study, StudyExportContext ctx, VirtualFile vf) throws Exception
    {
        LOG.info("Exporting study to " + vf.getLocation());

        Set<String> dataTypes = ctx.getDataTypes();

        // Hack for now to allow selection of CRF vs. Assay datasets.  TODO: More flexible export UI definition mechanism
        boolean exportDatasets = dataTypes.contains(StudyArchiveDataTypes.ASSAY_DATASETS) || dataTypes.contains(StudyArchiveDataTypes.CRF_DATASETS);

        // Call all the writers defined in the study module.
        for (Writer<StudyImpl, StudyExportContext> writer : StudySerializationRegistryImpl.get().getInternalStudyWriters())
        {
            try
            {
                String text = writer.getDataType();

                if (null == text || dataTypes.contains(text) || (exportDatasets && text.endsWith("Datasets")))
                    writer.write(study, ctx, vf);
            }
            catch (Exception e)
            {
                try (OutputStream out = vf.getOutputStream("error.log"))
                {
                    // Try to get some error information to the client instead of creating a completely invalid archive
                    PrintWriter errorWriter = PrintWriters.getPrintWriter(out);
                    e.printStackTrace(errorWriter);
                    errorWriter.flush();
                }
                throw e;
            }
        }

        LOG.info("Done exporting study to " + vf.getLocation());
    }
}
