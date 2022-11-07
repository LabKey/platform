/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

package org.labkey.api.reports.report.r;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.JdbcType;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineQueue;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.ReportIdentifier;
import org.labkey.api.reports.report.ReportUrls;
import org.labkey.api.reports.report.ScriptReportDescriptor;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.view.ViewContext;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import static org.labkey.api.util.StringUtilsLabKey.DEFAULT_CHARSET;

/**
 * User: Karl Lum
 * Date: Jun 11, 2007
 */
public class RReportJob extends PipelineJob implements Serializable
{
    private static final Logger _log = LogManager.getLogger(RReportJob.class);
    private static final ThreadLocal<String> _jobIdentifier = new ThreadLocal<>();

    public static final String PROCESSING_STATUS = "Processing";
    public static final String LOG_FILE_NAME = "report.log";
    public static final String LOG_FILE_PREFIX = "report";

    private ReportIdentifier _reportId;
    private Report _report;

    private final TreeMap<String,String> params = new TreeMap<>();

    // For serialization
    protected RReportJob() {}

    public RReportJob(String provider, ViewBackgroundInfo info, ReportIdentifier reportId, PipeRoot root)
    {
        super(provider, info, root);
        _reportId = reportId;
        init(this.getContainerId());
    }

    public RReportJob(String provider, ViewBackgroundInfo info, Report report, PipeRoot root)
    {
        super(provider, info, root);
        _report = report;
        init(this.getContainerId());
    }

    public void addReportParams(Map<String,String> map)
    {
        if (null != map)
            params.putAll(map);
    }

    protected void init(@NotNull String executingContainerId)
    {
        RReport report = getReport();
        if (report != null)
        {
            _jobIdentifier.set(getJobGUID());
            File logFile = new File(report.getReportDir(executingContainerId), LOG_FILE_NAME);
            setLogFile(logFile);
        }
    }

    @Override
    public boolean setQueue(PipelineQueue queue, TaskStatus initialState)
    {
        _jobIdentifier.remove();
        return super.setQueue(queue, initialState);
    }

    public static String getJobIdentifier()
    {
        return _jobIdentifier.get();
    }

    @Override
    public ActionURL getStatusHref()
    {
        File statusFile = getLogFile();
        if (statusFile != null && null != _reportId)
        {
            return PageFlowUtil.urlProvider(ReportUrls.class).urlRunReport(getContainer()).
                    addParameter(ReportDescriptor.Prop.reportId.name(), _reportId.toString());
        }
        return null;
    }

    @Override
    public String getDescription()
    {
        RReport report = getReport();
        String description = null != report ? report.getDescriptor().getProperty(ReportDescriptor.Prop.reportName) : "";

        if (StringUtils.isEmpty(description))
            description = "Unknown R Report";

        return description;
    }

    protected RReport getReport()
    {
        Report report = (_reportId != null) ? _reportId.getReport(getInfo()) : _report;
        if (null == report)
            return null;

        if (!(report instanceof RReport))
            throw new UnsupportedOperationException("The report is not a valid instance of an RReport");

        String s = report.getDescriptor().getProperty(ScriptReportDescriptor.Prop.runInBackground);
        Boolean runInBackground = (Boolean)JdbcType.BOOLEAN.convert(s);
        if (Boolean.TRUE != runInBackground)
            throw new UnsupportedOperationException("Report should have runInBackground property set to true.");

        return (RReport)report;
    }

    @Override
    public void run()
    {
        setStatus(PROCESSING_STATUS, "Job started at: " + DateUtil.nowISO());
        RReport report = getReport();
        if (null == report)
        {
            error("Error running R report job: report not found '" + _reportId + "'");
            return;
        }
        try
        {
            createPipelineTask(this, report, params).run();
            setStatus(TaskStatus.complete, "Job finished at: " + DateUtil.nowISO());
        }
        catch (PipelineJobException x)
        {
            error("Error running report", x);
        }
    }


    public Task createPipelineTask(PipelineJob job, Report report, Map<String,String> params)
    {
        // overrride to support different kind of report
        if (!(report instanceof RReport))
            throw new UnsupportedOperationException("Expected R report");
        return new RunRReportTask(job, ((RReport)report), params);
    }


    public static class RunRReportTask extends PipelineJob.Task
    {
        final RReport _report;
        final Map<String,String> _params;

        public RunRReportTask(PipelineJob job, RReport report, Map<String,String> params)
        {
            super(null, job);
            _report = Objects.requireNonNull(report);
            _params = new HashMap<>(params);
        }

        @Override
        public @NotNull RecordedActionSet run() throws PipelineJobException
        {
            // when Prop.runInBackground==true, RReportJob uses _jobsIdentifier to communicate with ScriptEngineReport.getReportDir(),
            _jobIdentifier.set(getJob().getJobGUID());
            getJob().setStatus(PROCESSING_STATUS, "Job started at: " + DateUtil.nowISO());
            getJob().info("Running R report task '" + _report.getDescriptor().getReportName() + "'");

            if (HttpView.hasCurrentView())
            {
                runReport(HttpView.currentContext());
            }
            else
            {
                // Must be a background thread... push a fake ViewContext on the HttpView stack if so HttpView.currentContext() succeeds.
                try (ViewContext.StackResetter resetter = ViewContext.pushMockViewContext(getJob().getUser(), getJob().getContainer(), getJob().getActionURL()))
                {
                    try
                    {
                        runReport(resetter.getContext());
                    }
                    finally
                    {
                        _jobIdentifier.remove();
                    }
                }
            }
            return new RecordedActionSet(makeRecordedAction());
        }

        protected RecordedAction makeRecordedAction()
        {
            RecordedAction ra = new RecordedAction(this.getClass().getSimpleName());
            for (Map.Entry<String, String> setting : _params.entrySet())
            {
                RecordedAction.ParameterType paramType = new RecordedAction.ParameterType(setting.getKey(), PropertyType.STRING);
                ra.addParameter(paramType, setting.getValue());
            }
            return ra;
        }

        protected void runReport(ViewContext context) throws PipelineJobException
        {
            try
            {
                // get the input file which should have been previously created
                File inputFile = inputFile(_report, context);
                List<ParamReplacement> outputSubst = new ArrayList<>();

                ActionURL url = context.cloneActionURL();
                url.addParameters(_params);
                context.setActionURL(url);
                String output = _report.runScript(context, outputSubst, inputFile, null);
                if (!StringUtils.isEmpty(output))
                {
                    getJob().info("CONSOLE OUTPUT:\n" + output);
                }

                processOutputs(_report, outputSubst);
            }
            catch (Exception e)
            {
                throw new PipelineJobException(e);
            }
        }

        protected File inputFile(RReport report, @NotNull ViewContext context) throws Exception
        {
            return new File(report.getReportDir(context.getContainer().getId()), RReport.DATA_INPUT);
        }

        protected void processOutputs(RReport report, List<ParamReplacement> outputSubst) throws Exception
        {
            if (outputSubst.size() > 0)
            {
                // write the output substitution map to disk so we can render the view later
                File reportDir = report.getReportDir(getJob().getContainerId());
                File substitutionMap;

                if (reportDir.getName().equals(getJobIdentifier()))
                {
                    File parentDir = reportDir.getParentFile();
                    // clean up the destination folder
                    for (File file : parentDir.listFiles())
                    {
                        if (!file.isDirectory() && !FileUtil.getExtension(file).equalsIgnoreCase("log"))
                            file.delete();
                    }

                    // rewrite the parameter replacement files to point to the destination folder
                    for (ParamReplacement replacement : outputSubst)
                    {
                        List<File> newFiles = new ArrayList<>();
                        for (File file : replacement.getFiles())
                        {
                            File newFile = new File(parentDir, file.getName());
                            FileUtils.moveFile(file, newFile);
                            newFiles.add(newFile);
                        }

                        replacement.clearFiles();
                        for (File file : newFiles)
                        {
                            replacement.addFile(file);
                        }
                    }

                    // move the remaining files and delete the pipeline specific directory
                    for (File file : reportDir.listFiles())
                    {
                        File newFile = new File(parentDir, file.getName());
                        // special handling for log file
                        if (LOG_FILE_NAME.equalsIgnoreCase(file.getName()))
                        {
                            // report.log == getLogFile(), copy it and update the path (this is the RReportJob case)
                            if (LOG_FILE_NAME.equalsIgnoreCase(getJob().getLogFile().getName()))
                            {
                                newFile = File.createTempFile(LOG_FILE_PREFIX, ".log", parentDir);
                                getJob().setLogFile(newFile);
                                FileUtils.copyFile(file, newFile);
                            }
                            // report.log != getLogFile(), just regular file
                            else
                            {
                                String logFileContenxt = FileUtils.readFileToString(file, DEFAULT_CHARSET);
                                if (!StringUtils.isEmpty(logFileContenxt))
                                    getJob().info("REPORT.LOG CONTENTS:\n" + logFileContenxt);
                                FileUtils.moveFile(file, newFile);
                            }
                        }
                        else
                        {
                            FileUtils.moveFile(file, newFile);
                        }
                    }
                    FileUtils.deleteDirectory(reportDir);
                    substitutionMap = new File(reportDir.getParent(), RReport.SUBSTITUTION_MAP);
                }
                else
                    substitutionMap = new File(reportDir, RReport.SUBSTITUTION_MAP);
                ParamReplacementSvc.get().toFile(outputSubst, substitutionMap);
            }
        }
    }
}
