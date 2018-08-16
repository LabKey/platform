/*
 * Copyright (c) 2013-2016 LabKey Corporation
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
package org.labkey.di.pipeline;

import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.di.ScheduledPipelineJobContext;
import org.labkey.api.di.ScheduledPipelineJobDescriptor;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.util.UnexpectedException;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;


/**
 * User: matthewb
 * Date: 2013-03-18
 * Time: 4:05 PM
 */
public class TransformQuartzJobRunner implements Job
{
    private static final Logger LOG = Logger.getLogger(TransformQuartzJobRunner.class);


    public TransformQuartzJobRunner()
    {
    }


    @Override
    public void execute(JobExecutionContext context)
    {
        String jobName = "";

        //most likely this will not happen - i.e. context will never be null
        if (null == context)
        {
            LOG.error("Unable to queue an ETL job, JobExecutionContext object is null.");
            return;
        }
        if (null == context.getJobDetail())
        {
            LOG.error("Unable to get ETL context job detail.");
            return;
        }

        if (null == context.getJobDetail().getKey())
        {
            LOG.info("ETL job detail key not found, will be unable to find job name.");
            jobName = "Unable to find Job name.";
        }
        else
        {
           jobName = context.getJobDetail().getKey().getName();
        }

        LOG.debug("Begin queuing of an ETL job: " + jobName);

        ScheduledPipelineJobContext infoTemplate;
        ScheduledPipelineJobContext info;
        ScheduledPipelineJobDescriptor d = null;

        try
        {
            infoTemplate = ScheduledPipelineJobContext.getFromQuartzJobDetail(context);
            info = infoTemplate.clone();
            d = getDescriptorFromJobDetail(context, info.getContainer());

            if (d.isPending(info) && !d.isAllowMultipleQueuing())
            {
                LOG.info(TransformManager.getJobPendingMessage(d.getId()));
                return;
            }
            boolean hasWork = d.checkForWork(info, true, info.isVerbose());

            if (!hasWork)
            {
                LOG.debug("No work. Job " + d.getId() + " not queued.");
                return;
            }

            PipelineJob job = d.getPipelineJob(info);
            if (null == job)
            {
                LOG.debug("ETL Job " + d.getId() + " not found.");
                return;
            }

            try
            {
                PipelineService.get().setStatus(job, PipelineJob.TaskStatus.waiting.toString(), null, true);
            }
            catch (Exception e)
            {
                LOG.error("Unable to queue ETL job", e);
            }

            try
            {
                PipelineService.get().queueJob(job);
            }
            catch (Exception e)
            {
                LOG.error("Unable to queue ETL job", e);
                job.setStatus(PipelineJob.TaskStatus.error, "Unable to queue ETL job. " + e.getMessage());
                job.done(null);
            }
        }
        catch (RuntimeException x)
        {
            LOG.error("Something went wrong while attempting to queue an ETL job " + jobName, x);
            throw x;
        }
        catch (Exception x)
        {
            LOG.error("Something went wrong while attempting to queue an ETL job " + jobName, x);
            throw new UnexpectedException(x);
        }

        LOG.debug("END queuing an ETL job " + jobName);
    }


    public static ScheduledPipelineJobDescriptor getDescriptorFromJobDetail(JobExecutionContext jobExecutionContext, Container c)
    {
        JobDataMap map = jobExecutionContext.getJobDetail().getJobDataMap();
        Object result = map.get(ScheduledPipelineJobDescriptor.class.getName());
        if (result == null)
        {
            map = jobExecutionContext.getJobDetail().getJobDataMap();
            result = map.get(ScheduledPipelineJobDescriptor.class.getName());
            if (result == null)
                throw new IllegalArgumentException("No ScheduledPipelineJobDescriptor found!");
        }
        return ((TransformDescriptor) result).getDescriptorFromCache(c);
    }
}
