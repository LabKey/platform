/*
 * Copyright (c) 2008 LabKey Corporation
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

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.pipeline.*;
import org.labkey.api.pipeline.cmd.CommandTask;
import org.labkey.api.pipeline.cmd.*;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.util.FileType;
import org.labkey.api.util.NetworkDrive;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.sql.SQLException;

/**
 * <code>CommandTask</code>
 *
 * @author brendanx
 */
public class CommandTaskImpl extends WorkDirectoryTask<CommandTaskImpl.Factory> implements CommandTask
{
    private static final Logger _log = Logger.getLogger(CommandTaskImpl.class);

    public static class Factory extends AbstractTaskFactory<CommandTaskFactorySettings, Factory>
    {
        private String _statusName = "COMMAND";
        private String _protocolActionName;
        private Map<String, TaskPath> _inputPaths = new HashMap<String, TaskPath>();
        private Map<String, TaskPath> _outputPaths = new HashMap<String, TaskPath>();
        private ListToCommandArgs _converter = new ListToCommandArgs();
        private boolean _copyInput;
        private boolean _removeInput;
        private boolean _pipeToOutput;
        private int _pipeOutputLineInterval;
        private boolean _preview;
        private String _actionableInput;

        public Factory()
        {
            super(new TaskId(CommandTask.class));
        }

        public Factory(String name)
        {
            super(new TaskId(CommandTask.class, name));
        }

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
        }

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

        public List<String> getProtocolActionNames()
        {
            return Collections.singletonList(_protocolActionName);
        }

        public String getStatusName()
        {
            return _statusName;
        }

        public boolean isJobComplete(PipelineJob job)
        {
            // TODO: Safer way to do this.
            FileAnalysisJobSupport support = (FileAnalysisJobSupport) job;
            for (TaskPath tp : getOutputPaths().values())
            {
                // TODO: Join somehow with code in CommandTaskImpl
                FileType type = tp.getType();
                File result = support.findOutputFile(type != null ?
                        type.getName(support.getBaseName()) : tp.getName());
                if (!result.exists())
                    return false;
            }

            // If there were no output paths, then the command is run for some
            // other reason than its outputs.
            return (getOutputPaths().size() > 0);
        }

        public boolean isParticipant(PipelineJob job) throws IOException, SQLException
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
            // Need to set up the work directory so that it can figure out what the arguments will be called
            WorkDirectory wd = createWorkDirectory(job.getJobGUID(), job.getJobSupport(FileAnalysisJobSupport.class), job.getLogger());
            task.setWorkDirectory(wd);
            try
            {
                // If it produces nothing for the command line, then this command should not be executed.
                return commandNameConverter.toArgs(task, new HashSet<TaskToCommandArgs>()).length != 0;
            }
            finally
            {
                wd.remove();
                task.setWorkDirectory(null);
            }
        }

        public FileType[] getInputTypes()
        {
            if (_actionableInput == null)
            {
                // If the config doesn't specify that only one of the inputs should have a button next to it,
                // use them all
                List<FileType> result = new ArrayList<FileType>(_inputPaths.size());
                for (TaskPath taskPath : _inputPaths.values())
                {
                    result.add(taskPath.getType());
                }
                return result.toArray(new FileType[result.size()]);
            }
            TaskPath tp = _inputPaths.get(_actionableInput);
            return (tp == null ? null : new FileType[] { tp.getType() });
        }

        public Map<String, TaskPath> getInputPaths()
        {
            return _inputPaths;
        }

        public FileType getOutputType()
        {
            TaskPath tp = _outputPaths.get(WorkDirectory.Function.output.toString());
            return (tp == null ? null : tp.getType());
        }

        public Map<String, TaskPath> getOutputPaths()
        {
            return _outputPaths;
        }

        public SwitchFormat getSwitchFormat()
        {
            return _converter.getSwitchFormat();
        }

        public List<TaskToCommandArgs> getConverters()
        {
            return _converter.getConverters();
        }

        public String[] toArgs(CommandTask task) throws IOException
        {
            return _converter.toArgs(task, new HashSet<TaskToCommandArgs>());
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
    }

    public CommandTaskImpl(PipelineJob job, Factory factory)
    {
        super(factory, job);
    }

    public FileAnalysisJobSupport getJobSupport()
    {
        return getJob().getJobSupport(FileAnalysisJobSupport.class);
    }

    public String getInputProcessPath(String key) throws IOException
    {
        return getProcessPath(WorkDirectory.Function.input,
                _factory.getInputPaths().get(key));
    }

    public String getOutputProcessPath(String key) throws IOException
    {
        return getProcessPath(WorkDirectory.Function.output,
                _factory.getOutputPaths().get(key));
    }

    public String getProcessPath(WorkDirectory.Function f, String key) throws IOException
    {
        return (WorkDirectory.Function.input.equals(f) ?
                getInputProcessPath(key) : getOutputProcessPath(key));
    }

    public String getProcessPath(WorkDirectory.Function f, TaskPath tp) throws IOException
    {
        return (tp == null ? null : _wd.getRelativePath(newWorkFile(f, tp)));
    }

    public File newWorkFile(WorkDirectory.Function f, TaskPath tp)
    {
        if (tp == null)
            return null;

        FileType type = tp.getType();
        if (type != null)
            return _wd.newFile(f, type);

        return _wd.newFile(f, tp.getName());
    }

    public void inputFile(TaskPath tp, String role, RecordedAction action) throws IOException
    {
        File fileInput = null;
        if (tp.getType() != null)
            fileInput = _wd.newFile(WorkDirectory.Function.input, tp.getType());
        else if (tp.getName() != null)
            fileInput = _wd.newFile(WorkDirectory.Function.input, tp.getName());

        if (fileInput != null)
        {
            // Nothing to do, if this file is optional and does not exist.
            if (tp.isOptional() && !NetworkDrive.exists(fileInput))
                return;

            _wd.inputFile(fileInput, tp.isCopyInput() || _factory.isCopyInput());
            action.addInput(fileInput, role);
        }
    }
    
    public void outputFile(TaskPath tp, String role, RecordedAction action) throws IOException
    {
        File fileWork = null;
        if (tp.getType() != null)
            fileWork = _wd.newFile(WorkDirectory.Function.output, tp.getType());
        else if (tp.getName() != null)
            fileWork = _wd.newFile(WorkDirectory.Function.output, tp.getName());

        if (fileWork != null)
        {
            File fileOutput = getJobSupport().findOutputFile(fileWork.getName());
            if (fileOutput != null)
            {
                // If the output file is optional, or in a shared directory outside
                // the analysis directory for this job, and it already exists,
                // then simply discard the work file, leaving the original.

                // CONSIDER: Unfortunately, with a local work directory, this may hide files
                // that are auto-generated by the command in place.  Such files like,
                // .mzXML.inspect for msInspect will not be recorded as output.
                if (tp.isOptional() ||
                        !getJobSupport().getAnalysisDirectory().equals(fileOutput.getParentFile()))
                {
                    if (NetworkDrive.exists(fileOutput))
                    {
                        _wd.discardFile(fileWork);
                        return;
                    }
                }
            }

            File f = _wd.outputFile(fileWork);
            action.addOutput(f, role, false);
        }
    }

    public List<RecordedAction> run() throws PipelineJobException
    {
        try
        {
            RecordedAction action = new RecordedAction(_factory.getProtocolActionName());
            
            // Input file location must be determined before creating the process command.
            if (!_factory.getInputPaths().isEmpty())
            {
                WorkDirectory.CopyingResource lock = null;
                try
                {
                    lock = _wd.ensureCopyingLock();
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
                finally
                {
                    if (lock != null) { lock.release(); }
                }
            }

            ProcessBuilder pb = new ProcessBuilder(_factory.toArgs(this));

            List<String> args = pb.command();

            if (args.size() == 0)
                return Collections.emptyList();

            String commandLine = StringUtils.join(args, " ");
            
            // Just output the command line, if debug mode is set.
            if (_factory.isPreview())
            {
                getJob().header(args.get(0) + " output");
                getJob().info(commandLine);

                return Collections.emptyList();
            }

            // Check if output file is to be generated from the stdout
            // stream of the process.
            File fileOutput = null;
            int lineInterval = 0;
            if (_factory.isPipeToOutput())
            {
                fileOutput = newWorkFile(WorkDirectory.Function.output,
                        _factory.getOutputPaths().get(WorkDirectory.Function.output.toString()));
                lineInterval = _factory.getPipeOutputLineInterval();
            }

            getJob().runSubProcess(pb, _wd.getDir(), fileOutput, lineInterval);

            if (!_factory.getOutputPaths().isEmpty())
            {
                WorkDirectory.CopyingResource lock = null;
                try
                {
                    lock = _wd.ensureCopyingLock();
                    for (Map.Entry<String, TaskPath> entry : _factory.getOutputPaths().entrySet())
                    {
                        TaskPath taskPath = entry.getValue();
                        String role = entry.getKey();
                        if (WorkDirectory.Function.output.toString().equals(role))
                        {
                            role = taskPath.getDefaultRole();
                        }
                        outputFile(taskPath, role, action);
                    }
                }
                finally
                {
                    if (lock != null) { lock.release(); }
                }
            }

            // Get rid of the work directory, and any copied input files.
            _wd.remove();

            // Should now be safe to remove the original input file, if required.
            if (_factory.isRemoveInput())
            {
                File fileInput = newWorkFile(WorkDirectory.Function.input,
                        _factory.getInputPaths().get(WorkDirectory.Function.input.toString()));
                if (fileInput != null)
                    fileInput.delete();
            }
            action.addParameter(RecordedAction.COMMAND_LINE_PARAM, commandLine);
            return Collections.singletonList(action);
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
        finally
        {
            _wd = null;
        }
    }
}
