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
public class SimpleQueryTransformStepMeta extends CopyConfig
{
    Class targetStepClass = SimpleQueryTransformStep.class;

    public Class getTargetStepClass()
    {
        return targetStepClass;
    }

    public void setTargetStepClass(Class targetStepClass)
    {
        this.targetStepClass = targetStepClass;
    }

    @Override
    public String toString()
    {
        return getSourceSchema().toString() + "." + getSourceQuery() + " --> " +
                getTargetSchema().toString() + "." + getTargetQuery();
    }
}
