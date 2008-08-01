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

import org.labkey.api.pipeline.*;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.pipeline.cmd.ConvertTaskId;
import org.labkey.api.pipeline.cmd.ConvertTaskFactorySettings;
import org.labkey.api.util.FileType;

import java.io.IOException;
import java.io.File;
import java.sql.SQLException;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * <code>ConvertTaskFactory</code> a task for converting from multiple possible
 * input formats to a single output format.
 *
 * TODO: Unfortunately, this task currently only supports converting from a
 * <code>PipelineJobs</code> input file format.  Ideally, it should convert
 * from any format which has been created during job processing.  This will
 * require better support in <code>PipelineJob</code>.
 */
public class ConvertTaskFactory extends AbstractTaskFactory<ConvertTaskFactorySettings>
{
    private String _statusName = "CONVERSION";
    private TaskId[] _commands;
    private FileType[] _initialTypes;
    private FileType _outputType;

    public ConvertTaskFactory()
    {
        super(new TaskId(ConvertTaskId.class));
    }

    public ConvertTaskFactory(String name)
    {
        super(new TaskId(ConvertTaskId.class, name));
    }

    public ConvertTaskFactory cloneAndConfigure(ConvertTaskFactorySettings settings) throws CloneNotSupportedException
    {
        ConvertTaskFactory factory = (ConvertTaskFactory) super.cloneAndConfigure(settings);

        return factory.configure(settings);
    }

    private ConvertTaskFactory configure(ConvertTaskFactorySettings settings)
    {
        if (settings.getStatusName() != null)
            _statusName = settings.getStatusName();

        if (settings.getOutputExt() != null)
            _outputType = new FileType(settings.getOutputExt());
        else if (_outputType == null)
            throw new IllegalArgumentException("Conversion must have output type.");

        if (settings.getCommandIds() != null)
            _commands = settings.getCommandIds();
        if (_commands == null)
            _commands = new TaskId[0];

        ArrayList<FileType> types = new ArrayList<FileType>();
        // Output type is allowed as an initial type, but this conversion will
        // be skipped, if the input is of the desired output type.
        types.add(_outputType);
        for (TaskId tid : _commands)
        {
            TaskFactory factory = PipelineJobService.get().getTaskFactory(tid);
            types.addAll(Arrays.asList(factory.getInputTypes()));
        }
        _initialTypes = types.toArray(new FileType[types.size()]);

        return this;
    }

    public TaskId getActiveId(PipelineJob job)
    {
        TaskFactory factory = findCommandFactory(job);
        if (factory != null)
            return factory.getActiveId(job);

        return super.getActiveId(job);
    }

    private File getInputFile(PipelineJob job)
    {
        File[] files = job.getJobSupport(FileAnalysisJobSupport.class).getInputFiles();
        assert files != null && files.length == 1 : "Conversion job must have one file.";
        return files[0];
    }

    private TaskFactory findCommandFactory(PipelineJob job)
    {
        // If this job is not actually running a conversion, then no
        // converter command can be determined.
        File[] files = job.getJobSupport(FileAnalysisJobSupport.class).getInputFiles();
        if (files == null || files.length != 1)
            return null;

        // Otherwise, find the appropriate converter.
        File fileInput = getInputFile(job);
        for (TaskId tid : _commands)
        {
            TaskFactory factory = PipelineJobService.get().getTaskFactory(tid);
            for (FileType ft : factory.getInputTypes())
            {
                if (ft.isType(fileInput))
                    return factory;
            }
        }

        return null;
    }

    public List<String> getProtocolActionNames()
    {
        List<String> result = new ArrayList<String>();
        for (TaskId tid : _commands)
        {
            TaskFactory<?> factory = PipelineJobService.get().getTaskFactory(tid);
            result.addAll(factory.getProtocolActionNames());
        }
        return result;
    }

    public PipelineJob.Task createTask(PipelineJob job)
    {
        throw new UnsupportedOperationException("No task associated with " + getClass() + ".");
    }

    public FileType[] getInputTypes()
    {
        return _initialTypes;
    }

    public String getStatusName()
    {
        return _statusName;
    }

    public boolean isJoin()
    {
        // For now conversion is always done one file at a time.
        return false;
    }

    public boolean isParticipant(PipelineJob job) throws IOException, SQLException
    {
        if (!super.isParticipant(job))
            return false;
        
        // Nothing to do, if the input is already the desired type.
        File fileInput = getInputFile(job);
        if (_outputType.isType(fileInput))
            return false;
        if (findCommandFactory(job) == null)
            throw new IOException("Conversion to " + _outputType + " not supported for " + fileInput + ".");
        return true;
    }

    public boolean isJobComplete(PipelineJob job) throws IOException, SQLException
    {
        TaskFactory factory = findCommandFactory(job);
        assert factory != null : "Unexpected missing converter for job: \n" +
                PipelineJobService.get().getJobStore().toXML(job);

        return factory.isJobComplete(job);
    }
}
