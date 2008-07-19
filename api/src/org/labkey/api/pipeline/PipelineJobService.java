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
package org.labkey.api.pipeline;

import java.io.FileNotFoundException;
import java.util.Map;

/**
 * <code>PipelineJobService</code> exposes the interface for dealing with
 * TaskPipelines and TaskFactories.  It is kept separate from the PipelineService,
 * because it must be available on remote machines running Tasks and TaskPipelines
 * under the Mule server.
 *
 * @author brendanx
 */
abstract public class PipelineJobService implements TaskPipelineRegistry
{
    private static PipelineJobService _instance;

    public static PipelineJobService get()
    {
        return _instance;
    }

    public static void setInstance(PipelineJobService instance)
    {
        PipelineJobService._instance = instance;
    }

    /**
     * <code>ApplicationProperties</code> are set through the Site Settings page
     * on the web server, and through config on remote machines.
     */
    public interface ApplicationProperties
    {
        String getToolsDirectory();

        String getBaseServerUrl();
        String getCallbackPassword();

        Character getNetworkDriveLetter();
        String getNetworkDrivePath();
        String getNetworkDriveUser();
        String getNetworkDrivePassword();
    }

    /**
     * <code>ConfigProperties</code> may be desirable on an machine, but may
     * only be set through config.
     */
    public interface ConfigProperties
    {
        String getSoftwarePackagePath(String packageName);
    }

    /**
     * <code>MuleServerProperties</code> are only used on a Mule Server instance.
     */
    public interface RemoteServerProperties
    {
        String getLocation();
        String getMuleConfig();
    }

    /**
     * <code>GlobusClientProperties</code> are only used on a machine running
     * the <code>PipelineJobRunnerGlobus</code>. 
     */
    public interface GlobusClientProperties extends ClusterSettings
    {
        String getJavaHome();
        String getLabKeyDir();
        String getGlobusServer();
        String getJobFactoryType();
        Map<String, String> getPathMapping();
    }

    abstract public ApplicationProperties getAppProperties();

    abstract public ConfigProperties getConfigProperties();

    abstract public RemoteServerProperties getRemoteServerProperties();

    abstract public GlobusClientProperties getGlobusClientProperties();

    abstract public String getExecutablePath(String exeRel, String packageName, String ver) throws FileNotFoundException;

    abstract public String getJarPath(String jarRel, String packageName, String ver) throws FileNotFoundException;

    abstract public String getJavaPath() throws FileNotFoundException;
    
    abstract public ParamParser createParamParser();

    abstract public WorkDirFactory getWorkDirFactory();

    abstract public PipelineStatusFile.StatusWriter getStatusWriter();

    abstract public PipelineStatusFile.JobStore getJobStore();

    public static String statusPathOf(String path)
    {
        return (path == null ? null : path.replace('\\', '/'));
    }
}
