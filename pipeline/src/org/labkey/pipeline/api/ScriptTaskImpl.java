/*
 * Copyright (c) 2013-2019 LabKey Corporation
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
package org.labkey.pipeline.api;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.RowMapFactory;
import org.labkey.api.data.Container;
import org.labkey.api.data.TSVMapWriter;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.WorkDirectory;
import org.labkey.api.pipeline.cmd.TaskPath;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.reports.ExternalScriptEngine;
import org.labkey.api.reports.ExternalScriptEngineDefinition;
import org.labkey.api.reports.ExternalScriptEngineFactory;
import org.labkey.api.reports.LabKeyScriptEngineManager;
import org.labkey.api.reports.report.r.RScriptEngine;
import org.labkey.api.reports.report.r.RserveScriptEngine;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.LogPrintWriter;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.pipeline.analysis.CommandTaskImpl;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * User: kevink
 * Date: 11/14/13
 *
 * Created via SimpleTaskFactory when parsing a task xml file.
 * Execute a script file or inline script fragment.
 */
public class ScriptTaskImpl extends CommandTaskImpl
{
    public static final Logger LOG = LogManager.getLogger(ScriptTaskImpl.class);

    private ScriptEngine _engine;

    /* package */ ScriptTaskImpl(PipelineJob job, SimpleTaskFactory factory)
    {
        super(job, factory);

        _writeTaskInfoFile = true;
    }

    private ScriptEngine getScriptEngine(Container c, LabKeyScriptEngineManager mgr, String extension)
    {
        ScriptEngine engine = mgr.getEngineByName(extension);
        if (engine == null)
        {
            engine = mgr.getEngineByExtension(c, extension, LabKeyScriptEngineManager.EngineContext.pipeline);
        }
        return engine;
    }

    // TODO: Rhino engine.  A non-ExternalScriptEngine won't use the PARAM_REPLACEMENT_MAP binding.
    // CONSIDER: Use ScriptEngineReport to generate a script prolog
    @Override
    protected boolean runCommand(RecordedAction action, @Nullable String apiKey, @Nullable Container container) throws IOException, PipelineJobException
    {
        // Get the script engine
        LabKeyScriptEngineManager mgr = LabKeyScriptEngineManager.get();
        if (mgr == null)
            throw new PipelineJobException("Script engine manager not available");

        ScriptTaskFactory factory = (ScriptTaskFactory)_factory;
        String extension = factory._scriptExtension;
        _engine = getScriptEngine(getJob().getContainer(), mgr, extension);
        if (_engine == null)
            throw new PipelineJobException("Script engine not found: " + extension);

        try
        {
            @Nullable File scriptFile = null;
            String scriptSource;
            if (factory._scriptInline != null)
            {
                scriptSource = factory._scriptInline;
            }
            else if (factory._scriptPath != null)
            {
                String[] paths = getProcessPaths(WorkDirectory.Function.module, factory._scriptPath.toString());
                if (paths.length != 1 || paths[0] == null)
                    throw new PipelineJobException("Script path not found: " + factory._scriptPath);

                String path = paths[0];
                scriptFile = new File(path);

                scriptSource = PageFlowUtil.getFileContentsAsString(scriptFile);
            }
            else
            {
                throw new PipelineJobException("Script path or inline script required");
            }

            // Tell the script engine where the script is and the working directory.
            // ExternalScriptEngine will copy script into working dir to perform replacements.
            Bindings bindings = _engine.getBindings(ScriptContext.ENGINE_SCOPE);

            // UNDONE: Need ability to map to more than one remote pipeline path?  For now, assume everything is under the remote's pipeline root setting
            PipeRoot pipelineRoot = PipelineService.get().findPipelineRoot(getJob().getContainer());
            File pipelineRootPath = pipelineRoot.getRootPath();
            getJob().info("folder pipeline root: " + pipelineRootPath);
            bindings.put(RserveScriptEngine.PIPELINE_ROOT, pipelineRootPath);

            if (_engine instanceof RserveScriptEngine)
            {
                // TODO: RServe currently only configures site-wide pipeline share.
                // TODO: We check that the current folder pipeline root is either equal to or is under the project's pipeline root.
                // TODO: This could fail if the project pipeline root isn't the same as the RServe script engine settings pipeline share
                PipeRoot projectPipeRoot = PipelineService.get().getPipelineRootSetting(getJob().getContainer().getProject());
                File projectRootPath = projectPipeRoot.getRootPath();
                getJob().info("project pipeline root: " + projectRootPath);
                if (projectRootPath != pipelineRootPath)
                {
                    if (pipelineRootPath.getPath().startsWith(projectRootPath.getPath()))
                    {
                        bindings.put(RserveScriptEngine.PROJECT_PIPELINE_ROOT, projectRootPath);
                    }
                    else
                    {
                        getJob().warn("RServe doesn't support folder pipeline roots that aren't under the site pipeline root");
                    }
                }
            }

            // NOTE: Local path to the script file doesn't need to be rewritten as a remote path
            if (scriptFile != null)
                bindings.put(ExternalScriptEngine.SCRIPT_PATH, scriptFile.toString());

            bindings.put(ExternalScriptEngine.WORKING_DIRECTORY, _wd.getDir().getPath());

            // Thread the timeout option through to the external script engine
            if (_factory.getTimeout() != null && _factory.getTimeout() > 0)
                bindings.put(ExternalScriptEngine.TIMEOUT, _factory.getTimeout());

            Map<String, String> replacements = createReplacements(scriptFile, apiKey, container);
            bindings.put(ExternalScriptEngine.PARAM_REPLACEMENT_MAP, replacements);

            // Write task properties file into the work directory
            // This needs to be called after the PIPELINE_ROOT is set in the engine bindings
            if (isWriteTaskInfoFile())
            {
                writeTaskInfo(getTaskInfoFile(), action);
            }

            // Just output the replaced script, if debug mode is set.
            if (AppProps.getInstance().isDevMode() || _factory.isPreview())
            {
                // TODO: dump replaced script, for now just dump the replacements
                getJob().header("Replacements");
                for (Map.Entry<String, String> entry : replacements.entrySet())
                    getJob().info(entry.getKey() + ": " + entry.getValue());

                if (_factory.isPreview())
                    return false;
            }

            // Script console output will be redirected to the job's log file as it is produced
            getJob().header("Executing script");
            LogPrintWriter writer = new LogPrintWriter(getJob().getLogger(), Level.INFO);
            _engine.getContext().setWriter(writer);
            writer.flush();

            // Execute the script
            Object o = _engine.eval(scriptSource);

            if (_factory.isPipeToOutput())
            {
                TaskPath tpOut = _factory.getOutputPaths().get(WorkDirectory.Function.output.toString());
                assert !tpOut.isSplitFiles() : "Invalid attempt to pipe output to split files.";
                File fileOutput = _wd.newWorkFile(WorkDirectory.Function.output,
                        tpOut, getJobSupport().getBaseName());
                FileUtils.write(fileOutput, String.valueOf(o), StringUtilsLabKey.DEFAULT_CHARSET);
            }

            // If we got this far, we were successful in running the script.
            // Delete the rewritten script and output files from the work directory
            // so they won't be attached as related outputs of the task.
            if (bindings.get(ExternalScriptEngine.REWRITTEN_SCRIPT_FILE) instanceof File)
            {
                File rewrittenScriptFile = (File)bindings.get(ExternalScriptEngine.REWRITTEN_SCRIPT_FILE);
                _wd.discardFile(rewrittenScriptFile);
            }

            // Delete the console out file (e.g., "script.Rout") from the work directory
            if (_engine.getFactory() instanceof ExternalScriptEngineFactory)
            {
                ExternalScriptEngineDefinition externalEngineDef = ((ExternalScriptEngineFactory)_engine.getFactory()).getDefinition();
                if (externalEngineDef.getOutputFileName() != null)
                {
                    File consoleOutputFile = ((ExternalScriptEngine) _engine).getConsoleOutputFile(_engine.getContext());
                    if (consoleOutputFile != null)
                        _wd.discardFile(consoleOutputFile);
                }
            }

            return true;
        }
        catch (ScriptException e)
        {
            throw new PipelineJobException(e);
        }
        finally
        {
            _engine = null;
        }
    }

    protected void writeTaskInfo(File file, RecordedAction action) throws IOException
    {
        List<String> columns = Arrays.asList("Name", "Value");
        RowMapFactory<Object> factory = new RowMapFactory<>(columns);
        List<Map<String, Object>> rows = new ArrayList<>();

        // Job information
        rows.add(factory.getRowMap("provider", getJob().getProvider()));
        rows.add(factory.getRowMap("description", getJob().getDescription()));
        rows.add(factory.getRowMap("jobGUID", getJob().getJobGUID()));
        rows.add(factory.getRowMap("parentGUID", getJob().getParentGUID()));
        rows.add(factory.getRowMap("splitJob", getJob().isSplitJob()));

        rows.add(factory.getRowMap("baseUrl", AppProps.getInstance().getBaseServerUrl()));
        rows.add(factory.getRowMap("contextPath", AppProps.getInstance().getContextPath()));
        rows.add(factory.getRowMap("containerPath", getJob().getContainer().getPath()));
        rows.add(factory.getRowMap("containerId", getJob().getContainer().getEntityId()));
        rows.add(factory.getRowMap("user", getJob().getUser().getEmail()));

        PipeRoot pipeRoot = getJob().getPipeRoot();
        rows.add(factory.getRowMap("pipeRoot", getJob().getPipeRoot().getRootPath()));

        // FileAnalysisJobSupport properties
        FileAnalysisJobSupport support = getJobSupport();
        rows.add(factory.getRowMap("protocol", support.getProtocolName()));
        rows.add(factory.getRowMap("baseName", support.getBaseName()));
        rows.add(factory.getRowMap("joinedBaseName", support.getJoinedBaseName()));
        rows.add(factory.getRowMap("analysisDirectory", rewritePath(support.getAnalysisDirectory().toString())));
        rows.add(factory.getRowMap("dataDirectory", rewritePath(support.getDataDirectory().toString())));

        // Task information
        rows.add(factory.getRowMap("taskId", _factory.getId()));

        // Write out the known inputs to this task
        for (RecordedAction.DataFile inputFile : action.getInputs())
        {
            String role = inputFile.getRole();
            if (role == null)
                continue;

            URI uri = inputFile.getURI();
            File f = new File(uri);
            if (f.exists())
            {
                String inputPath = _wd.getRelativePath(f);
                rows.add(factory.getRowMap(role, inputPath));
            }
        }

        for (Map.Entry<String, TaskPath> entry : _factory.getOutputPaths().entrySet())
        {
            String key = entry.getKey();
            TaskPath path = entry.getValue();

            // CONSIDER: Include the TaskPath information (optional, etc.)

            String[] outputPaths = getProcessPaths(WorkDirectory.Function.output, key);
            for (String outputPath : outputPaths)
            {
                rows.add(factory.getRowMap(key, outputPath, path.getType().getDefaultSuffix()));
            }
        }

        try (TSVMapWriter tsvWriter = new TSVMapWriter(columns, rows))
        {
            tsvWriter.setHeaderRowVisible(false);
            tsvWriter.write(file);
        }
    }

    @Override
    protected String rewritePath(String path)
    {
        if (_engine instanceof RScriptEngine)
        {
            // Ensure local path is absolute before converting to remote path
            File f = new File(path);
            if (!f.isAbsolute())
            {
                f = new File(_wd.getDir(), path);
                path = f.getAbsolutePath();
            }

            RScriptEngine rengine = (RScriptEngine) _engine;
            String remotePath = rengine.getRemotePath(path);
            if (null == remotePath)
                return path;

            getJob().debug("rewritePath: " + path + " -> " + remotePath);
            return remotePath;
        }
        else
        {
            return path;
        }
    }
}
