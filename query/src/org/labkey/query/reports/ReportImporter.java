/*
 * Copyright (c) 2009-2012 LabKey Corporation
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
package org.labkey.query.reports;

import org.labkey.api.admin.FolderImporter;
import org.labkey.api.admin.FolderImporterFactory;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.admin.ImportException;
import org.labkey.api.admin.InvalidFileException;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.reports.ReportService;
import org.labkey.api.pipeline.PipelineJobWarning;
import org.labkey.api.util.XmlValidationException;
import org.labkey.api.writer.VirtualFile;
import org.labkey.folder.xml.FolderDocument;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Collection;

/**
 * User: adam
 * Date: May 16, 2009
 * Time: 2:33:52 PM
 */
public class ReportImporter implements FolderImporter<FolderDocument.Folder>
{
    public String getDescription()
    {
        return "reports";
    }

    public void process(PipelineJob job, ImportContext<FolderDocument.Folder> ctx, VirtualFile root) throws IOException, SQLException, ImportException
    {
        File reportsDir = ctx.getDir("reports");
        if (null != reportsDir)
        {
            if (null != job)
                job.setStatus("IMPORT " + getDescription());
            ctx.getLogger().info("Loading " + getDescription());

            File[] reportsFiles = reportsDir.listFiles(new FilenameFilter() {
                public boolean accept(File dir, String name)
                {
                    return name.endsWith(".report.xml");
                }
            });

            for (File reportFile : reportsFiles)
            {
                try
                {
                    ReportService.get().importReport(ctx.getUser(), ctx.getContainer(), reportFile);
                }
                catch (XmlValidationException e)
                {
                    throw new InvalidFileException(root, reportFile, e);
                }
            }

            ctx.getLogger().info(reportsFiles.length + " report" + (1 == reportsFiles.length ? "" : "s") + " imported");
            ctx.getLogger().info("Done importing " + getDescription());
        }
    }

    public Collection<PipelineJobWarning> postProcess(ImportContext<FolderDocument.Folder> ctx, VirtualFile root) throws Exception
    {
        //nothing for now
        return null;
    }

    public static class Factory implements FolderImporterFactory
    {
        public FolderImporter create()
        {
            return new ReportImporter();
        }

        @Override
        public boolean isFinalImporter()
        {
            return false;
        }
    }
}
