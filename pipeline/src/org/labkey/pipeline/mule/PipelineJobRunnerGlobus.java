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
import org.labkey.api.module.ModuleLoader;
import org.labkey.pipeline.xstream.PathMapper;
import org.globus.exec.client.GramJobListener;
import org.globus.exec.client.GramJob;
import org.globus.exec.generated.*;
import org.globus.exec.utils.client.ManagedJobFactoryClientHelper;
import org.globus.exec.utils.ManagedJobFactoryConstants;
import org.globus.axis.util.Util;
import org.mule.extras.client.MuleClient;
import org.mule.umo.UMOException;
import org.mule.umo.UMOEventContext;
import org.mule.umo.lifecycle.Callable;
import org.oasis.wsrf.faults.BaseFaultTypeDescription;
import org.oasis.wsrf.faults.BaseFaultType;

import java.io.*;
import java.net.URL;
import java.net.URI;
import java.util.Map;

public class PipelineJobRunnerGlobus implements Callable
{
    private static Logger _log = Logger.getLogger(PipelineJobRunnerGlobus.class);

    private String _javaPath;
    private String _labkeyDir;
    private String _configDir;
    private String _modulesDir;
    private String _webappDir;
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
            File classesDir = new File(webinfDir, "classes"); 
            System.setProperty(GLOBUS_LOCATION, classesDir.getAbsolutePath());
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

    public String getJavaPath()
    {
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
            
            // Set up the job description
            JobDescriptionType jobDescription = new JobDescriptionType();
            jobDescription.setExecutable(_javaPath);

            // Transform an output file path to something that's useful on the cluster node
            File localOutputFile = PipelineJob.getClusterOutputFile(job.getStatusFile());
            String clusterOutputURI = _pathMapper.localToRemote(localOutputFile.toURI().toString());
            File clusterOutputFile = new File(new URI(clusterOutputURI));
            String clusterOutputPath = clusterOutputFile.toString().replace('\\', '/');
            jobDescription.setStdout(clusterOutputPath);
            jobDescription.setStderr(clusterOutputPath);

            String[] jobArgs =
                {
                    "-Xdebug",
                    "-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005",
                    "-cp",
                    _labkeyDir + "/labkeyBootstrap.jar",
                    "org.labkey.bootstrap.ClusterBootstrap",
                    "-modulesdir=" + getModulesDir(true),
                    "-webappdir=" + getWebappDir(true),
                    "-configdir=" + getConfigDir(true),
                    _pathMapper.localToRemote(serializedJobFile.getAbsoluteFile().toURI().toString())
                };
            jobDescription.setArgument(jobArgs);

            // Figure out where to send the job
            URL factoryUrl = ManagedJobFactoryClientHelper.getServiceURL(_globusEndpoint).getURL();
            EndpointReferenceType factoryEndpoint = ManagedJobFactoryClientHelper.getFactoryEndpoint(factoryUrl, ManagedJobFactoryConstants.FACTORY_TYPE.FORK);
/*
            ManagedJobFactoryPortType factoryPort = ManagedJobFactoryClientHelper.getPort(factoryEndpoint);

            // Load the proxy file
            ExtendedGSSManager manager = (ExtendedGSSManager)ExtendedGSSManager.getInstance();
            GSSCredential cred = manager.createCredential(GSSCredential.INITIATE_AND_ACCEPT);

            // Set up the security
            ClientSecurityDescriptor secDesc = new ClientSecurityDescriptor();
            secDesc.setGSITransport(Constants.ENCRYPTION);
            secDesc.setAuthz(new NoAuthorization());
            secDesc.setGSSCredential(cred);
            ((Stub) factoryPort)._setProperty(Constants.CLIENT_DESCRIPTOR, secDesc);
*/

            GramJob gramJob = new GramJob(jobDescription);
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

            String jobURI = "uuid:" + jobId + "/" + job.getActiveTaskId().getNamespaceClass().getName() + "/" + job.getActiveTaskId().getName();

            gramJob.submit(factoryEndpoint, false, false, jobURI);
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