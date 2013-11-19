/*
 * Copyright (c) 2007-2011 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.module.Module;

import java.util.Collection;

/**
 * <code>TaskPipelineRegistry</code>
*
* @author brendanx
*/
public interface TaskPipelineRegistry
{
    /**
     * Registers all tasks and pipelines found in the module under the module's "pipeline/tasks" and "pipeline/pipelines"
     * directories. The directories will be watch for file changes and tasks will be added or removed as they appear or disappear.
     * TaskFactory or TaskPipeline objects created by Spring bean config xml files are added to the registry via
     * the {@link TaskPipelineRegistrar} on startup.
     *
     * @param module Module delcaring tasks or pipelines.
     */
    void registerModule(Module module);

    @Nullable
    TaskPipeline getTaskPipeline(TaskId id);

    /**
     * Add or replace an existing TaskPipeline definition. Server-specific configuration overrides (usually specified
     * in a Spring Config.xml file) may replace a TaskPipeline that's built-in to a module with one that adds
     * or remove steps.
     */
    void addTaskPipeline(TaskPipelineSettings settings)
            throws CloneNotSupportedException;

    /**
     * Add or replace an existing TaskPipeline definition. Server-specific configuration overrides (usually specified
     * in a Spring Config.xml file) may replace a TaskPipeline that's built-in to a module with one that adds
     * or remove steps.
     */
    void addTaskPipeline(TaskPipeline pipeline);

    @NotNull
    Collection<TaskPipeline> getTaskPipelines(@Nullable Container container);

    /**
     * Get a list of task pipelines from the set of active modules in the container and of the given type.
     * @param container If not null, pipelines declared in modules that are active in the container are returned.
     * @param inter If not null, only pipelines of the given type are returned.
     * @return The list of pipelines.
     */
    @NotNull
    <T extends TaskPipeline> Collection<T> getTaskPipelines(@Nullable Container container, @Nullable Class<T> inter);

    @Nullable
    TaskFactory getTaskFactory(TaskId id);

    /**
     * Add or replace an existing TaskFactory definition. Server-specific configuration overrides (usually specified
     * in a Spring Config.xml file) may replace a TaskFactory that's built-in to a module with one that customizes
     * it for the local installation. Examples might include setting server-specific paths to files, setting timeouts
     * or memory limits, etc.
     */
    void addTaskFactory(TaskFactorySettings settings)
            throws CloneNotSupportedException;

    /**
     * Add or replace an existing TaskFactory definition. Server-specific configuration overrides (usually specified
     * in a Spring Config.xml file) may replace a TaskFactory that's built-in to a module with one that customizes
     * it for the local installation. Examples might include setting server-specific paths to files, setting timeouts
     * or memory limits, etc.
     */
    void addTaskFactory(TaskFactory factory);

    /**
     * Get a list of task factories from the set of active modules in the container.
     * @param container If not null, task factories declared in modules that are active in the container are returned.
     * @return The list of task factories.
     */
    @NotNull
    Collection<TaskFactory> getTaskFactories(@Nullable Container container);

    String getDefaultExecutionLocation();

    int getDefaultAutoRetry();
}
