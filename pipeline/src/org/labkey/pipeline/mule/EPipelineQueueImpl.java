/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.pipeline.*;
import org.labkey.api.data.Container;
import org.labkey.pipeline.api.PipelineStatusFileImpl;
import org.labkey.pipeline.api.PipelineStatusManager;
import org.labkey.pipeline.mule.filters.TaskJmsSelectorFilter;
import org.mule.extras.client.MuleClient;
import org.mule.umo.UMOException;
import org.mule.umo.endpoint.UMOEndpoint;
import org.mule.MuleManager;
import org.mule.impl.RequestContext;
import org.apache.log4j.Logger;
import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.*;
import java.io.IOException;
import java.io.File;
import java.sql.SQLException;
import java.util.*;

/**
 * EPipelineQueueImpl class
 * <p/>
 * Enterprise pipeline queue uses Mule to place jobs on a message queue.
 * <p/>
 * Created: Sep 28, 2007
 *
 * @author bmaclean
 */
public class EPipelineQueueImpl implements PipelineQueue
{
    private static Logger _log = Logger.getLogger(EPipelineQueueImpl.class);
    private static final String PIPELINE_QUEUE_NAME = "PipelineQueue";

    private static ThreadLocal<List<PipelineJob>> _outboundJobs = new ThreadLocal<List<PipelineJob>>();

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

    public boolean cancelJob(Container c, String jobId)
    {
        // todo: implement this!
        
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

        List<PipelineJob> result = new ArrayList<PipelineJob>();
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
            if (conn != null)
            {
                try
                {   conn.close(); }
                catch (JMSException e)
                {}
            }
        }
        return result;
    }

    public void addJob(PipelineJob job) throws IOException
    {
        // Duplicate code from PipelineQueueImpl, should be refactored into a superclass
        File logFile = job.getLogFile();
        try
        {
            if (logFile != null)
            {
                // Check if we have an existing entry in the database
                PipelineStatusFileImpl pipelineStatusFile = PipelineStatusManager.getStatusFile(logFile.getAbsolutePath());
                if (pipelineStatusFile == null)
                {
                    // Insert it if we don't
                    PipelineStatusManager.setStatusFile(job, job.getUser(), PipelineJob.WAITING_STATUS, null, true);
                }

                // Reset the ID in case this was a resubmit
                PipelineStatusManager.resetJobId(job.getLogFile().getAbsolutePath(), job.getJobGUID());
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }

        if (job.setQueue(this, PipelineJob.WAITING_STATUS))
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
