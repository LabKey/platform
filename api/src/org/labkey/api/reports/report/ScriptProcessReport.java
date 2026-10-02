/*
 * Copyright (c) 2008-2026 LabKey Corporation
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
package org.labkey.api.reports.report;

import org.apache.commons.lang3.BooleanUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Results;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.report.r.RReportJob;
import org.labkey.api.util.FileUtil;
import org.labkey.api.view.ViewContext;
import org.labkey.vfs.FileLike;

import java.io.IOException;

/**
 * A Report implementation that uses an external process or script interpreter to execute the associated script.
 *
 *  See Also ScriptEngineReport.
 *
 *  NOTE: this code was forked from ScriptEngineReport, and removing code duplication between this class and
 *  ScriptEngineReport and ExternalScriptEngineReport is a work in progress.
*/
public abstract class ScriptProcessReport extends ScriptReport implements Report.ResultSetGenerator
{
    final String defaultDescriptorType;
    private FileLike workingDirectory;


    ScriptProcessReport(String defaultDescriptorType)
    {
        this.defaultDescriptorType = defaultDescriptorType;
    }


    @Override
    public String getDescriptorType()
    {
        return defaultDescriptorType;
    }


    @Override
    public boolean supportsPipeline()
    {
        return true;
    }


    @Override
    public Results generateResults(ViewContext context, boolean allowAsyncQuery) throws Exception
    {
        return super._generateResults(context, allowAsyncQuery);
    }

    /**
     *
     * @param executingContainerId id of the container in which the report is running
     * @return directory, which has been created, to contain the generated report
     *
     * Note: This method used to stash results in members (_tempFolder and _tempFolderPipeline), but that no longer works
     * now that we cache reports between threads (e.g., Thread.currentThread().getId() is part of the path).
     */
    public FileLike getReportDir(@NotNull String executingContainerId) throws IOException
    {
        boolean isPipeline = BooleanUtils.toBoolean(getDescriptor().getProperty(ScriptReportDescriptor.Prop.runInBackground));
        return getReportDir(executingContainerId, isPipeline);
    }


    protected FileLike getReportDir(@NotNull String executingContainerId, boolean isPipeline) throws IOException
    {
        if (null == workingDirectory)
        {
            FileLike tempRoot = getTempRootFileLike(getDescriptor());
            String reportId = FileUtil.makeLegalName(String.valueOf(getDescriptor().getReportId())).replaceAll(" ", "_");

            FileLike tempFolder;

            if (isPipeline)
            {
                String identifier = RReportJob.getJobIdentifier();
                if (identifier != null)
                    tempFolder = tempRoot.resolveChild(executingContainerId).resolveChild("Report_" + reportId).resolveChild(identifier);
                else
                    tempFolder = tempRoot.resolveChild(executingContainerId).resolveChild("Report_" + reportId);
            }
            else
                tempFolder = tempRoot.resolveChild(executingContainerId).resolveChild("Report_" + reportId).resolveChild(String.valueOf(Thread.currentThread().getId()));

            if (!tempFolder.exists())
                tempFolder.mkdirs();

            workingDirectory = tempFolder;
        }
        return workingDirectory;
    }
}
