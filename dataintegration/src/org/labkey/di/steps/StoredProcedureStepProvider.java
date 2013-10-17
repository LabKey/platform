package org.labkey.di.steps;

import org.labkey.api.pipeline.PipelineJob;
import org.labkey.di.pipeline.TransformJobContext;
import org.labkey.di.pipeline.TransformTask;
import org.labkey.di.pipeline.TransformTaskFactory;

import java.util.Collections;
import java.util.List;

/**
 * User: tgaluhn
 * Date: 10/9/13
 */
public class StoredProcedureStepProvider implements StepProvider
{
    @Override
    public String getName()
    {
        return "StoredProcedure";
    }

    @Override
    public List<String> getLegacyNames()
    {
        return Collections.emptyList();
    }

    @Override
    public Class getStepClass()
    {
        return StoredProcedureStep.class;
    }

    @Override
    public StepMeta createMetaInstance()
    {
        return new StoredProcedureStepMeta();
    }

    @Override
    public TransformTask createStepInstance(TransformTaskFactory f, PipelineJob job, StepMeta meta, TransformJobContext context)
    {
       return new StoredProcedureStep(f, job, (StoredProcedureStepMeta)meta, context);
    }
}
