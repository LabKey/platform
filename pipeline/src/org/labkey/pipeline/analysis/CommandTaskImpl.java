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

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.HashMap;
import java.sql.SQLException;

/**
 * <code>CommandTask</code>
 *
 * @author brendanx
 */
public class CommandTaskImpl extends PipelineJob.Task implements CommandTask
{
    private static final Logger _log = Logger.getLogger(CommandTaskImpl.class);

    public static class Factory extends AbstractTaskFactory
    {
        private String _statusName = "COMMAND";
        private Map<String, TaskPath> _inputPaths = new HashMap<String, TaskPath>();
        private Map<String, TaskPath> _outputPaths = new HashMap<String, TaskPath>();
        private ListToCommandArgs _converter = new ListToCommandArgs();
        private boolean _copyInput;
        private boolean _removeInput;
        private boolean _pipeToOutput;
        private int _pipeOutputLineInterval;
        private boolean _preview;

        public Factory()
        {
            super(new TaskId(CommandTask.class));
        }

        public Factory(String name)
        {
            super(new TaskId(CommandTask.class, name));
        }

        public TaskFactory cloneAndConfigure(TaskFactorySettings settings) throws CloneNotSupportedException
        {
            Factory factory = (Factory) super.cloneAndConfigure(settings);

            return factory.configure((CommandTaskFactorySettings) settings);
        }

        private TaskFactory configure(CommandTaskFactorySettings settings)
        {
            if (settings.getStatusName() != null)
                _statusName = settings.getStatusName();

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

            return this;
        }

        public PipelineJob.Task createTask(PipelineJob job)
        {
            return new CommandTaskImpl(job, this);
        }

        public String getStatusName()
        {
            return _statusName;
        }

        public boolean isJobComplete(PipelineJob job) throws IOException, SQLException
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
            // The first converter is responsible for the command name.
            TaskToCommandArgs commandNameConverter = getConverters().get(0);

            // If it produces nothing for the command line, then this command should not
            // be executed.
            if (commandNameConverter.toArgs((CommandTask) createTask(job),
                    new HashSet<TaskToCommandArgs>()).length == 0)
            {
                return false;
            }

            return super.isParticipant(job);
        }

        public FileType[] getInputTypes()
        {
            TaskPath tp = _inputPaths.get(WorkDirectory.Function.input.toString());
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

    private Factory _factory;
    private WorkDirectory _wd;

    public CommandTaskImpl(PipelineJob job, Factory factory)
    {
        super(job);

        _factory = factory;
    }

    public Factory getFactory()
    {
        return _factory;
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

    public String getProcessExt(WorkDirectory.Function f, String key)
    {
        TaskPath tp = (WorkDirectory.Function.input.equals(f) ?
            _factory.getInputPaths().get(key) : _factory.getOutputPaths().get(key));
        return (tp == null ? null : tp.getType().getSuffix());
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

    public void inputFile(TaskPath tp) throws IOException
    {
        File fileInput = null;
        if (tp.getType() != null)
            fileInput = _wd.newFile(WorkDirectory.Function.input, tp.getType());
        else if (tp.getName() != null)
            fileInput = _wd.newFile(WorkDirectory.Function.input, tp.getName());
        
        if (fileInput != null)
            _wd.inputFile(fileInput, tp.isCopyInput() || _factory.isCopyInput());
    }
    
    public void outputFile(TaskPath tp) throws IOException
    {
        File fileWork = null;
        if (tp.getType() != null)
            fileWork = _wd.newFile(WorkDirectory.Function.output, tp.getType());
        else if (tp.getName() != null)
            fileWork = _wd.newFile(WorkDirectory.Function.output, tp.getName());

        if (fileWork != null)
            _wd.outputFile(fileWork);
    }

    public void run()
    {
        try
        {
            WorkDirFactory factory = PipelineJobService.get().getWorkDirFactory();
            _wd = factory.createWorkDirectory(getJob().getJobGUID(), getJobSupport(), getJob().getLogger());

            // Input file location must be determined before creating the process
            // command.
            for (TaskPath input : _factory.getInputPaths().values())
                inputFile(input);

            ProcessBuilder pb = new ProcessBuilder(_factory.toArgs(this));

            List<String> args = pb.command();
            if (args.size() == 0)
                return;

            // Just output the command line, if debug mode is set.
            if (_factory.isPreview())
            {
                getJob().header(args.get(0) + " output");
                getJob().info(StringUtils.join(args, " "));

                _wd.remove();
                return;
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

            for (TaskPath output : _factory.getOutputPaths().values())
                outputFile(output);

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
        }
        catch (PipelineJob.RunProcessException e)
        {
            // Handled in runSubProcess
        }
        catch (InterruptedException e)
        {
            // Handled in runSubProcess
        }
        catch (IOException e)
        {
            getJob().error(e.getMessage(), e);
        }
        finally
        {
            _wd = null;
        }
    }
}
