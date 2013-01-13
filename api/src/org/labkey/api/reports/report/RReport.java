/*
 * Copyright (c) 2007-2013 LabKey Corporation
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
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.files.FileContentService;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.ValidationError;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.RserveScriptEngine;
import org.labkey.api.reports.report.r.ParamReplacement;
import org.labkey.api.reports.report.r.ParamReplacementSvc;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.reports.report.view.ScriptReportBean;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AppProps;
import org.labkey.api.thumbnail.DynamicThumbnailProvider;
import org.labkey.api.thumbnail.Thumbnail;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspTemplate;
import org.labkey.api.view.ViewContext;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RReport extends ExternalScriptEngineReport implements DynamicThumbnailProvider
{
    public static final String TYPE = "ReportService.rReport";
    private static String DEFAULT_APP_PATH;
    public static final String DEFAULT_R_CMD = "CMD BATCH --slave";

    // consider:  move these Rserve specific items to a separate RserveRReport class
    public static final String DEFAULT_R_MACHINE = "127.0.0.1";
    public static final int DEFAULT_R_PORT = 6311;

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

    public String toR(String s)
    {
        String r = PageFlowUtil.jsString(s);
        return "\"" + StringUtils.strip(r, "'") + "\"";
    }

    private void appendParamList(StringBuilder labkey, Map<String, Object> inputParameters)
    {
        String sep = "";
        Set<Map.Entry<String, Object>> setParameters = inputParameters.entrySet();
        for (Map.Entry<String, Object> param : setParameters)
        {
            labkey.append(sep);
            appendParam(labkey, param.getKey(), String.valueOf(param.getValue()));
            sep = ",";
        }
    }

    // todo: remove this overload when we no longer need to pull parameters off the URL
    private void appendParamList(StringBuilder labkey, List<Pair<String,String>> inputParameters)
    {
        String sep = "";
        for (Pair<String, String> param : inputParameters)
        {
            labkey.append(sep);
            appendParam(labkey, param.getKey(), param.getValue());
            sep = ",";
        }
    }

    private void appendParam(StringBuilder labkey, String key, String value)
    {
        labkey.append(toR(key));
        labkey.append("=");
        labkey.append(toR(value));
    }

    private boolean hasParameters(Map<String, Object> inputParameters, List<Pair<String, String>> urlParameters)
    {
        // if inputParameters were explicitly passed in then override any url parameters we may have
        if (inputParameters != null)
            return (inputParameters.size() > 0);

        //
        // todo:  remove this fallback code when we refactor report execution.  Input parameters should be passed
        // in so that they are not taken off the URL.  Note that if inputParamters is not null but size 0 do not fallback
        //
        if (urlParameters != null)
           return (urlParameters.size() > 0);

        return false;
    }

    protected String getScriptProlog(ScriptEngine engine, ViewContext context, File inputFile, Map<String, Object> inputParameters)
    {
        StringBuilder labkey = new StringBuilder();

        if (inputFile != null && inputFile.exists())
        {
            labkey.append("labkey.data <- read.table(\"${input_data}\", header=TRUE, sep=\"\\t\", quote=\"\\\"\", comment.char=\"\")\n");
        }

        labkey.append("labkey.url <- function (controller, action, list){paste(labkey.url.base,controller,labkey.url.path,action,\".view?\",paste(names(list),list,sep=\"=\",collapse=\"&\"),sep=\"\")}\n" +
            "labkey.resolveLSID <- function(lsid){paste(labkey.url.base,\"experiment/resolveLSID.view?lsid=\",lsid,sep=\"\");}\n");
        labkey.append("labkey.user.email=").append(toR(context.getUser().getEmail())).append("\n");

        ActionURL url = context.getActionURL();
        labkey.append("labkey.url.path=").append(toR(url.getExtraPath() + "/")).append("\n");
        labkey.append("labkey.url.base=").append(toR(url.getBaseServerURI() + context.getContextPath() + "/")).append("\n");

        //
        // todo:  remove the fallback to URL parameters off the query string when we explicitly pass in parameters in the
        // render case
        //
        List<Pair<String,String>> urlParameters = url.getParameters();
        if (hasParameters(inputParameters, urlParameters))
        {
            labkey.append("labkey.url.params <- list(");

            if (inputParameters != null)
                appendParamList(labkey, inputParameters);
            else
                appendParamList(labkey, urlParameters);

            labkey.append(")\n");
        }
        else
        {
            labkey.append("labkey.url.params <- NULL\n");
        }

        // Root path to resolve system files in reports
        File root = ServiceRegistry.get(FileContentService.class).getFileRoot(context.getContainer(), FileContentService.ContentType.files);
        if (root != null)
        {
            labkey.append("labkey.file.root <- \"" + root.getPath().replaceAll("\\\\", "/") +"\"\n");
        }
        else
        {
            labkey.append("labkey.file.root <- NULL\n");
        }

        // Root path to resolve pipeline files in reports
        root = ServiceRegistry.get(FileContentService.class).getFileRoot(context.getContainer(), FileContentService.ContentType.pipeline);
        if (root != null)
        {
            labkey.append("labkey.pipeline.root <- \"" + root.getPath().replaceAll("\\\\", "/") + "\"\n");
        }
        else
        {
            labkey.append("labkey.pipeline.root <- NULL\n");
        }

        // pipeline path to resolve data files in reports
        if (AppProps.getInstance().isExperimentalFeatureEnabled(AppProps.EXPERIMENTAL_RSERVE_REPORTING))
        {
            RserveScriptEngine rengine = (RserveScriptEngine) engine;

            String localPath = getLocalPath(getPipelineRoot(context));
            labkey.append("labkey.pipeline.root <- \"" + localPath + "\"\n");

            // include remote paths so that the client can fixup any file references
            String remotePath = rengine.getRemotePipelinePath(localPath);
            labkey.append("labkey.remote.pipeline.root <- \"" + remotePath + "\"\n");
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

    // append the pipeline roots to the prolog
    public File getPipelineRoot(ViewContext context)
    {
        //
        // currently we ignore the supplemental directory and only return the primary directory/override
        // if the supplemental directory is important then consider making this a list
        //
        PipeRoot pipelineRoot = PipelineService.get().findPipelineRoot(context.getContainer());
        return pipelineRoot.getRootPath();
    }

    public void setScriptSource(String script)
    {
        getDescriptor().setProperty(ScriptReportDescriptor.Prop.script, script);
    }

    public static String getLocalPath(File f)
    {
        File fAbsolute = FileUtil.getAbsoluteCaseSensitiveFile(f);
        return fAbsolute.getAbsolutePath().replaceAll("\\\\", "/");
    }

    @Override
    protected String processInputReplacement(ScriptEngine engine, String script, File inputFile) throws Exception
    {
        String localPath = getLocalPath(inputFile);
        String scriptOut = null;

        if (AppProps.getInstance().isExperimentalFeatureEnabled(AppProps.EXPERIMENTAL_RSERVE_REPORTING))
        {
            //
            // if we are using Rserve then we need to replace the input parameters with the remote
            // path to the input file created on the labkey machine
            //
            RserveScriptEngine rengine = (RserveScriptEngine) engine;
            String remotePath = rengine.getRemoteReportPath(localPath);
            scriptOut = ParamReplacementSvc.get().processInputReplacement(script, INPUT_FILE_TSV, remotePath);
        }
        else
        {
            scriptOut = ParamReplacementSvc.get().processInputReplacement(script, INPUT_FILE_TSV, localPath);
        }

        return scriptOut;
    }

    @Override
    protected String processOutputReplacements(ScriptEngine engine, String script, List<ParamReplacement> replacements) throws Exception
    {
        File reportDir = getReportDir();
        String scriptOut = null;

        if (AppProps.getInstance().isExperimentalFeatureEnabled(AppProps.EXPERIMENTAL_RSERVE_REPORTING))
        {
            RserveScriptEngine rengine = (RserveScriptEngine)engine;
            String localPath = getLocalPath(reportDir);
            String remoteRoot = rengine.getRemoteReportPath(localPath);
            scriptOut = ParamReplacementSvc.get().processParamReplacement(script, reportDir, remoteRoot, replacements);
        }
        else
        {
            scriptOut = ParamReplacementSvc.get().processParamReplacement(script, reportDir, null, replacements);
        }

        return scriptOut;
    }

    protected String createScript(ScriptEngine engine, ViewContext context, List<ParamReplacement> outputSubst, File inputDataTsv, Map<String, Object> inputParameters) throws Exception
    {
        String script = super.createScript(engine, context, outputSubst, inputDataTsv, inputParameters);
        File inputData = new File(getReportDir(), DATA_INPUT);

        /**
         * for each included script, the source script is processed for input/output replacements
         * and the result copied into this scripts working directory so it can be loaded via the source command
         */
        for (String includedReport : ((RReportDescriptor)getDescriptor()).getIncludedReports())
        {
            ReportIdentifier reportId = ReportService.get().getReportIdentifier(includedReport);

            if (reportId != null)
            {
                Report report = reportId.getReport(context);

                if (validateSharedPermissions(context, report) && RReport.class.isAssignableFrom(report.getClass()))
                {
                    final String rName = report.getDescriptor().getProperty(ReportDescriptor.Prop.reportName);
                    final String rScript = report.getDescriptor().getProperty(ScriptReportDescriptor.Prop.script);
                    final File rScriptFile = new File(getReportDir(), rName + ".R");

                    String includedScript = processScript(engine, context, rScript, inputData, outputSubst, inputParameters);

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

    @Override
    public Thumbnail getStaticThumbnail()
    {
        InputStream is = RReport.class.getResourceAsStream("Rlogo.jpg");
        return new Thumbnail(is, "image/jpeg");
    }

    @Override
    public String getStaticThumbnailCacheKey()
    {
        return "Reports:RReportStatic";
    }

    @Override
    public Thumbnail generateDynamicThumbnail(@Nullable ViewContext context)
    {
        if (null != context)
            return getThumbnail(context);
        else
            return null;
    }

    @Override
    public String getDynamicThumbnailCacheKey()
    {
        return "Reports:" + getReportId();
    }

    @Override
    public boolean canEdit(User user, Container container, List<ValidationError> errors)
    {
        // hack : prevent editing on module R reports, but for long term we probably want
        // to create a module R report and handle the permission checking directly.
        if (getDescriptorType().equals(ModuleRReportDescriptor.TYPE))
            return false;
        
        return super.canEdit(user, container, errors);    //To change body of overridden methods use File | Settings | File Templates.
    }
}

