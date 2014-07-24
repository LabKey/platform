package org.labkey.di.steps;

import org.labkey.api.pipeline.PipelineJob;
import org.labkey.di.pipeline.TransformJobContext;
import org.labkey.di.pipeline.TransformTask;
import org.labkey.di.pipeline.TransformTaskFactory;

import java.util.Collections;
import java.util.List;

/**
 * User: tgaluhn
 * Date: 7/21/2014
 */
public class TaskrefTransformStepProvider implements StepProvider
{
    @Override
    public String getName()
    {
        return TaskrefTransformStep.class.getSimpleName();
    }

    @Override
    public List<String> getLegacyNames()
    {
        return Collections.emptyList();
    }

    @Override
    public Class getStepClass()
    {
        return TaskrefTransformStep.class;
    }

    @Override
    public StepMeta createMetaInstance()
    {
        return new TaskrefTransformStepMeta();
    }

    @Override
    public TransformTask createStepInstance(TransformTaskFactory f, PipelineJob job, StepMeta meta, TransformJobContext context)
    {
        return new TaskrefTransformStep(f, job, (TaskrefTransformStepMeta)meta, context);
    }
}
