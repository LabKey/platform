package org.labkey.pipeline.api;

import common.Logger;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlOptions;
import org.jetbrains.annotations.NotNull;
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
import org.labkey.api.pipeline.cmd.ValueToMultiCommandArgs;
import org.labkey.api.pipeline.cmd.ValueWithSwitch;
import org.labkey.api.resource.Resource;
import org.labkey.api.util.FileType;
import org.labkey.api.util.Pair;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.util.StringSubstitution;
import org.labkey.api.util.SubstitutionFormat;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.util.XmlValidationException;
import org.labkey.pipeline.analysis.CommandTaskImpl;
import org.labkey.pipeline.xml.DoubleInputType;
import org.labkey.pipeline.xml.ExecType;
import org.labkey.pipeline.xml.FileInputType;
import org.labkey.pipeline.xml.InputType;
import org.labkey.pipeline.xml.InputsType;
import org.labkey.pipeline.xml.IntInputType;
import org.labkey.pipeline.xml.NamedTaskType;
import org.labkey.pipeline.xml.OutputsType;
import org.labkey.pipeline.xml.PropertyInputType;
import org.labkey.pipeline.xml.TaskDocument;
import org.labkey.pipeline.xml.TextInputType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: kevink
 * Date: 11/14/13
 */
public class ScriptTask extends CommandTaskImpl
{
    public static final Logger LOG = Logger.getLogger(ScriptTask.class);

    public ScriptTask(PipelineJob job, Factory factory)
    {
        super(job, factory);
    }

    public static class Factory extends CommandTaskImpl.Factory
    {
        public Factory(TaskId taskId)
        {
            super(taskId);
            setStatusName(taskId.getName());
        }

        public static Factory create(TaskId taskId, Resource taskConfig)
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
            catch (XmlException |XmlValidationException |IOException e)
            {
                LOG.error(e.getMessage());
                return null;
            }

            NamedTaskType xtask = doc.getTask();
            if (xtask == null)
                throw new IllegalArgumentException("<task> element required");

            if (!taskId.getName().equals(xtask.getName()))
                throw new IllegalArgumentException(String.format("Task factory config must have the name '%s'", taskId.getName()));

            Factory factory = new Factory(taskId);
            factory.setDeclaringModule(module);

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
                ListToCommandArgs converter = createExecConverter(xtask.getExec(), inputs, params, outputs);
                factory.setConverter(converter);
            }
            else if (xtask.isSetScript())
            {
                // TODO
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

            for (FileInputType xfileInput : xinputs.getFileArray())
            {
                // TODO: Get settings to the path argument
                String name = xfileInput.getName();
                String label = xfileInput.getLabel();
                String description = xfileInput.getDescription();
                String help = xfileInput.getHelp();
                String switchName = xfileInput.getSwitch();
                boolean required = xfileInput.getRequired();

                FileType fileType = createFileInput(xfileInput);
                TaskPath taskPath = new TaskPath(fileType);
                taskPath.setOptional(!required);

                ret.put(name, taskPath);
            }

            return ret;
        }

        private static Map<String, JobParamToCommandArgs> createInputParams(InputsType xinputs)
        {
            Set<String> names = new HashSet<>();
            Map<String, JobParamToCommandArgs> ret = new LinkedHashMap<>();

            for (XmlObject xobj : xinputs.selectPath("./*"))
            {
                if (xobj instanceof InputType)
                {
                    InputType xinput = (InputType)xobj;
                    String name = xinput.getName();
                    if (names.contains(name))
                        throw new IllegalArgumentException("Duplicate input name '" + name + "'");

                    if (xobj instanceof FileInputType)
                        continue;

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

        private static FileType createFileInput(FileInputType file)
        {
            //noinspection unchecked
            List<String> suffixes = new ArrayList<String>(file.getSuffixes());

            String defaultSuffix = null;
            if (suffixes.size() > 0)
                defaultSuffix = suffixes.get(0);

            boolean dir = file.isSetDirectory() && file.getDirectory();

            FileType.gzSupportLevel gz = FileType.gzSupportLevel.NO_GZ;
            if (file.isSetGz())
            {
                if (file.getGz() == FileInputType.Gz.SUPPORTS)
                    gz = FileType.gzSupportLevel.SUPPORT_GZ;
                else if (file.getGz() == FileInputType.Gz.PREFERS)
                    gz = FileType.gzSupportLevel.PREFER_GZ;
            }

            String contentType = file.isSetContentType() ? file.getContentType() : null;

            FileType ft = new FileType(suffixes, defaultSuffix, dir, gz, contentType);
            return ft;
        }

        private static Map<String, TaskPath> createOutputPaths(OutputsType outputs)
        {
            Set<String> names = new HashSet<>();
            Map<String, TaskPath> ret = new LinkedHashMap<String, TaskPath>();

            // UNDONE

            return ret;
        }

        private static ListToCommandArgs createExecConverter(ExecType exec, Map<String, TaskPath> inputs, Map<String, JobParamToCommandArgs> params, Map<String, TaskPath> outputs)
        {
            ListToCommandArgs args = new ListToCommandArgs();

            String exeName = exec.getExe();
            String command = exec.getStringValue();
            if (command != null)
                command = command.trim();

            List<TaskToCommandArgs> converters = parseCommand(command, exeName, inputs, params, outputs);
            args.setConverters(converters);

            return args;
        }

        private static List<TaskToCommandArgs> parseCommand(
                String command, String exeName,
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

                if (part.startsWith("${") && part.endsWith("}"))
                {
                    String key = part.substring("${".length(), part.length() - "}".length());
                    if (key.equals("exe") || key.equals(exeName))
                    {
                        ExeToCommandArgs arg = new ExeToCommandArgs();
                        arg.setExePath(exeName);
                        ret.add(arg);
                    }
                    else if (inputs.containsKey(key))
                    {
                        TaskPath taskPath = inputs.get(key);
                        // TODO: Get help, switch, optional/required settings
                        PathInLine arg = new PathInLine();
                        arg.setFunction(WorkDirectory.Function.input);
                        arg.setKey(key);
                        ret.add(arg);
                    }
                    else if (outputs.containsKey(key))
                    {
                        TaskPath taskPath = outputs.get(key);
                        PathInLine arg = new PathInLine();
                        arg.setFunction(WorkDirectory.Function.output);
                        arg.setKey(key);
                        ret.add(arg);
                    }
                    else if (params.containsKey(key))
                    {
                        JobParamToCommandArgs arg = params.get(key);
                        ret.add(arg);
                    }
                    else
                    {
                        // unknown parameter or input/output
                        // CONSIDER: If it's ${input} add to inputs and otherwise add the key to the params map?
                        // CONSIDER: For ${input.txt} we can create an input with .txt extension
                        // CONSIDER: For more than one input the input can end in an index: ${input2.foo}
                        JobParamToCommandArgs arg = new ValueInLine();
                        arg.setParameter(key);
                        ret.add(arg);
                    }
                }
                else
                {
                    RequiredInLine arg = new RequiredInLine();
                    arg.setValue(part);
                    ret.add(arg);
                }
            }

            return ret;
        }

        @Override
        public CommandTaskImpl createTask(PipelineJob job)
        {
            return new ScriptTask(job, this);
        }

        @Override
        public String[] toArgs(CommandTask task) throws IOException
        {
            // See: JobParamToCommandArgs.getValue(job)
            // ExeToCommandArgs ?
            return super.toArgs(task);
        }
    }

    // XXX: Only called by applyEnvironment() at the moment, but may be useful for the inline scripts?
    @Override
    public String variableSubstitution(String src, Map<String, String> map)
    {
        return super.variableSubstitution(src, map);
    }

}


