package org.labkey.di.steps;

import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.di.pipeline.TransformTask;
import org.labkey.di.pipeline.TransformTaskFactory;

/**
 * User: tgaluhn
 * Date: 11/19/2014
 *
 * Allow certain types of regular pipeline tasks to be registered within an ETL context.
 * So far TransformPipelineJob has been modified to implement FileAnalysisJobSupport; this provides
 * basic support to run instances of CommandTask as steps in an ETL
 */
public class ExternalPipelineTaskStep extends TransformTask
{
    public ExternalPipelineTaskStep(TransformTaskFactory factory, PipelineJob job, StepMeta meta)
    {
        super(factory, job, meta);
    }

    @Override
    public void doWork(RecordedAction action) throws PipelineJobException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean hasWork()
    {
        return true;
    }
}
