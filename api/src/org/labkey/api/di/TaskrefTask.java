/*
 * Copyright (c) 2014 LabKey Corporation
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

import org.apache.log4j.Logger;
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
 * the signature of the run() method here intentionally matches that of PipelineJob.Task<TaskFactory>.run().
 * Also as such, implementors of this interface should ideally be decoupled from api.di and dataintegration. This is why
 * we downcast the TransformJobContext to the ContainerUser interface when passing it to the task.
 *
 */
public interface TaskrefTask
{
    /**
     * The method that does the real work in the implementing class. Signature matches PipelineJob.Task<TaskFactory>.run()
     * @return RecordedActionSet Any parameters of any RecordedActions in the set get persisted in the job parameter set in
     *          dataintegration.TransformConfiguration.TransformState
     * @throws PipelineJobException
     */
    public RecordedActionSet run() throws PipelineJobException;

    /**
     * For upfront validation; if any of these are missing, we won't even try to run the task
     */
    public List<String> getRequiredSettings();

    /**
     * Settings read from the etl xml (or elsewhere). Eventually would be good to use TaskFactorySettings instead.
     */
    public void setSettings(Map<String, String> settings);

    /**
     * Downcast from the ScheduledPipelineJobContext or TransformJobContext that the etl job will be otherwise passing
     * around. There's probably a more pipeline-y way of passing this context info to a task, so this method may eventually
     * go away.
     *
     */
    public void setContainerUser(ContainerUser containerUser);

    /**
     * A log4j logger. In this context it is from the ETL job so messages are logged into the ETL job log. As with the
     * ContainerUser context, there may be a more pipeline-y way of passing this.
     *
     */
    public void setLogger(Logger logger);
}
