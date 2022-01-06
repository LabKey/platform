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

import org.apache.commons.lang3.SystemUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.reader.Readers;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.TestContext;
import org.labkey.bootstrap.ClusterBootstrap;
import org.labkey.pipeline.AbstractPipelineStartup;
import org.labkey.pipeline.mule.test.DummyPipelineJob;
import org.mule.umo.manager.UMOManager;
import org.springframework.beans.factory.BeanFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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
    public void run(List<File> moduleFiles, List<File> moduleConfigFiles, List<File> customConfigFiles, File webappDir, String[] args) throws IOException, PipelineJobException
    {
        Map<String, BeanFactory> factories = initContext("ClusterStartup", "org/labkey/pipeline/mule/config/cluster.log4j.properties", moduleFiles, moduleConfigFiles, customConfigFiles, webappDir, PipelineJobService.LocationType.RemoteExecutionEngine);

        // First arg is URI to serialized job's JSON file, based on the web server's file system
        if (args.length < 1)
        {
            // Passing no args is used to explode the modules and exit, preparing for future jobs
            System.out.println("No job file provided, exiting after extracting modules");
            System.exit(0);
        }

        UMOManager manager = null;
        try
        {
            String originalURI = args[0];

            URI localURI;
            // Translate from the web server's path to the local file version
            try
            {
                localURI = PipelineJobService.get().getPathMapper().remoteToLocal(new URI(originalURI));
            }
            catch (URISyntaxException e)
            {
                throw new IllegalArgumentException("Invalid URI. Could not find serialized job file: " + args[0], e);
            }

            if (!localURI.isAbsolute() || !"file".equals(localURI.getScheme()))
            {
                throw new IllegalArgumentException("Invalid URI. Could not find serialized job file: " + localURI);
            }

            File file = new File(localURI);
            if (!file.isFile())
            {
                throw new IllegalArgumentException("Could not find serialized job file: " + localURI);
            }

            doSharedStartup(moduleFiles);

            String hostName = InetAddress.getLocalHost().getHostName();
            manager = setupMuleConfig("org/labkey/pipeline/mule/config/clusterRemoteMuleConfig.xml", factories, hostName);

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
                int exitVal = 0;
                if (job.getActiveTaskStatus() == PipelineJob.TaskStatus.error)
                {
                    job.error("Task failed");
                    exitVal = 1;
                }
                else if (job.getActiveTaskStatus() != PipelineJob.TaskStatus.complete)
                {
                    job.error("Task finished running but was not marked as complete - it was in state " + job.getActiveTaskStatus());
                }

                //NOTE: we need to set error status before writing out the XML so this information is retained
                job.writeToFile(file);

                System.exit(exitVal);
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

    public static class TestCase
    {
        private File _tempDir;

        @Before
        public void setup() throws IOException
        {
            _tempDir = File.createTempFile("testJobDir", "dir").getAbsoluteFile();
            if (!_tempDir.delete())
            {
                throw new RuntimeException("Failed to delete file " + _tempDir);
            }
            if (!_tempDir.mkdir())
            {
                throw new RuntimeException("Failed to create dir " + _tempDir);
            }
        }

        @After
        public void cleanup()
        {
            FileUtil.deleteDir(_tempDir);
        }

        @Test
        public void testSuccess() throws IOException, InterruptedException
        {
            DummyPipelineJob job = new DummyPipelineJob(JunitUtil.getTestContainer(), TestContext.get().getUser(), DummyPipelineJob.Worker.success);
            String output = executeJobRemote(createArgs(job), 0);
            String jobLog = PageFlowUtil.getFileContentsAsString(job.getLogFile());
            Assert.assertTrue("Couldn't find logging", jobLog.contains("Successful worker!"));
            Assert.assertTrue("Couldn't find logging", output.contains("Exploding module archives"));
        }

        @Test
        public void testFailure() throws IOException, InterruptedException
        {
            DummyPipelineJob job = new DummyPipelineJob(JunitUtil.getTestContainer(), TestContext.get().getUser(), DummyPipelineJob.Worker.failure);
            executeJobRemote(createArgs(job), 1);
            String jobLog = PageFlowUtil.getFileContentsAsString(job.getLogFile());
            Assert.assertTrue("Couldn't find logging", jobLog.contains("Oopsies"));
            Assert.assertTrue("Couldn't find logging", jobLog.contains("java.lang.UnsupportedOperationException"));
        }

        @Test
        public void testExtractOnly() throws IOException, InterruptedException
        {
            List<String> args = createArgs(null);
            String output = executeJobRemote(args, 0);
            Assert.assertTrue("Couldn't find logging", output.contains("Exploding module archives"));
        }

        @Test
        public void testBadPath() throws IOException, InterruptedException
        {
            DummyPipelineJob job = new DummyPipelineJob(JunitUtil.getTestContainer(), TestContext.get().getUser(), DummyPipelineJob.Worker.failure);
            List<String> args = createArgs(job);
            // Last argument is supposed to be the URI to the serialized job's file, hack it to something else
            args.set(args.size() - 1, "NotAValidURI.json");
            String output = executeJobRemote(args, 1);
            Assert.assertTrue("Couldn't find logging", output.contains("Could not find serialized job file"));
        }

        protected String executeJobRemote(List<String> args, int expectedExitCode) throws IOException, InterruptedException
        {
            ProcessBuilder pb = new ProcessBuilder(args);
            pb.directory(_tempDir);
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            StringBuilder sb = new StringBuilder();

            // Spin up a separate thread so that we can time and kill the process if it hangs
            new Thread(() -> {
                try (BufferedReader procReader = Readers.getReader(proc.getInputStream()))
                {
                    String line;
                    while ((line = procReader.readLine()) != null)
                    {
                        sb.append(line);
                        sb.append("\n");
                    }
                }
                catch (IOException e)
                {
                    throw new RuntimeException(e);
                }
            }).start();

            if (!proc.waitFor(1, TimeUnit.MINUTES))
            {
                proc.destroy();
                Assert.fail("Process did not complete. Output:\n" + sb.toString());
            }
            Assert.assertEquals("Wrong exit code, output: " + sb.toString(), expectedExitCode, proc.exitValue());
            return sb.toString();
        }

        @NotNull
        private List<String> createArgs(@Nullable PipelineJob job) throws IOException
        {
            List<String> args = new ArrayList<>();
            args.add(System.getProperty("java.home") + "/bin/java" + (SystemUtils.IS_OS_WINDOWS ? ".exe" : ""));
            File labkeyBootstrap = new File(new File(new File(System.getProperty("catalina.home")), "lib"), "labkeyBootstrap.jar");

            // Uncomment this line if you want to debug the forked process
//            args.add("-agentlib:jdwp=transport=dt_socket,server=n,suspend=y,address=*:5005");
            args.add("-cp");
            args.add(labkeyBootstrap.getPath());
            args.add(ClusterBootstrap.class.getName());
            args.add("-webappdir=" + ModuleLoader.getServletContext().getRealPath(""));

            if (job != null)
            {
                // Serialize to a file
                File serializedJob = new File(_tempDir, "job.json");
                File log = new File(_tempDir, "job.log");
                job.setLogFile(log);
                job.writeToFile(serializedJob);
                args.add(serializedJob.toURI().toString());
            }

            return args;
        }
    }
}
