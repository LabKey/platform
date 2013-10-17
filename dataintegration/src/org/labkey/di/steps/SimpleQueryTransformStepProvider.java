package org.labkey.di.steps;

import org.labkey.api.pipeline.PipelineJob;
import org.labkey.di.pipeline.TransformJobContext;
import org.labkey.di.pipeline.TransformTask;
import org.labkey.di.pipeline.TransformTaskFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * User: tgaluhn
 * Date: 10/9/13
 */
public class SimpleQueryTransformStepProvider implements StepProvider
{
    @Override
    public String getName()
    {
        return "SimpleQueryTransformStep";
    }

    @Override
    public List<String> getLegacyNames()
    {
        return Collections.unmodifiableList(Arrays.asList(null, "org.labkey.di.pipeline.TransformTask"));
    }

    @Override
    public Class getStepClass()
    {
        return SimpleQueryTransformStep.class;
    }

    @Override
    public StepMeta createMetaInstance()
    {
        return new SimpleQueryTransformStepMeta();
    }

    @Override
    public TransformTask createStepInstance(TransformTaskFactory f, PipelineJob job, StepMeta meta, TransformJobContext context)
    {
        return new SimpleQueryTransformStep(f, job, (SimpleQueryTransformStepMeta)meta, context);
    }
}
