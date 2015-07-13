/*
 * Copyright (c) 2014-2015 LabKey Corporation
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
package org.labkey.api.di;

import org.apache.xmlbeans.XmlException;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.writer.ContainerUser;

import java.util.List;
import java.util.Map;

/**
 * User: tgaluhn
 * Date: 7/22/2014
 */

/**
 * A more generic interface for ETL transform task types that aren't going to be fully defined in the etl.xsd
 * The fully qualified classname of an implementor of this interface can be specified in an ETL transform taskref
 * element, with configuration in settings/setting child elements. The taskref element is compatible with the
 * definition in pipeline.xsd
 *
 * Ultimately we'd like to be able to share tasks between ETL and Pipeline; as a step in that direction
 * the signature of the run() method here is similar to that of PipelineJob.Task<TaskFactory>.run(). If this is the route to take
 * for unification, we'll need to reconcile between the logger being passed in here vs the the job member variable in the task.
 * Also to aid in unification, implementors of this interface should ideally be decoupled from api.di and dataintegration. This is why
 * we downcast the TransformJobContext to the ContainerUser interface when passing it to the task.
 *
 */
public interface TaskRefTask
{
    /**
     * The method that does the real work in the implementing class.
     * @return RecordedActionSet Any parameters of any RecordedActions in the set get persisted in the job parameter set in
     *          dataintegration.TransformConfiguration.TransformState
     * @throws PipelineJobException
     * @param job
     */
    RecordedActionSet run(@NotNull PipelineJob job) throws PipelineJobException;

    /**
     * For upfront validation; if any of these are missing, we won't even try to run the task
     */
    List<String> getRequiredSettings();

    /**
     * Settings read from the etl xml (or elsewhere). Eventually would be good to use TaskFactorySettings instead.
     */
    void setSettings(Map<String, String> settings) throws XmlException;

    /**
     * Downcast from the ScheduledPipelineJobContext or TransformJobContext that the etl job will be otherwise passing
     * around. There's probably a more pipeline-y way of passing this context info to a task, so this method may eventually
     * go away.
     *
     */
    void setContainerUser(ContainerUser containerUser);
}
