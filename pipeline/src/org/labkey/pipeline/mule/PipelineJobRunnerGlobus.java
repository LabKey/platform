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

import org.apache.log4j.Logger;
import org.apache.axis.message.addressing.EndpointReferenceType;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.util.AppProps;
import org.labkey.pipeline.xstream.PathMapper;
import org.globus.exec.client.GramJobListener;
import org.globus.exec.client.GramJob;
import org.globus.exec.generated.*;
import org.globus.exec.utils.client.ManagedJobFactoryClientHelper;
import org.globus.exec.utils.ManagedJobFactoryConstants;
import org.globus.exec.utils.ManagedJobConstants;
import org.globus.axis.util.Util;
import org.globus.wsrf.NotificationConsumerManager;
import org.globus.wsrf.impl.notification.ServerNotificationConsumerManager;
import org.globus.wsrf.impl.security.descriptor.GSITransportAuthMethod;
import org.globus.wsrf.impl.security.descriptor.ResourceSecurityDescriptor;
import org.globus.wsrf.impl.security.authorization.Authorization;
import org.mule.extras.client.MuleClient;
import org.mule.umo.UMOException;
import org.mule.umo.UMOEventContext;
import org.mule.umo.lifecycle.Callable;
import org.oasis.wsrf.faults.BaseFaultTypeDescription;
import org.oasis.wsrf.faults.BaseFaultType;

import javax.xml.namespace.QName;
import java.io.*;
import java.net.URL;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.MalformedURLException;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

public class PipelineJobRunnerGlobus implements Callable
{
    private static Logger _log = Logger.getLogger(PipelineJobRunnerGlobus.class);

    private String _javaHome;
    private String _javaPath;
    private String _labkeyDir;
    private String _configDir;
    private String _modulesDir;
    private String _webappDir;
    private String _globusEndpoint;
    private String _jobFactoryType = ManagedJobFactoryConstants.FACTORY_TYPE.FORK;
    private String _queue;

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
    }

    public Map<String, String> getPathMapping()
    {
        return _pathMapper.getPathMap();
    }

    public void setPathMapping(Map<String, String> pathMapping)
    {
        _pathMapper.setPathMap(pathMapping);
    }

    public String getGlobusEndpoint()
    {
        return _globusEndpoint;
    }

    public void setGlobusEndpoint(String globusEndpoint)
    {
        _globusEndpoint = globusEndpoint;
    }

    public String getQueue()
    {
        return _queue;
    }

    public void setQueue(String queue)
    {
        _queue = queue;
    }

    public String getJavaHome()
    {
        return _javaHome;
    }

    public void setJavaHome(String javaHome)
    {
        _javaHome = javaHome;
    }

    public String getJavaPath()
    {
        if (_javaPath == null)
        {
            return _javaHome + "/bin/java";
        }
        return _javaPath;
    }

    public void setJavaPath(String javaPath)
    {
        _javaPath = javaPath;
    }

    public String getLabkeyDir()
    {
        return _labkeyDir;
    }

    public void setLabkeyDir(String labkeyDir)
    {
        _labkeyDir = labkeyDir;
    }

    public String getConfigDir()
    {
        return getConfigDir(false);
    }

    public String getConfigDir(boolean enforceSetting)
    {
        return getDir(_configDir, "config", enforceSetting);
    }

    public String getJobFactoryType()
    {
        return _jobFactoryType;
    }

    public void setJobFactoryType(String jobFactoryType)
    {
        _jobFactoryType = jobFactoryType;
    }

    private String getDir(String override, String defaultSubDir, boolean enforceSetting)
    {
        if (override != null)
        {
            return override;
        }
        if (_labkeyDir != null)
        {
            return _labkeyDir + "/" + defaultSubDir;
        }
        if (enforceSetting)
        {
            throw new IllegalStateException(defaultSubDir + "Dir was not set");
        }
        return null;
    }


    public void setConfigDir(String configDir)
    {
        _configDir = configDir;
    }

    public String getModulesDir()
    {
        return getModulesDir(false);
    }

    public String getModulesDir(boolean enforceSetting)
    {
        return getDir(_modulesDir, "modules", enforceSetting);
    }

    public void setModulesDir(String modulesDir)
    {
        _modulesDir = modulesDir;
    }

    public String getWebappDir()
    {
        return getWebappDir(false);
    }

    public String getWebappDir(boolean enforceSetting)
    {
        return getDir(_webappDir, "labkeywebapp", enforceSetting);
    }

    public void setWebappDir(String webappDir)
    {
        _webappDir = webappDir;
    }

    public Object onCall(UMOEventContext eventContext) throws Exception
    {
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
            URL factoryUrl = ManagedJobFactoryClientHelper.getServiceURL(_globusEndpoint).getURL();
            EndpointReferenceType factoryEndpoint = ManagedJobFactoryClientHelper.getFactoryEndpoint(factoryUrl, _jobFactoryType);

            String jobURI = "uuid:" + jobId + "/" + job.getActiveTaskId();

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

    private JobDescriptionType createJobDescription(PipelineJob job, File serializedJobFile)
            throws URISyntaxException
    {
        // Set up the job description
        JobDescriptionType jobDescription = new JobDescriptionType();
        jobDescription.setExecutable(getJavaPath());

        // Transform an output file path to something that's useful on the cluster node
        File localOutputFile = PipelineJob.getClusterOutputFile(job.getStatusFile());
        String clusterOutputURI = _pathMapper.localToRemote(localOutputFile.toURI().toString());
        File clusterOutputFile = new File(new URI(clusterOutputURI));
        String clusterOutputPath = clusterOutputFile.toString().replace('\\', '/');
        jobDescription.setStdout(clusterOutputPath);
        jobDescription.setStderr(clusterOutputPath);
        if (_queue != null)
        {
            jobDescription.setQueue(_queue);
        }

        jobDescription.setEnvironment(new NameValuePairType[] { new NameValuePairType("JAVA_HOME", _javaHome) });

        String[] jobArgs =
            {
//                "-Xdebug",
//                "-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005",
                "-cp",
                _labkeyDir + "/labkeyBootstrap.jar",
                "org.labkey.bootstrap.ClusterBootstrap",
                "-modulesdir=" + getModulesDir(true),
                "-webappdir=" + getWebappDir(true),
                "-configdir=" + getConfigDir(true),
                _pathMapper.localToRemote(serializedJobFile.getAbsoluteFile().toURI().toString())
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
        job.setActiveTaskStatus(status);
        MuleClient client = new MuleClient();
        client.dispatch(EPipelineQueueImpl.PIPELINE_QUEUE_NAME, job, null);
    }
}