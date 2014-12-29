/*
 * Copyright (c) 2008-2014 LabKey Corporation
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

import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.pipeline.AbstractPipelineStartup;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

/**
 * User: jeckels
 * Date: Apr 8, 2008
 */
public class ClusterStartup extends AbstractPipelineStartup
{
    /**
     * This method is invoked by reflection - don't change its signature without changing org.labkey.bootstrap.ClusterBootstrap 
     */
    public void run(List<File> moduleFiles, List<File> moduleConfigFiles, List<File> customConfigFiles, File webappDir, String[] args) throws IOException, URISyntaxException, PipelineJobException
    {
        initContext("org/labkey/pipeline/mule/config/cluster.log4j.properties", moduleFiles, moduleConfigFiles, customConfigFiles, webappDir, PipelineJobService.LocationType.Cluster);

        if (args.length < 1)
        {
            throw new IllegalArgumentException("First arg should be URI to XML file, based on the web server's file system");
        }

        String localFile = PipelineJobService.get().getPathMapper().remoteToLocal(args[0]);
        
        File file = new File(new URI(localFile));
        if (!file.isFile())
        {
            throw new IllegalArgumentException("Could not find file " + file.getAbsolutePath());
        }

        PipelineJob job = PipelineJob.readFromFile(file);
        
        System.out.println("Starting to run task for job " + job);
        job.runActiveTask();
        System.out.println("Finished running task for job " + job);

        job.writeToFile(file);

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
}
