/*
 * Copyright (c) 2007-2017 LabKey Corporation
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
 * Exposes the interface for dealing with
 * TaskPipelines and TaskFactories.  It is kept separate from the {@link PipelineService},
 * because it must be available on remote machines running Tasks and TaskPipelines
 * under the Mule server, not just on the web server.
 *
 * @author brendanx
 */

public interface PipelineJobService extends TaskPipelineRegistry
{
    String VERSION_SUBSTITUTION = "${version}";
    String VERSION_PLAIN_SUBSTITUTION = "${versionPlain}";

    /**
     * We use Spring-based XML to register and wire up implementations, which breaks our standard ServiceRegistry
     * methodology. Use this hack to manage the instance
     */
    class InstanceHolder
    {
        private static PipelineJobService INSTANCE;
    }

    static PipelineJobService get()
    {
        return InstanceHolder.INSTANCE;
    }

    static void setInstance(PipelineJobService instance)
    {
        InstanceHolder.INSTANCE = instance;
    }

    /**
     * <code>ApplicationProperties</code> are set through the Site Settings page
     * on the web server, and through config on remote machines.
     */
    interface ApplicationProperties
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
    interface ConfigProperties
    {
        String getSoftwarePackagePath(String packageName);
    }

    /**
     * <code>RemoteServerProperties</code> are only used on a remote server instance.
     */
    interface RemoteServerProperties
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
    interface RemoteExecutionEngineConfig
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

    ApplicationProperties getAppProperties();

    ConfigProperties getConfigProperties();

    RemoteServerProperties getRemoteServerProperties();

    @NotNull LocationType getLocationType();

    /** @return all of the engines that are currently known to the pipeline module */
    List<? extends RemoteExecutionEngine<?>> getRemoteExecutionEngines();

    /** Registers a remote execution engine. Intended for calling during module startup */
    void registerRemoteExecutionEngine(RemoteExecutionEngine engine);

    /**
     * @param exeRel if relative, interpreted based on either the installPath or tools directory
     * @param installPath if non-null, use this as the path to the file instead of the standard tools directory
     */
    String getExecutablePath(String exeRel, @Nullable String installPath, String packageName, String ver, Logger jobLogger) throws FileNotFoundException;

    /**
     * Similar to getExecutablePath(), but allows resolution of non-executable tool directory files
     *
     * @param exeRel if relative, interpreted based on either the installPath or tools directory
     * @param installPath if non-null, use this as the path to the file instead of the standard tools directory
     */
    String getToolPath(String exeRel, @Nullable String installPath, String packageName, String ver, Logger jobLogger) throws FileNotFoundException;

    /**
     * @param jarRel if relative, interpreted based on either the installPath or tools directory
     * @param installPath if non-null, use this as the path to the file instead of the standard tools directory
     */
    String getJarPath(String jarRel, @Nullable String installPath, String packageName, String ver) throws FileNotFoundException;

    String getJavaPath() throws FileNotFoundException;
    
    ParamParser createParamParser();

    WorkDirFactory getWorkDirFactory();

    WorkDirFactory getLargeWorkDirFactory();

    PathMapper getPathMapper();

    PipelineStatusFile.StatusWriter getStatusWriter();

    PipelineStatusFile.JobStore getJobStore();

    static String statusPathOf(String path)
    {
        return (path == null ? null : path.replace('\\', '/'));
    }

    enum LocationType
    {
        /** Any of the various queues that are managed and run directly on the web server */
        WebServer,
        /** Any external server that is monitoring for jobs to be assigned to it via JMS messaging */
        RemoteServer,
        /** Any external computational resource to which something on the web server submits jobs via {@link RemoteExecutionEngine} */
        RemoteExecutionEngine
    }
}
