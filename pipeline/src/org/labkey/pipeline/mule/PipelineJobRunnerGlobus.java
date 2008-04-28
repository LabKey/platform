/*
 * Copyright (c) 2005 LabKey Software, LLC
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

import org.apache.log4j.Logger;
import org.apache.axis.message.addressing.EndpointReferenceType;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineJob;
import org.globus.exec.client.GramJobListener;
import org.globus.exec.client.GramJob;
import org.globus.exec.generated.*;
import org.globus.exec.utils.client.ManagedJobFactoryClientHelper;
import org.globus.exec.utils.ManagedJobFactoryConstants;
import org.globus.axis.util.Util;
import org.mule.extras.client.MuleClient;
import org.mule.umo.UMOException;

import java.io.*;
import java.net.URL;

public class PipelineJobRunnerGlobus
{
    private static Logger _log = Logger.getLogger(PipelineJobRunnerGlobus.class);

    private static final String MANAGED_JOB_FACTORY_URL = "https://140.107.154.30:8443/wsrf/services/ManagedJobFactoryService";
    
    static
    {
        Util.registerTransport();
    }

    public void run(String xmlJob) throws Exception
    {
        boolean submitted = false;
        final PipelineJob job = PipelineJobService.get().getJobStore().fromXML(xmlJob);
        try
        {
            String jobId = job.getJobGUID();

//            File originalFile = PipelineJob.getSerializedFile(job.getStatusFile());
            // Write the file to disk
            File serializedJobFile = new File(new File("Z:\\edi\\pipeline\\Test\\jeckels\\cluster"), jobId + ".job.xml");

            FileOutputStream fOut = null;
            try
            {
                fOut = new FileOutputStream(serializedJobFile);
                PrintWriter writer = new PrintWriter(fOut);
                writer.write(xmlJob);
                writer.flush();
            }
            finally
            {
                if (fOut != null) { try { fOut.close(); } catch (IOException e) {} }
            }
            
            // Set up the job description
            JobDescriptionType jobDescription = new JobDescriptionType();
            jobDescription.setExecutable("/usr/bin/java");
            jobDescription.setStdout("/home/edi/pipeline/Test/jeckels/cluster/" + jobId + ".out");
            jobDescription.setStderr("/home/edi/pipeline/Test/jeckels/cluster/" + jobId + ".err");
            String[] jobArgs =
                {
                    "-Xdebug",
                    "-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005",
                    "-cp",
                    "/usr/local/labkey/labkeyBootstrap.jar",
                    "org.labkey.bootstrap.ClusterBootstrap",
                    "-modulesdir=/usr/local/labkey/modules",
                    "-webappdir=/usr/local/labkey/webapp",
                    "-configdir=/usr/local/labkey/config",
                    "/home/edi/pipeline/Test/jeckels/cluster/" + serializedJobFile.getName()
                };
            jobDescription.setArgument(jobArgs);

            // Figure out where to send the job
            URL factoryUrl = ManagedJobFactoryClientHelper.getServiceURL(MANAGED_JOB_FACTORY_URL).getURL();
            EndpointReferenceType factoryEndpoint = ManagedJobFactoryClientHelper.getFactoryEndpoint(factoryUrl, ManagedJobFactoryConstants.FACTORY_TYPE.FORK);
            ManagedJobFactoryPortType factoryPort = ManagedJobFactoryClientHelper.getPort(factoryEndpoint);
/*
            // Load the proxy file
            ExtendedGSSManager manager = (ExtendedGSSManager)ExtendedGSSManager.getInstance();
            GSSCredential cred = manager.createCredential(GSSCredential.INITIATE_AND_ACCEPT);

            // Set up the security
            ClientSecurityDescriptor secDesc = new ClientSecurityDescriptor();
            secDesc.setGSITransport(Constants.ENCRYPTION);
            secDesc.setAuthz(new NoAuthorization());
            secDesc.setGSSCredential(cred);
            ((Stub) factoryPort)._setProperty(Constants.CLIENT_DESCRIPTOR, secDesc);

            // Create a subscription request
            NotificationConsumerManager notifConsumerManager = NotificationConsumerManager.getInstance();

            notifConsumerManager.startListening();
            List<QName> topicPath = new ArrayList<QName>();
            topicPath.add(ManagedJobConstants.RP_STATE);

            ResourceSecurityDescriptor resourceSecDesc = new ResourceSecurityDescriptor();
            resourceSecDesc.setAuthz(Authorization.AUTHZ_NONE);

            List<GSITransportAuthMethod> authMethods = new ArrayList<GSITransportAuthMethod>();
            authMethods.add(GSITransportAuthMethod.BOTH);
            resourceSecDesc.setAuthMethods(authMethods);

//            EndpointReferenceType notificationConsumerEndpoint = notifConsumerManager.createNotificationConsumer(topicPath, this, resourceSecDesc);
            EndpointReferenceType notificationConsumerEndpoint = new EndpointReferenceType(new Address("http://140.107.155.164/labkey/services/ManagedJobFactoryService"));
            Subscribe subscriptionReq = new Subscribe();
            subscriptionReq.setConsumerReference(notificationConsumerEndpoint);

            TopicExpressionType topicExpression = new TopicExpressionType(WSNConstants.SIMPLE_TOPIC_DIALECT, ManagedJobConstants.RP_STATE);
            subscriptionReq.setTopicExpression(topicExpression);

            // Set up the actual job request
            CreateManagedJobInputType jobInput = new CreateManagedJobInputType();
            jobInput.setJobID(new AttributedURI("uuid:" +  UUIDGenFactory.getUUIDGen().nextUUID()));
            jobInput.setSubscribe(subscriptionReq);
            CreateManagedJobOutputType createResponse = factoryPort.createManagedJob(jobInput);
*/
            
            GramJob gramJob = new GramJob(jobDescription);
            gramJob.addListener(new GramJobListener()
            {
                public void stateChanged(GramJob gramJob)
                {
                    PipelineJob.TaskStatus newStatus = null;
                    if (gramJob.getState() == StateEnumeration.Done)
                    {
                        newStatus = PipelineJob.TaskStatus.complete;
                    }
                    else if (gramJob.getState() == StateEnumeration.Failed)
                    {
                        newStatus = PipelineJob.TaskStatus.error;
                    }

                    if (newStatus != null)
                    {
                        try
                        {
                            MuleClient client = new MuleClient();
                            job.setActiveTaskStatus(newStatus);
                            client.dispatch(EPipelineQueueImpl.PIPELINE_QUEUE_NAME, job, null);
                        }
                        catch (UMOException e)
                        {
                            throw new RuntimeException(e);
                        }
                    }
                }
            });

            String jobURI = "uuid:" + jobId;

            gramJob.submit(factoryEndpoint, false, false, jobURI);
            submitted = true;

//            List<String> command = new ArrayList<String>();
//            command.add(PipelineJobService.get().getJavaPath());
//            StringBuilder sb = new StringBuilder();
//            String separator = "";
//            for (File dir : ModuleLoader.getInstance().getModuleDirectories())
//            {
//                sb.append(separator);
//                separator = File.pathSeparator;
//                sb.append(dir.getAbsolutePath());
//            }
//            command.add("-cp");
//            command.add("C:\\tomcat\\server\\lib\\labkeyBootstrap.jar");
//            command.add("org.labkey.bootstrap.ClusterBootstrap");
//            command.add("-modulesdir=" + sb.toString());
//            command.add("-configdir=C:\\labkey\\docs\\mule\\config-cluster");
//            command.add("-webappdir=" + ModuleLoader.getInstance().getWebappDir().getAbsolutePath());
//            command.add(serializedJobFile.getAbsolutePath());
//
//            ProcessBuilder builder = new ProcessBuilder(command);
//
//            job.runSubProcess(builder, serializedJobFile.getParentFile());
        }
        catch (Exception e)
        {
            job.getLogger().error("Failed submitting job to Globus", e);
            throw e;
        }
        finally
        {
            if (!submitted)
            {
                job.setActiveTaskStatus(PipelineJob.TaskStatus.error);
                MuleClient client = new MuleClient();
                client.dispatch(EPipelineQueueImpl.PIPELINE_QUEUE_NAME, job, null);
            }
        }
    }
}