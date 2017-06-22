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
package org.labkey.query.reports;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.admin.AbstractFolderImportFactory;
import org.labkey.api.admin.FolderImporter;
import org.labkey.api.admin.FolderArchiveDataTypes;
import org.labkey.api.admin.ImportContext;
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

    public String getDescription()
    {
        return getDataType().toLowerCase();
    }

    public void process(PipelineJob job, ImportContext ctx, VirtualFile root) throws IOException, SQLException, ImportException
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
                if (!reportFileName.endsWith(".report.xml"))
                    continue;

                if (null == reportsDir.getXmlBean(reportFileName))
                    throw new IllegalArgumentException("Specified report does not exist: " + reportFileName);

                try
                {
                    if (ReportService.get().importReport(ctx, reportsDir.getXmlBean(reportFileName), reportsDir) != null)
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

    @NotNull
    public Collection<PipelineJobWarning> postProcess(ImportContext ctx, VirtualFile root) throws Exception
    {
        // in 13.2, there was a change to use dataset names instead of label for query references in reports, views, etc.
        // fire the query change listeners for older archives to fix-up these dataset label references
        if (ctx.getArchiveVersion() != null && ctx.getArchiveVersion() < 13.11)
        {
            StudyService svc = StudyService.get();
            Study study = svc != null ? svc.getStudy(ctx.getContainer()) : null;
            if (study != null)
            {
                List<QueryChangeListener.QueryPropertyChange> queryPropertyChanges = new ArrayList<>();
                for (Dataset dataset : study.getDatasets())
                {
                    if (!dataset.getName().equals(dataset.getLabel()))
                    {
                        queryPropertyChanges.add(new QueryChangeListener.QueryPropertyChange<>(
                                QueryService.get().getUserSchema(ctx.getUser(), ctx.getContainer(), "study").getQueryDefForTable(dataset.getName()),
                                QueryChangeListener.QueryProperty.Name,
                                dataset.getLabel(),
                                dataset.getName()
                        ));
                    }
                }

                if (queryPropertyChanges.size() > 0)
                {
                    ctx.getLogger().info("Post-processing reports, custom views, and query snapshots to use dataset name instead of label");
                    QueryService.get().fireQueryChanged(ctx.getUser(), ctx.getContainer(), null, new SchemaKey(null, "study"), QueryChangeListener.QueryProperty.Name, queryPropertyChanges);
                    ctx.getLogger().info("Done post-processing dataset label to name conversion");
                }
            }
        }

        return Collections.emptyList();
    }

    @Override
    public boolean isValidForImportArchive(ImportContext ctx) throws ImportException
    {
        return ctx.getDir("reports") != null;
    }

    public static class Factory extends AbstractFolderImportFactory
    {
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
