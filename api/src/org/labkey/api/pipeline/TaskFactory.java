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
package org.labkey.api.pipeline;

import org.labkey.api.util.FileType;

import java.io.IOException;
import java.sql.SQLException;

/**
 * <code>TaskFactory</code> is responsible for creating a task to run on a
 * PipelineJob.  Create an implementation of this interface to support custom
 * Task configuration inside the Mule configuration Spring context.
 *
 * @author brendanx
 */
public interface TaskFactory extends ClusterSettings
{
    TaskId getId();

    TaskId getActiveId(PipelineJob job);

    PipelineJob.Task createTask(PipelineJob job);

    TaskFactory cloneAndConfigure(TaskFactorySettings settings) throws CloneNotSupportedException;

    FileType[] getInputTypes();

    String getStatusName();

    boolean isJoin();

    boolean isJobComplete(PipelineJob job) throws IOException, SQLException;

    boolean isParticipant(PipelineJob job) throws IOException, SQLException;

    String getExecutionLocation();

    /**
     * Task is run on the LabKey Server.
     */
    static final String WEBSERVER = "webserver";
}
