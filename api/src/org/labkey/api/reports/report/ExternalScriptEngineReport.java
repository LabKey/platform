/*
 * Copyright (c) 2008-2016 LabKey Corporation
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
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.QueryService.NamedParameterNotProvided;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reports.ExternalScriptEngine;
import org.labkey.api.reports.RConnectionHolder;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.RserveScriptEngine;
import org.labkey.api.reports.report.r.ParamReplacement;
import org.labkey.api.reports.report.r.ParamReplacementSvc;
import org.labkey.api.reports.report.r.view.ConsoleOutput;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.reports.report.view.RunReportView;
import org.labkey.api.thumbnail.Thumbnail;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.labkey.api.writer.ContainerUser;
import org.labkey.api.writer.PrintWriters;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 *
 * User: Karl Lum
 * Date: Dec 3, 2008
 * Time: 2:45:37 PM
 *
 * A Report implementation that uses a ExternalScriptEngine instance to execute the associated script. External
 * script engines are invoked by running an application in an external process. Information is exchanged between the
 * web server and application through the file system.
 */
public class ExternalScriptEngineReport extends ScriptEngineReport implements AttachmentParent, Report.ScriptExecutor
{
    public static final String TYPE = "ReportService.externalScriptEngineReport";
    public static final String CACHE_DIR = "cached";
    private static final Map<ReportIdentifier, ActionURL> _cachedReportURLMap = new HashMap<>();
    private static String DEFAULT_PERL_PATH;

    public String getType()
    {
        return TYPE;
    }

    public HttpView renderReport(ViewContext context) throws Exception
    {
        final VBox view = new VBox();

        // todo: pass inputParameters down from upper layers like we do for
        // executeScript API below.  Currently they are still taken off the
        // URL under the covers
        renderReport(context, null, new Renderer<HttpView>()
        {
            @Override
            public void handleValidationError(String error)
            {
                view.addView(new HtmlView("<span class=\"labkey-error\">" + error + "</span>"));
            }

            @Override
            public boolean handleRuntimeException(Exception e)
            {
                view.addView(handleException(e));
                return true;
            }

            @Override
            public HttpView render(List<ParamReplacement> parameters) throws IOException
            {
                return renderViews(ExternalScriptEngineReport.this, view, parameters, false);
            }
        });

        return view;
    }

    public List<ScriptOutput> executeScript(ViewContext context, Map<String, Object> inputParameters) throws Exception
    {
        final List<ScriptOutput> scriptOutputs = new ArrayList<>();

        renderReport(context, inputParameters, new Renderer<List<ScriptOutput>>()
        {
            @Override
            public void handleValidationError(String message)
            {
                scriptOutputs.add(new ScriptOutput(ScriptOutput.ScriptOutputType.error, "Validation error", message));
            }

            @Override
            public boolean handleRuntimeException(Exception e)
            {
                String message = ReportUtil.makeExceptionString(e, "%s: %s");
                scriptOutputs.add(new ScriptOutput(ScriptOutput.ScriptOutputType.error, e.getClass().getName(), message));
                return true;
            }

            @Override
            public List<ScriptOutput> render(List<ParamReplacement> parameters) throws IOException
            {

                return renderParameters(ExternalScriptEngineReport.this, scriptOutputs, parameters, false );
            }
        });

        return scriptOutputs;
    }

    public Thumbnail getThumbnail(ViewContext context)
    {
        try
        {
            return renderReport(context, null, new Renderer<Thumbnail>()
            {
                @Override
                public void handleValidationError(String error)
                {
                }

                @Override
                public boolean handleRuntimeException(Exception e)
                {
                    return false;
                }

                @Override
                public Thumbnail render(List<ParamReplacement> parameters) throws IOException
                {
                    return getThumbnail(parameters);
                }
            });
        }
        catch (IOException e)
        {
            return null;
        }
    }


    interface Renderer<K>
    {
        void handleValidationError(String error);
        boolean handleRuntimeException(Exception e);
        K render(List<ParamReplacement> parameters) throws IOException;
    }

    protected <K> K renderReport(ViewContext context, Map<String, Object> inputParameters, Renderer<K> renderer) throws IOException
    {
        String script = getDescriptor().getProperty(ScriptReportDescriptor.Prop.script);

/*
        if (validateConfiguration(getRExe(), getRCmd(), getTempFolder(), getRScriptHandler()) != null)
        {
            final String error = "The R program has not been configured to be used by the LabKey server yet, navigate to the <a href='" + PageFlowUtil.filter(PageFlowUtil.urlProvider(AdminUrls.class).getAdminConsoleURL()) + "'>admin console</a> to configure R.";
            view.addView(new HtmlView("<span class=\"labkey-error\">" + error + "</span>"));
            return view;
        }
*/

        List<String> errors = new ArrayList<>();

        if (!validateScript(script, errors))
        {
            for (String error : errors)
                renderer.handleValidationError(error);

            return null;
        }

        List<ParamReplacement> outputSubst = new ArrayList<>();

        if (!getCachedReport(context, outputSubst))
        {
            try
            {
                runScript(context, outputSubst, createInputDataFile(context), inputParameters);
            }
            catch (ScriptException e)
            {
                boolean continueOn = renderer.handleRuntimeException(e);

                if (!continueOn)
                    return null;
            }
            catch (ValidationException e)
            {
                boolean continueOn = renderer.handleRuntimeException(e);

                if (!continueOn)
                    return null;
            }
            catch (NamedParameterNotProvided e)
            {
                boolean continueOn = renderer.handleRuntimeException(e);

                if (!continueOn)
                    return null;
            }
            catch (Exception e)
            {
                ExceptionUtil.logExceptionToMothership(context.getRequest(), e);
                boolean continueOn = renderer.handleRuntimeException(e);

                if (!continueOn)
                    return null;
            }

            cacheResults(context, outputSubst);
        }

        return renderer.render(outputSubst);
    }

    private HttpView handleException(Exception e)
    {
        return new HtmlView(ReportUtil.makeExceptionString(e, "<font class=\"labkey-error\">%s</font><pre>%s</pre>"));
    }

    @Override
    public String runScript(ViewContext context, List<ParamReplacement> outputSubst, File inputDataTsv, Map<String, Object> inputParameters) throws ScriptException
    {
        ScriptEngine engine = getScriptEngine();

        if (engine != null)
        {
            try
            {
                Object output = runScript(engine, context, outputSubst, inputDataTsv, inputParameters);
                saveConsoleOutput(output, outputSubst, context);
                saveAdditionalFileOutput(outputSubst, context);
                return output != null ? output.toString() : "";
            }
            catch(Exception e)
            {
                throw new ScriptException(e);
            }
        }

        throw new ScriptException("A script engine implementation was not found for the specified report");
    }

    protected void saveConsoleOutput(Object output, List<ParamReplacement> outputSubst, @NotNull ContainerUser context) throws IOException
    {
        if (null != output)
        {
            File console = new File(getReportDir(context.getContainer().getId()), CONSOLE_OUTPUT);

            try (PrintWriter pw = PrintWriters.getPrintWriter(console))
            {
                pw.write(output.toString());
            }

            ParamReplacement param = ParamReplacementSvc.get().getHandlerInstance(ConsoleOutput.ID);
            param.setName("console");
            param.addFile(console);
            outputSubst.add(param);
        }
    }

    /**
     * Searches for additional file artifacts
     */
    protected void saveAdditionalFileOutput(List<ParamReplacement> outputSubst, @NotNull ContainerUser context) throws IOException
    {
        File reportDir = getReportDir(context.getContainer().getId());
        if (reportDir != null && reportDir.exists())
        {
            Set<String> boundFiles = new CaseInsensitiveHashSet();
            for (ParamReplacement param : outputSubst)
            {
                for (File file : param.getFiles())
                    boundFiles.add(file.getName());
            }

            File[] additionalFiles = reportDir.listFiles(new FilenameFilter()
            {
                @Override
                public boolean accept(File dir, String name)
                {
                    if (boundFiles.contains(name))
                        return false;
                    else if ("input_data.tsv".equalsIgnoreCase(name))
                        return false;
                    else if ("script.r".equalsIgnoreCase(name))
                        return false;
                    else if ("script.rout".equalsIgnoreCase(name))
                        return false;
                    return true;
                }
            });

            for (File file : additionalFiles)
            {
                for (ParamReplacement param : outputSubst)
                {
                    if (param.getRegex() != null)
                    {
                        if (Pattern.matches(param.getRegex(), file.getName()))
                            param.addFile(file);
                    }
                }
            }
        }
    }

    protected Object runScript(ScriptEngine engine, ViewContext context, List<ParamReplacement> outputSubst, File inputDataTsv, Map<String, Object> inputParameters) throws ScriptException
    {
        RConnectionHolder rh = null;
        try
        {
            Bindings bindings = engine.getBindings(ScriptContext.ENGINE_SCOPE);
            bindings.put(ExternalScriptEngine.WORKING_DIRECTORY, getReportDir(context.getContainer().getId()).getAbsolutePath());

            if (engine instanceof RserveScriptEngine)
            {
                //
                // pass along the report temp directory and the pipeline root to the script engine for
                // replacement of the file share directories with their remote representation.
                //
                // for example:  c:\data\fcs -> /volumes/fcs
                //
                bindings.put(RserveScriptEngine.TEMP_ROOT, getTempRoot(getDescriptor()));

                PipeRoot pipelineRoot = PipelineService.get().findPipelineRoot(context.getContainer());
                bindings.put(RserveScriptEngine.PIPELINE_ROOT, pipelineRoot.getRootPath());

                //
                // See if a valid report session id was passed in
                // An empty session id is allowed; we just
                // don't do any session sharing in this case
                //
                String reportSessionId = (String) context.get(renderParam.reportSessionId.name());

                if (!StringUtils.isEmpty(reportSessionId))
                {
                    rh = (RConnectionHolder) context.getSession().getAttribute(reportSessionId);
                    if (rh == null)
                    {
                        //
                        // This can happen if the client never called the createSession action or
                        // if the session expired
                        //
                        throw new ScriptException("The report session is invalid");
                    }
                    rh.acquire();
                    //
                    // if the report exposes any functions that can be called by the shared session
                    // then add them now
                    //
                    rh.addCallableFunctions(getDescriptor().getCallableFunctions());
                    bindings.put(RserveScriptEngine.R_SESSION, rh);
                }
            }

            return engine.eval(createScript(engine, context, outputSubst, inputDataTsv, inputParameters));
        }
        catch(Exception e)
        {
            throw new ScriptException(e);
        }
        finally
        {
            if (rh != null)
                rh.release();
        }
    }

    protected void cacheResults(ViewContext context, List<ParamReplacement> replacements)
    {
        if (getDescriptor().getReportId() != null &&
            BooleanUtils.toBoolean(getDescriptor().getProperty(ReportDescriptor.Prop.cached))) {
            synchronized (_cachedReportURLMap) {
                File cacheDir = getCacheDir(context.getContainer().getId());

                if (null == cacheDir)
                    return;

                try {
                    File mapFile = new File(cacheDir, SUBSTITUTION_MAP);

                    for (ParamReplacement param : replacements)
                    {
                        List<Pair<File, File>> changes = new ArrayList<>();
                        for (File src : param.getFiles())
                        {
                            File dst = new File(cacheDir, src.getName());
                            if (src.exists() && dst.createNewFile())
                            {
                                FileUtil.copyFile(src, dst);

                                if (param.getId().equals(ConsoleOutput.ID))
                                {
                                    try (BufferedWriter bw = new BufferedWriter(new FileWriter(dst, true)))
                                    {
                                        bw.write("\nLast cached update : " + DateUtil.formatDateTime(context.getContainer()) + "\n");
                                    }
                                }
                                changes.add(new Pair<>(src, dst));
                            }
                        }

                        for (Pair<File, File> change : changes)
                        {
                            param.getFiles().remove(change.getKey());
                            param.addFile(change.getValue());
                        }
                    }
                    ParamReplacementSvc.get().toFile(replacements, mapFile);
                    _cachedReportURLMap.put(getDescriptor().getReportId(), getCacheURL(context.getActionURL()));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    public void clearCache(@NotNull String executingContainerId)
    {
        File cacheDir = getCacheDir(executingContainerId);
        if (null != cacheDir && cacheDir.exists())
            FileUtil.deleteDir(cacheDir);
    }

    protected boolean getCachedReport(ViewContext context, List<ParamReplacement> replacements)
    {
        if (getDescriptor().getReportId() != null &&
            BooleanUtils.toBoolean(getDescriptor().getProperty(ReportDescriptor.Prop.cached)))
        {
            synchronized(_cachedReportURLMap)
            {
                if (urlDirty(context.getActionURL()))
                {
                    clearCache(context.getContainer().getId());
                    return false;
                }
                File cacheDir = getCacheDir(context.getContainer().getId());
                if (null == cacheDir)
                    return false;

                try
                {
                    for (ParamReplacement param : ParamReplacementSvc.get().fromFile(new File(cacheDir, SUBSTITUTION_MAP)))
                    {
                        replacements.add(param);
                    }
                    return !replacements.isEmpty();
                }
                catch (Exception e)
                {
                    throw new RuntimeException(e);
                }
            }
        }
        return false;
    }

    private ActionURL getCacheURL(ActionURL url)
    {
        return url.clone().deleteParameter(RunReportView.CACHE_PARAM).
                deleteParameter(RunReportView.TAB_PARAM);
    }

    /**
     * Detect whether the URL params have changed since this cached report was last rendered.
     */
    private boolean urlDirty(ActionURL url)
    {
        ActionURL cachedURL = _cachedReportURLMap.get(getDescriptor().getReportId());
        if (cachedURL != null)
        {
            Map cur = PageFlowUtil.mapFromQueryString(getCacheURL(url).getQueryString());
            Map prev = PageFlowUtil.mapFromQueryString(cachedURL.getQueryString());

            return !cur.equals(prev);
        }
        return true;
    }

    /**
     * Called before this report is saved or updated
     * @param context
     */
    public void beforeSave(ContainerUser context)
    {
        super.beforeSave(context);
        clearCache();
    }

    /**
     * Called before this report is deleted
     * @param context
     */
    public void beforeDelete(ContainerUser context)
    {
        // clean up any temp files
        clearCache();
        deleteReportDir(context);
        super.beforeDelete(context);
    }

    protected File getCacheDir(@NotNull String executingContainerId)
    {
        if (getDescriptor().getReportId() == null)
            return null;

        File cacheDir = new File(getTempRoot(getDescriptor()), executingContainerId + File.separator + "Report_" + FileUtil.makeLegalName(getDescriptor().getReportId().toString()) + File.separator + CACHE_DIR);

        if (!cacheDir.exists())
            cacheDir.mkdirs();

        return cacheDir;
    }

    public HttpView renderDataView(ViewContext context) throws Exception
    {
        QueryView view = createQueryView(context, getDescriptor());

        if (view != null)
            return view;
        else
            return new HtmlView("No Data view available for this report");
    }

    public static synchronized String getDefaultPerlPath()
    {
        if (DEFAULT_PERL_PATH == null)
        {
            DEFAULT_PERL_PATH = getDefaultAppPath(new FilenameFilter()
            {
                public boolean accept(File dir, String name)
                {
                    if ("perl.exe".equalsIgnoreCase(name) || "perl".equalsIgnoreCase(name))
                        return true;
                    return false;
                }
            });
        }
        return DEFAULT_PERL_PATH;
    }

    protected static synchronized String getDefaultAppPath(FilenameFilter filter)
    {
        String appPath = "";
        String path = System.getenv("PATH");

        for (String dir : path.split(File.pathSeparator))
        {
            File part = new File(dir);
            if (part.isDirectory())
            {
                File[] files = part.listFiles(filter);

                if (files != null && files.length > 0)
                {
                    appPath = files[0].getAbsolutePath().replaceAll("\\\\", "/");
                    break;
                }
            }
            else if (filter.accept(part.getParentFile(), part.getName()))
            {
                appPath = part.getAbsolutePath().replaceAll("\\\\", "/");
                break;
            }
        }
        return appPath;
    }
}
