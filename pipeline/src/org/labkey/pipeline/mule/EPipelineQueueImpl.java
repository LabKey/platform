/*
 * Copyright (c) 2007-2014 LabKey Corporation
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
package org.labkey.pipeline.mule;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobData;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineStatusFile;
import org.labkey.api.pipeline.TaskFactory;
import org.labkey.api.security.User;
import org.labkey.api.util.JobRunner;
import org.labkey.pipeline.api.AbstractPipelineQueue;
import org.labkey.pipeline.api.PipelineJobServiceImpl;
import org.labkey.pipeline.api.properties.GlobusClientPropertiesImpl;
import org.labkey.pipeline.mule.filters.JobIdJmsSelectorFilter;
import org.labkey.pipeline.mule.filters.TaskJmsSelectorFilter;
import org.mule.MuleManager;
import org.mule.extras.client.MuleClient;
import org.mule.impl.RequestContext;
import org.mule.umo.UMOException;
import org.mule.umo.endpoint.UMOEndpoint;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.QueueBrowser;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

/**
 * EPipelineQueueImpl class
 * <p/>
 * Enterprise pipeline queue uses Mule to place jobs on a message queue.
 * <p/>
 * Created: Sep 28, 2007
 *
 * @author bmaclean
 */
public class EPipelineQueueImpl extends AbstractPipelineQueue
{
    private static Logger _log = Logger.getLogger(EPipelineQueueImpl.class);
    private static final String PIPELINE_QUEUE_NAME = "PipelineQueue";

    private static ThreadLocal<List<PipelineJob>> _outboundJobs = new ThreadLocal<>();

    public static List<PipelineJob> getOutboundJobs()
    {
        return _outboundJobs.get();
    }

    public static void resetOutboundJobs()
    {
        _outboundJobs.set(null);
    }

    private ConnectionFactory _factoryJms;
    private boolean _local;
    private boolean _transient;

    public EPipelineQueueImpl(ConnectionFactory factory)
    {
        assert factory != null : "Enterprise Pipeline requires a JMS connection factory.";

        if (factory instanceof ActiveMQConnectionFactory)
        {
            String brokerUrl = ((ActiveMQConnectionFactory) factory).getBrokerURL();
            assert brokerUrl != null : "ActiveMQConnectionFactory requires a broker URL."; 

            // Detect default server configuration for the Enterprise Pipeline, which does
            // not persist JMS messages between server restarts.
            _local = brokerUrl.startsWith("vm:");
            _transient = (brokerUrl.indexOf("persistent=false") > 0);
        }
        _factoryJms = factory;
    }

    public ConnectionFactory getJMSFactory()
    {
        return _factoryJms;
    }

    public boolean cancelJob(User user, Container c, PipelineStatusFile statusFile)
    {
        if (statusFile.getJobStore() != null)
        {
            PipelineJob job = PipelineJobService.get().getJobStore().fromXML(statusFile.getJobStore());
            if (job != null)
            {
                job.getLogger().info("Attempting to cancel job as requested by " + user + ".");
                PipelineJob.logStartStopInfo("Attempting to cancel job ID " + job.getJobGUID() + ", " + statusFile.getFilePath() + " as requested by " + user);
            }

            // Connect to the queue to see if we can grab the job before it starts running
            if (removeFromJMSQueue(statusFile, job)) return true;
            // If we can't find it on the queue, see if it's been submitted to Globus
            if (cancelGlobusJob(statusFile, job)) return true;
        }


        return false;
    }

    /** @return true if we found it on a Globus server and killed it, thus cancelling the job */
    private boolean cancelGlobusJob(final PipelineStatusFile statusFile, final PipelineJob job)
    {
        TaskFactory taskFactory = job.getActiveTaskFactory();
        for (GlobusClientPropertiesImpl globus : PipelineJobServiceImpl.get().getGlobusClientPropertiesList())
        {
            String name = globus.getLocation();
            // Check if it's running through Globus
            if (taskFactory != null && taskFactory.getExecutionLocation() != null && taskFactory.getExecutionLocation().equals(name))
            {
                // It is running through Globus - kill it
                JobRunner.getDefault().execute(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        try
                        {
                            GlobusJobWrapper wrapper = new GlobusJobWrapper(job, false, false);
                            job.getLogger().info("Cancelling job by submitting request to Globus.");
                            PipelineJob.logStartStopInfo("Cancelling job by submitting request to Globus. Job ID: " + job.getJobGUID() + ", " + statusFile.getFilePath());
                            wrapper.cancel();
                        }
                        catch (Exception e)
                        {
                            _log.error("Error attempting to cancel job " + statusFile.getFilePath(), e);
                        }
                    }

                });
                return true;
            }
        }
        return false;
    }


    /** @return true if we found it on the queue and removed it, thus cancelling the job */
    private boolean removeFromJMSQueue(PipelineStatusFile statusFile, PipelineJob job)
    {
        // Check that we're configured with a JobQueue
        Map endpoints = MuleManager.getInstance().getEndpoints();
        UMOEndpoint ep = (UMOEndpoint) endpoints.get("JobQueue");
        if (ep == null)
        {
            PipelineJob.logStartStopInfo("JobQueue is not available in JMS. Unable to cancel job ID: " + job.getJobGUID() + " if it is in the JMS queue");
            return false;
        }

        Connection conn = null;
        MessageConsumer consumer = null;
        try
        {
            conn = _factoryJms.createConnection();
            Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

            JobIdJmsSelectorFilter filter = new JobIdJmsSelectorFilter(statusFile.getJobId());

            consumer = session.createConsumer(session.createQueue(ep.getEndpointURI().getAddress()), filter.getExpression());
            conn.start();
            // Don't block - just see if there's anything there right now
            Message message = consumer.receiveNoWait();
            if (message != null)
            {
                // We found it, which means we removed it from the queue
                // Set it to CANCELLED because it's now dead
                if (job != null)
                {
                    job.getLogger().info("Cancelling job by deleting from JMS queue.");
                    PipelineJob.logStartStopInfo("Cancelling job by deleting from JMS queue. Job ID: " + job.getJobGUID() + ", " + statusFile.getFilePath());
                }
                else
                {
                    PipelineJob.logStartStopInfo("Failed to deserialize job being canceled. Job ID: " + statusFile.getJobId() + ", " + statusFile.getFilePath());
                }
                statusFile.setStatus(PipelineJob.TaskStatus.cancelled.toString());
                statusFile.save();
            }
            else
            {
                PipelineJob.logStartStopInfo("Failed find job in JMS queue to cancel it. It may already be running its next task. Job ID: " + job.getJobGUID() + ", " + statusFile.getFilePath());
            }
        }
        catch (JMSException e)
        {
            _log.error("Error browsing message queue at '" + ep.getEndpointURI(), e);
        }
        finally
        {
            if (consumer != null) { try { consumer.close(); } catch (JMSException ignored) {} }
            if (conn != null) { try { conn.close(); } catch (JMSException ignored) {} }
        }
        return false;
    }

    public List<PipelineJob> findJobs(String location)
    {
        Map endpoints = MuleManager.getInstance().getEndpoints();
        UMOEndpoint ep = (UMOEndpoint) endpoints.get("JobQueue");
        if (ep == null)
        {
            return Collections.emptyList();
        }

        List<PipelineJob> result = new ArrayList<>();
        Connection conn = null;
        try
        {
            conn = _factoryJms.createConnection();
            Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

            TaskJmsSelectorFilter filter = new TaskJmsSelectorFilter();
            filter.setLocation(location);

            QueueBrowser browser = session.createBrowser(session.createQueue(ep.getEndpointURI().getAddress()), filter.getExpression());
            conn.start();
            for (Enumeration msgs = browser.getEnumeration(); msgs.hasMoreElements() ;)
            {
                Message msg = (Message) msgs.nextElement();

                PipelineJob job = PipelineJobService.get().getJobStore().fromXML(((TextMessage)msg).getText());
                result.add(job);
            }
        }
        catch (JMSException e)
        {
            _log.error("Error browsing message queue at '" + ep.getEndpointURI(), e);
        }
        finally
        {
            if (conn != null) { try { conn.close(); } catch (JMSException ignored) {} }
        }
        return result;
    }

    protected void enqueue(PipelineJob job)
    {
        if (RequestContext.getEvent() == null)
        {
            try
            {
                dispatchJob(job);
            }
            catch (UMOException e)
            {
                _log.error(e);

                // If dispatch failed, make sure the job is set to error,
                // so it can be retried.
                job.error(e.getMessage(), e);
            }
        }
        else
        {
            _log.debug("MuleClient does not work reliably from inside an event. Using outbound routing.");
            if (_outboundJobs.get() == null)
                _outboundJobs.set(new ArrayList<PipelineJob>());
            _outboundJobs.get().add(job);
        }
    }

    public boolean isLocal()
    {
        return _local;
    }

    public boolean isTransient()
    {
        return _transient;
    }

    public static void dispatchJob(PipelineJob job) throws UMOException
    {
        assert RequestContext.getEvent() == null :
                "RequestContext found dispatching job.";

        MuleClient client = null;
        try
        {
            client = new MuleClient();
            client.dispatch(EPipelineQueueImpl.PIPELINE_QUEUE_NAME, job, null);
        }
        finally
        {
            if (client != null)
            {
                try { client.dispose(); } catch (Exception e) {}
                RequestContext.clear();
            }
        }        
    }

    public PipelineJob findJobInMemory(Container c, String statusFile)
    {
        throw new UnsupportedOperationException("No useful information about jobs in memory.");
    }

    public PipelineJobData getJobDataInMemory(Container c)
    {
        throw new UnsupportedOperationException("No useful information about jobs in memory.");
    }

    public void starting(PipelineJob job, Thread thread)
    {
        throw new UnsupportedOperationException("Mini-pipeline maintenance notification not supported.");
    }

    public void done(PipelineJob job)
    {
        throw new UnsupportedOperationException("Mini-pipeline maintenance notification not supported.");
    }
}
