/*
 * Copyright (c) 2008-2016 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.file.PathMapper;

import java.io.FileNotFoundException;
import java.util.List;

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
    public static final String VERSION_SUBSTITUTION = "${version}";
    public static final String VERSION_PLAIN_SUBSTITUTION = "${versionPlain}";

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
     * <code>RemoteServerProperties</code> are only used on a remote server instance.
     */
    public interface RemoteServerProperties
    {
        String getLocation();
        String getMuleConfig();

        /**
         *
         * @return the hostName to be recorded as activeHostName for the task in PipelineStatusFile
         */
        @NotNull String getHostName();
    }

    /** Configuration for a {@link RemoteExecutionEngine}. Expected to be registered via Spring XML configuration files. */
    public interface RemoteExecutionEngineConfig
    {
        /** @return the pipeline location to which this configuration is bound, and for which jobs should be routed to the associated engine */
        @NotNull
        String getLocation();

        /** @return the type of engine. Must match a registered engine's {@link RemoteExecutionEngine#getType()} */
        @NotNull
        String getType();

        /** @return the path mapper that knows how to translate file paths from the web server's perspective to the remote execution engine's perspective */
        @NotNull
        PathMapper getPathMapper();
    }

    abstract public ApplicationProperties getAppProperties();

    abstract public ConfigProperties getConfigProperties();

    abstract public RemoteServerProperties getRemoteServerProperties();

    @NotNull
    abstract public LocationType getLocationType();

    /** @return all of the engines that are currently known to the pipeline module */
    abstract public List<? extends RemoteExecutionEngine<?>> getRemoteExecutionEngines();

    /** Registers a remote execution engine. Intended for calling during module startup */
    abstract public void registerRemoteExecutionEngine(RemoteExecutionEngine engine);

    /**
     * @param exeRel if relative, interpreted based on either the installPath or tools directory
     * @param installPath if non-null, use this as the path to the file instead of the standard tools directory
     */
    abstract public String getExecutablePath(String exeRel, @Nullable String installPath, String packageName, String ver, Logger jobLogger) throws FileNotFoundException;

    /**
     * @param jarRel if relative, interpreted based on either the installPath or tools directory
     * @param installPath if non-null, use this as the path to the file instead of the standard tools directory
     */
    abstract public String getJarPath(String jarRel, @Nullable String installPath, String packageName, String ver) throws FileNotFoundException;

    abstract public String getJavaPath() throws FileNotFoundException;
    
    abstract public ParamParser createParamParser();

    abstract public WorkDirFactory getWorkDirFactory();

    abstract public WorkDirFactory getLargeWorkDirFactory();

    abstract public PathMapper getPathMapper();

    abstract public PipelineStatusFile.StatusWriter getStatusWriter();

    abstract public PipelineStatusFile.JobStore getJobStore();

    public static String statusPathOf(String path)
    {
        return (path == null ? null : path.replace('\\', '/'));
    }

    public enum LocationType
    {
        /** Any of the various queues that are managed and run directly on the web server */
        WebServer,
        /** Any external server that is monitoring for jobs to be assigned to it via JMS messaging */
        RemoteServer,
        /** Any external computational resource to which something on the web server submits jobs via {@link RemoteExecutionEngine} */
        RemoteExecutionEngine
    }
}
