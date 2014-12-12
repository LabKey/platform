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
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.files.FileContentService;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.ValidationError;
import org.labkey.api.reports.LabkeyScriptEngineManager;
import org.labkey.api.reports.RScriptEngine;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.RserveScriptEngine;
import org.labkey.api.reports.report.r.ParamReplacement;
import org.labkey.api.reports.report.r.ParamReplacementSvc;
import org.labkey.api.reports.report.r.view.KnitrOutput;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.reports.report.view.ScriptReportBean;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.thumbnail.Thumbnail;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspTemplate;
import org.labkey.api.view.ViewContext;
import org.labkey.api.writer.ContainerUser;
import org.labkey.api.writer.DefaultContainerUser;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RReport extends ExternalScriptEngineReport
{
    public static final String TYPE = "ReportService.rReport";
    private static String DEFAULT_APP_PATH;
    public static final String DEFAULT_R_CMD = "CMD BATCH --slave";

    // consider:  move these Rserve specific items to a separate RserveRReport class

    // Use the source command to load in the script. Wrap the command to capture the output.
    // We use .try_quietly instead of try so the R stack is included in the error
    // See http://stackoverflow.com/questions/16879821/save-traceback-on-error-using-trycatch
    // Note that clients can override this command in their rserve script engine settings
    public static final String DEFAULT_RSERVE_CMD = "tools:::.try_quietly(capture.output(source(\"%s\")))";
    public static final String DEFAULT_R_MACHINE = "127.0.0.1";
    public static final int DEFAULT_R_PORT = 6311;

    private RReportDescriptor.KnitrFormat _knitrFormat = null;

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

    //
    // For R reports honor the 'remote' metadata for scriptEngines if provided
    //
    public ScriptEngine getScriptEngine()
    {
        ScriptEngineManager mgr = ServiceRegistry.get().getService(ScriptEngineManager.class);

        if (mgr instanceof LabkeyScriptEngineManager)
            return ((LabkeyScriptEngineManager)mgr).getEngineByExtension("r", requestRemote());

        // bypass the normal discovery mechanism
        return mgr.getEngineByExtension("r");
    }

    private boolean requestRemote()
    {
        Map<String, String> requestedEngineProperties = getDescriptor().getScriptEngineProperties();

        if (requestedEngineProperties.containsKey(RScriptEngine.PROP_REMOTE))
            return Boolean.valueOf(requestedEngineProperties.get(RScriptEngine.PROP_REMOTE));

        return false;
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

    public static String toR(String s)
    {
        String r = PageFlowUtil.jsString(s);
        return "\"" + StringUtils.strip(r, "'") + "\"";
    }

    // static for access by RserveScriptEngine with no backing report
    public static void appendParamList(StringBuilder labkey, Map<String, Object> inputParameters)
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

    private static void appendParam(StringBuilder labkey, String key, String value)
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

    public RReportDescriptor.KnitrFormat getKnitrFormat()
    {
        if (null == _knitrFormat)
        {
            _knitrFormat = RReportDescriptor.KnitrFormat.None;
            ReportDescriptor d = getDescriptor();
            String value = d.getProperty(ScriptReportDescriptor.Prop.knitrFormat);
            if (value != null)
                _knitrFormat = RReportDescriptor.getKnitrFormatFromString(value);
        }

        return _knitrFormat;
    }

   protected String getKnitrBeginChunk()
    {
        if (getKnitrFormat() == RReportDescriptor.KnitrFormat.Html)
            return "<!--begin.rcode labkey, echo=FALSE\n";

        if (getKnitrFormat() == RReportDescriptor.KnitrFormat.Markdown)
            return "```{r labkey, echo=FALSE}\n";

       return "";
    }

    protected String getKnitrEndChunk()
    {
        if (getKnitrFormat() == RReportDescriptor.KnitrFormat.Html)
            return "end.rcode-->\n";

        if (getKnitrFormat() == RReportDescriptor.KnitrFormat.Markdown)
            return "```\n";

        return "";
    }

    protected String getScriptProlog(ScriptEngine engine, ViewContext context, File inputFile, Map<String, Object> inputParameters)
    {
        StringBuilder labkey = new StringBuilder();

        labkey.append(getKnitrBeginChunk());

        if (inputFile != null && inputFile.exists())
        {
            labkey.append("labkey.debug.startReadLabkeyData <- Sys.time();\n");
            labkey.append("labkey.data <- read.table(\"${input_data}\", header=TRUE, sep=\"\\t\", quote=\"\\\"\", comment.char=\"\");\n");
            labkey.append("labkey.debug.endReadLabkeyData <- Sys.time();\n");
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

        // pipeline path to resolve data files in reports if we are using a remote engine
        if (engine instanceof RserveScriptEngine)
        {
            RserveScriptEngine rengine = (RserveScriptEngine) engine;

            File pipelineRoot = getPipelineRoot(context);
            String localPath = getLocalPath(pipelineRoot);
            labkey.append("labkey.pipeline.root <- \"" + localPath + "\"\n");

            // include remote paths so that the client can fixup any file references
            String remotePath = rengine.getRemotePath(pipelineRoot);
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

        labkey.append(getKnitrEndChunk());

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

        if (engine instanceof RserveScriptEngine)
        {
            //
            // if we are using Rserve then we need to replace the input parameters with the remote
            // path to the input file created on the labkey machine
            //
            RserveScriptEngine rengine = (RserveScriptEngine) engine;
            String remotePath = rengine.getRemotePath(inputFile);
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

        if (engine instanceof RserveScriptEngine)
        {
            RserveScriptEngine rengine = (RserveScriptEngine)engine;
            String localPath = getLocalPath(reportDir);
            String remoteRoot = rengine.getRemotePath(localPath);
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
                ContainerUser containerUser = new DefaultContainerUser(getOwningContainer(context), context.getUser());
                Report report = reportId.getReport(containerUser);

                if (validateSharedPermissions(containerUser, report) && RReport.class.isAssignableFrom(report.getClass()))
                {
                    final String rName = report.getDescriptor().getProperty(ReportDescriptor.Prop.reportName);
                    final String rScript = report.getDescriptor().getProperty(ScriptReportDescriptor.Prop.script);
                    final File rScriptFile = new File(getReportDir(), rName + ".R");

                    String includedScript = processScript(engine, context, rScript, inputData, outputSubst, inputParameters);

                    try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(rScriptFile))))
                    {
                        pw.write(includedScript);
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

    /**
     * If this R Report is using knitr then put all the results into the cache directory instead of a temp
     * directory.  This enables knitr to do its own caching.  If this is a non-Knitr report or we don't have a cache
     * directory because the report was not saved yet, then fall back to the non-cache case.  Note that knitr caching
     * is not the same as report caching.  Report caching saves off the output parameters of the report and then serves
     * them up without executing the script again if the incoming URL is the same.
     * For Knitr caching, we always run the R script and let the knitr library manage the caching options.
     */
    @Override
    public File getReportDir()
    {
        File reportDir = null;

        if (getKnitrFormat() != RReportDescriptor.KnitrFormat.None)
            reportDir = getCacheDir();

        if (null == reportDir)
            reportDir = super.getReportDir();

         return reportDir;
    }

    @Override
    public void clearCache()
    {
        if (getKnitrFormat() == RReportDescriptor.KnitrFormat.None)
            super.clearCache();
    }

    @Override
    public boolean shouldCleanup()
    {
        if (getKnitrFormat() == RReportDescriptor.KnitrFormat.None)
            return super.shouldCleanup();

        return false;
    }


    @Override
    public String runScript(ViewContext context, List<ParamReplacement> outputSubst, File inputDataTsv, Map<String, Object> inputParameters) throws ScriptException
    {
        ScriptEngine engine = getScriptEngine();
        if (engine != null)
        {
            try
            {
                Bindings bindings = engine.getBindings(ScriptContext.ENGINE_SCOPE);
                bindings.put(RScriptEngine.KNITR_FORMAT, getKnitrFormat());

                Object output = runScript(engine, context, outputSubst, inputDataTsv, inputParameters);

                if (getKnitrFormat() == RReportDescriptor.KnitrFormat.None)
                {
                    saveConsoleOutput(output, outputSubst);
                }
                else
                {
                    File knitrOutput = new File((String)bindings.get(RScriptEngine.KNITR_OUTPUT));
                    saveKnitrOutput(knitrOutput, outputSubst);
                }

                return output != null ? output.toString() : "";
            }
            catch(Exception e)
            {
                throw new ScriptException(e);
            }
        }

        throw new ScriptException("A script engine implementation was not found for the specified report");
    }

    private void saveKnitrOutput(File knitrOutput, List<ParamReplacement> outputSubst) throws Exception
    {
        KnitrOutput param = new KnitrOutput();
        param.setName("Knitr");
        param.setReport(this);
        param.setFile(knitrOutput);
        outputSubst.add(param);
    }

    private boolean validateSharedPermissions(ContainerUser context, Report report)
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

    private Container getOwningContainer(ViewContext context)
    {
        Container c = context.getContainer();

        // issue 20205 if this is a shared report the sharable scripts should come from the owning container
        if (ReportUtil.isReportInherited(c, this))
        {
            Container base = ContainerManager.getForId(this.getContainerId());
            if (base != null)
                c = base;
        }
        return c;
    }

    public List<Report> getAvailableSharedScripts(ViewContext context, ScriptReportBean bean) throws Exception
    {
        List<Report> scripts = new ArrayList<>();

        String reportKey = ReportUtil.getReportKey(bean.getSchemaName(), bean.getQueryName());
        String reportName = bean.getReportName();
        Container c = getOwningContainer(context);

        for (Report r : ReportUtil.getReports(c, context.getUser(), StringUtils.isBlank(reportKey) ? null : reportKey, true))
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
    public String getStaticThumbnailPath()
    {
        return "/report/RLogo.jpg";
    }

    @Override
    public boolean supportsDynamicThumbnail()
    {
        return true;
    }

    @Override
    public Thumbnail generateThumbnail(@Nullable ViewContext context)
    {
        if (null != context)
            return getThumbnail(context);
        else
            return null;
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

    @Nullable
    @Override
    public String getEditAreaSyntax()
    {
        return "text/x-rsrc";
    }
}

