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
