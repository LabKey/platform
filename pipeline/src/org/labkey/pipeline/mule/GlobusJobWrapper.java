/*
 * Copyright (c) 2012-2013 LabKey Corporation
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

import org.junit.Assert;
import org.apache.axis.message.addressing.EndpointReferenceType;
import org.apache.axis.types.NonNegativeInteger;
import org.globus.exec.client.GramJob;
import org.globus.exec.generated.JobDescriptionType;
import org.globus.exec.generated.JobTypeEnumeration;
import org.globus.exec.generated.NameValuePairType;
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
import org.junit.Test;
import org.labkey.api.pipeline.GlobusKeyPair;
import org.labkey.api.pipeline.GlobusSettings;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.TaskFactory;
import org.labkey.api.pipeline.cmd.CommandTaskFactorySettings;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.NetworkDrive;
import org.labkey.pipeline.analysis.CommandTaskImpl;
import org.labkey.pipeline.api.PipelineJobServiceImpl;
import org.labkey.pipeline.api.properties.GlobusClientPropertiesImpl;

import javax.xml.namespace.QName;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Wrapper around the GramJob class that understands how to get a PipelineJob ready to submit to Globus
 * User: jeckels
 * Date: Feb 16, 2012
 */
public class GlobusJobWrapper
{
    private GramJob _gramJob;
    private PipelineJobService.GlobusClientProperties _settings;
    /** Unique ID for the job */
    private String _jobURI;
    private boolean _submitted = false;

    public GlobusJobWrapper(PipelineJob job, boolean forceFileGeneration, boolean createListener) throws Exception
    {
        _jobURI = "uuid:" + job.getJobGUID() + "/" + job.getActiveTaskId();
        
        // Write the file to disk
        File serializedJobFile = GlobusJobWrapper.getSerializedFile(job.getLogFile());

        if (forceFileGeneration || !NetworkDrive.exists(serializedJobFile))
        {
            job.writeToFile(serializedJobFile);
        }

        List<GlobusClientPropertiesImpl> allGlobusSettings = PipelineJobServiceImpl.get().getGlobusClientPropertiesList();

        if (allGlobusSettings.isEmpty())
        {
            throw new IllegalStateException("No Globus configuration registered");
        }

        _settings = getGlobusSettings(job.getActiveTaskFactory(), job.getParameters(), allGlobusSettings);

        JobDescriptionType jobDescription = createJobDescription(job, serializedJobFile, _settings);

        List<QName> topicPath = new ArrayList<QName>();
        topicPath.add(ManagedJobConstants.RP_STATE);

        ResourceSecurityDescriptor resourceSecDesc = new ResourceSecurityDescriptor();
        resourceSecDesc.setAuthz(Authorization.AUTHZ_NONE);

        List<GSITransportAuthMethod> authMethods = new ArrayList<GSITransportAuthMethod>();
        resourceSecDesc.setAuthMethods(authMethods);

        if (jobDescription == null)
        {
            _gramJob = new GramJob();
        }
        else
        {
            _gramJob = new GramJob(jobDescription);
        }
        // Tell it where to talk to the Globus server
        URL factoryUrl = ManagedJobFactoryClientHelper.getServiceURL(_settings.getGlobusEndpoint()).getURL();
        EndpointReferenceType factoryEndpoint = ManagedJobFactoryClientHelper.getFactoryEndpoint(factoryUrl, _settings.getJobFactoryType());
        _gramJob.setEndpoint(factoryEndpoint);

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

        // Create a proxy cert that lasts as long as the original cert
        GlobusCredential cred = factory.createCredential(keyPair.getCertificates(), keyPair.getPrivateKey(), 512, 0, proxyType, extSet);

        GlobusGSSCredentialImpl credentials = new GlobusGSSCredentialImpl(cred, GSSCredential.INITIATE_AND_ACCEPT);

        if (_settings.getTerminationTime() != null)
        {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.MINUTE, _settings.getTerminationTime().intValue());
            _gramJob.setTerminationTime(cal.getTime());
        }

        _gramJob.setCredentials(credentials);

        if (createListener)
        {
            NotificationConsumerManager notifConsumerManager = new ServerNotificationConsumerManager()
            {
                public URL getURL()
                {
                    try
                    {
                        URL result = new URL(AppProps.getInstance().getBaseServerUrl() + AppProps.getInstance().getContextPath() + "/services/");
                        int port = result.getPort();
                        if (port == -1)
                        {
                            if (result.getProtocol().toLowerCase().equals("http"))
                            {
                                port = 80;
                            }
                            else if (result.getProtocol().toLowerCase().equals("https"))
                            {
                                port = 443;
                            }
                            else
                            {
                                throw new IllegalArgumentException("Unrecognized protocol: " + result.getProtocol() + ", only http and https are supported");
                            }
                        }

                        return new URL(result.getProtocol(), result.getHost(), port, result.getFile());
                    }
                    catch (MalformedURLException e)
                    {
                        throw new RuntimeException(e);
                    }
                }
            };

            notifConsumerManager.startListening();

            EndpointReferenceType notificationConsumerEndpoint = notifConsumerManager.createNotificationConsumer(topicPath, _gramJob, resourceSecDesc);
            
            _gramJob.setNotificationConsumerEPR(notificationConsumerEndpoint);
            _gramJob.addListener(new GlobusListener(job, notifConsumerManager));
        }
    }

    /** For unit testing only */
    private GlobusJobWrapper()
    {
    }

    /** Submit to Globus. Safe to call multiple times - additional calls will no-op since Globus will recognize the job's ID */
    public void submit() throws Exception
    {
        // We need to call submit() on the underlying GramJob to be able to do other operations, like cancel()
        // or refreshStatus()
        if (!_submitted)
        {
            _gramJob.submit(_gramJob.getEndpoint(), false, false, _jobURI);
            _submitted = true;
        }
    }

    /** Check if it's still live */
    public void refreshStatus() throws Exception
    {
        submit();
        _gramJob.refreshStatus();
    }

    /** Kill the job, whether or not it's already running */
    public void cancel() throws Exception
    {
        submit();
        _gramJob.cancel();
    }

    public enum OutputType { out, err }

    public GramJob getGramJob()
    {
        return _gramJob;
    }

    public PipelineJobService.GlobusClientProperties getSettings()
    {
        return _settings;
    }

    private PipelineJobService.GlobusClientProperties getGlobusSettings(TaskFactory taskFactory, Map<String, String> parameters, List<GlobusClientPropertiesImpl> allGlobusSettings)
    {
        String location = getGlobusLocation(taskFactory, parameters, allGlobusSettings);
        GlobusClientPropertiesImpl settings = null;
        if (location == null)
        {
            settings = allGlobusSettings.get(0);
        }
        else
        {
            for (GlobusClientPropertiesImpl possibleSettings : allGlobusSettings)
            {
                if (possibleSettings.getLocation().equalsIgnoreCase(location))
                {
                    settings = possibleSettings;
                    break;
                }
            }
        }
        if (settings == null)
        {
            throw new IllegalArgumentException("Could not find Globus settings for location " + location);
        }

        settings = settings.mergeOverrides(taskFactory.getGlobusSettings());
        settings = settings.mergeOverrides(new JobGlobusSettings(taskFactory.getGroupParameterName(), parameters));
        if (location != null)
        {
            settings.setLocation(location);
        }
        return settings;
    }

    /** Transform an output file path to something that's useful on the cluster node */
    private String getClusterOutputPath(File statusFile, OutputType outputType)
    {
        try
        {
            File f = PipelineJobRunnerGlobus.getOutputFile(statusFile, outputType);
            String clusterURI = getClusterPath(f.toURI().toString());
            File clusterFile = new File(new URI(clusterURI));
            return clusterFile.toString().replace('\\', '/');
        }
        catch (URISyntaxException e)
        {
            throw new RuntimeException("This shouldn't happen", e);
        }
    }

    private JobDescriptionType createJobDescription(PipelineJob job, File serializedJobFile, PipelineJobService.GlobusClientProperties settings)
        throws Exception
    {
        // Set up the job description
        JobDescriptionType jobDescription = new JobDescriptionType();
        jobDescription.setJobType(JobTypeEnumeration.single);

        jobDescription.setStdout(getClusterOutputPath(job.getLogFile(), OutputType.out));
        jobDescription.setStderr(getClusterOutputPath(job.getLogFile(), OutputType.err));

        if (settings.getQueue() != null)
        {
            jobDescription.setQueue(settings.getQueue().trim());
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

        String javaHome = settings.getJavaHome();
        String labKeyDir = settings.getLabKeyDir();

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

    private String getClusterPath(String localPath)
    {
        // This PathMapper considers "local" from a cluster node's point of view.
        return PipelineJobService.get().getClusterPathMapper().remoteToLocal(localPath);
    }

    private String getGlobusLocation(TaskFactory taskFactory, Map<String, String> parameters, List<GlobusClientPropertiesImpl> allGlobusSettings)
    {
        GlobusSettings factorySettings = taskFactory.getGlobusSettings();
        JobGlobusSettings jobSettings = new JobGlobusSettings(taskFactory.getGroupParameterName(), parameters);
        PipelineJobService.GlobusClientProperties matchingQueueSettings = null;
        String jobQueue = jobSettings.getQueue();
        String jobLocation = jobSettings.getLocation();
        if (jobQueue != null)
        {
            for (int i = 0; i < allGlobusSettings.size(); i++)
            {
                GlobusClientPropertiesImpl globusSettings = allGlobusSettings.get(i);
                if (globusSettings.getAvailableQueues().contains(jobSettings.getQueue()) && (jobLocation == null || jobLocation.equalsIgnoreCase(globusSettings.getLocation())))
                {
                    if (i == 0)
                    {
                        // If there's a matching queue on the default Globus server, use it without checking any of the
                        // other servers
                        return globusSettings.getLocation();
                    }

                    if (matchingQueueSettings != null)
                    {
                        throw new IllegalArgumentException("Multiple Globus locations define queue " + jobSettings.getQueue());
                    }
                    matchingQueueSettings = globusSettings;
                }
            }
            if (matchingQueueSettings != null)
            {
                return matchingQueueSettings.getLocation();
            }

            // No matching queue found, use the default Globus location and assume that the user has specified a queue
            // that exists there
        }
        return factorySettings.mergeOverrides(jobSettings).getLocation();
    }

    public static class TestCase extends Assert
    {
        private GlobusClientPropertiesImpl getDefaultGlobusSettings()
        {
            GlobusClientPropertiesImpl result = new GlobusClientPropertiesImpl();
            result.setLocation("location1");
            result.setAvailableQueues(Arrays.asList("default", "location1-A", "duplicateQueue"));
            return result;
        }

        private GlobusClientPropertiesImpl getGlobusSettings2()
        {
            GlobusClientPropertiesImpl result = new GlobusClientPropertiesImpl();
            result.setLocation("location2");
            result.setAvailableQueues(Arrays.asList("location2-A", "location2-B", "duplicateQueue", "duplicateQueue2"));
            return result;
        }

        private GlobusClientPropertiesImpl getGlobusSettings3()
        {
            GlobusClientPropertiesImpl result = new GlobusClientPropertiesImpl();
            result.setLocation("location3");
            result.setAvailableQueues(Arrays.asList("location3-A", "duplicateQueue2"));
            return result;
        }

        @Test
        public void testLocation() throws Exception
        {
            CommandTaskFactorySettings settings = new CommandTaskFactorySettings("UnitTestCommand");
            settings.setGroupParameterName("UnitTestGroup");
            settings.setLocation("location1");
            CommandTaskImpl.Factory factory = new CommandTaskImpl.Factory().cloneAndConfigure(settings);

            List<GlobusClientPropertiesImpl> globusSettings = Collections.singletonList(getGlobusSettings2());

            GlobusJobWrapper runner = new GlobusJobWrapper();
            assertEquals("location", "location1", runner.getGlobusLocation(factory, Collections.<String, String>emptyMap(), globusSettings));
            assertEquals("location", "otherLocation", runner.getGlobusLocation(factory, Collections.singletonMap("UnitTestGroup, globus location", "otherLocation"), globusSettings));
        }

        @Test
        public void testQueue() throws Exception
        {
            CommandTaskFactorySettings settings = new CommandTaskFactorySettings("UnitTestCommand");
            settings.setGroupParameterName("UnitTestGroup");
            settings.setLocation("location1");
            CommandTaskImpl.Factory factory = new CommandTaskImpl.Factory().cloneAndConfigure(settings);

            GlobusJobWrapper runner = new GlobusJobWrapper();

            List<GlobusClientPropertiesImpl> singleGlobusSettings = Collections.singletonList(getDefaultGlobusSettings());
            PipelineJobService.GlobusClientProperties taskSettings = runner.getGlobusSettings(factory, Collections.<String, String>emptyMap(), singleGlobusSettings);
            assertEquals("location", "location1", taskSettings.getLocation());
            assertEquals("location", "default", taskSettings.getQueue());

            // Setting nothing should get the default location and queue
            List<GlobusClientPropertiesImpl> tripleGlobusSettings = Arrays.asList(getDefaultGlobusSettings(), getGlobusSettings2(), getGlobusSettings3());
            PipelineJobService.GlobusClientProperties taskSettings2 = runner.getGlobusSettings(factory, Collections.<String, String>emptyMap(), tripleGlobusSettings);
            assertEquals("location", "location1", taskSettings2.getLocation());
            assertEquals("location", "default", taskSettings2.getQueue());

            // Request a queue that has been configured for the default location
            PipelineJobService.GlobusClientProperties taskSettings3 = runner.getGlobusSettings(factory, Collections.singletonMap("UnitTestGroup, globus queue", "location1-A"), tripleGlobusSettings);
            assertEquals("location", "location1", taskSettings3.getLocation());
            assertEquals("location", "location1-A", taskSettings3.getQueue());

            // Request a queue that has been configured for another location/Globus server
            PipelineJobService.GlobusClientProperties taskSettings4 = runner.getGlobusSettings(factory, Collections.singletonMap("UnitTestGroup, globus queue", "location2-A"), tripleGlobusSettings);
            assertEquals("location", "location2", taskSettings4.getLocation());
            assertEquals("location", "location2-A", taskSettings4.getQueue());

            // A queue that hasn't been configured should be assumed to be on the default location/Globus server
            PipelineJobService.GlobusClientProperties taskSettings5 = runner.getGlobusSettings(factory, Collections.singletonMap("UnitTestGroup, globus queue", "unknownQueue"), tripleGlobusSettings);
            assertEquals("location", "location1", taskSettings5.getLocation());
            assertEquals("location", "unknownQueue", taskSettings5.getQueue());

            // A duplicate queue name assumed to be on the default location/Globus server
            PipelineJobService.GlobusClientProperties taskSettings6 = runner.getGlobusSettings(factory, Collections.singletonMap("UnitTestGroup, globus queue", "duplicateQueue"), tripleGlobusSettings);
            assertEquals("location", "location1", taskSettings6.getLocation());
            assertEquals("location", "duplicateQueue", taskSettings6.getQueue());

            try
            {
                runner.getGlobusSettings(factory, Collections.singletonMap("UnitTestGroup, globus queue", "duplicateQueue2"), tripleGlobusSettings);
                fail("Ambiguous queue name should result in exception");
            }
            catch (IllegalArgumentException ignored) {}

            // Requesting a duplicate queue name is fine if you also specify the location
            Map<String, String> params7 = new HashMap<String, String>();
            params7.put("UnitTestGroup, globus queue", "duplicateQueue2");
            params7.put("UnitTestGroup, globus location", "location2");
            PipelineJobService.GlobusClientProperties taskSettings7 = runner.getGlobusSettings(factory, params7, tripleGlobusSettings);
            assertEquals("location", "location2", taskSettings7.getLocation());
            assertEquals("location", "duplicateQueue2", taskSettings7.getQueue());

            Map<String, String> params8 = new HashMap<String, String>();
            params8.put("UnitTestGroup, globus queue", "duplicateQueue2");
            params8.put("UnitTestGroup, globus location", "location3");
            PipelineJobService.GlobusClientProperties taskSettings8 = runner.getGlobusSettings(factory, params8, tripleGlobusSettings);
            assertEquals("location", "location3", taskSettings8.getLocation());
            assertEquals("location", "duplicateQueue2", taskSettings8.getQueue());

            // Setting to non-default location should get the default queue for that server
            PipelineJobService.GlobusClientProperties taskSettings9 = runner.getGlobusSettings(factory, Collections.singletonMap("UnitTestGroup, globus location", "location3"), tripleGlobusSettings);
            assertEquals("location", "location3", taskSettings9.getLocation());
            assertEquals("location", "location3-A", taskSettings9.getQueue());
        }
    }

    public static File getSerializedFile(File statusFile)
    {
        if (statusFile == null)
        {
            return null;
        }

        String name = statusFile.getName();

        // Assume the status file's extension has a single period (e.g. .status or .log),
        // and remove that extension.
        int index = name.lastIndexOf('.');
        if (index != -1)
        {
            name = name.substring(0, index);
        }
        return new File(statusFile.getParentFile(), name + ".job.xml");
    }

}
