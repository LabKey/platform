/*
 * Copyright (c) 2013 LabKey Corporation
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

import common.Logger;
import org.apache.commons.io.FileUtils;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.WorkDirectory;
import org.labkey.api.pipeline.cmd.TaskPath;
import org.labkey.api.reports.ExternalScriptEngine;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.pipeline.analysis.CommandTaskImpl;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
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
    public static final Logger LOG = Logger.getLogger(ScriptTaskImpl.class);

    /* package */ ScriptTaskImpl(PipelineJob job, SimpleTaskFactory factory)
    {
        super(job, factory);
    }

    /**
     * Create the replacements map that will be used by the ExternalScriptEngine
     * to generate the script before executing it.  The replaced paths will be
     * resolved to paths in the work directory.
     */
    private Map<String, String> createReplacements() throws IOException
    {
        Map<String, String> replacements = new HashMap<>();
        for (String key : _factory.getInputPaths().keySet())
        {
            String[] inputPaths = getProcessPaths(WorkDirectory.Function.input, key);
            if (inputPaths.length == 1)
                replacements.put(key, inputPaths[0]);
        }

        for (String key : _factory.getOutputPaths().keySet())
        {
            String[] outputPaths = getProcessPaths(WorkDirectory.Function.output, key);
            if (outputPaths.length == 1)
                replacements.put(key, outputPaths[0]);
        }

        replacements.putAll(getJob().getParameters());
        return replacements;
    }

    // TODO: I believe script task can only run on the webserver since the paths the ExternalScriptEngine is configured to are local to the server.
    // TODO: RServe
    // TODO: Rhino engine.  A non-ExternalScriptEngine won't use the PARAM_REPLACEMENT_MAP binding.
    // CONSIDER: Use ScriptEngineReport to generate a script prolog
    @Override
    protected boolean runCommand(RecordedAction action) throws IOException, PipelineJobException
    {
        ScriptEngineManager mgr = ServiceRegistry.get().getService(ScriptEngineManager.class);
        if (mgr == null)
            throw new PipelineJobException("Script engine manager not available");

        SimpleTaskFactory factory = (SimpleTaskFactory)_factory;
        String extension = factory._scriptExtension;
        ScriptEngine engine = mgr.getEngineByName(extension);
        if (engine == null)
            engine = mgr.getEngineByExtension(extension);
        if (engine == null)
            throw new PipelineJobException("Script engine not found: " + extension);

        try
        {
            String scriptSource = null;
            File scriptFile = null;
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

                scriptSource = FileUtils.readFileToString(scriptFile);
            }
            else
            {
                throw new PipelineJobException("Script path or inline script required");
            }

            // Tell the script engine where the script is and the working directory.
            // ExternalScriptEngine will copy script into working dir to perform replacements.
            Bindings bindings = engine.getBindings(ScriptContext.ENGINE_SCOPE);
            if (scriptFile != null)
                bindings.put(ExternalScriptEngine.SCRIPT_PATH, scriptFile);
            bindings.put(ExternalScriptEngine.WORKING_DIRECTORY, _wd.getDir().getPath());

            Map<String, String> replacements = createReplacements();
            bindings.put(ExternalScriptEngine.PARAM_REPLACEMENT_MAP, replacements);

            // Just output the replaced script, if debug mode is set.
            if (_factory.isPreview())
            {
                // TODO: dump replaced script, for now just dump the replacements
                getJob().header("replacements");
                for (Map.Entry<String, String> entry : replacements.entrySet())
                    getJob().info(entry.getKey() + ": " + entry.getValue());

                return false;
            }

            // Execute the script
            Object o = engine.eval(scriptSource);

            if (_factory.isPipeToOutput())
            {
                TaskPath tpOut = _factory.getOutputPaths().get(WorkDirectory.Function.output.toString());
                assert !tpOut.isSplitFiles() : "Invalid attempt to pipe output to split files.";
                File fileOutput = _wd.newWorkFile(WorkDirectory.Function.output,
                        tpOut, getJobSupport().getBaseName());
                FileUtils.write(fileOutput, String.valueOf(o), "UTF-8");
            }

            File rewrittenScriptFile;
            if (bindings.get(ExternalScriptEngine.REWRITTEN_SCRIPT_FILE) instanceof File)
                rewrittenScriptFile = (File)bindings.get(ExternalScriptEngine.REWRITTEN_SCRIPT_FILE);
            else
                rewrittenScriptFile = scriptFile;

            // TODO: process output?
            // TODO: Perhaps signal to _wd that rewrittenScriptFile is a copied input so it can be deleted

            if (scriptFile != null)
                action.addInput(scriptFile, "Script File"); // CONSIDER: Add replacement script instead?

            return true;
        }
        catch (ScriptException e)
        {
            throw new PipelineJobException(e);
        }
    }

}


