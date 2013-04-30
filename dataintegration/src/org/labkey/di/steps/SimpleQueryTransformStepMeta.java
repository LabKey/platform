/*
 * Copyright (c) 2013 LabKey Corporation
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
package org.labkey.di.steps;

import org.labkey.api.etl.CopyConfig;
import org.labkey.api.query.SchemaKey;

/**
 * Created with IntelliJ IDEA.
 * User: matthewb
 * Date: 2013-04-03
 * Time: 2:22 PM
 *
 * Metadata for a simple query transform
 */
public class SimpleQueryTransformStepMeta extends CopyConfig implements StepMeta
{
    Class targetStepClass = SimpleQueryTransformStep.class;
    Class taskClass;
    private String description;

    public Class getTargetStepClass()
    {
        return targetStepClass;
    }

    public void setTargetStepClass(Class targetStepClass)
    {
        this.targetStepClass = targetStepClass;
    }

    public Class getTaskClass()
    {
        return taskClass;
    }

    public void setTaskClass(Class taskClass)
    {
        this.taskClass = taskClass;
    }

    public String getDescription()
    {
        return description;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    @Override
    public String toString()
    {
        return getSourceSchema().toString() + "." + getSourceQuery() + " --> " +
                getTargetSchema().toString() + "." + getTargetQuery();
    }
}
