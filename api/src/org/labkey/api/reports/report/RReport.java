/*
 * Copyright (c) 2007-2009 LabKey Corporation
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

import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.labkey.api.admin.AdminUrls;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.data.*;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.*;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.r.ParamReplacement;
import org.labkey.api.reports.report.r.ParamReplacementSvc;
import org.labkey.api.reports.report.r.view.*;
import org.labkey.api.reports.report.view.ReportQueryView;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.reports.report.view.RunRReportView;
import org.labkey.api.reports.report.view.RunReportView;
import org.labkey.api.security.UserManager;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.view.*;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.common.util.Pair;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.io.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RReport extends ExternalScriptEngineReport implements AttachmentParent
{
    public static final String TYPE = "ReportService.rReport";

    public static final String DEFAULT_R_CMD = "CMD BATCH --slave";

    public String getType()
    {
        return TYPE;
    }

    public String getTypeDescription()
    {
        return "R View";
    }

    public String getDescriptorType()
    {
        return RReportDescriptor.TYPE;
    }

    public static boolean isEnabled()
    {
        ScriptEngineManager mgr = ServiceRegistry.get().getService(ScriptEngineManager.class);
        return mgr.getEngineByExtension("r") != null;
    }

    public ScriptEngine getScriptEngine()
    {
        ScriptEngineManager mgr = ServiceRegistry.get().getService(ScriptEngineManager.class);

        // bypass the normal discovery mechanism
        return mgr.getEngineByExtension("r");
    }

    protected String getScriptProlog(ViewContext context)
    {
        StringBuffer labkey = new StringBuffer();
        labkey.append("labkey.data <- read.table(\"${input_data}\", header=TRUE, sep=\"\\t\", quote=\"\", comment.char=\"\")\n" +
            "labkey.url <- function (controller, action, list){paste(labkey.url.base,controller,labkey.url.path,action,\".view?\",paste(names(list),list,sep=\"=\",collapse=\"&\"),sep=\"\")}\n" +
            "labkey.resolveLSID <- function(lsid){paste(labkey.url.base,\"experiment/resolveLSID.view?lsid=\",lsid,sep=\"\");}\n");
        labkey.append("labkey.user.email=\"" + context.getUser().getEmail() + "\"\n");

        ActionURL url = context.getActionURL();
        labkey.append("labkey.url.path=\"" + url.getExtraPath() + "/\"\n");
        labkey.append("labkey.url.base=\"" + url.getBaseServerURI() + context.getContextPath() + "/\"\n");

        // url parameters
        Pair<String, String>[] params = url.getParameters();
        if (params.length > 0)
        {
            String sep = "";
            labkey.append("labkey.url.params <- list(");
            for (Pair<String, String> param : params)
            {
                labkey.append(sep);
                labkey.append("\"");
                labkey.append(param.getKey());
                labkey.append("\"");
                labkey.append("=");
                labkey.append("\"");
                labkey.append(param.getValue());
                labkey.append("\"");
                sep = ",";
            }
            labkey.append(")\n");
        }
        else
            labkey.append("labkey.url.params <- NULL\n");

        // session information
        if (context.getRequest() != null)
        {
            labkey.append("labkey.sessionCookieName = \"JSESSIONID\"\n");
            labkey.append("labkey.sessionCookieContents = \"");
            labkey.append(PageFlowUtil.getCookieValue(context.getRequest().getCookies(), "JSESSIONID", ""));
            labkey.append("\"\n");
        }
        return labkey.toString();
    }

    protected String createScript(ViewContext context, List<ParamReplacement> outputSubst, File inputDataTsv) throws Exception
    {
        String script = super.createScript(context, outputSubst, inputDataTsv);
        File inputData = new File(getReportDir(), DATA_INPUT);

        /**
         * for each included script, the source script is process for input/output replacements
         * and the result copied into this scripts working directory so it can be loaded via the source command
         */
        for (String includedReport : ((RReportDescriptor)getDescriptor()).getIncludedReports())
        {
            ReportIdentifier reportId = ReportService.get().getReportIdentifier(includedReport);
            if (reportId != null)
            {
                Report report = reportId.getReport();

                if (validateSharedPermissions(context, report) && RReport.class.isAssignableFrom(report.getClass()))
                {
                    final String rName = report.getDescriptor().getProperty(ReportDescriptor.Prop.reportName);
                    final String rScript = report.getDescriptor().getProperty(RReportDescriptor.Prop.script);
                    final File rScriptFile = new File(getReportDir(), rName + ".R");

                    String includedScript = processScript(context, rScript, inputData, outputSubst);

                    try
                    {
                        PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(rScriptFile)));
                        pw.write(includedScript);
                        pw.close();
                    }
                    catch(IOException e)
                    {
                        ExceptionUtil.logExceptionToMothership(null, e);
                    }
                }
            }
        }
        return script;
    }

    private boolean validateSharedPermissions(ViewContext context, Report report)
    {
        if (report != null)
        {
            if (ReportUtil.canReadReport(report, context.getUser()))
            {
                // if it's not in this container, check that it was shared
                if (!context.getContainer().getId().equals(report.getDescriptor().getContainerId()))
                {
                    return ReportUtil.isReportInherited(context.getContainer(), report);
                }
                else
                    return true;
            }
        }
        return false;
    }

    public HttpView getRunReportView(ViewContext context) throws Exception
    {
        return new RunRReportView(this);
    }

    public ActionURL getEditReportURL(ViewContext context)
    {
        if (getDescriptor().canEdit(context))
        {
            return ReportUtil.getRunReportURL(context, this).addParameter(TabStripView.TAB_PARAM, RunRReportView.TAB_SOURCE);
        }
        return null;
    }
}

