/*
 * Copyright (c) 2007-2011 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.r.ParamReplacement;
import org.labkey.api.reports.report.view.AjaxRunScriptReportView;
import org.labkey.api.reports.report.view.AjaxScriptReportView.Mode;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.reports.report.view.RunRReportView;
import org.labkey.api.reports.report.view.ScriptReportBean;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspTemplate;
import org.labkey.api.view.TabStripView;
import org.labkey.api.view.ViewContext;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class RReport extends ExternalScriptEngineReport implements AttachmentParent
{
    public static final String TYPE = "ReportService.rReport";
    private static String DEFAULT_APP_PATH;

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

    public static synchronized String getDefaultRPath()
    {
        if (DEFAULT_APP_PATH == null)
        {
            DEFAULT_APP_PATH = getDefaultAppPath(new FilenameFilter()
            {
                public boolean accept(File dir, String name)
                {
                    return "r.exe".equalsIgnoreCase(name) || "r".equalsIgnoreCase(name);
                }
            });
        }

        return DEFAULT_APP_PATH;
    }

    protected String getScriptProlog(ViewContext context, File inputFile)
    {
        StringBuilder labkey = new StringBuilder();

        if (inputFile != null && inputFile.exists())
        {
            labkey.append("labkey.data <- read.table(\"${input_data}\", header=TRUE, sep=\"\\t\", quote=\"\", comment.char=\"\")\n");
        }

        labkey.append("labkey.url <- function (controller, action, list){paste(labkey.url.base,controller,labkey.url.path,action,\".view?\",paste(names(list),list,sep=\"=\",collapse=\"&\"),sep=\"\")}\n" +
            "labkey.resolveLSID <- function(lsid){paste(labkey.url.base,\"experiment/resolveLSID.view?lsid=\",lsid,sep=\"\");}\n");
        labkey.append("labkey.user.email=\"").append(context.getUser().getEmail()).append("\"\n");

        ActionURL url = context.getActionURL();
        labkey.append("labkey.url.path=\"").append(url.getExtraPath()).append("/\"\n");
        labkey.append("labkey.url.base=\"").append(url.getBaseServerURI()).append(context.getContextPath()).append("/\"\n");

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
        {
            labkey.append("labkey.url.params <- NULL\n");
        }

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


    public void setScriptSource(String script)
    {
        getDescriptor().setProperty(ScriptReportDescriptor.Prop.script, script);
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
                    final String rScript = report.getDescriptor().getProperty(ScriptReportDescriptor.Prop.script);
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
                {
                    return true;
                }
            }
        }

        return false;
    }

    public HttpView getRunReportView(ViewContext context) throws Exception
    {
        // TODO: Move to ScriptReport?
        String tabId = (String)context.get("tabId");

        if (null == tabId)
            tabId = context.getActionURL().getParameter("tabId");

        Mode mode = ("Source".equals(tabId) ? Mode.update : Mode.view);
        return new AjaxRunScriptReportView(this, mode);
    }

    public ActionURL getEditReportURL(ViewContext context)
    {
        if (getDescriptor().canEdit(context.getUser(), context.getContainer()))
        {
            return ReportUtil.getRunReportURL(context, this).addParameter(TabStripView.TAB_PARAM, RunRReportView.TAB_SOURCE);
        }
        return null;
    }

    public List<Report> getAvailableSharedScripts(ViewContext context, ScriptReportBean bean) throws Exception
    {
        List<Report> scripts = new ArrayList<Report>();

        String reportKey = ReportUtil.getReportKey(bean.getSchemaName(), bean.getQueryName());
        String reportName = bean.getReportName();

        for (Report r : ReportUtil.getReports(context.getContainer(), context.getUser(), reportKey, true))
        {
            // List only R scripts.  TODO: Need better, more general mechanism for this
            if (!RReportDescriptor.class.isAssignableFrom(r.getDescriptor().getClass()))
                continue;

            if (reportName == null || !reportName.equals(r.getDescriptor().getProperty(ReportDescriptor.Prop.reportName)))
                scripts.add(r);
        }

        return scripts;
    }

    public String getDownloadDataHelpMessage()
    {
        return "LabKey Server automatically exports query data into a data frame called \"labkey.data\". You can download the data via this link to help with the development of your R script.";
    }

    public @Nullable String getDesignerHelpHtml()
    {
        try
        {
            return new JspTemplate("/org/labkey/api/reports/report/view/rReportDesignerSyntaxRef.jsp").render();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getDefaultScript()
    {
        return "# This sample code returns the query data in tab-separated values format, which LabKey then\n" +
               "# renders as HTML. Replace this code with your R script. See the Help tab for more details.\n" +
               "write.table(labkey.data, file = \"${tsvout:tsvfile}\", sep = \"\\t\", qmethod = \"double\", col.names=NA)\n";
    }
}

