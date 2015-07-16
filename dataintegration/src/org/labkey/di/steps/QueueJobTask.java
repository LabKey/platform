package org.labkey.di.steps;

import org.apache.xmlbeans.XmlException;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.di.ScheduledPipelineJobDescriptor;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.view.NotFoundException;
import org.labkey.di.pipeline.TaskRefTaskImpl;
import org.labkey.di.pipeline.TransformJobContext;
import org.labkey.di.pipeline.TransformManager;
import org.labkey.di.pipeline.TransformPipelineJob;

import java.util.Map;

/**
 * User: tgaluhn
 * Date: 5/8/2015
 *
 * Allows one ETL to queue another (or to requeue itself)
 */
public class QueueJobTask extends TaskRefTaskImpl
{
    private String _transformId;

    @Override
    public RecordedActionSet run(@NotNull PipelineJob job) throws PipelineJobException
    {
        if (!(job instanceof TransformPipelineJob)) // shouldn't ever happen, but otherwise we can't get at transform properties
            throw new PipelineJobException("PipelineJob instance is not a TransformPipelineJob");
        TransformPipelineJob transformJob = (TransformPipelineJob) job;

        // Default to requeue self if no transformId set
        if (null == _transformId)
            _transformId = transformJob.getTransformDescriptor().getId();
        ScheduledPipelineJobDescriptor newEtl = TransformManager.get().getDescriptor(_transformId);
        if (newEtl == null)
            throw new NotFoundException(_transformId);
        TransformJobContext context = transformJob.getTransformJobContext();
        if (newEtl.isPending(context))
        {
            transformJob.info(TransformManager.getJobPendingMessage(null));
        }
        else
        {
            Integer jobId = TransformManager.get().runNowPipeline(newEtl, context.getContainer(), context.getUser(), context.getParams(), transformJob.getParameters(), transformJob.getAnalysisDirectory(), transformJob.getBaseName());
            if (null == jobId)
                transformJob.info("No work for queued ETL " + _transformId);
            else transformJob.info("Queued job " + jobId.toString() + " for ETL " + _transformId);
        }
        return new RecordedActionSet(makeRecordedAction());
    }

    @Override
    public void setSettings(Map<String, String> xmlSettings) throws XmlException
    {
        super.setSettings(xmlSettings);
        _transformId = settings.get("transformId");
        if (null != _transformId && null == TransformManager.get().getDescriptor(_transformId))
            throw new XmlException(QueueJobTask.class.getName() + " can't find ETL to be queued: " + _transformId);
    }
}
