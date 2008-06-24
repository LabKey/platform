/*
 * Copyright (c) 2007-2008 LabKey Corporation
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
package org.labkey.experiment.pipeline;

import org.labkey.api.exp.ExperimentPipelineJob;
import org.labkey.api.exp.FileXarSource;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.pipeline.XarGeneratorId;
import org.labkey.api.exp.pipeline.XarImportFactorySettings;
import org.labkey.api.exp.pipeline.XarImportTaskId;
import org.labkey.api.pipeline.*;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.util.FileType;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

/**
 * <code>XarImportTask</code>
 */
public class XarImportTask extends PipelineJob.Task
{
     public static class Factory extends AbstractTaskFactory
    {
        private FileType _inputType = XarGeneratorId.FT_PIPE_XAR_XML;

        public Factory()
        {
            super(XarImportTaskId.class);
        }

        public TaskFactory cloneAndConfigure(TaskFactorySettings settings) throws CloneNotSupportedException
        {
            Factory factory = (Factory) super.cloneAndConfigure(settings);

            return factory.configure((XarImportFactorySettings) settings);
        }

        private TaskFactory configure(XarImportFactorySettings settings)
        {
            if (settings.getInputExt() != null)
                _inputType = new FileType(settings.getInputExt());

            return this;
        }

        public PipelineJob.Task createTask(PipelineJob job)
        {
            return new XarImportTask(job, this);
        }

        public FileType[] getInputTypes()
        {
            return new FileType[] { _inputType };
        }

        public String getStatusName()
        {
            return "IMPORT EXPERIMENT";
        }

        public boolean isJobComplete(PipelineJob job) throws IOException, SQLException
        {
            // Check parameters to see if loading is required.
            return ("no".equalsIgnoreCase(job.getParameters().get("pipeline, load")));
        }
    }

    private Factory _factory;

    protected XarImportTask(PipelineJob job, Factory factory)
    {
        super(job);

        _factory = factory;
    }

    public FileAnalysisJobSupport getJobSupport()
    {
        return getJob().getJobSupport(FileAnalysisJobSupport.class);
    }

    public void run()
    {
        String baseName = getJobSupport().getBaseName();
        File dirAnalysis = getJobSupport().getAnalysisDirectory();

        File fileExperimentXML = _factory.getInputTypes()[0].newFile(dirAnalysis, baseName);

        FileXarSource source = new FileXarSource(fileExperimentXML);
        if (ExperimentPipelineJob.loadExperiment(getJob(), source, false))
        {
            ExpRun run = source.getExperimentRun();
            if (run != null)
                getJobSupport().setExperimentRunRowId(run.getRowId());
        }
    }
}
