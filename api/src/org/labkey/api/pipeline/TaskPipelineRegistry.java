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

/**
 * <code>TaskPipelineRegistry</code>
*
* @author brendanx
*/
public interface TaskPipelineRegistry
{
    TaskPipeline getTaskPipeline(TaskId id);

    void addTaskPipeline(TaskPipelineSettings settings)
            throws CloneNotSupportedException;

    void addTaskPipeline(TaskPipeline pipeline);

    <T extends TaskPipeline> T[] getTaskPipelines(Class<T> inter);

    TaskFactory getTaskFactory(TaskId id);

    void addTaskFactory(TaskFactorySettings settings)
            throws CloneNotSupportedException;

    void addTaskFactory(TaskFactory factory);

    TaskFactory[] getTaskFactories();

    TaskFactory.ExecutionLocation getDefaultExecutionLocation();
}
