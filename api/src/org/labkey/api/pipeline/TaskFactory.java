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

import org.apache.log4j.Logger;
import org.labkey.api.module.Module;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.util.FileType;

import java.io.IOException;
import java.util.List;

/**
 * <code>TaskFactory</code> is responsible for creating a task to run on a
 * PipelineJob.  Create an implementation of this interface to support custom
 * Task configuration inside the Mule configuration Spring context.
 *
 * @author brendanx
 */
public interface TaskFactory<SettingsType extends TaskFactorySettings>
{
    TaskId getId();

    TaskId getActiveId(PipelineJob job);

    PipelineJob.Task createTask(PipelineJob job);

    TaskFactory cloneAndConfigure(SettingsType settings) throws CloneNotSupportedException;

    /** @return the types of files that are consumable by this task as input */
    List<FileType> getInputTypes();

    /**
     * All of the ProtocolAction names that this task may include when it runs. It need not execute all of them for
     * each invocation.
     * These names are used to build up a full Experiment Protocol for each pipeline to which this tasks belongs.
     */
    List<String> getProtocolActionNames();

    /** The name of the status to be shown when this task is running, waiting, etc */
    String getStatusName();

    /** The prefix for a parameter group. Used to collect task-specific properties like remote execution engine configuration overrides */
    String getGroupParameterName();

    /**
     * @return true if this task operates on all of the split items (say, multiple input files) as a whole, or false
     * if each split item should be operated on independently (and potentially in parallel)
     */
    boolean isJoin();

    /** Invoked on the web server to figure out if the task has already been run */
    boolean isJobComplete(PipelineJob job);

    /**
     * @return whether the task is expected to execute as part of a particular job. Individual tasks in a pipeline
     * might not be relevant if they are, for example, optional and have been disabled with a parameter in the protocol
     */
    boolean isParticipant(PipelineJob job) throws IOException;

    /**
     * Ensure that the job has enough configuration to succeed. Should not be used for anything expensive - just for
     * checking the parameters themselves
     */
    void validateParameters(PipelineJob job) throws PipelineValidationException;

    boolean isAutoRetryEnabled(PipelineJob job);

    /**
     * @return the name of the location on which the task should be executed. This is an abstract name, which needn't map
     * to a specific machine name
     */
    String getExecutionLocation();

    int getAutoRetry();

    public WorkDirectory createWorkDirectory(String jobGUID, FileAnalysisJobSupport jobSupport, Logger logger) throws IOException;

    void setDeclaringModule(Module declaringModule);

    /** @return the module that declared/defined this task */
    Module getDeclaringModule();

    /**
     * Location name for task to run on the LabKey Server itself (inside the Tomcat process).
     */
    static final String WEBSERVER = "webserver";
}
