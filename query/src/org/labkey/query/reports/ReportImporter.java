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
import org.labkey.study.xml.StudyDocument;

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
public class ReportImporter implements FolderImporter
{
    public String getDescription()
    {
        return "reports";
    }

    public void process(PipelineJob job, ImportContext ctx, VirtualFile root) throws IOException, SQLException, ImportException
    {
        VirtualFile reportsDir = getReportsDir(ctx, root);

        if (null != reportsDir)
        {
            if (null != job)
                job.setStatus("IMPORT " + getDescription());
            ctx.getLogger().info("Loading " + getDescription());

            int count = 0;
            String[] reportFileNames = reportsDir.list();
            for (String reportFileName : reportFileNames)
            {
                // skip over any files that don't end with the expected extension
                if (!reportFileName.endsWith(".report.xml"))
                    continue;

                if (null == reportsDir.getXmlBean(reportFileName))
                    throw new IllegalArgumentException("Specified report does not exist: " + reportFileName);

                try
                {
                    ReportService.get().importReport(ctx.getUser(), ctx.getContainer(), reportsDir.getXmlBean(reportFileName));
                    count++;
                }
                catch (XmlValidationException e)
                {
                    throw new InvalidFileException(root.getRelativePath(reportFileName), e);
                }
            }

            ctx.getLogger().info(count + " report" + (1 == count ? "" : "s") + " imported");
            ctx.getLogger().info("Done importing " + getDescription());
        }
    }

    public Collection<PipelineJobWarning> postProcess(ImportContext ctx, VirtualFile root) throws Exception
    {
        return null;
    }

    private VirtualFile getReportsDir(ImportContext ctx, VirtualFile root) throws ImportException
    {
        String dirPath = null;
        if (ctx.getXml() instanceof StudyDocument.Study)
        {
            StudyDocument.Study xml = (StudyDocument.Study)ctx.getXml();
            if (xml.isSetReports())
                dirPath = xml.getReports().getDir();
        }
        else if (ctx.getXml() instanceof FolderDocument.Folder)
        {
            FolderDocument.Folder xml = (FolderDocument.Folder)ctx.getXml();
            if (xml.isSetReports())
                dirPath = xml.getReports().getDir();
        }

        return null != dirPath ? root.getDir(dirPath) : null;
    }

    @Override
    public boolean supportsVirtualFile()
    {
        return true;
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
