/*
 * Copyright (c) 2009-2018 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.admin.AbstractFolderImportFactory;
import org.labkey.api.admin.FolderArchiveDataTypes;
import org.labkey.api.admin.FolderImportContext;
import org.labkey.api.admin.FolderImporter;
import org.labkey.api.admin.ImportException;
import org.labkey.api.admin.InvalidFileException;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobWarning;
import org.labkey.api.query.QueryChangeListener;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.reports.ReportService;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.XmlValidationException;
import org.labkey.api.writer.VirtualFile;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.labkey.api.reports.report.ScriptReportDescriptor.REPORT_METADATA_EXTENSION;

/**
 * User: adam
 * Date: May 16, 2009
 * Time: 2:33:52 PM
 */
public class ReportImporter implements FolderImporter
{
    @Override
    public String getDataType()
    {
        return FolderArchiveDataTypes.REPORTS_AND_CHARTS;
    }

    @Override
    public String getDescription()
    {
        return getDataType().toLowerCase();
    }

    @Override
    public void process(PipelineJob job, FolderImportContext ctx, VirtualFile root) throws IOException, SQLException, ImportException
    {
        if (isValidForImportArchive(ctx))
        {
            VirtualFile reportsDir = ctx.getDir("reports");

            if (null != job)
                job.setStatus("IMPORT " + getDescription());
            ctx.getLogger().info("Loading " + getDescription());

            int count = 0;

            for (String reportFileName : reportsDir.list())
            {
                // skip over any files that don't end with the expected extension
                if (!reportFileName.endsWith(REPORT_METADATA_EXTENSION))
                    continue;

                if (null == reportsDir.getXmlBean(reportFileName))
                    throw new IllegalArgumentException("Specified report does not exist: " + reportFileName);

                try
                {
                    if (ReportService.get().importReport(ctx, reportsDir.getXmlBean(reportFileName), reportsDir, reportFileName) != null)
                        count++;
                    else
                        ctx.getLogger().warn("Unable to import report file: " + reportFileName);
                }
                catch (XmlValidationException e)
                {
                    throw new InvalidFileException(root.getRelativePath(reportFileName), e);
                }
            }

            ctx.getLogger().info(StringUtilsLabKey.pluralize(count, "report") + " imported");
            ctx.getLogger().info("Done importing " + getDescription());
        }
    }

    @Override
    public boolean isValidForImportArchive(FolderImportContext ctx) throws ImportException
    {
        return ctx.getDir("reports") != null;
    }

    public static class Factory extends AbstractFolderImportFactory
    {
        @Override
        public FolderImporter create()
        {
            return new ReportImporter();
        }

        @Override
        public int getPriority()
        {
            return 75;
        }
    }
}
