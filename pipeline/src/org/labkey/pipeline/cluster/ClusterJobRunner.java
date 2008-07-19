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

package org.labkey.pipeline.cluster;

import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.pipeline.xstream.PathMapper;
import org.labkey.pipeline.api.PipelineJobServiceImpl;
import org.labkey.pipeline.mule.LoggerUtil;
import org.springframework.context.support.FileSystemXmlApplicationContext;
import org.apache.log4j.PropertyConfigurator;

import java.io.*;
import java.util.List;
import java.util.ArrayList;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * User: jeckels
 * Date: Apr 8, 2008
 */
public class ClusterJobRunner
{
    public void run(String[] springConfigPaths, String[] args) throws IOException, URISyntaxException
    {
        LoggerUtil.initLogging("org/labkey/pipeline/mule/config/cluster.log4j.properties");

        // Set up the PipelineJobService so that Spring can configure it
        PipelineJobServiceImpl.initDefaults();

        // Initialize the Spring context
        FileSystemXmlApplicationContext context = new FileSystemXmlApplicationContext(springConfigPaths);

        if (args.length < 1)
        {
            throw new IllegalArgumentException("First arg should be URI to XML file, based on the web server's file system");
        }

        String localFile = PathMapper.getInstance().remoteToLocal(args[0]);

        File file = new File(new URI(localFile));
        if (!file.isFile())
        {
            throw new IllegalArgumentException("Could not find file " + file.getAbsolutePath());
        }

        StringBuilder xml = readFile(file);

        PipelineJob job = PipelineJobService.get().getJobStore().fromXML(xml.toString());
        System.out.println("Starting to run task for job " + job);
        job.runActiveTask();
        System.out.println("Finished running task for job " + job);

        if (job.getActiveTaskStatus() == PipelineJob.TaskStatus.error)
        {
            job.error("Task failed");
            System.exit(1);
        }
        else if (job.getActiveTaskStatus() != PipelineJob.TaskStatus.complete)
        {
            job.error("Task finished running but was not marked as complete - it was in state " + job.getActiveTaskStatus());
            System.exit(1);
        }
    }

    private StringBuilder readFile(File file) throws IOException
    {
        InputStream fIn = null;
        StringBuilder xml = new StringBuilder();
        try
        {
            fIn = new FileInputStream(file);
            BufferedReader reader = new BufferedReader(new InputStreamReader(fIn));
            String line;
            while ((line = reader.readLine()) != null)
            {
                xml.append(line);
            }
        }
        finally
        {
            if (fIn != null) { try { fIn.close(); } catch (IOException e) {} }
        }
        return xml;
    }
}
