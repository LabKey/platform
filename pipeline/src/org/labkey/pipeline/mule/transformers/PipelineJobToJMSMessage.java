/*
 * Copyright (c) 2007-2016 LabKey Corporation
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
package org.labkey.pipeline.mule.transformers;

import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.TaskFactory;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipeline;
import org.mule.providers.jms.transformers.ObjectToJMSMessage;
import org.mule.transformers.AbstractEventAwareTransformer;
import org.mule.umo.UMOEventContext;
import org.mule.umo.transformer.TransformerException;

import javax.jms.JMSException;
import javax.jms.Message;
import java.io.IOException;
import java.sql.SQLException;

/**
 * <code>PipelineJobToJMSMessage</code> transforms a PipelineJob to a JMS Message
 * with XML payload, and special job JMS headers for routing.
 */
public class PipelineJobToJMSMessage extends AbstractEventAwareTransformer
{
    private ObjectToJMSMessage _transformerToJMS;

    public Object transform(Object src, String encoding, UMOEventContext context) throws TransformerException
    {
        try
        {
            return transformJob((PipelineJob) src);
        }
        catch (Exception e)
        {
            logger.error("Failed transforming job " + src, e);
            throw new TransformerException(this, e);
        }
    }

    protected Message transformJob(PipelineJob job) throws Exception
    {
        String xml = PipelineJobService.get().getJobStore().toXML(job);

        if (logger.isDebugEnabled())
            logger.debug("Job xml for " + job.getClass() + ":\n" + xml);

        Message msg = (Message) getJMSTransformer().transform(xml);
        setJmsProperties(job, msg);
        return msg;
    }

    protected ObjectToJMSMessage getJMSTransformer()
    {
        if (_transformerToJMS == null)
        {
            _transformerToJMS = new ObjectToJMSMessage();
            _transformerToJMS.setEndpoint(getEndpoint());
        }

        return _transformerToJMS;
    }

    protected void setJmsProperties(PipelineJob job, Message msg) throws JMSException, IOException, SQLException
    {
        setJmsProperty(msg, PipelineJob.LABKEY_JOBTYPE_PROPERTY, job.getClass().getName());
        setJmsProperty(msg, PipelineJob.LABKEY_JOBID_PROPERTY, job.getJobGUID());
        setJmsProperty(msg, PipelineJob.LABKEY_CONTAINERID_PROPERTY, job.getContainerId());
        TaskPipeline tp = job.getTaskPipeline();
        if (tp == null)
        {
            setJmsProperty(msg, PipelineJob.LABKEY_TASKPIPELINE_PROPERTY, PipelineJob.class.getName());            
            setJmsProperty(msg, PipelineJob.LABKEY_TASKSTATUS_PROPERTY, job.getActiveTaskStatus().toString());
        }
        else
        {
            setJmsProperty(msg, PipelineJob.LABKEY_TASKPIPELINE_PROPERTY, tp.getId().toString());
            setJmsProperty(msg, PipelineJob.LABKEY_TASKSTATUS_PROPERTY, job.getActiveTaskStatus().toString());
            TaskId task = job.getActiveTaskId();

            // Determine execution location based on web server's notion of who owns what tasks
            String location = null;
            if (task != null)
            {
                setJmsProperty(msg, PipelineJob.LABKEY_TASKID_PROPERTY, task.toString());
                TaskFactory factory = PipelineJobService.get().getTaskFactory(task);
                if (factory != null)
                {
                    location = factory.getExecutionLocation();
                }
            }

            // Default to running in the main thread pool on the web server
            if (location == null)
            {
                location = TaskFactory.WEBSERVER;
            }
            setJmsProperty(msg, PipelineJob.LABKEY_LOCATION_PROPERTY, location);
        }
    }

    protected void setJmsProperty(Message msg, String key, String value)
    {
        try
        {
            msg.setStringProperty(key, value);
        }
        catch (JMSException e)
        {
            // Various JMS servers have slightly different rules to what
            // can be set as an object property on the message; therefore
            // we have to take a hit n' hope approach
            if (logger.isDebugEnabled())
            {
                logger.debug("Unable to set property '" + key + "' to '" +
                        value + "': " + e.getMessage());
            }
        }
    }
}
