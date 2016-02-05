/*
 * Copyright (c) 2008-2016 LabKey Corporation
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

import org.mule.config.builders.MuleXmlConfigurationBuilder;
import org.mule.extras.client.MuleClient;
import org.mule.impl.RequestContext;
import org.springframework.beans.factory.BeanFactory;
import org.labkey.pipeline.api.PipelineJobServiceImpl;
import org.labkey.pipeline.AbstractPipelineStartup;
import org.labkey.pipeline.mule.filters.TaskJmsSelectorFilter;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.util.BreakpointThread;
import org.apache.log4j.Logger;

import javax.jms.*;
import java.io.*;
import java.lang.IllegalStateException;
import java.util.*;

/*
* User: jeckels
* Date: Jun 26, 2008
*/
public class RemoteServerStartup extends AbstractPipelineStartup
{
    private static Logger _log = Logger.getLogger(RemoteServerStartup.class);
    private static final String JOB_QUEUE_ADDRESS = "job.queue";

    /**
     * This method is invoked by reflection - don't change its signature without changing org.labkey.bootstrap.RemoteServerBootstrap 
     */
    public void run(List<File> moduleFiles, List<File> moduleConfigFiles, List<File> customConfigFiles, File webappDir, String[] args) throws Exception
    {
        Map<String, BeanFactory> factories = initContext("log4j.xml", moduleFiles, moduleConfigFiles, customConfigFiles, webappDir, PipelineJobService.LocationType.RemoteServer);
        LoggerUtil.rollErrorLogFile(_log);

        _log.info("Starting up LabKey Remote Server");

        doSharedStartup(moduleFiles);

        PipelineJobService.RemoteServerProperties props = PipelineJobServiceImpl.get().getRemoteServerProperties();
        if (props == null)
        {
            throw new IllegalArgumentException("No RemoteServerProperties object registered. Be sure that your configuration directory is set correctly.");
        }
        String muleConfig = props.getMuleConfig();
        if (muleConfig == null)
            muleConfig = "org/labkey/pipeline/mule/config/remoteMuleConfig.xml";

        setupMuleConfig(muleConfig, factories, props.getHostName());

        // Grab the set of job ids before we start trying to process anything
        RequeueLostJobsRequest request = getRequeueRequest(factories.get("Pipeline"));

        // Request the the server requeue any jobs that might have been in process when this location shut down
        MuleClient client = null;
        try
        {
            // Ask the server to requeue all of the jobs that are assigned to this location but aren't on the queue
            // anymore. These jobs represent what was in progress when this server was last shut down.
            // We give the server a list of the job ids that are already on the queue so that we can start cranking on
            // whatever's left on the queue right away.
            client = new MuleClient();
            client.dispatch("StatusQueue", request, null);
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

    /**
     * Browse the JMS queue to grab all the tasks currently assigned to this location and build up a set of JobIds.
     */
    private RequeueLostJobsRequest getRequeueRequest(BeanFactory beanFactory)
    {
        // Figure out what location we're supposed to be
        PipelineJobService.RemoteServerProperties remoteProps = PipelineJobService.get().getRemoteServerProperties();
        if (remoteProps == null)
        {
            throw new java.lang.IllegalStateException("No remoteServerProperties registered with the PipelineJobService.");
        }
        String location = remoteProps.getLocation();
        String hostName = remoteProps.getHostName();

        // Figure out where to talk to the JMS queue
        Object bean = beanFactory.getBean("activeMqConnectionFactory");
        if (bean == null)
        {
            throw new IllegalStateException("Could not find activeMqConnectionFactory bean in the pipeline module's bean factory");
        }
        if (!(bean instanceof ConnectionFactory))
        {
            throw new IllegalStateException("The activeMqConnectionFactory bean in the pipeline module's bean factory was expected to be a " + ConnectionFactory.class.getName() + " but was a " + bean.getClass().getName());
        }

        return getRequeueRequest((ConnectionFactory)bean, Collections.singleton(location), hostName);
    }

    /**
     * Browse the JMS queue to grab all the tasks currently assigned to this location and build up a set of JobIds.
     */
    public RequeueLostJobsRequest getRequeueRequest(ConnectionFactory connectionFactory, Collection<String> locations, String hostName)
    {
        Set<String> ids = new HashSet<>();
        Connection conn = null;
        try
        {
            conn = connectionFactory.createConnection();
            Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
            for (String location : locations)
            {
                TaskJmsSelectorFilter filter = new TaskJmsSelectorFilter();
                filter.setLocation(location);

                QueueBrowser browser = session.createBrowser(session.createQueue(JOB_QUEUE_ADDRESS), filter.getExpression());
                conn.start();
                for (Enumeration msgs = browser.getEnumeration(); msgs.hasMoreElements() ;)
                {
                    Message msg = (Message) msgs.nextElement();

                    PipelineJob job = PipelineJobService.get().getJobStore().fromXML(((TextMessage)msg).getText());
                    ids.add(job.getJobGUID());
                }
            }
        }
        catch (JMSException e)
        {
            _log.error("Error browsing message queue at '" + JOB_QUEUE_ADDRESS, e);
        }
        finally
        {
            if (conn != null) { try { conn.close(); } catch (JMSException ignored) {} }
        }
        return new RequeueLostJobsRequest(locations, ids, hostName);
    }

}