/*
 * Copyright (c) 2008-2017 LabKey Corporation
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
import org.labkey.api.util.ContextListener;
import org.labkey.pipeline.AbstractPipelineStartup;
import org.mule.umo.manager.UMOManager;
import org.springframework.beans.factory.BeanFactory;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

/**
 * Entry point for pipeline jobs that are invoked on a cluster node. After completion of the job, the process
 * should exit with a zero exit code in the case of success.
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
        Map<String, BeanFactory> factories = initContext("org/labkey/pipeline/mule/config/cluster.log4j.properties", moduleFiles, moduleConfigFiles, customConfigFiles, webappDir, PipelineJobService.LocationType.RemoteExecutionEngine);

        // First arg should be URI to XML file, based on the web server's file system
        // Passing no args could be used to explode the modules and exit, preparing for future jobs
        if (args.length < 1)
        {
            System.out.println("No job file provided, exiting");
            System.exit(0);
        }

        String localFile = PipelineJobService.get().getPathMapper().remoteToLocal(args[0]);
        File file = new File(new URI(localFile));
        if (!file.isFile())
        {
            throw new IllegalArgumentException("Could not find file " + file.getAbsolutePath());
        }

        doSharedStartup(moduleFiles);

        String hostName = InetAddress.getLocalHost().getHostName();
        UMOManager manager = setupMuleConfig("org/labkey/pipeline/mule/config/clusterRemoteMuleConfig.xml", factories, hostName);

        try
        {
            PipelineJob job = PipelineJob.readFromFile(file);

            System.out.println("Starting to run task for job " + job + " on host: " + hostName);
            //this is debugging to verify jms.
            job.setStatus("RUNNING ON CLUSTER");
            try
            {
                job.runActiveTask();
                System.out.println("Finished running task for job " + job);
            }
            catch (Throwable e)
            {
                System.out.println("Error running job");
                job.error(String.valueOf(e.getMessage()), e);
            }
            finally
            {
                if (job.getActiveTaskStatus() == PipelineJob.TaskStatus.error)
                {
                    job.error("Task failed");
                }
                else if (job.getActiveTaskStatus() != PipelineJob.TaskStatus.complete)
                {
                    job.error("Task finished running but was not marked as complete - it was in state " + job.getActiveTaskStatus());
                }

                //NOTE: we need to set error status before writing out the XML so this information is retained
                job.writeToFile(file);

                if (job.getErrors() > 0)
                {
                    System.exit(1);
                }
            }
        }
        finally
        {
            if (manager != null)
            {
                try
                {
                    System.out.println("Stopping mule.  manager is running: " + manager.isStarted());
                    manager.stop();
                    manager.dispose();
                }
                catch (Exception e)
                {
                    System.out.println("Failed to stop mule");
                    System.out.println(e.getMessage());
                    e.printStackTrace(System.out);
                }
            }

            ContextListener.callShutdownListeners();
        }

        //System.exit(0);
    }
}
