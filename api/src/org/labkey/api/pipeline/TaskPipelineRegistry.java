/*
 * Copyright (c) 2007-2017 LabKey Corporation
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

import org.apache.xmlbeans.SchemaType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.pipeline.file.AbstractFileAnalysisProtocolFactory;
import org.labkey.api.util.Path;
import org.labkey.pipeline.xml.TaskType;

import java.util.Collection;

/**
 * <code>TaskPipelineRegistry</code>
*
* @author brendanx
*/
public interface TaskPipelineRegistry
{
    String LOCAL_TASK_PREFIX = "#";

    @Nullable
    TaskPipeline getTaskPipeline(TaskId id);

    @NotNull
    TaskPipeline getTaskPipeline(String taskIdString);

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
     * Adds a TaskFactory that is locally defined in a TaskPipeline and is not intended to be shared.
     * The factory's id must be prefixed with the pipeline id and LOCAL_TASK_PREFIX.
     */
    void addLocalTaskFactory(TaskId pipelineId, TaskFactory factory);

    /**
     * Get a list of task factories from the set of active modules in the container.
     * @param container If not null, task factories declared in modules that are active in the container are returned.
     * @return The list of task factories.
     */
    @NotNull
    Collection<TaskFactory> getTaskFactories(@Nullable Container container);

    String getDefaultExecutionLocation();

    int getDefaultAutoRetry();

    void removeTaskPipeline(TaskId pipelineId);

    void removeTaskFactory(TaskId taskId);

    /**
     * Register a XMLBeanTaskFactoryFactory that will parse the xml task factory definition into a TaskFactory instance.
     * @param schemaType The SchemaType key.
     * @param factoryFactory The XMLBeanTaskFactoryFactory that will parse the xml task factory definition.
     */
    void registerTaskFactoryFactory(SchemaType schemaType, XMLBeanTaskFactoryFactory factoryFactory);

    /**
     * Create a TaskFactory using the schema type associated with the registered XMLBeanTaskFactoryFactory.
     * The TaskFactory is created, but won't be added to the registry.
     */
    TaskFactory createTaskFactory(TaskId taskId, TaskType xtask, Path tasksDir);

    @NotNull
    AbstractFileAnalysisProtocolFactory getProtocolFactory(TaskPipeline taskPipeline);
}
