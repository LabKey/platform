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

package org.labkey.api.reports.report;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.action.LabKeyError;
import org.labkey.api.assay.DefaultDataTransformer;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.JdbcType;
import org.labkey.api.files.FileContentService;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.SimpleValidationError;
import org.labkey.api.query.ValidationError;
import org.labkey.api.reports.ExternalScriptEngine;
import org.labkey.api.reports.ExternalScriptEngineDefinition;
import org.labkey.api.reports.LabKeyScriptEngine;
import org.labkey.api.reports.LabKeyScriptEngineManager;
import org.labkey.api.reports.RScriptEngine;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.RserveScriptEngine;
import org.labkey.api.reports.report.r.ParamReplacement;
import org.labkey.api.reports.report.r.ParamReplacementSvc;
import org.labkey.api.reports.report.r.view.KnitrOutput;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.reports.report.view.ScriptReportBean;
import org.labkey.api.rstudio.RStudioService;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.thumbnail.Thumbnail;
import org.labkey.api.util.CSRFUtil;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspTemplate;
import org.labkey.api.view.ViewContext;
import org.labkey.api.writer.ContainerUser;
import org.labkey.api.writer.DefaultContainerUser;
import org.labkey.api.writer.PrintWriters;
import org.springframework.validation.BindException;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
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

    private boolean _useDefaultOutputOptions = true;

    @Override
    public String getType()
    {
        return TYPE;
    }

    @Override
    public String getTypeDescription()
    {
        return "R Report";
    }

    @Override
    public String getDescriptorType()
    {
        return null==_descriptor ? RReportDescriptor.TYPE : getDescriptor().getDescriptorType();
    }

    @Override
    public ScriptReportDescriptor getDescriptor()
    {
        return super.getDescriptor();
    }

    public static boolean isEnabled()
    {
        LabKeyScriptEngineManager mgr = LabKeyScriptEngineManager.get();
        return !mgr.getEngineDefinitions(ExternalScriptEngineDefinition.Type.R).isEmpty();
    }

    //
    // For R reports honor the 'remote' metadata for scriptEngines if provided
    //
    @Override
    public ScriptEngine getScriptEngine(Container c)
    {
        LabKeyScriptEngineManager mgr = LabKeyScriptEngineManager.get();
        Container srcContainer = c;

        if (getDescriptor().isInherited(c))
        {
            // if this is an inherited report, get the engine scoped to the container that the report
            // was defined in
            srcContainer = ContainerManager.getForId(getContainerId()) != null ? ContainerManager.getForId(getContainerId()) : c;
        }
        return mgr.getEngineByExtension(srcContainer, "r", LabKeyScriptEngineManager.EngineContext.report);
    }

    @Nullable
    private RStudioService getRStudioService()
    {
        RStudioService rs = RStudioService.get();
        if (null != rs && rs.isEditInRStudioAvailable())
            return rs;
        else return null;
    }

    @Override
    public Map<String, Object> getExternalEditorConfig(ViewContext viewContext)
    {
        RStudioService rs = getRStudioService();
        if (rs != null)
        {
           return rs.getRStudioEditorConfig(viewContext, this);
        }
        return null;
    }

    @Override
    public Pair<String, String> startExternalEditor(ViewContext context, String script, BindException errors)
    {
        Pair<String, String> externalEditor = null;
        RStudioService rs = getRStudioService();
        if (null != rs)
        {
            try
            {
                externalEditor = rs.editInRStudio(this, context, errors);
            }
            catch (Exception e)
            {
                errors.addError(new LabKeyError(e));
            }

            if (errors.hasErrors())
                externalEditor = null;
        }
        return externalEditor;
    }

    @Override
    public void saveFromExternalEditor(ContainerUser context, String script)
    {
        getDescriptor().setProperty(ScriptReportDescriptor.Prop.script, this.stripScriptProlog(script));
        ReportService.get().saveReport(context, this.getDescriptor().getReportKey(), this);
    }

    // legacy method used to provide rserve hints from module based reports, note this property was never exposed
    // in the UI
    @Deprecated
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
            DEFAULT_APP_PATH = getDefaultAppPath((dir, name) -> "r.exe".equalsIgnoreCase(name) || "r".equalsIgnoreCase(name));
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
        // in so that they are not taken off the URL.  Note that if inputParameters is not null but size 0 do not fallback
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

    public boolean isUseDefaultOutputOptions()
    {
        ReportDescriptor d = getDescriptor();
        String value = d.getProperty(ScriptReportDescriptor.Prop.useDefaultOutputFormat);
        if (value == null)
            return true;
        return (Boolean)JdbcType.BOOLEAN.convert(value);
    }

    public String getRmarkdownOutputOptions()
    {
        ReportDescriptor d = getDescriptor();
        return d.getProperty(ScriptReportDescriptor.Prop.rmarkdownOutputOptions);
    }

    public String getKnitrBeginChunk()
    {
        if (getKnitrFormat() == RReportDescriptor.KnitrFormat.Html)
            return "<!--begin.rcode labkey, echo=FALSE\n";

        if (getKnitrFormat() == RReportDescriptor.KnitrFormat.Markdown)
            return "```{r labkey, echo=FALSE}\n";

       return "";
    }

    public String getKnitrEndChunk()
    {
        if (getKnitrFormat() == RReportDescriptor.KnitrFormat.Html)
            return "end.rcode-->\n";

        if (getKnitrFormat() == RReportDescriptor.KnitrFormat.Markdown)
            return "```\n";

        return "";
    }

    @Override
    protected String getScriptProlog(ScriptEngine engine, ViewContext context, File inputFile, Map<String, Object> inputParameters)
    {
        return getScriptProlog(engine, context, inputFile, inputParameters, false);
    }

    protected String getScriptProlog(ScriptEngine engine, ViewContext context, File inputFile, Map<String, Object> inputParameters, boolean isRStudio)
    {
        StringBuilder labkey = new StringBuilder();

        labkey.append(getKnitrBeginChunk());

        if (inputFile != null && inputFile.exists())
        {
            labkey.append("labkey.debug.startReadLabkeyData <- Sys.time();\n");
            labkey.append("# ${input_data:inputFileTsv}\n"); // labkey R comment syntax, not actual comment, don't drop
            labkey.append("labkey.data <- read.table(\"inputFileTsv\", header=TRUE, sep=\"\\t\", quote=\"\\\"\", comment.char=\"\");\n");
            labkey.append("labkey.debug.endReadLabkeyData <- Sys.time();\n");
        }
        else if (isRStudio)
        {
            RStudioService rs = getRStudioService();
            if (rs != null)
            {
                labkey.append(rs.getInputDataProlog(context, this));
            }
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

        if (!isRStudio)
        {
            // Root path to resolve system files in reports
            Path root = FileContentService.get().getFileRootPath(context.getContainer(), FileContentService.ContentType.files);
            if (root != null)
            {
                labkey.append("labkey.file.root <- \"").append(FileUtil.getAbsolutePath(root).replaceAll("\\\\", "/")).append("\"\n");
            }
            else
            {
                labkey.append("labkey.file.root <- NULL\n");
            }

            // Root path to resolve pipeline files in reports
            root = FileContentService.get().getFileRootPath(context.getContainer(), FileContentService.ContentType.pipeline);
            if (root != null)
            {
                labkey.append("labkey.pipeline.root <- \"").append(FileUtil.getAbsolutePath(root).replaceAll("\\\\", "/")).append("\"\n");
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
                labkey.append("labkey.pipeline.root <- \"").append(localPath).append("\"\n");

                // include remote paths so that the client can fixup any file references
                String remotePath = rengine.getRemotePath(pipelineRoot);
                labkey.append("labkey.remote.pipeline.root <- \"").append(remotePath).append("\"\n");
            }

            // The ${apikey} token will be replaced by the value in the map stashed in script context bindings ExternalScriptEngine.PARAM_REPLACEMENT_MAP
            // CONSIDER: Should we use: labkey.setDefaults(apiKey=\"${apikey}\")
            labkey.append("labkey.apiKey <- \"${" + SecurityManager.API_KEY + "}\"\n");
        }

        labkey.append(getKnitrEndChunk());

        return labkey.toString();
    }

    @Override
    protected String concatScriptProlog(ScriptEngine engine, ViewContext context, String script, File inputFile, Map<String, Object> inputParameters, boolean isRStudio)
    {
        String yamlScript = "";
        String yamlSyntaxPrefix = "---\n";
        if (getKnitrFormat() != null && script.startsWith(yamlSyntaxPrefix))
        {
            yamlScript = script.substring(0, script.indexOf(yamlSyntaxPrefix, yamlSyntaxPrefix.length()) + yamlSyntaxPrefix.length());
            script = script.substring(yamlScript.length());
        }

        String ret;
        ret =
            yamlScript +
            (isRStudio ? (getCommentLineStartEnd(false) + "8<" + getCommentLineStartEnd(true)) : "") +
            StringUtils.defaultString(getScriptProlog(engine, context, inputFile, inputParameters, isRStudio)) +
            (isRStudio ? (getCommentLineStartEnd(false) + ">8" + getCommentLineStartEnd(true)) : "") +
            script;
        return ret;
    }

    public String getCommentLineStartEnd(boolean isEnd)
    {
        boolean isKnitr = getKnitrFormat() == RReportDescriptor.KnitrFormat.Html || getKnitrFormat() == RReportDescriptor.KnitrFormat.Markdown;
        return isKnitr ? (isEnd ? " -->\n" : "<!-- ") : (isEnd ? "\n" : "# ");
    }

    public String stripScriptProlog(String script)
    {
        return stripScriptProlog(script, false);
    }

    /**
     *
     * @param script the script containing prolog
     * @param extractProlog True to extract prolog out of script, false to extract source out of script
     * @return
     */
    public String stripScriptProlog(String script, boolean extractProlog)
    {
        String[] lines = StringUtils.split(script, '\n');
        int[] anchors = getPrologAnchors(script);
        int cutstart = anchors[0], cutend = anchors[1], yamlstart = anchors[2], yamlend = anchors[3];

        if (cutstart == -1 || cutend == -1)
            return extractProlog ? null : script;

        List<String> list = Arrays.asList(lines);

        if (extractProlog)
        {
            if (cutstart + 1 > cutend)
                return null;
            return StringUtils.join(list.subList(cutstart + 1, cutend), "\n") + "\n";
        }

        // write out lines before prolog cut 8<
        String ret = "";
        if (cutstart > 0)
            ret = StringUtils.join(list.subList(0,cutstart), "\n") + "\n";
        if (yamlstart == 0 && (yamlend > cutstart && yamlend < cutend))
            ret += "---\n";
        if (cutend < lines.length-1)
        {
            String endPrologLine = list.get(cutend);
            int sourceStartIndex = script.indexOf(endPrologLine) + endPrologLine.length();
            ret += script.substring(sourceStartIndex + 1); // plus 1 for newline character
        }
        return ret;
    }

    /**
     *
     * @param script
     * @return [cutstart, cutend, yamlstart, yamlend]
     */
    public int[] getPrologAnchors(String script)
    {
        String[] lines = StringUtils.split(script, '\n');
        int cutstart = -1, cutend = -1, cutStartCharInd = 0, cutEndCharInd = script.length() - 1;
        int yamlstart = -1;
        int yamlend = -1;
        if (lines.length > 0 && lines[0].trim().startsWith("---"))
            yamlstart = 0;
        for (int i=0 ; i<lines.length ; i++)
        {
            String line = lines[i].trim();
            cutStartCharInd = script.indexOf(line, cutStartCharInd);
            if (i > 0 && yamlend == -1 && line.startsWith("---"))
                yamlend = i;
            if (line.startsWith("#") && line.substring(1).trim().startsWith("8<"))
            {
                cutstart = i;
                break;
            }
            if (line.startsWith("<!--") && line.endsWith("-->") && line.substring(4,line.length()-3).trim().startsWith("8<"))
            {
                cutstart = i;
                break;
            }
        }
        String scriptCopy = script;
        for (int i=lines.length-1 ; i>=0 ; i--)
        {
            String line = lines[i].trim();
            cutEndCharInd = scriptCopy.lastIndexOf(line);;
            scriptCopy = scriptCopy.substring(0, cutEndCharInd);

            if (line.startsWith("#") && line.trim().endsWith(">8"))
            {
                cutend = i;
                break;
            }
            if (line.startsWith("<!--") && line.endsWith("-->") && line.substring(4,line.length()-3).trim().endsWith(">8"))
            {
                cutend = i;
                break;
            }
        }

        return new int[]{cutstart, cutend, yamlstart, yamlend, cutStartCharInd, cutEndCharInd};
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
    protected String processInputReplacement(ScriptEngine engine, String script, @Nullable File inputFile, boolean isRStudio)
    {
        RScriptEngine rengine = (RScriptEngine) engine;
        String remotePath = inputFile == null ? null : rengine.getRemotePath(inputFile);
        return ParamReplacementSvc.get().processInputReplacement(script, INPUT_FILE_TSV, remotePath, isRStudio, null);
    }

    @Override
    protected String processOutputReplacements(ScriptEngine engine, String script, List<ParamReplacement> replacements, @NotNull ContainerUser context, boolean isRStudio) throws Exception
    {
        File reportDir = getReportDir(context.getContainer().getId());
        RScriptEngine rengine = (RScriptEngine)engine;
        String localPath = getLocalPath(reportDir);
        String remoteRoot = rengine.getRemotePath(localPath);
        return ParamReplacementSvc.get().processParamReplacement(script, reportDir, remoteRoot, replacements, isRStudio);
    }

    @Override
    protected String createScript(ScriptEngine engine, ViewContext context, List<ParamReplacement> outputSubst, File inputDataTsv, Map<String, Object> inputParameters) throws Exception
    {
        return createScript(engine, context, outputSubst, inputDataTsv, inputParameters, false);
    }

    @Override
    public String createScript(ScriptEngine engine, ViewContext context, List<ParamReplacement> outputSubst, File inputDataTsv, Map<String, Object> inputParameters, boolean isRStudio) throws Exception
    {
        String script = super.createScript(engine, context, outputSubst, inputDataTsv, inputParameters, isRStudio);
        File inputData = new File(getReportDir(context.getContainer().getId()), DATA_INPUT);

        /*
          for each included script, the source script is processed for input/output replacements
          and the result copied into this scripts working directory so it can be loaded via the source command
         */
        for (String includedReport : ((RReportDescriptor)getDescriptor()).getIncludedReports())
        {
            ReportIdentifier reportId = ReportService.get().getReportIdentifier(includedReport, context.getUser(), context.getContainer());

            if (reportId != null)
            {
                ContainerUser containerUser = new DefaultContainerUser(getOwningContainer(context), context.getUser());
                Report report = reportId.getReport(containerUser);

                if (validateSharedPermissions(containerUser, report) && RReport.class.isAssignableFrom(report.getClass()))
                {
                    final String rName = report.getDescriptor().getProperty(ReportDescriptor.Prop.reportName);
                    final String rScript = report.getDescriptor().getProperty(ScriptReportDescriptor.Prop.script);
                    final String rExtension = ((RReport) report).getScriptFileExtension();
                    final File rScriptFile = new File(getReportDir(context.getContainer().getId()), rName + rExtension);

                    String includedScript = processScript(engine, context, rScript, inputData, outputSubst, inputParameters, false, isRStudio);

                    try (PrintWriter pw = PrintWriters.getPrintWriter(rScriptFile))
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

    public String getScriptFileExtension()
    {
        if (getKnitrFormat() == RReportDescriptor.KnitrFormat.Html)
            return ".Rhtml";

        if (getKnitrFormat() == RReportDescriptor.KnitrFormat.Markdown)
            return ".Rmd";

        return ".R";
    }

    /**
     * If this R Report is using knitr then put all the results into the cache directory instead of a temp
     * directory.  This enables knitr to do its own caching.  If this is a non-Knitr report or we don't have a cache
     * directory because the report was not saved yet, then fall back to the non-cache case.  Note that knitr caching
     * is not the same as report caching.  Report caching saves off the output parameters of the report and then serves
     * them up without executing the script again if the incoming URL is the same.
     * For Knitr caching, we always run the R script and let the knitr library manage the caching options.
     * @param executingContainerId id of the container in which the report is running
     */
    @Override
    public File getReportDir(@NotNull String executingContainerId)
    {
        File reportDir = null;

        if (getKnitrFormat() != RReportDescriptor.KnitrFormat.None)
            reportDir = getCacheDir(executingContainerId);

        if (null == reportDir)
            reportDir = super.getReportDir(executingContainerId);

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
        return false;
    }


    @Override
    public String runScript(ViewContext context, List<ParamReplacement> outputSubst, File inputDataTsv, Map<String, Object> inputParameters) throws ScriptException
    {
        ScriptEngine engine = getScriptEngine(context.getContainer());
        if (engine != null)
        {
            try
            {
                Bindings bindings = engine.getBindings(ScriptContext.ENGINE_SCOPE);
                bindings.put(RScriptEngine.KNITR_FORMAT, getKnitrFormat());
                bindings.put(RScriptEngine.PANDOC_USE_DEFAULT_OUTPUT_FORMAT, isUseDefaultOutputOptions());
                bindings.put(RScriptEngine.PANDOC_OUTPUT_OPTIONS_LIST, getRmarkdownOutputOptions());

                Object output = runScript(engine, context, outputSubst, inputDataTsv, inputParameters);

                if (getKnitrFormat() == RReportDescriptor.KnitrFormat.None)
                {
                    saveConsoleOutput(output, outputSubst, context);
                }
                else
                {
                    File knitrOutput = new File((String)bindings.get(RScriptEngine.KNITR_OUTPUT));
                    saveKnitrOutput(knitrOutput, outputSubst);
                }
                saveAdditionalFileOutput(outputSubst, context);

                return output != null ? output.toString() : "";
            }
            catch (Exception e)
            {
                throw new ScriptException(e);
            }
        }

        throw new ScriptException("A script engine implementation was not found for the specified report");
    }

    private void saveKnitrOutput(File knitrOutput, List<ParamReplacement> outputSubst)
    {
        KnitrOutput param = new KnitrOutput();
        param.setName("Knitr");
        param.setReport(this);
        param.addFile(knitrOutput);
        outputSubst.add(param);
    }

    private boolean validateSharedPermissions(ContainerUser context, Report report)
    {
        if (report != null)
        {
            if (report.getDescriptor().isModuleBased())
            {
                return true;
            }

            if (canReadReport(context.getUser()))
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

    /**
     * Moved from ReportUtil
     */
    private boolean canReadReport(User user)
    {
        if (getDescriptor().isShared())
            return true;

        return (getDescriptor().getOwner().equals(user.getUserId()));
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

    @Override
    public List<Report> getAvailableSharedScripts(ViewContext context, ScriptReportBean bean)
    {
        List<Report> scripts = new ArrayList<>();

        String reportKey = ReportUtil.getReportKey(bean.getSchemaName(), bean.getQueryName());
        String reportName = bean.getReportName();
        Container c = getOwningContainer(context);

        for (Report r : ReportUtil.getReportsIncludingInherited(c, context.getUser(), StringUtils.isBlank(reportKey) ? null : reportKey))
        {
            // List only R scripts.  TODO: Need better, more general mechanism for this
            if (!RReportDescriptor.class.isAssignableFrom(r.getDescriptor().getClass()))
                continue;

            if (reportName == null || !reportName.equals(r.getDescriptor().getProperty(ReportDescriptor.Prop.reportName)))
                scripts.add(r);
        }

        return scripts;
    }

    @Override
    public String getDownloadDataHelpMessage()
    {
        return "LabKey Server automatically exports query data into a data frame called \"labkey.data\". You can download the data via this link to help with the development of your R script.";
    }

    @Override
    public @NotNull String getDesignerHelpHtml()
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
        // issue 27527: only use the write.table default script if we have a labkey.data data frame
        return getDescriptor().getProperty("dataRegionName") == null
            ? ""
            : "# This sample code returns the query data in tab-separated values format, which LabKey then\n" +
                "# renders as HTML. Replace this code with your R script. See the Help tab for more details.\n\n" +
                "# ${tsvout:tsvfile}\n" +
                "write.table(labkey.data, file = \"tsvfile\", sep = \"\\t\", qmethod = \"double\", col.names=NA)\n";
    }

    @Override
    public String getStaticThumbnailPath()
    {
        return "/reports/r_logo.svg";
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
        if (!super.hasEditPermissions(user, container, errors))
            return false;

        // TODO consider: create subclass ModuleRReport
        // Why aren't these two checks consistent???
        if (getDescriptor() instanceof ModuleReportDescriptor)
        {
            if (!((ModuleReportDescriptor)getDescriptor()).canEdit(user, errors))
                return false;
        }
        else if (getDescriptor().isModuleBased())
        {
            return false;
        }

        if (!user.isTrustedAnalyst())
        {
            errors.add(new SimpleValidationError("You must be either a PlatformDeveloper or TrustedAnalyst to update an R report."));
        }
        else if (!user.isPlatformDeveloper())
        {
            ScriptEngine engine = getScriptEngine(container);
            boolean sandboxed;
            sandboxed = ((LabKeyScriptEngine)engine).isSandboxed();
            if (!sandboxed)
                errors.add(new SimpleValidationError("You may not edit reports in non-sandboxed execution environment."));
        }

        return errors.isEmpty();
    }

    @Nullable
    @Override
    public String getEditAreaSyntax()
    {
        return "text/x-rsrc";
    }

    @Override
    public boolean allowShareButton(User user, Container container)
    {
        // allow sharing if this R report is a DB report and the user canShare
        return !getDescriptor().isModuleBased() && canShare(user, container);
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testProlog()
        {
            RReport report = new RReport();
            report.getDescriptor().setProperty(ScriptReportDescriptor.Prop.knitrFormat, "r");
            ViewContext context = HttpView.currentContext();
            RScriptEngine r = (RScriptEngine)LabKeyScriptEngineManager.get().getEngineByExtension(context.getContainer(), "r");
            Map<String,String> params = PageFlowUtil.map("a", "1", "b", "2");
            String pre = "print('hello world')\n\nprint('line 3')\n";
            boolean isRStudio = false;
            String post = report.concatScriptProlog(r, context, pre, null, (Map)params, isRStudio);
            assertTrue( post.endsWith(pre) );

            isRStudio = true;
            post = report.concatScriptProlog(r, context, pre, null, (Map)params, isRStudio);
            assertTrue( post.endsWith(pre) );
            assertTrue( post.contains("# 8<") );
            assertTrue( post.contains(">8") );
            String strip = report.stripScriptProlog(post);
            assertEquals(pre, strip);
            assertTrue( post.endsWith(pre) );
            boolean isRStudioEnabled = RStudioService.get() != null;
            int[] expected = isRStudioEnabled ? new int[]{0, 12, -1, -1, 0, 584} : new int[]{0, 7, -1, -1, 0, post.indexOf("# >8")};
            assertArrayEquals(expected, report.getPrologAnchors(post));
        }

        @Test
        public void testPrologHtml()
        {
            RReport report = new RReport();
            report.getDescriptor().setProperty(ScriptReportDescriptor.Prop.knitrFormat, "html");
            ViewContext context = HttpView.currentContext();
            RScriptEngine r = (RScriptEngine)LabKeyScriptEngineManager.get().getEngineByExtension(context.getContainer(), "r");
            //r.getBindings(ScriptContext.ENGINE_SCOPE).put(RScriptEngine.KNITR_FORMAT, RReportDescriptor.KnitrFormat.Html);
            //assertEquals(RReportDescriptor.KnitrFormat.Html, r.getKnitrFormat());
            Map<String,String> params = PageFlowUtil.map("a", "1", "b", "2");
            String pre = "<b>hello world</b>\n";
            boolean isRStudio = false;
            String post = report.concatScriptProlog(r, context, pre, null, (Map)params, isRStudio);
            assertTrue( post.endsWith("<b>hello world</b>\n") );

            isRStudio = true;
            post = report.concatScriptProlog(r, context, pre, null, (Map)params, isRStudio);
            assertTrue( post.contains("<!-- 8<") );
            assertTrue( post.contains(">8 -->") );
            String strip = report.stripScriptProlog(post);
            assertEquals(pre, strip);
            assertTrue( post.endsWith("<b>hello world</b>\n") );

            boolean isRStudioEnabled = RStudioService.get() != null;
            int[] expected = isRStudioEnabled ? new int[]{0, 14, -1, -1, 0, 639} : new int[]{0, 9, -1, -1, 0, post.indexOf("<!-- >8 -->")};
            assertArrayEquals(expected, report.getPrologAnchors(post));
        }

        @Test
        public void testPrologMd()
        {
            RReport report = new RReport();
            report.getDescriptor().setProperty(ScriptReportDescriptor.Prop.knitrFormat, "markdown");
            ViewContext context = HttpView.currentContext();
            RScriptEngine r = (RScriptEngine)LabKeyScriptEngineManager.get().getEngineByExtension(context.getContainer(), "r");
            //r.getBindings(ScriptContext.ENGINE_SCOPE).put(RScriptEngine.KNITR_FORMAT, RReportDescriptor.KnitrFormat.Markdown);
            Map<String,String> params = PageFlowUtil.map("a", "1", "b", "2");
            String pre = "---\n" +
                    "title: My Report\n" +
                    "---\n" +
                    "hello \n\nworld\n";

            boolean isRStudio = false;
            String post = report.concatScriptProlog(r, context, pre, null, (Map)params, isRStudio);
            assertTrue( post.startsWith("---\n") );
            assertTrue( post.endsWith("hello \n\nworld\n") );

            isRStudio = true;
            post = report.concatScriptProlog(r, context, pre, null, (Map)params, isRStudio);
            assertTrue( post.startsWith("---\n") );
            assertTrue( post.endsWith("hello \n\nworld\n") );
            assertTrue( post.contains("<!-- 8<") );
            assertTrue( post.contains(">8") );
            String strip = report.stripScriptProlog(post);
            assertEquals(pre, strip);

            boolean isRStudioEnabled = RStudioService.get() != null;
            int[] expected = isRStudioEnabled ? new int[]{3, 17, 0, 2, 25, 646} : new int[]{3, 12, 0, 2, 25, post.indexOf("<!-- >8 -->")};
            assertArrayEquals(expected, report.getPrologAnchors(post));
        }
    }
}

