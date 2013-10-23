package org.labkey.di.steps;

import org.labkey.api.pipeline.PipelineJob;
import org.labkey.di.pipeline.TransformJobContext;
import org.labkey.di.pipeline.TransformTask;
import org.labkey.di.pipeline.TransformTaskFactory;

import java.util.Collections;
import java.util.List;

/**
 * User: gktaylor
 * Date: 10/10/13
 */
public class RemoteQueryTransformStepProvider implements StepProvider
{
    @Override
    public String getName()
    {
        return "RemoteQueryTransformStep";
    }

    @Override
    public List<String> getLegacyNames()
    {
        return Collections.emptyList();
    }

    @Override
    public Class getStepClass()
    {
        return RemoteQueryTransformStep.class;
    }

    @Override
    public StepMeta createMetaInstance()
    {
        return new RemoteQueryTransformStepMeta();
    }

    @Override
    public TransformTask createStepInstance(TransformTaskFactory f, PipelineJob job, StepMeta meta, TransformJobContext context)
    {
        return new RemoteQueryTransformStep(f, job, (RemoteQueryTransformStepMeta)meta, context);
    }
}
