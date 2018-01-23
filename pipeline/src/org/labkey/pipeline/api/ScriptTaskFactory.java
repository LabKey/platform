/*
 * Copyright (c) 2013-2017 LabKey Corporation
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

import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.TaskFactory;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.XMLBeanTaskFactoryFactory;
import org.labkey.api.pipeline.cmd.CommandTask;
import org.labkey.api.pipeline.cmd.JobParamToCommandArgs;
import org.labkey.api.pipeline.cmd.TaskPath;
import org.labkey.api.pipeline.cmd.TaskToCommandArgs;
import org.labkey.api.pipeline.cmd.ValueInLine;
import org.labkey.api.reports.report.r.ParamReplacementSvc;
import org.labkey.api.resource.Resource;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.pipeline.analysis.CommandTaskImpl;
import org.labkey.pipeline.xml.ScriptTaskType;
import org.labkey.pipeline.xml.ScriptType;
import org.labkey.pipeline.xml.TaskType;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User: kevink
 * Date: 12/30/13
 */
public class ScriptTaskFactory extends SimpleTaskFactory
{
    protected Path _scriptPath = null;
    protected String _scriptExtension = null;
    protected String _scriptInline = null;

    public ScriptTaskFactory(TaskId taskId)
    {
        super(taskId);
        setStatusName(taskId.getName());
    }

    public static class FactoryFactory implements XMLBeanTaskFactoryFactory
    {
        @Override
        public TaskFactory create(TaskId taskId, TaskType xobj, Path tasksDir)
        {
            if (!(xobj instanceof ScriptTaskType))
                throw new IllegalArgumentException("XML instance must be a ScriptTaskType");

            return ScriptTaskFactory.create(taskId, (ScriptTaskType)xobj, tasksDir);
        }
    }

    private static ScriptTaskFactory create(TaskId taskId, ScriptTaskType xtask, Path tasksDir)
    {
        ScriptTaskFactory factory = new ScriptTaskFactory(taskId);

        SimpleTaskFactory.create(factory, xtask, tasksDir);

        createScript(factory, xtask.getScript());

        return factory;
    }

    private static void createScript(ScriptTaskFactory factory, ScriptType xscript)
    {
        if (xscript == null)
            throw new IllegalArgumentException("Script task requires <script> element");

        if (xscript.isSetTimeout() && xscript.getTimeout() > 0)
            factory.setTimeout(xscript.getTimeout());

        Map<String, TaskPath> inputs = factory.getInputPaths();
        Map<String, TaskPath> outputs = factory.getOutputPaths();
        Map<String, JobParamToCommandArgs> params = factory._params;

        Set<String> tokens;

        if (xscript.getFile() != null)
        {
            // find the file
            String file = xscript.getFile();
            Resource r = findResource(factory.getDeclaringModule(), factory.getModuleTaskPath(), file);
            if (r == null || !r.isFile())
                throw new IllegalArgumentException("script file not found: " + file);

            String source;

            try (InputStream is = r.getInputStream())
            {
                source = PageFlowUtil.getStreamContentsAsString(is);
            }
            catch (IOException e)
            {
                throw new IllegalArgumentException("Failed to read script file.");
            }

            tokens = ParamReplacementSvc.get().tokens(source);
            String ext = xscript.getInterpreter();
            if (ext == null)
                ext = FileUtil.getExtension(file);
            if (ext == null)
                throw new IllegalArgumentException("Failed to determine script interpreter from script filename.  Please add an 'interpreter' attribute or an extension to the script filename.");

            factory._scriptPath = r.getPath();
            factory._scriptExtension = ext;
        }
        else if (xscript.getInterpreter() != null)
        {
            // inline script
            String script = xscript.getStringValue();
            if (script == null)
                throw new IllegalArgumentException("<script> element must have one of either 'file' attribute or 'interpreter' attribute with an inline script.");

            tokens = ParamReplacementSvc.get().tokens(script);
            factory._scriptInline = script;
            factory._scriptExtension = xscript.getInterpreter();
        }
        else
        {
            throw new IllegalArgumentException("<script> element must have one of either 'file' attribute or 'interpreter' attribute with an inline script.");
        }

        // Check script engine available for the extension
        ensureEngine(factory._scriptExtension);

        // Add any implicit inputs and outputs from the script
        for (String key : tokens)
        {
            if (RESERVED_TOKENS.contains(key))
                continue;

            if (!(inputs.containsKey(key) || outputs.containsKey(key) || params.containsKey(key)))
            {
                TaskToCommandArgs arg = null;
                if (key.startsWith("input"))
                {
                    // Attempt to resolve unknown input
                    arg = parseUnknownInput(inputs, key);
                }
                else if (key.startsWith("output"))
                {
                    // Attempt to resolve unknown output
                    arg = parseUnknownOutput(outputs, key);
                }

                if (arg == null)
                {
                    // Not found in inputs, outputs, or params and doesn't start with "input" or "output".
                    // Treat as a unknown parameter
                    ValueInLine param = new ValueInLine();
                    param.setParameter(key);
                    params.put(key, param);
                }
            }
        }
    }

    private static ScriptEngine ensureEngine(String interpreter)
    {
        ScriptEngineManager mgr = ServiceRegistry.get().getService(ScriptEngineManager.class);
        if (mgr == null)
            throw new IllegalStateException("Script engine manager not available");

        ScriptEngine engine = mgr.getEngineByName(interpreter);
        if (engine == null)
            engine = mgr.getEngineByExtension(interpreter);
        if (engine == null)
            throw new IllegalArgumentException("Script engine not found: " + interpreter);
        return engine;
    }

    @Override
    public boolean isParticipant(PipelineJob job) throws IOException
    {
        return true;
    }

    @Override
    public CommandTaskImpl createTask(PipelineJob job)
    {
        return new ScriptTaskImpl(job, this);
    }

    @Override
    public List<String> toArgs(CommandTask task) throws IOException
    {
        throw new IllegalStateException();
    }
}
