/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.r.ParamReplacement;
import org.labkey.api.reports.report.r.ParamReplacementSvc;
import org.labkey.api.reports.report.view.RReportBean;
import org.labkey.api.util.DateUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.view.ViewContext;

import java.io.File;
import java.io.Serializable;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Jun 11, 2007
 */
public class RReportJob extends PipelineJob implements Serializable
{
    private static final Logger _log = Logger.getLogger(RReportJob.class);
    public static final String PROCESSING_STATUS = "Processing";
    public static final String LOG_FILE_NAME = "report.log";
    private int _reportId = -1;
    private ActionURL _status = null;
    private RReportBean _form;

    public RReportJob(String provider, ViewBackgroundInfo info, int reportId) throws SQLException
    {
        super(provider, info);
        _reportId = reportId;
        init();
    }

    public RReportJob(String provider, ViewBackgroundInfo info, RReportBean form) throws Exception
    {
        super(provider, info);
        _form = form;
        init();
    }

    private void init()
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
        File statusFile = getStatusFile();
        if (statusFile != null && _reportId != -1)
        {
            return new ActionURL("reports", "runReport", getContainer()).
                    addParameter(ReportDescriptor.Prop.reportId.name(), _reportId);
/*
            ActionURL url = new ActionURL("reports", "renderBackgroundRReport", getContainer());
            url.addParameter("path", statusFile.getAbsolutePath().replace("\\", "/"));
            url.addParameter("reportId", _reportId);
            return url;
*/
        }
        return null;
    }

    public String getDescription()
    {
        String description = getReport().getDescriptor().getProperty("reportName");

        if (StringUtils.isEmpty(description))
            description = "Unknown R Report";

        return description;
    }

    private RReport getReport()
    {
        try {
            Report report = null;
            if (_reportId != -1)
                report = ReportService.get().getReport(_reportId);
            else if (_form != null)
                report = _form.getReport();
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
        try {
            // get the input file which should have been previously created
            File inputFile = new File(report.getReportDir(), RReport.DATA_INPUT);
            ViewContext context = new ViewContext(getInfo());

            if (inputFile != null && inputFile.exists())
            {
                List<ParamReplacement> outputSubst = new ArrayList();
                List<String> errors = new ArrayList();

                String output = report.runScript(context, outputSubst, inputFile);
                if (!StringUtils.isEmpty(output))
                    info(output);

                if (outputSubst.size() > 0)
                {
                    // write the output substitution map to disk so we can render the view later
                    File file = new File(report.getReportDir(), RReport.SUBSTITUTION_MAP);
                    ParamReplacementSvc.get().toFile(outputSubst, file);
                }
                setStatus(PipelineJob.COMPLETE_STATUS, "Job finished at: " + DateUtil.nowISO());
            }
            else
            {
                setStatus(PipelineJob.ERROR_STATUS, "Job finished at: " + DateUtil.nowISO());
            }
        }
        catch (Exception e)
        {
            _log.error("Error occurred running the report background job", e);
            error("Error occurred running the report background job", e);
            setStatus(PipelineJob.ERROR_STATUS, "Job finished at: " + DateUtil.nowISO());
        }
    }
}
