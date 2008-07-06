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

import org.labkey.api.pipeline.*;
import org.labkey.api.data.Container;
import org.labkey.pipeline.api.PipelineStatusManager;
import org.labkey.pipeline.PipelineModule;
import org.mule.extras.client.MuleClient;
import org.mule.umo.UMOException;
import org.mule.umo.transformer.UMOTransformer;
import org.mule.umo.transformer.TransformerException;
import org.mule.umo.endpoint.UMOEndpoint;
import org.mule.MuleManager;
import org.mule.impl.RequestContext;
import org.apache.log4j.Logger;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.jms.*;
import java.io.IOException;
import java.io.File;
import java.sql.SQLException;
import java.util.Enumeration;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

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

    public EPipelineQueueImpl(ConnectionFactory factory)
    {
        assert factory != null : "Enterprise Pipeline requires a JMS connection factory.";
        
        _factoryJms = factory;
    }

    public PipelineJobData getJobData(Container c)
    {
        PipelineJobData data =  new PipelineJobData();
        Map endpoints = MuleManager.getInstance().getEndpoints();
        UMOEndpoint ep = (UMOEndpoint) endpoints.get("JobQueue");
        if (ep == null)
            return data;

        Connection conn = null;
        Session session = null;
        QueueBrowser browser = null;
        try
        {
            conn = _factoryJms.createConnection();
            session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
            browser = session.createBrowser(session.createQueue(ep.getEndpointURI().getAddress()));
            conn.start();
            for (Enumeration msgs = browser.getEnumeration(); msgs.hasMoreElements() ;)
            {
                Message msg = (Message) msgs.nextElement();

                PipelineJob job = PipelineJobService.get().getJobStore().fromXML(((TextMessage)msg).getText());
                data.addPendingJob(job);
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

        return data;
    }

    public void starting(PipelineJob job, Thread thread)
    {
    }

    public void done(PipelineJob job)
    {
    }

    public boolean cancelJob(Container c, int jobId)
    {
        return false;
    }

    public PipelineJob findJob(Container c, String statusFile)
    {
        return null;
    }

    public void addJob(PipelineJob job) throws IOException
    {
        addJob(job, PipelineJob.WAITING_STATUS);
    }

    public void addJob(PipelineJob job, String initialState) throws IOException
    {
        try
        {
            // Make sure status file path and Job ID are in synch.
            File statusFile = job.getStatusFile();
            if (statusFile != null)
                PipelineStatusManager.resetJobId(job.getStatusFile().getAbsolutePath(), job.getJobGUID());
        }
        catch (SQLException e)
        {
            _log.warn(e);  // This is not currently a hard dependency.
        }

        job.setQueue(this, initialState);
        if (RequestContext.getEvent() == null)
        {
            try
            {
                dispatchJob(job);
            }
            catch (UMOException e)
            {
                // CONSIDER: Throw something?
                _log.error(e);
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

    public static void dispatchJob(PipelineJob job) throws UMOException
    {
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
                client.dispose();
                RequestContext.clear();
            }
        }        
    }
}
