/*
 * Copyright (c) 2007-2016 LabKey Corporation
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
package org.labkey.api.pipeline;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.module.Module;

/**
 * <code>TaskPipeline</code> identifies a set of {@link org.labkey.api.pipeline.TaskFactory}
 * objects that can be used to create {@link org.labkey.api.pipeline.PipelineJob.Task} instances for processing
 * a pipeline job.
 */
public interface TaskPipeline<SettingsType extends TaskPipelineSettings>
{
    /**
     * Returns a description of this pipeline to be displayed in the
     * user interface for initiating a job that will run the pipeline.
     *
     * @return a description of this pipeline
     */
    String getDescription();

    TaskId getId();

    TaskId[] getTaskProgression();

    TaskPipeline cloneAndConfigure(SettingsType settings, TaskId[] taskProgression) throws CloneNotSupportedException;

    /** @return ObjectId to use in the LSID for the generated Experiment protocol */
    String getProtocolIdentifier();

    /** @return Name to show in the UI for the generated Experiment protocol */
    String getProtocolShortDescription();

    void setDeclaringModule(Module declaringModule);

    Module getDeclaringModule();

    /**
     * For pipelines integrated with workflows, the correct workflow process to use.
     */
    @Nullable
    String getWorkflowProcessKey();

    /**
     * For pipelines integrated with workflows, the module in which the workflow process is defined.
     */
    @Nullable
    String getWorkflowProcessModule();

    /**
     *
     * @return When true, move the input files into a unique directory (with timestamped name) before the analysis
     */
    boolean isUseUniqueAnalysisDirectory();
}
