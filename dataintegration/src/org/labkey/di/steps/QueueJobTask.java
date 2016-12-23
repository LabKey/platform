/*
 * Copyright (c) 2015-2016 LabKey Corporation
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
        if (newEtl.isPending(context) && !newEtl.isAllowMultipleQueuing())
        {
            transformJob.info(TransformManager.getJobPendingMessage(null));
        }
        else
        {
            if (transformJob.getOutputFileBaseNames().isEmpty())
            {
                queueJob(transformJob, newEtl, context, transformJob.getBaseName());
            }
            else
            {
                for (String basename : transformJob.getOutputFileBaseNames())
                {
                    queueJob(transformJob, newEtl, context, basename);
                }
            }
        }
        return new RecordedActionSet(makeRecordedAction());
    }

    private void queueJob(TransformPipelineJob transformJob, ScheduledPipelineJobDescriptor newEtl, TransformJobContext context, String basename) throws PipelineJobException
    {
        TransformJobContext newContext = (TransformJobContext) newEtl.getJobContext(context.getContainer(), context.getUser(), context.getParams());
        // If the etl is requeuing itself, don't pass on the incrementalWindow setting; let the requeued job pick up the filter settings from the original
        // Otherwise, do chain the incrementalWindow through
        if (!newContext.getTransformId().equals(context.getTransformId()))
        {
            newContext.setIncrementalWindow(context.getIncrementalWindow());
        }
        Integer jobId = TransformManager.get().runNowPipeline(newEtl, newContext, transformJob.getParameters(), transformJob.getAnalysisDirectory(), basename);
        if (null == jobId)
            transformJob.info("No work for queued ETL " + _transformId);
        else transformJob.info("Queued job " + jobId.toString() + " for ETL " + _transformId);
    }

    @Override
    public void setSettings(Map<String, String> xmlSettings) throws XmlException
    {
        super.setSettings(xmlSettings);
        _transformId = settings.get("transformId");

        // Removed because this check introduces re-entrancy during cache loading. TODO: Do this validation elsewhere...
//        if (null != _transformId && null == TransformManager.get().getDescriptor(_transformId))
//            throw new XmlException(QueueJobTask.class.getName() + " can't find ETL to be queued: " + _transformId);
    }
}
