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
import org.springframework.context.support.FileSystemXmlApplicationContext;
import org.labkey.pipeline.api.PipelineJobServiceImpl;
import org.labkey.api.pipeline.PipelineJobService;

/*
* User: jeckels
* Date: Jun 26, 2008
*/
public class MuleStartup
{
    public void run(String[] springConfigPaths, String[] args) throws ConfigurationException
    {
        // Set up the PipelineJobService so that Spring can configure it
        PipelineJobServiceImpl.initDefaults();

        // Initialize the Spring context
        FileSystemXmlApplicationContext context = new FileSystemXmlApplicationContext(springConfigPaths);

        PipelineJobService.RemoteServerProperties props = PipelineJobServiceImpl.get().getRemoteServerProperties();
        if (props == null)
        {
            throw new IllegalArgumentException("No RemoteServerProperties object registered. Be sure that your configuration directory is set correctly.");
        }
        String muleConfig = props.getMuleConfig();
        if (muleConfig == null)
            muleConfig = "org/labkey/pipeline/mule/config/remoteMuleConfig.xml";

        LabKeySpringContainerContext.setContext(context);
        MuleXmlConfigurationBuilder builder = new MuleXmlConfigurationBuilder();
        builder.configure(muleConfig);
    }
}