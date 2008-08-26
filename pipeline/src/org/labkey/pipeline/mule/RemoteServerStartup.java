/*
 * Copyright (c) 2008 LabKey Corporation
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
import org.mule.config.ConfigurationException;
import org.springframework.beans.factory.BeanFactory;
import org.labkey.pipeline.api.PipelineJobServiceImpl;
import org.labkey.pipeline.AbstractPipelineStartup;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineJob;
import org.apache.log4j.Logger;

import java.net.URLEncoder;
import java.net.URL;
import java.net.HttpURLConnection;
import java.io.*;
import java.util.List;
import java.util.Map;

/*
* User: jeckels
* Date: Jun 26, 2008
*/
public class RemoteServerStartup extends AbstractPipelineStartup
{
    private static Logger _log = Logger.getLogger(RemoteServerStartup.class);

    public void run(List<File> moduleFiles, List<File> moduleConfigFiles, List<File> customConfigFiles, String[] args) throws ConfigurationException, IOException
    {
        Map<String, BeanFactory> factories = initContext("org/labkey/pipeline/mule/config/remote.log4j.properties", moduleFiles, moduleConfigFiles, customConfigFiles);

        PipelineJobService.RemoteServerProperties props = PipelineJobServiceImpl.get().getRemoteServerProperties();
        if (props == null)
        {
            throw new IllegalArgumentException("No RemoteServerProperties object registered. Be sure that your configuration directory is set correctly.");
        }
        String muleConfig = props.getMuleConfig();
        if (muleConfig == null)
            muleConfig = "org/labkey/pipeline/mule/config/remoteMuleConfig.xml";

        LabKeySpringContainerContext.setContext(factories.get(PipelineService.MODULE_NAME));

        requeueLostJobs();

        MuleXmlConfigurationBuilder builder = new MuleXmlConfigurationBuilder();
        builder.configure(muleConfig);
    }

    public void requeueLostJobs() throws IOException
    {
        PipelineJobService.RemoteServerProperties remoteProps = PipelineJobService.get().getRemoteServerProperties();
        if (remoteProps == null)
        {
            throw new IllegalStateException("No remoteServerProperties registered with the PipelineJobService.");
        }
        if (remoteProps.getBaseServerUrl() == null)
        {
            throw new IllegalStateException("No baseServerUrl is set for the remoteServerProperties on PipelineJobServer. This is required so that the remote server can communicate with the web server.");
        }

        PipelineJobService.ApplicationProperties appProps = PipelineJobService.get().getAppProperties();
        if (appProps == null)
        {
            throw new IllegalStateException("No appProps is registered with the PipelineJobService.");
        }

        String location = remoteProps.getLocation();
        String baseServerURL = remoteProps.getBaseServerUrl();

        String urlString = baseServerURL + "Pipeline-Status/requeueLostJobs.view?location=" + URLEncoder.encode(location, "UTF-8");
        if (appProps.getCallbackPassword() != null)
        {
            urlString += "&callbackPassword=" + URLEncoder.encode(appProps.getCallbackPassword(), "UTF-8");
        }
        URL url = new URL(urlString);
        HttpURLConnection connection = null;
        BufferedReader reader = null;
        try
        {
            try
            {
                connection = (HttpURLConnection)url.openConnection();
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK)
                {
                    // The exception adds the stack trace to the log, which causes it to stand out more.
                    throw new IllegalStateException("Got response code " + connection.getResponseCode() + " from server trying requeue lost jobs");
                }
            }
            catch (IOException e)
            {
                throw new IllegalStateException("Failed to contact web server at " + url, e);
            }

            reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String status = reader.readLine();
            if (!PipelineJob.COMPLETE_STATUS.equals(status))
            {
                throw new IOException("When trying to requeue lost jobs, did not get expected " + PipelineJob.COMPLETE_STATUS + " status from server. Instead, got: " + status);
            }
        }
        finally
        {
            if (reader != null) { try { reader.close(); } catch (IOException e) {} }
            if (connection != null) { connection.disconnect(); }
        }
    }

}