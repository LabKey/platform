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

import org.labkey.experiment.pipeline.ExperimentPipelineJob;
import org.labkey.api.exp.FileXarSource;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.pipeline.XarTemplateSubstitutionId;
import org.labkey.api.exp.pipeline.XarImportFactorySettings;
import org.labkey.api.exp.pipeline.XarImportTaskId;
import org.labkey.api.exp.pipeline.XarGeneratorId;
import org.labkey.api.pipeline.*;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.util.FileType;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Collections;

/**
 * <code>XarImportTask</code>
 */
public class XarImportTask extends PipelineJob.Task<XarImportTask.Factory>
{
     public static class Factory extends AbstractTaskFactory<XarImportFactorySettings, Factory>
    {
        private FileType _inputType = XarGeneratorId.FT_PIPE_XAR_XML;

        public Factory()
        {
            super(XarImportTaskId.class);
        }

        protected void configure(XarImportFactorySettings settings)
        {
            super.configure(settings);

            if (settings.getInputExt() != null)
                _inputType = new FileType(settings.getInputExt());
        }

        public PipelineJob.Task createTask(PipelineJob job)
        {
            return new XarImportTask(this, job);
        }

        public FileType[] getInputTypes()
        {
            return new FileType[] { _inputType };
        }

        public String getStatusName()
        {
            return "IMPORT EXPERIMENT";
        }

        public List<String> getProtocolActionNames()
        {
            return Collections.emptyList();
        }

        public boolean isJobComplete(PipelineJob job)
        {
            // Check parameters to see if loading is required.
            return ("no".equalsIgnoreCase(job.getParameters().get("pipeline, load")));
        }
    }

    protected XarImportTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    public FileAnalysisJobSupport getJobSupport()
    {
        return getJob().getJobSupport(FileAnalysisJobSupport.class);
    }

    public List<RecordedAction> run()
    {
        String baseName = getJobSupport().getBaseName();
        File dirAnalysis = getJobSupport().getAnalysisDirectory();

        File fileExperimentXML = _factory.getInputTypes()[0].newFile(dirAnalysis, baseName);

        FileXarSource source = new FileXarSource(fileExperimentXML, getJob());
        if (ExperimentPipelineJob.loadExperiment(getJob(), source, false))
        {
            ExpRun run = source.getExperimentRun();
            if (run != null)
            {
                // TODO - need another way to send in the run info without clearing the action set? OK for now at least
                getJob().clearActionSet(run);
            }
        }

        return Collections.emptyList();
    }
}
