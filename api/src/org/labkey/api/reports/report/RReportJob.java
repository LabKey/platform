/*
 * Copyright (c) 2007-2014 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.report.r.ParamReplacement;
import org.labkey.api.reports.report.r.ParamReplacementSvc;
import org.labkey.api.reports.report.view.RReportBean;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.view.ViewContext;

import java.io.File;
import java.io.Serializable;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * User: Karl Lum
 * Date: Jun 11, 2007
 */
public class RReportJob extends PipelineJob implements Serializable
{
    private static final Logger _log = Logger.getLogger(RReportJob.class);
    public static final String PROCESSING_STATUS = "Processing";
    public static final String LOG_FILE_NAME = "report.log";
    private ReportIdentifier _reportId;
    private RReportBean _form;

    public RReportJob(String provider, ViewBackgroundInfo info, ReportIdentifier reportId, PipeRoot root) throws SQLException
    {
        super(provider, info, root);
        _reportId = reportId;
        init();
    }

    public RReportJob(String provider, ViewBackgroundInfo info, RReportBean form, PipeRoot root) throws Exception
    {
        super(provider, info, root);
        _form = form;
        init();
    }

    protected void init()
    {
        Report report = getReport();

        if (report instanceof RReport)
        {
            File logFile = new File(((RReport)report).getReportDir(), LOG_FILE_NAME);
            this.setLogFile(logFile);
        }
    }

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

    public String getDescription()
    {
        String description = getReport().getDescriptor().getProperty(ReportDescriptor.Prop.reportName);

        if (StringUtils.isEmpty(description))
            description = "Unknown R Report";

        return description;
    }

    protected RReport getReport()
    {
        try
        {
            Report report = null;
            if (_reportId != null)
                report = _reportId.getReport(getInfo());
            else if (_form != null)
                report = _form.getReport(getInfo());
            if (report instanceof RReport)
                return (RReport)report;

            throw new RuntimeException("The report is not a valid instance of an RReport");
        }
        catch (Exception e)
        {
            throw new RuntimeException("Exception retrieving report", e);
        }
    }

    public void run()
    {
        setStatus(PROCESSING_STATUS, "Job started at: " + DateUtil.nowISO());
        RReport report = getReport();
        info("Running R report job '" + report.getDescriptor().getReportName() + "'");

        if (HttpView.hasCurrentView())
        {
            runReport(HttpView.currentContext(), report);
        }
        else
        {
            // Must be a background thread... push a fake ViewContext on the HttpView stack if so HttpView.currentContext() succeeds.
            try (ViewContext.StackResetter resetter = ViewContext.pushMockViewContext(getUser(), getContainer(), getActionURL()))
            {
                runReport(resetter.getContext(), report);
            }
        }
    }

    private void runReport(ViewContext context, RReport report)
    {
        try
        {
            // get the input file which should have been previously created
            File inputFile = inputFile(report, context);

            if (inputFile.exists())
            {
                List<ParamReplacement> outputSubst = new ArrayList<>();

                // todo: figure out a way to pass script input parameters for a script job if needed.
                String output = report.runScript(context, outputSubst, inputFile, null);
                if (!StringUtils.isEmpty(output))
                    info(output);

                processOutputs(report, outputSubst);
                setStatus(TaskStatus.complete, "Job finished at: " + DateUtil.nowISO());
            }
            else
            {
                setStatus(TaskStatus.error, "Job finished at: " + DateUtil.nowISO());
            }
        }
        catch (Exception e)
        {
            _log.error("Error occurred running the report background job", e);
            error("Error occurred running the report background job", e);
            setStatus(TaskStatus.error, "Job finished at: " + DateUtil.nowISO());
        }
    }

    protected File inputFile(RReport report, ViewContext context) throws Exception
    {
        return new File(report.getReportDir(), RReport.DATA_INPUT);
    }

    protected void processOutputs(RReport report, List<ParamReplacement> outputSubst) throws Exception
    {
        if (outputSubst.size() > 0)
        {
            // write the output substitution map to disk so we can render the view later
            File file = new File(report.getReportDir(), RReport.SUBSTITUTION_MAP);
            ParamReplacementSvc.get().toFile(outputSubst, file);
        }
    }
}
