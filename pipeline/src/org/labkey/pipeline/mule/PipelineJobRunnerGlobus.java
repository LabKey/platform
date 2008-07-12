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

import org.apache.axis.message.addressing.EndpointReferenceType;
import org.apache.log4j.Logger;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.globus.axis.util.Util;
import org.globus.exec.client.GramJob;
import org.globus.exec.client.GramJobListener;
import org.globus.exec.generated.FaultType;
import org.globus.exec.generated.JobDescriptionType;
import org.globus.exec.generated.NameValuePairType;
import org.globus.exec.generated.StateEnumeration;
import org.globus.exec.utils.ManagedJobConstants;
import org.globus.exec.utils.client.ManagedJobFactoryClientHelper;
import org.globus.gsi.GSIConstants;
import org.globus.gsi.GlobusCredential;
import org.globus.gsi.X509ExtensionSet;
import org.globus.gsi.bc.BouncyCastleCertProcessingFactory;
import org.globus.gsi.gssapi.GlobusGSSCredentialImpl;
import org.globus.gsi.proxy.ext.ProxyCertInfo;
import org.globus.gsi.proxy.ext.ProxyCertInfoExtension;
import org.globus.gsi.proxy.ext.ProxyPolicy;
import org.globus.wsrf.NotificationConsumerManager;
import org.globus.wsrf.impl.notification.ServerNotificationConsumerManager;
import org.globus.wsrf.impl.security.authorization.Authorization;
import org.globus.wsrf.impl.security.descriptor.GSITransportAuthMethod;
import org.globus.wsrf.impl.security.descriptor.ResourceSecurityDescriptor;
import org.ietf.jgss.GSSCredential;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.*;
import org.labkey.api.util.AppProps;
import org.labkey.pipeline.xstream.PathMapper;
import org.mule.extras.client.MuleClient;
import org.mule.umo.UMOEventContext;
import org.mule.umo.UMOException;
import org.mule.umo.lifecycle.Callable;
import org.mule.impl.RequestContext;
import org.oasis.wsrf.faults.BaseFaultType;
import org.oasis.wsrf.faults.BaseFaultTypeDescription;

import javax.xml.namespace.QName;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.InvalidKeyException;
import java.security.Security;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class PipelineJobRunnerGlobus implements Callable
{
    private static Logger _log = Logger.getLogger(PipelineJobRunnerGlobus.class);

    private String _globusEndpoint;
    private PathMapper _pathMapper = PathMapper.createMapper();

    private static final String GLOBUS_LOCATION = "GLOBUS_LOCATION";

    static
    {
        Util.registerTransport();
        if (System.getProperty(GLOBUS_LOCATION) == null)
        {
            File webappDir = new File(ModuleLoader.getServletContext().getRealPath("/"));
            File webinfDir = new File(webappDir, "WEB-INF"); 
            System.setProperty(GLOBUS_LOCATION, webinfDir.getAbsolutePath());
        }
        
        Security.addProvider(new BouncyCastleProvider());
    }

    public PipelineJobRunnerGlobus()
    {
        // Note: can't throw exception at config time, for missing globus
        //       client information, since it must be possible to run the web
        //       server without globus configuration.  The built-in Mule
        //       config runs this constructor with a JMS filter for task
        //       location "cluster".
        PipelineJobService.GlobusClientProperties props = getProps();
        if (props != null)
        {
            _globusEndpoint = props.getGlobusServer();
            if (!"http".equalsIgnoreCase(_globusEndpoint.substring(0, 4)))
                _globusEndpoint = "https://" + _globusEndpoint;
            if (_globusEndpoint.lastIndexOf('/') < 8)
            {
                if (_globusEndpoint.lastIndexOf(':') < 6)
                    _globusEndpoint += ":8443";
                _globusEndpoint += "/wsrf/services/ManagedJobFactoryService";
            }

            Map<String, String> pathMap = props.getGlobusPathMapping();
            if (pathMap != null)
                _pathMapper.setPathMap(pathMap);
        }
    }

    public Object onCall(UMOEventContext eventContext) throws Exception
    {
        if (_globusEndpoint == null || "".equals(_globusEndpoint))
            throw new IllegalArgumentException("GlobusClientProperties must specify a server to run tasks on a cluster. Check configuration.");

        boolean submitted = false;
        String xmlJob = eventContext.getMessageAsString();
        final PipelineJob job = PipelineJobService.get().getJobStore().fromXML(xmlJob);

        try
        {
            String jobId = job.getJobGUID();

            // Write the file to disk
            File serializedJobFile = PipelineJob.getSerializedFile(job.getStatusFile());

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
            JobDescriptionType jobDescription = createJobDescription(job, serializedJobFile);

            // Figure out where to send the job
            URL factoryUrl = ManagedJobFactoryClientHelper.getServiceURL(getGlobusEndpoint()).getURL();
            EndpointReferenceType factoryEndpoint = ManagedJobFactoryClientHelper.getFactoryEndpoint(factoryUrl, getJobFactoryType());

            String jobURI = "uuid:" + jobId + "/" + job.getActiveTaskId();

            // TODO: This is a hack to get GRAM to retry errors.
            //       Really, we need to figure out how to tell GRAM that
            //       a job has completed (success or failure), so that it
            //       releases the jobURI for repeated submission.
            if (job.getErrors() > 0)
                jobURI += "/" + job.getErrors();

            NotificationConsumerManager notifConsumerManager = new ServerNotificationConsumerManager()
            {
                public URL getURL()
                {
                    try
                    {
                        String url = AppProps.getInstance().getBaseServerUrl();
                        return new URL(url + AppProps.getInstance().getContextPath() + "/services/");
                    }
                    catch (MalformedURLException e)
                    {
                        throw new RuntimeException(e);
                    }
                }
            };

            notifConsumerManager.startListening();
            List<QName> topicPath = new ArrayList<QName>();
            topicPath.add(ManagedJobConstants.RP_STATE);

            ResourceSecurityDescriptor resourceSecDesc = new ResourceSecurityDescriptor();
            resourceSecDesc.setAuthz(Authorization.AUTHZ_NONE);

            List<GSITransportAuthMethod> authMethods = new ArrayList<GSITransportAuthMethod>();
            resourceSecDesc.setAuthMethods(authMethods);

            GramJob gramJob = new GramJob(jobDescription);
            
            EndpointReferenceType notificationConsumerEndpoint = notifConsumerManager.createNotificationConsumer(topicPath, gramJob, resourceSecDesc);

            int proxyType = GSIConstants.GSI_4_IMPERSONATION_PROXY;

            BouncyCastleCertProcessingFactory factory = BouncyCastleCertProcessingFactory.getDefault();

            ProxyPolicy policy = new ProxyPolicy(ProxyPolicy.IMPERSONATION);
            ProxyCertInfo proxyCertInfo = new ProxyCertInfo(policy);
            X509ExtensionSet extSet = new X509ExtensionSet();
            // RFC compliant OID
            extSet.add(new ProxyCertInfoExtension(proxyCertInfo));

            PipeRoot pipeRoot = PipelineService.get().findPipelineRoot(job.getContainer());
            GlobusKeyPair keyPair = pipeRoot.getGlobusKeyPair();
            if (keyPair == null)
            {
                throw new InvalidKeyException("No Globus SSL Key Pair configured, ask an administrator to set this up for your folder's pipeline root");
            }
            keyPair.validateMatch();

            GlobusCredential cred = factory.createCredential(keyPair.getCertificates(), keyPair.getPrivateKey(), 512, 3600 * 12, proxyType, extSet);

            GlobusGSSCredentialImpl credentials = new GlobusGSSCredentialImpl(cred, GSSCredential.INITIATE_AND_ACCEPT);

            gramJob.setCredentials(credentials);

            gramJob.setNotificationConsumerEPR(notificationConsumerEndpoint);
            gramJob.addListener(new GramJobListener()
            {
                public void stateChanged(GramJob gramJob)
                {
                    PipelineJob.TaskStatus newStatus = null;
                    if (gramJob.getState() == StateEnumeration.Done)
                    {
                        if (gramJob.getExitCode() != 0)
                        {
                            newStatus = PipelineJob.TaskStatus.error;
                            job.error("Job completed with exit code " + gramJob.getExitCode());
                        }
                        else
                        {
                            newStatus = PipelineJob.TaskStatus.complete;
                        }
                    }
                    else if (gramJob.getState() == StateEnumeration.Failed)
                    {
                        newStatus = PipelineJob.TaskStatus.error;
                    }
                    else if (gramJob.getState() == StateEnumeration.Active)
                    {
                        newStatus = PipelineJob.TaskStatus.running;
                    }

                    if (newStatus != null)
                    {
                        try
                        {
                            FaultType fault = gramJob.getFault();
                            if (fault != null)
                            {
                                logFault(fault, job);
                            }
                            updateStatus(job, newStatus);
                        }
                        catch (UMOException e)
                        {
                            job.error("Failed up update job status to " + newStatus, e);
                        }
                    }
                }
            });

            gramJob.submit(factoryEndpoint, false, false, jobURI);
            StringBuilder sb = new StringBuilder();
            sb.append("Submitted job to Globus ");
            sb.append(getJobFactoryType());
            if (getQueue() != null)
            {
                sb.append(" with queue ");
                sb.append(getQueue());
            }
            sb.append(": ");
            sb.append(jobDescription.getExecutable());
            for (String arg : jobDescription.getArgument())
            {
                sb.append(" ");
                sb.append(arg);
            }
            job.getLogger().info(sb.toString());
            submitted = true;
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
                updateStatus(job, PipelineJob.TaskStatus.error);
            }
        }
        return null;
    }

    private static PipelineJobService.GlobusClientProperties getProps()
    {
        return PipelineJobService.get().getGlobusClientProperties();
    }

    private String getClusterPath(String localPath)
    {
        // This PathMapper considers "local" from a cluster node's point of view.
        return _pathMapper.remoteToLocal(localPath);
    }

    private String getGlobusEndpoint()
    {
        return _globusEndpoint;
    }

    private String getQueue()
    {
        return getProps().getGlobusQueue();
    }

    private String getJavaHome()
    {
        return getProps().getGlobusJavaHome();
    }

    private String getJavaPath()
    {
        return getJavaHome() + "/bin/java";
    }

    private String getLabkeyDir()
    {
        return getProps().getGlobusLabkeyDir();
    }

    private String getConfigDir()
    {
        return getLabkeyDir() + "/config";
    }

    private String getModulesDir()
    {
        return getLabkeyDir() + "/modules";
    }

    private String getWebappDir()
    {
        return getLabkeyDir() + "/labkeywebapp";
    }

    private String getJobFactoryType()
    {
        return getProps().getGlobusJobFactoryType();
    }
/*
    private byte[] readFile(File keyFile) throws IOException
    {
        ByteArrayOutputStream bOut = new ByteArrayOutputStream();
        FileInputStream fIn = null;
        try
        {
            fIn = new FileInputStream(keyFile);
            byte[] b = new byte[4096];
            int i;
            while((i = fIn.read(b)) != -1)
            {
                bOut.write(b, 0, i);
            }
            return bOut.toByteArray();
        }
        finally
        {
            if (fIn != null) { try { fIn.close(); } catch (IOException e) {} }
        }
    }
*/
    private JobDescriptionType createJobDescription(PipelineJob job, File serializedJobFile)
            throws URISyntaxException
    {
        // Set up the job description
        JobDescriptionType jobDescription = new JobDescriptionType();
        jobDescription.setExecutable(getJavaPath());

        // Transform an output file path to something that's useful on the cluster node
        File localOutputFile = PipelineJob.getClusterOutputFile(job.getStatusFile());
        String clusterOutputURI = getClusterPath(localOutputFile.toURI().toString());
        File clusterOutputFile = new File(new URI(clusterOutputURI));
        String clusterOutputPath = clusterOutputFile.toString().replace('\\', '/');
        jobDescription.setStdout(clusterOutputPath);
        jobDescription.setStderr(clusterOutputPath);
        if (getQueue() != null)
        {
            jobDescription.setQueue(getQueue());
        }

        jobDescription.setEnvironment(new NameValuePairType[] { new NameValuePairType("JAVA_HOME", getJavaHome()) });

        String[] jobArgs =
            {
//                "-Xdebug",
//                "-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005",
                "-cp",
                getLabkeyDir() + "/labkeyBootstrap.jar",
                "org.labkey.bootstrap.ClusterBootstrap",
                "-modulesdir=" + getModulesDir(),
                "-webappdir=" + getWebappDir(),
                "-configdir=" + getConfigDir(),
                getClusterPath(serializedJobFile.getAbsoluteFile().toURI().toString())
            };
        jobDescription.setArgument(jobArgs);
        return jobDescription;
    }

    private void logFault(BaseFaultType fault, PipelineJob job)
    {
        if (fault instanceof FaultType)
        {
            job.error("Fault received from Globus on \"" + ((FaultType)fault).getCommand() + "\" command");
        }
        else
        {
            job.error("Fault received from Globus");
        }
        if (fault.getDescription() != null)
        {
            for (BaseFaultTypeDescription description : fault.getDescription())
            {
                job.error(description.get_value());
            }
        }
        if (fault.getFaultCause() != null)
        {
            for (BaseFaultType cause : fault.getFaultCause())
            {
                logFault(cause, job);
            }
        }
    }

    private void updateStatus(PipelineJob job, PipelineJob.TaskStatus status) throws UMOException
    {
        assert status != PipelineJob.TaskStatus.waiting :
                "Reset cluster task status to 'waiting' is not allowed.";
        
        job.setActiveTaskStatus(status);

        // Only re-queue the job if status is 'complete' (not 'running' or 'error').
        if (status == PipelineJob.TaskStatus.complete)
        {
            EPipelineQueueImpl.dispatchJob(job);
        }
    }
}