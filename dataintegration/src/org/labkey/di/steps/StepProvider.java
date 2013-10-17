package org.labkey.di.steps;

import org.labkey.api.pipeline.PipelineJob;
import org.labkey.di.pipeline.TransformJobContext;
import org.labkey.di.pipeline.TransformTask;
import org.labkey.di.pipeline.TransformTaskFactory;

import java.util.List;

/**
 * User: tgaluhn
 * Date: 10/9/13
 */
public interface StepProvider
{
    public String getName();
    public List<String> getLegacyNames();
    public Class getStepClass();
    public StepMeta createMetaInstance();
    public TransformTask createStepInstance(TransformTaskFactory f, PipelineJob job, StepMeta meta, TransformJobContext context);
}
