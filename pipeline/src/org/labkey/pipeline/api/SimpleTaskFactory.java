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

import org.apache.commons.io.IOUtils;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.WorkDirectory;
import org.labkey.api.pipeline.cmd.CommandTask;
import org.labkey.api.pipeline.cmd.ExeToCommandArgs;
import org.labkey.api.pipeline.cmd.JobParamToCommandArgs;
import org.labkey.api.pipeline.cmd.ListToCommandArgs;
import org.labkey.api.pipeline.cmd.PathInLine;
import org.labkey.api.pipeline.cmd.RequiredInLine;
import org.labkey.api.pipeline.cmd.TaskPath;
import org.labkey.api.pipeline.cmd.TaskToCommandArgs;
import org.labkey.api.pipeline.cmd.ValueInLine;
import org.labkey.api.pipeline.cmd.ValueToCommandArgs;
import org.labkey.api.pipeline.cmd.ValueWithSwitch;
import org.labkey.api.reports.report.r.ParamReplacementSvc;
import org.labkey.api.resource.Resource;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.util.XmlValidationException;
import org.labkey.pipeline.analysis.CommandTaskImpl;
import org.labkey.pipeline.xml.DoubleInputType;
import org.labkey.pipeline.xml.ExecType;
import org.labkey.pipeline.xml.FileInputOutputType;
import org.labkey.pipeline.xml.FileInputType;
import org.labkey.pipeline.xml.FileOutputType;
import org.labkey.pipeline.xml.InputsType;
import org.labkey.pipeline.xml.IntInputType;
import org.labkey.pipeline.xml.NamedTaskType;
import org.labkey.pipeline.xml.OutputsType;
import org.labkey.pipeline.xml.PropertyInputType;
import org.labkey.pipeline.xml.ScriptType;
import org.labkey.pipeline.xml.SimpleInputType;
import org.labkey.pipeline.xml.TaskDocument;
import org.labkey.pipeline.xml.TextInputType;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User: kevink
 * Date: 11/18/13
 *
 * SimpleTaskFactory is created from a file-based module task definition and will create pipeline
 * Tasks for running command-line tasks (CommandTaskImpl) or execute scripts (ScriptTaskImpl).
 */
public class SimpleTaskFactory extends CommandTaskImpl.Factory
{
    private enum Type { exec, script }

    protected Type _type;
    protected Path _scriptPath = null;
    protected String _scriptExtension = null;
    protected String _scriptInline = null;

    public SimpleTaskFactory(TaskId taskId)
    {
        super(taskId);
        setStatusName(taskId.getName());
    }

    public static SimpleTaskFactory create(TaskId taskId, Resource taskConfig)
    {
        if (taskId.getName() == null)
            throw new IllegalArgumentException("Task factory must by named");

        if (taskId.getType() != TaskId.Type.task)
            throw new IllegalArgumentException("Task factory must by of type 'task'");

        if (taskId.getModuleName() == null)
            throw new IllegalArgumentException("Task factory must be defined by a module");

        Module module = ModuleLoader.getInstance().getModule(taskId.getModuleName());

        TaskDocument doc;
        try
        {
            XmlOptions options = XmlBeansUtil.getDefaultParseOptions();
            doc = TaskDocument.Factory.parse(taskConfig.getInputStream(), options);
            XmlBeansUtil.validateXmlDocument(doc, "Task factory config '" + taskConfig.getPath() + "'");
        }
        catch (XmlValidationException e)
        {
            ScriptTaskImpl.LOG.error(e);
            return null;
        }
        catch (XmlException |IOException e)
        {
            ScriptTaskImpl.LOG.error(e.getMessage());
            return null;
        }

        NamedTaskType xtask = doc.getTask();
        if (xtask == null)
            throw new IllegalArgumentException("<task> element required");

        if (!taskId.getName().equals(xtask.getName()))
            throw new IllegalArgumentException(String.format("Task factory config must have the name '%s'", taskId.getName()));

        SimpleTaskFactory factory = new SimpleTaskFactory(taskId);
        factory.setDeclaringModule(module);

        Path taskDir = taskConfig.getPath().getParent();
        factory.setModuleTaskPath(taskDir);

        Map<String, JobParamToCommandArgs> params = createInputParams(xtask.getInputs());

        Map<String, TaskPath> inputs = createInputPaths(xtask.getInputs());
        factory.setInputPaths(inputs);

        Map<String, TaskPath> outputs = createOutputPaths(xtask.getOutputs());
        factory.setOututPaths(outputs);

        if (xtask.isSetLocation())
            factory.setLocation(xtask.getLocation());

//            if (xtask.isSetLargeWork())
//                factory.setLargeWork(xtask.isLargeWork());

        if (xtask.isSetExec())
        {
            ListToCommandArgs converter = createExecConverter(module, taskDir, xtask.getExec(), inputs, params, outputs);
            factory.setConverter(converter);
            factory._type = Type.exec;
        }
        else if (xtask.isSetScript())
        {
            createScript(factory, xtask.getScript(), inputs, params, outputs);
            factory._type = Type.script;
        }
        else
        {
            throw new IllegalArgumentException("Task factory config must specify one of either <exec> or <script>");
        }

        return factory;

    }

    private static Map<String, TaskPath> createInputPaths(InputsType xinputs)
    {
        Map<String, TaskPath> ret = new LinkedHashMap<>();
        if (xinputs == null)
            return ret;

        for (FileInputType xfileInput : xinputs.getFileArray())
        {
            // TODO: Get switch/help/etc settings to the PathInLine/PathWithSwitch argument created in parseCommand()
            String name = xfileInput.getName();
            TaskPath taskPath = createTaskPath(xfileInput);
            ret.put(name, taskPath);
        }

        return ret;
    }

    private static Map<String, TaskPath> createOutputPaths(OutputsType xoutputs)
    {
        Map<String, TaskPath> ret = new LinkedHashMap<>();
        if (xoutputs == null)
            return ret;

        for (FileOutputType xfileOutput : xoutputs.getFileArray())
        {
            String name = xfileOutput.getName();
            TaskPath taskPath = createTaskPath(xfileOutput);
            ret.put(name, taskPath);
        }

        return ret;
    }

    private static Map<String, JobParamToCommandArgs> createInputParams(InputsType xinputs)
    {
        Map<String, JobParamToCommandArgs> ret = new LinkedHashMap<>();
        if (xinputs == null)
            return ret;

        for (XmlObject xobj : xinputs.selectPath("./*"))
        {
            if (xobj instanceof SimpleInputType)
            {
                SimpleInputType xinput = (SimpleInputType)xobj;
                String name = xinput.getName();

                // CONSIDER: parameter category (group?) to compose a parameter of "group, name"
                String label = xinput.getLabel();
                String description = xinput.getDescription();
                String help = xinput.getHelp();
                String switchName = xinput.getSwitch();
                boolean required = xinput.getRequired();

                ValueToCommandArgs arg;
                if (switchName == null)
                {
                    arg = new ValueInLine();
                }
                else
                {
                    ValueWithSwitch withSwitch = new ValueWithSwitch();
                    withSwitch.setSwitchName(switchName);
                    arg = withSwitch;
                }
                arg.setParameter(name);
                arg.setHelp(help);
                ret.put(name, arg);

                if (xinput instanceof PropertyInputType)
                {
                    // TODO
                }
                else if (xinput instanceof TextInputType)
                {
                    TextInputType xtext = (TextInputType)xinput;
                    if (xtext.isSetDefault())
                        arg.setDefault(xtext.getDefault());
                }
                else if (xinput instanceof IntInputType)
                {
                    IntInputType xint = (IntInputType)xinput;
                    if (xint.isSetDefault())
                        arg.setDefault(String.valueOf(xint.getDefault()));

                    // TODO: Validate the type of the input
                    //arg.setParamValidator();
                }
                else if (xinput instanceof DoubleInputType)
                {
                    DoubleInputType xdouble = (DoubleInputType)xinput;
                    if (xdouble.isSetDefault())
                        arg.setDefault(String.valueOf(xdouble.getDefault()));

                    // TODO: Validate the type of the input
                    //arg.setParamValidator();
                }
            }
        }

        return ret;
    }

    private static TaskPath createTaskPath(FileInputOutputType xfile)
    {
        // TODO: Get switch/help/etc settings to the PathInLine/PathWithSwitch argument created in parseCommand()
        String description = xfile.getDescription();
        String help = xfile.getHelp();
        boolean required = xfile.getRequired();

        TaskPath taskPath;
        if (xfile.isSetSuffixes())
        {
            // Task expects to match inputs based upon the set of suffixes
            FileType fileType = createFileType(xfile);
            taskPath = new TaskPath(fileType);
        }
        else
        {
            // UNDONE: FileAnalysisTaskPipelineImpl expects all inputs to have FileTypes -- inputs matching by exact name aren't supported very well.
//            // Task expects to match inputs based complete filename, not just suffix. See TaskPath.setName().
//            taskPath = new TaskPath();
//            taskPath.setName(xfile.getName());
            throw new IllegalArgumentException("Task inputs and outputs must specify suffixes");
        }
        taskPath.setOptional(!required);
        return taskPath;
    }

    private static FileType createFileType(FileInputOutputType xfile)
    {
        assert xfile.isSetSuffixes();

        //noinspection unchecked
        List<String> suffixes = new ArrayList<String>(xfile.getSuffixes());

        String defaultSuffix = null;
        if (suffixes.size() > 0)
            defaultSuffix = suffixes.get(0);

        boolean dir = xfile.isSetDirectory() && xfile.getDirectory();

        String contentType = xfile.isSetContentType() ? xfile.getContentType() : null;

        FileType ft = new FileType(suffixes, defaultSuffix, dir, FileType.gzSupportLevel.NO_GZ, contentType);
        return ft;
    }

    private static ListToCommandArgs createExecConverter(Module module, Path taskDir, ExecType xexec, Map<String, TaskPath> inputs, Map<String, JobParamToCommandArgs> params, Map<String, TaskPath> outputs)
    {
        ListToCommandArgs args = new ListToCommandArgs();

        String exeName = xexec.getExe();
        String command = xexec.getStringValue();
        if (command != null)
            command = command.trim();

        List<TaskToCommandArgs> converters = parseCommand(module, taskDir, command, exeName, inputs, params, outputs);
        args.setConverters(converters);

        return args;
    }

    /**
     * Parse the command
     * New inputs and outputs may be created if the command contains references that don't exist yet.
     * References to files within the module are converted into module relative paths which will be resolved
     * to an absolute path by CommandTaskImpl.getProcessPaths() at execution time.
     */
    private static List<TaskToCommandArgs> parseCommand(
            Module module, Path taskDir,
            @NotNull String command, @Nullable String exeName,
            Map<String, TaskPath> inputs,
            Map<String, JobParamToCommandArgs> params,
            Map<String, TaskPath> outputs)
    {
        List<TaskToCommandArgs> ret = new ArrayList<>();

        // TODO: Better parsing: handle quoting and whitespace in tokens
        String[] parts = command.split(" ");
        for (int i = 0; i < parts.length; i++)
        {
            String part = parts[i];
            part = part.trim();
            if (part.length() == 0)
                continue;

            TaskToCommandArgs arg = null;

            if (part.startsWith("${") && part.endsWith("}"))
            {
                //
                // Token replacement
                //

                String key = part.substring("${".length(), part.length() - "}".length());
                if (exeName != null && (key.equals("exe") || key.equals(exeName)))
                {
                    ExeToCommandArgs exe = new ExeToCommandArgs();
                    exe.setExePath(exeName);
                    arg = exe;
                }
                else if (inputs.containsKey(key))
                {
                    TaskPath taskPath = inputs.get(key);
                    // TODO: Get help, switch, optional/required settings
                    PathInLine path = new PathInLine();
                    path.setFunction(WorkDirectory.Function.input);
                    path.setKey(key);
                    arg = path;
                }
                else if (outputs.containsKey(key))
                {
                    TaskPath taskPath = outputs.get(key);
                    PathInLine path = new PathInLine();
                    path.setFunction(WorkDirectory.Function.output);
                    path.setKey(key);
                    arg = path;
                }
                else if (params.containsKey(key))
                {
                    arg = params.get(key);
                }
                else
                {
                    //
                    // Unknown token
                    //

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
                        arg = param;
                    }
                }
            }
            else
            {
                //
                // String literal
                //

                // Attempt to resolve the token to a resource within the module.
                // For example, if "script.R" resolves to "pipeline/tasks/script.R", add it as a PathInLine argument.
                Resource r = findResource(module, taskDir, part);

                // If we don't have an exe name yet, the first token MUST be the executable name
                if (exeName == null)
                {
                    // CONSIDER: If we resolved the part to a resource in the module, use it as the executable name.
                    exeName = part;
                    ExeToCommandArgs exe = new ExeToCommandArgs();
                    exe.setExePath(exeName);
                    arg = exe;
                }
                else
                {
                    if (r != null)
                    {
                        // Add a refernce to the path relative from the module root.
                        PathInLine p = new PathInLine();
                        p.setFunction(WorkDirectory.Function.module);
                        p.setKey(r.getPath().toString());
                        arg = p;
                    }
                    else
                    {
                        // Add the part as a literal string.
                        RequiredInLine literal = new RequiredInLine();
                        literal.setValue(part);
                        arg = literal;
                    }
                }
            }

            if (arg != null)
                ret.add(arg);
        }

        return ret;
    }

    /**
     * Check if the part is a filename that exists in the module.
     * Must be relative from either the current directory or from the module's root
     * and are under the 'pipeline' directory. Resources from the root directory are disallowed.
     *
     * <pre>
     *     "a.foo"         => "<module>/pipeline/tasks/<task-name>/a.foo"
     *     "pipline/a.foo" => "<module>/pipeline/a.foo"
     *     "query/a.foo"   => not allowed
     * </pre>
     */
    @Nullable
    private static Resource findResource(@NotNull Module module, @NotNull Path taskDir, @NotNull String part)
    {
        Path partPath = Path.parse(part);
        Path path = taskDir.append(partPath);
        Resource r = module.getModuleResource(path);
        if (r == null && partPath.size() > 1 && partPath.toString().startsWith(PipelineJobServiceImpl.MODULE_PIPELINE_DIR))
            r = module.getModuleResource(partPath);

        return r;
    }

    /**
     * Attempt to resolve the token as an input.
     *
     * The unknown token must start with "input", and is one of the forms:
     * <pre>
     *     ${input}      => resolves to first input, will not create any new inputs.
     *     ${input.txt}  => resolves to first input. If it already exists, it must have a .txt suffix or name of 'input.txt'.
     *                      If it doesn't exist, a new input will be created with the extension '.txt'.
     *     ${input1}     => resolves to first input, will not create any new inputs.
     *     ${input2.txt} => resolves to second input.  If it already exists, it must have .txt suffix or name of 'input2.txt'.
     *                      If it doesn't exist and there is already at least one input, a second input is created with extension '.txt'.
     *     ${inputX.txt} => Non-numeric index is not allowed.
     * </pre>
     * Indices are 1-based.
     *
     * If the input can't be resolved or if there is a conflict (extensions don't match or input index is too large),
     * an <code>IllegalArgumentException</code> is thrown.
     *
     * @param inputs An ordered map of current inputs.  New inputs may be added to the map.
     * @param key The token.
     * @return A PathInLine for the resolved or created input.
     */
    private static PathInLine parseUnknownInput(Map<String, TaskPath> inputs, String key)
    {
        return parseUnknownInputOutput(WorkDirectory.Function.input, inputs, key);
    }

    private static PathInLine parseUnknownOutput(Map<String, TaskPath> outputs, String key)
    {
        return parseUnknownInputOutput(WorkDirectory.Function.output, outputs, key);
    }

    private static PathInLine parseUnknownInputOutput(WorkDirectory.Function pathType, Map<String, TaskPath> paths, String key)
    {
        String prefix = pathType.name();
        assert key.startsWith(prefix);

        // CONSIDER: take extension from first dot after 'index' to allow multi-part extensions: .txt.gz
        // Get a file extension if it exists
        String keyExt = FileUtil.getExtension(key);
        if (keyExt != null)
            keyExt = "." + keyExt;

        // Find an int at the end of "input" and before the extension if it exists.
        String num;
        if (keyExt != null)
            num = key.substring(prefix.length(), key.length() - keyExt.length());
        else
            num = key.substring(prefix.length());

        // Index is 1-based
        int index = 1;
        if (num.length() > 0)
        {
            try
            {
                index = Integer.parseInt(num);
                if (index < 1)
                    throw new IllegalArgumentException("File replacement '${" + key + "}' index must be greater than 0.");

                // Can't reference or create a new input at arbitrary indices
                if (index > paths.size()+1)
                    throw new IllegalArgumentException("File replacement '${" + key + "}' index must be less than current " + prefix + " size '" + (paths.size()+1) + "'.");
            }
            catch (NumberFormatException e)
            {
                // num isn't an integer value
                throw new IllegalArgumentException("File replacement '${" + key + "}' must have numeric index greater than 0.");
            }
        }

        // Paths must be ordered so we can refer to them by index
        assert paths instanceof LinkedHashMap;
        Set<String> pathsKeySet = paths.keySet();
        String[] pathsKeys = pathsKeySet.toArray(new String[pathsKeySet.size()]);

        String pathKey = null;
//        if (index == -1)
//        {
//            // We have the form 'inputXYZ' or 'inputXYZ.txt'
//            // The key must not already exist in the inputs map.
//            assert !paths.containsKey(key) : "File replacement ${" + key + "} already exists; should have been found earlier in parseCommand().";
//
//            // Create a new input and add it to inputs map
//            TaskPath newPath = new TaskPath();
//            newPath.setName(key);
//
//            paths.put(key, newPath);
//            pathKey = key;
//        }
//        else
        if (index < pathsKeys.length+1)
        {
            // Reference an existing input/output
            pathKey = pathsKeys[index-1];
            TaskPath existing = paths.get(pathKey);
            assert existing != null;

            if (existing.getType() != null)
            {
                // If the user included an extension and the matched path has a FileType, check the suffix matches.
                if (keyExt != null && !keyExt.equals(existing.getType().getDefaultSuffix()))
                    throw new IllegalArgumentException("File replacement '${" + key + "}' extension doesn't match exepcted file type: " + existing.getType());
            }
//            else
//            {
//                // If the matched input has a name, check the entire ${input} name matches.
//                assert existing.getName() != null;
//                if (existing.getName().equals(key))
//                    throw new IllegalArgumentException("File replacement '${" + key + "}' name doesn't match exepcted file name: " + existing.getName());
//            }

        }
        else if (index == pathsKeys.length+1)
        {
            // Create a new input and add it to inputs map
            TaskPath newPath = new TaskPath();
            if (keyExt == null)
                throw new IllegalArgumentException("File replacement '${" + key + "}' not found.  Implicitly created inputs must have an extension");

            newPath.setExtension(keyExt);
            paths.put(key, newPath);
            pathKey = key;
        }

        // This shouldn't happen -- we've checked the index is in range and the inputs map shouldn't contain null entries.
        if (pathKey == null)
            throw new IllegalStateException("Failed to resolve or create file replacement for '${" + key + "}'");

        PathInLine path = new PathInLine();
        path.setFunction(pathType);
        path.setKey(pathKey);
        return path;
    }


    private static void createScript(SimpleTaskFactory factory, ScriptType xscript, Map<String, TaskPath> inputs, Map<String, JobParamToCommandArgs> params, Map<String, TaskPath> outputs)
    {
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
                source = IOUtils.toString(is);
                tokens = tokens(source);
            }
            catch (IOException e)
            {
                throw new IllegalArgumentException("Failed to read script file.");
            }

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

            tokens = tokens(script);
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

    private static Set<String> tokens(String script)
    {
        // Preserving the order the tokens is found is important -- ${input1.txt} must be found before ${input2.txt}
        Set<String> tokens = new LinkedHashSet<>();
        Pattern pattern = ParamReplacementSvc.defaultScriptPattern;
        Matcher m = pattern.matcher(script);

        while (m.find())
        {
            String token = m.group(1);
            if (token != null && token.length() > 0)
                tokens.add(token);
        }

        return tokens;
    }

    @Override
    public boolean isParticipant(PipelineJob job) throws IOException
    {
        if (_type == Type.exec)
            return super.isParticipant(job);
        else
            return true;
    }

    @Override
    public CommandTaskImpl createTask(PipelineJob job)
    {
        if (_type == Type.exec)
            return new CommandTaskImpl(job, this);
        else if (_type == Type.script)
            return new ScriptTaskImpl(job, this);
        throw new IllegalStateException();
    }

    @Override
    public String[] toArgs(CommandTask task) throws IOException
    {
        assert _type == Type.exec;
        return super.toArgs(task);
    }


    public static class ParseInputTests
    {
        private void assertTaskPath(Map<String, TaskPath> inputs, String key, String name, String defaultExt)
        {
            TaskPath path = inputs.get(key);
            Assert.assertNotNull("Expected TaskPath in input map with key '" + key + "'", path);

            if (name == null)
                Assert.assertNull("Expected TaskPath without name", path.getName());
            else
                Assert.assertEquals("Expected TaskPath name", name, path.getName());

            if (defaultExt == null)
                Assert.assertNull("Expected TaskPath without extension", path.getType().getDefaultSuffix());
            else
                Assert.assertEquals("Expected TaskPath with extension", defaultExt, path.getType().getDefaultSuffix());
        }

        @Test
        public void implicitInputExtensionRequired()
        {
            Map<String, TaskPath> inputs = new LinkedHashMap<>();
            try
            {
                SimpleTaskFactory.parseUnknownInput(inputs, "input");
                Assert.fail("Expected IllegalArgumentException");
            }
            catch (IllegalArgumentException e)
            {
                Assert.assertEquals("File replacement '${input}' not found.  Implicitly created inputs must have an extension", e.getMessage());
            }
        }

        @Test
        public void inputCreated()
        {
            Map<String, TaskPath> inputs = new LinkedHashMap<>();
            PathInLine arg = SimpleTaskFactory.parseUnknownInput(inputs, "input.txt");
            Assert.assertEquals("Expected PathInLine argument with name 'input.txt'", "input.txt", arg.getKey());
            Assert.assertEquals(1, inputs.size());
            assertTaskPath(inputs, "input.txt", null, ".txt");
        }

        @Test
        public void inputExists()
        {
            Map<String, TaskPath> inputs = new LinkedHashMap<>();
            TaskPath path1 = new TaskPath(".1st");
            inputs.put("first", path1);

            PathInLine arg1 = SimpleTaskFactory.parseUnknownInput(inputs, "input");
            Assert.assertEquals("Expected PathInLine argument with name 'first'", "first", arg1.getKey());
            Assert.assertEquals("Expected no new inputs", 1, inputs.size());

            PathInLine arg2 = SimpleTaskFactory.parseUnknownInput(inputs, "input.1st");
            Assert.assertEquals("Expected PathInLine argument with name 'first'", "first", arg2.getKey());
            Assert.assertEquals("Expected no new inputs", 1, inputs.size());

            PathInLine arg3 = SimpleTaskFactory.parseUnknownInput(inputs, "input1");
            Assert.assertEquals("Expected PathInLine argument with name 'first'", "first", arg3.getKey());
            Assert.assertEquals("Expected no new inputs", 1, inputs.size());

            PathInLine arg4 = SimpleTaskFactory.parseUnknownInput(inputs, "input1.1st");
            Assert.assertEquals("Expected PathInLine argument with name 'first'", "first", arg4.getKey());
            Assert.assertEquals("Expected no new inputs", 1, inputs.size());
        }

        @Test
        public void inputExists2()
        {
            Map<String, TaskPath> inputs = new LinkedHashMap<>();
            TaskPath path1 = new TaskPath(".1st");
            inputs.put("first", path1);

            TaskPath path2 = new TaskPath(".2nd");
            inputs.put("second", path2);

            PathInLine arg1 = SimpleTaskFactory.parseUnknownInput(inputs, "input2");
            Assert.assertEquals("Expected PathInLine argument with name 'second'", "second", arg1.getKey());
            Assert.assertEquals("Expected no new inputs", 2, inputs.size());

            PathInLine arg2 = SimpleTaskFactory.parseUnknownInput(inputs, "input2.2nd");
            Assert.assertEquals("Expected PathInLine argument with name 'second'", "second", arg2.getKey());
            Assert.assertEquals("Expected no new inputs", 2, inputs.size());

            PathInLine arg3 = SimpleTaskFactory.parseUnknownInput(inputs, "input3.3rd");
            Assert.assertEquals("Expected PathInLine argument with name 'input3.3rd", "input3.3rd", arg3.getKey());
            Assert.assertEquals("Expected new input created", 3, inputs.size());
            assertTaskPath(inputs, "input3.3rd", null, ".3rd");

            try
            {
                SimpleTaskFactory.parseUnknownInput(inputs, "input4");
                Assert.fail("Expected Exception");
            }
            catch (IllegalArgumentException e)
            {
                Assert.assertEquals("File replacement '${input4}' not found.  Implicitly created inputs must have an extension", e.getMessage());
            }
        }

        @Test
        public void inputIndexOutOfBounds()
        {
            Map<String, TaskPath> inputs = new LinkedHashMap<>();
            try
            {
                SimpleTaskFactory.parseUnknownInput(inputs, "input0");
                Assert.fail("Expected IllegalArgumentException");
            }
            catch (IllegalArgumentException e)
            {
                Assert.assertEquals("File replacement '${input0}' index must be greater than 0.", e.getMessage());
            }

            try
            {
                SimpleTaskFactory.parseUnknownInput(inputs, "input2");
                Assert.fail("Expected IllegalArgumentException");
            }
            catch (IllegalArgumentException e)
            {
                Assert.assertEquals("File replacement '${input2}' index must be less than current input size '1'.", e.getMessage());
            }

            try
            {
                SimpleTaskFactory.parseUnknownInput(inputs, "input100.txt");
                Assert.fail("Expected IllegalArgumentException");
            }
            catch (IllegalArgumentException e)
            {
                Assert.assertEquals("File replacement '${input100.txt}' index must be less than current input size '1'.", e.getMessage());
            }
        }

        @Test
        public void inputExtensionMatches()
        {
            Map<String, TaskPath> inputs = new LinkedHashMap<>();
            inputs.put("first", new TaskPath(".foo"));

            PathInLine arg1 = SimpleTaskFactory.parseUnknownInput(inputs, "input.foo");
            Assert.assertEquals("Expected PathInLine argument with name 'first'", "first", arg1.getKey());
            Assert.assertEquals("Expected no new inputs", 1, inputs.size());

            try
            {
                SimpleTaskFactory.parseUnknownInput(inputs, "input.txt");
                Assert.fail("Expected IllegalArgumentException");
            }
            catch (IllegalArgumentException e)
            {
                Assert.assertEquals("File replacement '${input.txt}' extension doesn't match exepcted file type: [.foo]", e.getMessage());
            }
        }

    }
}
