/*
 * Copyright (c) 2008-2018 LabKey Corporation
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
package org.labkey.pipeline.analysis;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jmock.Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.assay.DefaultDataTransformer;
import org.labkey.api.data.Container;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.module.Module;
import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.WorkDirectory;
import org.labkey.api.pipeline.WorkDirectoryTask;
import org.labkey.api.pipeline.cmd.CommandTask;
import org.labkey.api.pipeline.cmd.CommandTaskFactorySettings;
import org.labkey.api.pipeline.cmd.ListToCommandArgs;
import org.labkey.api.pipeline.cmd.TaskPath;
import org.labkey.api.pipeline.cmd.TaskToCommandArgs;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.resource.FileResource;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityManager.TransformSession;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.FileType;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.Path;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <code>CommandTask</code>
 *
 * @author brendanx
 */
public class CommandTaskImpl extends WorkDirectoryTask<CommandTaskImpl.Factory> implements CommandTask
{
    public static FileType OUTPUT_PARAMS = new FileType(".params-out.tsv");

    public static class Factory extends AbstractTaskFactory<CommandTaskFactorySettings, Factory>
    {
        private String _statusName = "COMMAND";
        private String _protocolActionName;
        private Map<String, String> _environment = new HashMap<>();
        private Map<String, TaskPath> _inputPaths = new HashMap<>();
        private Map<String, TaskPath> _outputPaths = new HashMap<>();
        private ListToCommandArgs _converter = new ListToCommandArgs();
        private boolean _copyInput;
        private boolean _removeInput;
        private boolean _pipeToOutput;
        private int _pipeOutputLineInterval;
        private boolean _preview;
        private String _actionableInput;
        private String _installPath;
        private Integer _timeout;
        private Path _moduleTaskPath;

        public Factory()
        {
            super(new TaskId(CommandTask.class));
        }

        public Factory(String name)
        {
            super(new TaskId(CommandTask.class, name));
        }

        public Factory(TaskId taskId)
        {
            super(taskId);
        }

        @Override
        protected void configure(CommandTaskFactorySettings settings)
        {
            super.configure(settings);

            if (settings.getStatusName() != null)
                _statusName = settings.getStatusName();

            if (settings.getProtocolActionName() != null)
                _protocolActionName = settings.getProtocolActionName();

            if (settings.getInputPaths() != null && settings.getInputPaths().size() > 0)
                _inputPaths = settings.getInputPaths();

            if (settings.getOutputPaths() != null && settings.getOutputPaths().size() > 0)
                _outputPaths = settings.getOutputPaths();

            if (settings.getEnvironment() != null && !settings.getEnvironment().isEmpty())
                _environment = settings.getEnvironment();

            if (settings.getConverter() != null && settings.getConverter().getConverters() != null)
                _converter = settings.getConverter();    

            if (settings.isCopyInputSet())
                _copyInput = settings.isCopyInput();

            if (settings.isRemoveInputSet())
                _removeInput = settings.isRemoveInput();

            if (settings.isPipeToOutputSet())
                _pipeToOutput = settings.isPipeToOutput();

            if (settings.isPipeOutputLineIntervalSet())
                _pipeOutputLineInterval = settings.getPipeOutputLineInterval();

            if (settings.isPreviewSet())
                _preview = settings.isPreview();

            if (settings.getActionableInput() != null)
                _actionableInput = settings.getActionableInput();

            if (settings.getInstallPath() != null)
                _installPath = settings.getInstallPath();

            if (settings.getTimeout() != null)
                _timeout = settings.getTimeout();
        }

        @Override
        public CommandTaskImpl createTask(PipelineJob job)
        {
            return new CommandTaskImpl(job, this);
        }

        protected String getProtocolActionName()
        {
            if (_protocolActionName == null)
            {
                return getId().getName();
            }
            else
            {
                return _protocolActionName;
            }
        }

        @Override
        public List<String> getProtocolActionNames()
        {
            return Collections.singletonList(getProtocolActionName());
        }

        @Override
        public String getStatusName()
        {
            return _statusName;
        }

        protected void setStatusName(String statusName)
        {
            _statusName = statusName;
        }

        public String getInstallPath()
        {
            return _installPath;
        }

        public void setInstallPath(String installPath)
        {
            _installPath = installPath;
        }

        @Override
        public boolean isJobComplete(PipelineJob job)
        {
            // TODO: Safer way to do this.
            FileAnalysisJobSupport support = (FileAnalysisJobSupport) job;
            int outputCount = 0;
            boolean hasOptional = false;
            for (TaskPath tp : getOutputPaths().values())
            {
                // TODO: Join somehow with code in CommandTaskImpl
                FileType type = tp.getType();

                // TODO: do we really want to only check for files of default extension type?
                //       possibly author did not realize that type.getName single-arg overload used here does that
                //       (bpratt renamed it to getDefaultName to make its function more obvious)
                String fileName;
                if (type == null)
                {
                    fileName = tp.getName();
                }
                else
                {
                    // CONSIDER: More flexable input/output file naming -- perhaps a string expression with protocol, task, job-id available.
                    // CONSIDER: Or explicitly wire outputs from an upstream task as an input to this task which would make the baseName concept less important.
                    String baseName = support.getBaseName();
                    if (tp.isUseProtocolNameAsBaseName())
                        baseName = support.getProtocolName();
                    else if (tp.isUseFileTypeBaseName())
                        baseName = support.getBaseNameForFileType(tp.getType());

                    fileName = type.getDefaultName(baseName);
                }

                File result;
                // Check if the output is specifically flagged to go into a special location so we check in the right
                // place when deciding if the task has already been performed
                switch (tp.getOutputLocation())
                {
                    case ANALYSIS_DIR:
                        result = new File(support.getAnalysisDirectory(), fileName);
                        break;

                    case DATA_DIR:
                        result = new File(support.getDataDirectory(), fileName);
                        break;

                    case PATH:
                        result = support.findOutputFile(tp.getOutputDir(), fileName);
                        break;

                    case DEFAULT:
                    default:
                        result = support.findOutputFile(fileName);
                        break;
                }

                if (tp.isOptional())
                {
                    hasOptional = true;
                }
                else
                {
                    if(!result.exists())
                    {
                        return false;
                    }
                }
                if (result.exists())
                {
                    outputCount++;
                }
            }

            if (hasOptional)
            {
                return outputCount > 0;
            }

            // If there were no output paths, then the command is run for some
            // other reason than its outputs.
            return (getOutputPaths().size() > 0);
        }

        @Override
        public boolean isParticipant(PipelineJob job) throws IOException
        {
            if (!super.isParticipant(job))
            {
                return false;
            }
            
            // The first converter is responsible for the command name.
            List<TaskToCommandArgs> converters = getConverters();
            assert converters != null && converters.size() > 0 :
                    "No converters found in " + getId();
            TaskToCommandArgs commandNameConverter = converters.get(0);

            CommandTaskImpl task = createTask(job);
            // Need to set up the work directory so that it can figure out what arguments will be used in the call
            WorkDirectory wd = createWorkDirectory(job.getJobGUID(), job.getJobSupport(FileAnalysisJobSupport.class), job.getLogger());
            task.setWorkDirectory(wd);
            try
            {
                // If it produces nothing for the command line, then this command should not be executed.
                return commandNameConverter.toArgs(task, job.getParameters(), new HashSet<TaskToCommandArgs>()).size() != 0;
            }
            finally
            {
                wd.remove(true);
                task.setWorkDirectory(null);
            }
        }

        @Override
        public List<FileType> getInputTypes()
        {
            if (_actionableInput == null)
            {
                // If the config doesn't specify that only one of the inputs should have a button next to it,
                // use them all
                List<FileType> result = new ArrayList<>(_inputPaths.size());
                for (TaskPath taskPath : _inputPaths.values())
                {
                    FileType ft = taskPath.getType();
                    if (ft != null)
                        result.add(ft);
                }
                return result;
            }
            TaskPath tp = _inputPaths.get(_actionableInput);
            return (tp == null ? null : Collections.singletonList(tp.getType()));
        }

        public Map<String, TaskPath> getInputPaths()
        {
            return _inputPaths;
        }

        protected void setInputPaths(Map<String, TaskPath> inputPaths)
        {
            _inputPaths = inputPaths;
        }

        public Map<String, TaskPath> getOutputPaths()
        {
            return _outputPaths;
        }

        protected void setOututPaths(Map<String, TaskPath> outputPaths)
        {
            _outputPaths = outputPaths;
        }

        public List<TaskToCommandArgs> getConverters()
        {
            return _converter.getConverters();
        }

        protected void setConverter(ListToCommandArgs converter)
        {
            _converter = converter;
        }

        public List<String> toArgs(CommandTask task, Map<String, String> params) throws IOException
        {
            return _converter.toArgs(task, params, new HashSet<TaskToCommandArgs>());
        }

        public boolean isCopyInput()
        {
            return _copyInput;
        }

        public boolean isRemoveInput()
        {
            return _removeInput;
        }

        public boolean isPipeToOutput()
        {
            return _pipeToOutput;
        }

        public int getPipeOutputLineInterval()
        {
            return _pipeOutputLineInterval;
        }

        public boolean isPreview()
        {
            return _preview;
        }

        public Integer getTimeout()
        {
            return _timeout;
        }

        public void setTimeout(Integer timeout)
        {
            _timeout = timeout;
        }

        /**
         * Directory containing the module task.xml file used to create this TaskFactory.
         * @return The module relative path (e.g, "pipeline/tasks" for a task declared in "pipeline/tasks/foo.task.xml".)
         */
        public Path getModuleTaskPath()
        {
            return _moduleTaskPath;
        }

        protected void setModuleTaskPath(Path moduleTaskPath)
        {
            _moduleTaskPath = moduleTaskPath;
        }
    }

    // Write out a tsv file with job and task information before executing the command
    protected boolean _writeTaskInfoFile = false;

    public CommandTaskImpl(PipelineJob job, Factory factory)
    {
        super(factory, job);
    }

    public FileAnalysisJobSupport getJobSupport()
    {
        return getJob().getJobSupport(FileAnalysisJobSupport.class);
    }

    @Override
    public String getInstallPath()
    {
        return _factory.getInstallPath();
    }

    public boolean isWriteTaskInfoFile()
    {
        return _writeTaskInfoFile;
    }

    /**
     * The task info file will be written into the analysis directory, similar to the .log file.
     * Since it will remain in the pipeline root after the job is complete, it shouldn't contain sensitive information.
     */
    public File getTaskInfoFile()
    {
        if (!isWriteTaskInfoFile())
            return null;

        String infoFileName = getJobSupport().getBaseName() + "-taskInfo.tsv";
        return new File(getJobSupport().getAnalysisDirectory(), infoFileName);
    }

    /**
     * Returns a file path to a resource from the declaring module.
     */
    protected String getModuleResourcePath(String path)
    {
        Module module = _factory.getDeclaringModule();
        if (module == null)
            return null;

        Resource dir = module.getModuleResource(path);
        if (dir == null || !(dir instanceof FileResource))
            return null;

        File f = ((FileResource)dir).getFile();
        return f.getPath();
    }

    @Override
    public String[] getProcessPaths(WorkDirectory.Function f, String key) throws IOException
    {
        if (f == WorkDirectory.Function.module)
        {
            String path = getModuleResourcePath(key);
            return path == null ? new String[0] : new String[] { path };
        }
        else
        {
            TaskPath tp = (WorkDirectory.Function.input.equals(f) ?
                    _factory.getInputPaths().get(key) : _factory.getOutputPaths().get(key));

            ArrayList<String> paths = new ArrayList<>();
            for (File file : _wd.getWorkFiles(f, tp))
                paths.add(_wd.getRelativePath(file));
            return paths.toArray(new String[paths.size()]);
        }
    }

    private void inputFile(TaskPath tp, String role, RecordedAction action) throws IOException
    {
        List<File> filesInput = _wd.getWorkFiles(WorkDirectory.Function.input, tp);
        for (File fileInput : filesInput)
        {
            // Nothing to do, if this file is optional and does not exist.
            if (tp.isOptional() && !NetworkDrive.exists(fileInput))
                return;

            _wd.inputFile(fileInput, tp.isCopyInput() || _factory.isCopyInput());
            action.addInput(fileInput, role);
        }
    }

    /**
     * The parameter replacement map created as the task is run and used
     * to replace tokens the script or the command line before executing it.
     * The replaced paths will be resolved to paths in the work directory.
     */
    protected Map<String, String> createReplacements(@Nullable File scriptFile, @Nullable String apiKey, @Nullable Container container) throws IOException
    {
        Map<String, String> replacements = new HashMap<>();

        // Input paths
        for (String key : _factory.getInputPaths().keySet())
        {
            String[] inputPaths = getProcessPaths(WorkDirectory.Function.input, key);
            if (inputPaths.length == 0)
            {
                // Replace empty file token with empty string
                replacements.put(key, "");
            }
            else if (inputPaths.length == 1)
            {
                if (inputPaths[0] == null)
                    replacements.put(key, "");
                else
                    replacements.put(key, Matcher.quoteReplacement(inputPaths[0].replaceAll("\\\\", "/")));
            }
            else
            {
                // CONSIDER: Add replacement for each file?  ${input[0].txt}, ${input[1].txt}, ${input[*].txt}
                // NOTE: The script parser matches ${input1.txt} to the first input file which isn't the same as ${input1[1].txt} which may be the 2nd file in the set of files represented by "input1.txt"
            }
        }

        // Output paths
        for (String key : _factory.getOutputPaths().keySet())
        {
            String[] outputPaths = getProcessPaths(WorkDirectory.Function.output, key);
            if (outputPaths.length == 0)
            {
                // Replace empty file token with empty string
                replacements.put(key, "");
            }
            else if (outputPaths.length == 1)
            {
                if (outputPaths[0] == null)
                    replacements.put(key, "");
                else
                    replacements.put(key, Matcher.quoteReplacement(outputPaths[0].replaceAll("\\\\", "/")));
            }
            else
            {
                // CONSIDER: Add replacement for each file?  ${input[0].txt}, ${input[1].txt}, ${input[*].txt}
            }
        }

        // Job parameters
        for (Map.Entry<String, String> entry : getJob().getParameters().entrySet())
        {
            String value = entry.getValue();
            if (value != null)
                value = Matcher.quoteReplacement(value);
            replacements.put(entry.getKey(), value);
        }

        // Task info replacement
        File taskInfoFile = getTaskInfoFile();
        if (taskInfoFile != null)
        {
            String taskInfoRelativePath = _wd.getRelativePath(taskInfoFile);
            replacements.put(PipelineJob.PIPELINE_TASK_INFO_PARAM, taskInfoRelativePath);
        }

        // Task output parameters file replacement
        if (_wd != null)
        {
            File taskOutputParamsFile = _wd.newFile(CommandTaskImpl.OUTPUT_PARAMS);
            String taskOutputParamsRelativePath = _wd.getRelativePath(taskOutputParamsFile);
            replacements.put(PipelineJob.PIPELINE_TASK_OUTPUT_PARAMS_PARAM, taskOutputParamsRelativePath);
        }

        DefaultDataTransformer.addStandardParameters(null, container, scriptFile, apiKey, replacements);

        return replacements;
    }

    // CONDISER: Use PipelineJobService.get().getPathMapper() when translating paths
    protected String rewritePath(String path)
    {
        return path;
    }

    @Override
    @NotNull
    public RecordedActionSet run() throws PipelineJobException
    {
        TransformSession session = null;
        Container container = null;
        if (PipelineJobService.get().isWebServer())
        {
            // We're inside of the web server so we have access to the DB and can set up a transform session, among
            // other resources
            session = SecurityManager.createTransformSession(getJob().getUser());
            container = getJob().getContainer();
        }
        try
        {
            RecordedAction action = new RecordedAction(_factory.getProtocolActionName());
            
            // Input file location must be determined before creating the process command.
            if (!_factory.getInputPaths().isEmpty())
            {
                try (WorkDirectory.CopyingResource lock = _wd.ensureCopyingLock())
                {
                    for (Map.Entry<String, TaskPath> entry : _factory.getInputPaths().entrySet())
                    {
                        TaskPath taskPath = entry.getValue();
                        String role = entry.getKey();
                        if (WorkDirectory.Function.input.toString().equals(role))
                        {
                            role = taskPath.getDefaultRole();
                        }
                        inputFile(taskPath, role, action);
                    }
                }
            }

            // Always copy in the parameters file if it is not null. It's small and in some cases is useful to the process we're launching
            // It is known to be null if the task is running from inside an ETL
            if (getJobSupport().getParametersFile() != null)
                _wd.inputFile(getJobSupport().getParametersFile(), true);

            if (!runCommand(action, session == null ? null : session.getApiKey(), container))
                return new RecordedActionSet();

            // Read output parameters file, record output parameters, and discard it.
            readOutputParameters(action);

            // Get rid of any copied input files.
            _wd.discardCopiedInputs();

            _wd.acceptFilesAsOutputs(_factory.getOutputPaths(), action);

            // Get rid of the work directory, which should now be empty
            _wd.remove(true);

            // Should now be safe to remove the original input file, if required.
            if (_factory.isRemoveInput())
            {
                TaskPath tpInput = _factory.getInputPaths().get(WorkDirectory.Function.input.toString());
                for (File fileInput : _wd.getWorkFiles(WorkDirectory.Function.input, tpInput))
                    fileInput.delete();
            }

            return new RecordedActionSet(action);
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
        finally
        {
            _wd = null;
            if (session != null)
            {
                session.close();
            }
        }
    }

    /**
     * Run the command line task.
     * @param action The recorded action.
     * @param apiKey API key to use for the duration of the command execution
     * @return true if the task was run, false otherwise.
     */
    // TODO: Add task and job version information to the recorded action.
    protected boolean runCommand(RecordedAction action, @Nullable String apiKey, @Nullable Container container) throws IOException, PipelineJobException
    {
        Map<String, String> replacements = container == null ? Collections.emptyMap() : createReplacements(null, apiKey, container);

        ProcessBuilder pb = new ProcessBuilder(_factory.toArgs(this, replacements));
        applyEnvironment(pb);

        List<String> args = pb.command();

        if (args.size() == 0)
            return false;

        String commandLine = StringUtils.join(args, " ");

        if (AppProps.getInstance().isDevMode())
        {
            getJob().header("Replacements");
            for (Map.Entry<String, String> entry : replacements.entrySet())
                getJob().info(entry.getKey() + ": " + entry.getValue());
        }

        // Just output the command line, if debug mode is set.
        if (_factory.isPreview())
        {
            getJob().header(args.get(0) + " output");
            getJob().info(commandLine);

            return false;
        }

        // Check if output file is to be generated from the stdout
        // stream of the process.
        File fileOutput = null;
        int lineInterval = 0;
        if (_factory.isPipeToOutput())
        {
            TaskPath tpOut = _factory.getOutputPaths().get(WorkDirectory.Function.output.toString());
            assert !tpOut.isSplitFiles() : "Invalid attempt to pipe output to split files.";
            fileOutput = _wd.newWorkFile(WorkDirectory.Function.output,
                    tpOut, getJobSupport().getBaseName());
            lineInterval = _factory.getPipeOutputLineInterval();
        }

        action.setStartTime(new Date());
        action.addParameter(RecordedAction.COMMAND_LINE_PARAM, commandLine);
        int timeout = _factory._timeout != null ? _factory._timeout : 0;
        getJob().runSubProcess(pb, _wd.getDir(), fileOutput, lineInterval, false, timeout, TimeUnit.SECONDS);
        action.setEndTime(new Date());
        return true;
    }

    private static final Pattern pat = Pattern.compile("\\$\\{[^}]*}");

    public String variableSubstitution(String src, Map<String, String> map)
    {
        StringBuffer sb = new StringBuffer();
        Matcher matcher = pat.matcher(src);
        while (matcher.find())
        {
            String varName = src.substring(matcher.start() + 2, matcher.end() - 1);

            String substValue = map.get(varName);
            if (substValue == null)
            {
                //by default substitute "" for unmatched substitutions
                substValue = "";
            }

            matcher.appendReplacement(sb, substValue);
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testSubstitution()
        {
            Mockery context = new Mockery();
            context.setImposteriser(ClassImposteriser.INSTANCE);
            Factory factory = new Factory();
            CommandTaskImpl impl = new CommandTaskImpl(context.mock(PipelineJob.class), factory);

            assertEquals("/originalPath:/morePath", impl.variableSubstitution("${TEST}:/morePath", Collections.singletonMap("TEST", "/originalPath")));
            assertEquals(":/morePath", impl.variableSubstitution("${TEST}:/morePath", Collections.emptyMap()));
            assertEquals("/originalPath:/morePath:/originalPath", impl.variableSubstitution("${TEST}:/morePath:${TEST}", Collections.singletonMap("TEST", "/originalPath")));
        }
    }

    private void applyEnvironment(ProcessBuilder pb)
    {
        Map<String, String> originalEnvironment = new HashMap<>(pb.environment());
        for (Map.Entry<String, String> entry : _factory._environment.entrySet())
        {
            pb.environment().put(entry.getKey(), variableSubstitution(entry.getValue(), originalEnvironment));
        }
    }

    protected void readOutputParameters(RecordedAction action) throws IOException
    {
        File file = _wd.newFile(CommandTaskImpl.OUTPUT_PARAMS);
        if (file.exists())
        {
            getJob().header("Output parameters");
            Map<String, String> currParams = new HashMap<>(getJob().getParameters());

            TabLoader loader = new TabLoader(file, true, null);
            loader.setInferTypes(false);
            for (Map<String, Object> row : loader.load())
            {
                String name = Objects.toString(row.get("Name"), null);
                String value = Objects.toString(row.get("Value"), null);
                String type = Objects.toString(row.get("Type"), null);
                if (name == null || value == null)
                    continue;

                // Skip null values and parameters that haven't changed
                String prevValue = currParams.get(name);
                if (prevValue == null || !prevValue.equals(value))
                {
                    // Record the new parameter -- it will be merged into the job's parameters automatically
                    getJob().info(name + ": " + value);
                    action.addOutputParameter(new RecordedAction.ParameterType(name, PropertyType.STRING), value);
                }
            }

            _wd.discardFile(file);
        }
    }
}
