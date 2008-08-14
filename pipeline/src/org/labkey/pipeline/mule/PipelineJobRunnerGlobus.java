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
import org.apache.axis.types.NonNegativeInteger;
import org.apache.log4j.Logger;
import org.apache.commons.io.FileUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.globus.axis.util.Util;
import org.globus.exec.client.GramJob;
import org.globus.exec.generated.JobDescriptionType;
import org.globus.exec.generated.NameValuePairType;
import org.globus.exec.generated.JobTypeEnumeration;
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
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.NetworkDrive;
import org.labkey.pipeline.xstream.PathMapperImpl;
import org.mule.umo.UMOEventContext;
import org.mule.umo.UMOException;
import org.mule.umo.lifecycle.Callable;
import org.mule.impl.RequestContext;

import javax.xml.namespace.QName;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.InvalidKeyException;
import java.security.Security;
import java.security.Provider;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PipelineJobRunnerGlobus implements Callable
{
    private static Logger _log = Logger.getLogger(PipelineJobRunnerGlobus.class);

    private String _globusEndpoint;
    private PathMapperImpl _pathMapper = new PathMapperImpl();

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

        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME);
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

            Map<String, String> pathMap = props.getPathMapping();
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

        GlobusSettings settings = PipelineJobService.get().getGlobusClientProperties();
        settings = settings.mergeOverrides(job.getActiveTaskFactory().getGlobusSettings());
        settings = settings.mergeOverrides(new JobGlobusSettings(job.getActiveTaskFactory().getGroupParameterName(), job.getParameters()));

        try
        {
            String jobId = job.getJobGUID();

            // Write the file to disk
            final File serializedJobFile = PipelineJob.getSerializedFile(job.getStatusFile());

            job.writeToFile(serializedJobFile);
            
            JobDescriptionType jobDescription = createJobDescription(job, serializedJobFile, settings);

            // Figure out where to send the job
            URL factoryUrl = ManagedJobFactoryClientHelper.getServiceURL(_globusEndpoint).getURL();
            EndpointReferenceType factoryEndpoint = ManagedJobFactoryClientHelper.getFactoryEndpoint(factoryUrl, PipelineJobService.get().getGlobusClientProperties().getJobFactoryType());

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
            gramJob.addListener(new GlobusListener(job, notifConsumerManager));

            gramJob.submit(factoryEndpoint, false, false, jobURI);
            StringBuilder sb = new StringBuilder();
            sb.append("Submitted job to Globus ");
            sb.append(PipelineJobService.get().getGlobusClientProperties().getJobFactoryType());
            if (settings.getQueue() != null)
            {
                sb.append(" with queue ");
                sb.append(settings.getQueue());
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
        finally
        {
            if (!submitted)
            {
                try
                {
                    updateStatus(job, PipelineJob.TaskStatus.error);
                }
                catch (IOException e)
                {
                    _log.error("Failed to update status after failing to submit job", e);
                }
                catch (UMOException e)
                {
                    _log.error("Failed to update status after failing to submit job", e);
                }
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

    public static void updateStatus(PipelineJob job, PipelineJob.TaskStatus status) throws UMOException, IOException
    {
        assert status != PipelineJob.TaskStatus.waiting :
                "Reset cluster task status to 'waiting' is not allowed.";

        if (!status.isActive())
        {
            appendAndDeleteLogFile(job, OutputType.out);
            appendAndDeleteLogFile(job, OutputType.err);

            // Clean up the serialized job file if Globus is done trying to run it
            File serializedFile = PipelineJob.getSerializedFile(job.getStatusFile());
            if (NetworkDrive.exists(serializedFile))
            {
                job = PipelineJob.readFromFile(serializedFile);
                serializedFile.delete();
            }
        }

        job.setActiveTaskStatus(status);

        // Only re-queue the job if status is 'complete' (not 'running' or 'error').
        if (status == PipelineJob.TaskStatus.complete || status == PipelineJob.TaskStatus.error)
        {
            // And only, if this update didn't happen in the process of
            // handling an existing Mule request, in which case, Mule will
            // requeue if necessary.
            if (RequestContext.getEvent() == null)
                EPipelineQueueImpl.dispatchJob(job);
        }
    }

    private static void appendAndDeleteLogFile(PipelineJob job, OutputType outputType)
    {
        File f = getOutputFile(job.getStatusFile(), outputType);
        if (NetworkDrive.exists(f))
        {
            if (f.length() > 0)
            {
                FileInputStream fIn = null;
                try
                {
                    fIn = new FileInputStream(f);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(fIn));
                    String line;
                    StringBuilder sb = new StringBuilder();
                    while ((line = reader.readLine()) != null)
                    {
                        sb.append(line);
                        sb.append("\n");
                    }
                    job.getLogger().info("Content of std" + outputType + ":\n" + sb.toString());
                }
                catch (IOException e)
                {
                    job.getLogger().warn("Failed to append contents from log file " + f, e);
                }
                finally
                {
                    if (fIn != null) { try { fIn.close(); } catch (IOException e) {} }
                }
            }

            try
            {
                // This is a nasty hack. We want to delete the log file. One would think that Globus would be done
                // writing to the file before it gives us a callback to tell us that the job is complete. However,
                // in my testing about 50% of the time it's not quite done. We try to delete the file. Globus closes
                // the file, which ends up recreating it. Worse, it comes back with different file permissions, set
                // so that we can't read or write to it anymore, meaning that we can't delete it again or copy its
                // contents to the main job log. So, we wait a bit for it to be flushed and then try deleting it.
                // We'll have to wait and see if it's reliably done after five seconds or not.
                Thread.sleep(5000);
            }
            catch (InterruptedException e) {}

            f.delete();
        }
    }

    private enum OutputType { out, err }

    /** Transform an output file path to something that's useful on the cluster node */
    private String getClusterOutputPath(File statusFile, OutputType outputType)
    {
        try
        {
            File f = getOutputFile(statusFile, outputType);
            String clusterURI = getClusterPath(f.toURI().toString());
            File clusterFile = new File(new URI(clusterURI));
            return clusterFile.toString().replace('\\', '/');
        }
        catch (URISyntaxException e)
        {
            throw new RuntimeException("This shouldn't happen", e);
        }
    }

    private static File getOutputFile(File statusFile, OutputType outputType)
    {
        if (statusFile == null)
        {
            return null;
        }

        String name = statusFile.getName();
        int index = name.indexOf('.');
        if (index != -1)
        {
            name = name.substring(0, index);
        }
        return new File(statusFile.getParentFile(), name + ".cluster." + outputType);
    }

    private JobDescriptionType createJobDescription(PipelineJob job, File serializedJobFile, GlobusSettings settings)
        throws URISyntaxException, IOException
    {
        // Set up the job description
        JobDescriptionType jobDescription = new JobDescriptionType();
        jobDescription.setJobType(JobTypeEnumeration.single);
        
        // Create the output files with the right permissions
        FileUtils.touch(getOutputFile(job.getStatusFile(), OutputType.out));
        FileUtils.touch(getOutputFile(job.getStatusFile(), OutputType.err));

        jobDescription.setStdout(getClusterOutputPath(job.getStatusFile(), OutputType.out));
        jobDescription.setStderr(getClusterOutputPath(job.getStatusFile(), OutputType.err));
        
        if (settings.getQueue() != null)
        {
            jobDescription.setQueue(settings.getQueue());
        }
        if (settings.getMaxCPUTime() != null)
        {
            jobDescription.setMaxCpuTime(settings.getMaxCPUTime());
        }
        if (settings.getMaxMemory() != null)
        {
            jobDescription.setMaxMemory(new NonNegativeInteger(settings.getMaxMemory().toString()));
        }
        if (settings.getMaxTime() != null)
        {
            jobDescription.setMaxTime(settings.getMaxTime());
        }
        if (settings.getMaxWallTime() != null)
        {
            jobDescription.setMaxWallTime(settings.getMaxWallTime());
        }

        String javaHome = PipelineJobService.get().getGlobusClientProperties().getJavaHome();
        String labKeyDir = PipelineJobService.get().getGlobusClientProperties().getLabKeyDir();

        jobDescription.setEnvironment(new NameValuePairType[] { new NameValuePairType("JAVA_HOME", javaHome) });

        jobDescription.setExecutable(javaHome + "/bin/java");
        String[] jobArgs =
            {
//                "-Xdebug",
//                "-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005",
                "-cp",
                labKeyDir + "/labkeyBootstrap.jar",
                "org.labkey.bootstrap.ClusterBootstrap",
                "-modulesdir=" + labKeyDir + "/modules",
                "-webappdir=" + labKeyDir + "/labkeywebapp",
                "-configdir=" + labKeyDir + "/config",
                getClusterPath(serializedJobFile.getAbsoluteFile().toURI().toString())
            };
        jobDescription.setArgument(jobArgs);
        return jobDescription;
    }
}