/*
 * Copyright (c) 2005-2017 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.ImportOptions;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.pipeline.file.AbstractFileAnalysisProtocolFactory;
import org.labkey.api.pipeline.view.SetupForm;
import org.labkey.api.query.QueryView;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.view.ViewContext;
import org.springframework.validation.BindException;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Capabilities provided by the Pipeline module to other modules. These methods are only available to code
 * that is running within the web server. {@link PipelineJobService} provides basic pipeline job and task
 * functionality that is also available on remote execution pipeline servers.
 */
public interface PipelineService extends PipelineStatusFile.StatusReader, PipelineStatusFile.StatusWriter
{
    String MODULE_NAME = "Pipeline";
    String UNZIP_DIR = "unzip";
    String EXPORT_DIR = "export";
    String CACHE_DIR = "cache";

    String PRIMARY_ROOT = "PRIMARY";
    
    static PipelineService get()
    {
        return ServiceRegistry.get(PipelineService.class);
    }

    static void setInstance(PipelineService instance)
    {
        ServiceRegistry.get().registerService(PipelineService.class, instance);
    }

    /**
     * Statically register a single PipelineProvider that's implemented in code.
     * @param provider PipelineProvider to register
     * @param aliases Alternate names for this provider
     */
    void registerPipelineProvider(PipelineProvider provider, String... aliases);

    /**
     * Register a supplier of (likely multiple) PipelineProviders. Suppliers are called any time the service returns all
     * providers or resolves a single provider. This allows the provider list to be dynamic, for example, as file-based
     * assay definitions change.
     * @param supplier PipelineProviderSupplier
     */
    void registerPipelineProviderSupplier(PipelineProviderSupplier supplier);

    /**
     * Looks up the container hierarchy until it finds a pipeline root defined which is being
     * inherited by the specified container
     * @return null if there's no specific pipeline override and the default root is unavailable or misconfigured
     */
    @Nullable
    PipeRoot findPipelineRoot(Container container);

    /**
     * Looks up the container hierarchy until it finds a pipeline root defined which is being
     * inherited by the specified container
     * @return null if there's no specific pipeline override and the default root is unavailable or misconfigured
     */
    @Nullable
    PipeRoot findPipelineRoot(Container container, String type);


    /** @return true if this container (or an inherited parent container) has a pipeline root that exists on disk */
    boolean hasValidPipelineRoot(Container container);

    @NotNull
    Map<Container, PipeRoot> getAllPipelineRoots();

    @Nullable
    PipeRoot getPipelineRootSetting(Container container);

    /**
     * Gets the pipeline root that was explicitly configured for this container, or falls back to the default file root.
     * Does NOT look up the container hierarchy for a pipeline override defined in a parent container. In most
     * places where not explicitly doing pipeline configuration, use findPipelineRoot() instead.
     */
    @Nullable
    PipeRoot getPipelineRootSetting(Container container, String type);

    void setPipelineRoot(User user, Container container, String type, boolean searchable, URI... roots) throws SQLException;

    boolean canModifyPipelineRoot(User user, Container container);

    @NotNull
    List<PipelineProvider> getPipelineProviders();

    @Nullable
    PipelineProvider getPipelineProvider(String name);

    boolean isEnterprisePipeline();

    @NotNull
    PipelineQueue getPipelineQueue();

    /**
     * Add a <code>PipelineJob</code> to this queue to be run.
     *
     * @param job Job to be run
     */
    void queueJob(PipelineJob job) throws PipelineValidationException;

    /**
     * This will update the active task status of this job and re-queue that job if the task is complete
     */
    void setPipelineJobStatus(PipelineJob job, PipelineJob.TaskStatus status) throws PipelineJobException;

    void setPipelineProperty(Container container, String name, String value);

    String getPipelineProperty(Container container, String name);

    @NotNull
    String startFileAnalysis(AnalyzeForm form, @Nullable Map<String, String> variableMap, ViewContext viewContext) throws IOException, PipelineValidationException;

    @NotNull
    String startFileAnalysis(AnalyzeForm form, @Nullable Map<String, String> variableMap, ViewBackgroundInfo context) throws IOException, PipelineValidationException;

    /** Configurations for the pipeline job webpart ButtonBar */
    enum PipelineButtonOption { Minimal, Assay, Standard }

    QueryView getPipelineQueryView(ViewContext context, PipelineButtonOption buttonOption);

    HttpView getSetupView(SetupForm form);

    boolean savePipelineSetup(ViewContext context, SetupForm form, BindException errors) throws Exception;

    // TODO: This should be on PipelineProtocolFactory
    String getLastProtocolSetting(PipelineProtocolFactory factory, Container container, User user);

    // TODO: This should be on PipelineProtocolFactory
    void rememberLastProtocolSetting(PipelineProtocolFactory factory, Container container,
                                     User user, String protocolName);

    String getLastSequenceDbSetting(PipelineProtocolFactory factory, Container container, User user);

    void rememberLastSequenceDbSetting(PipelineProtocolFactory factory, Container container, User user,
                                       String sequenceDbPath, String sequenceDb);

    List<String> getLastSequenceDbPathsSetting(PipelineProtocolFactory factory, Container container, User user);

    void rememberLastSequenceDbPathsSetting(PipelineProtocolFactory factory, Container container,
                                            User user, List<String> sequenceDbPaths);

    boolean hasSiteDefaultRoot(Container container);

    TableInfo getJobsTable(User user, Container container);

    TableInfo getTriggersTable(User user, Container container);

    boolean runFolderImportJob(Container c, User user, ActionURL url, File studyXml, String originalFilename, BindException errors, PipeRoot pipelineRoot, ImportOptions options);

    Integer getJobId(User u, Container c, String jobGUID);

    FileAnalysisProperties getFileAnalysisProperties(Container c, String taskId, String path);

    class FileAnalysisProperties
    {
        private final PipeRoot _pipeRoot;
        private final File _dirData;
        private final AbstractFileAnalysisProtocolFactory _factory;

        public FileAnalysisProperties(PipeRoot pipeRoot, File dirData, AbstractFileAnalysisProtocolFactory factory)
        {
            _pipeRoot = pipeRoot;
            _dirData = dirData;
            _factory = factory;
        }

        public PipeRoot getPipeRoot()
        {
            return _pipeRoot;
        }

        public File getDirData()
        {
            return _dirData;
        }

        public AbstractFileAnalysisProtocolFactory getFactory()
        {
            return _factory;
        }
    }

    boolean isProtocolDefined(AnalyzeForm form);

    @Nullable
    File getProtocolParametersFile(ExpRun expRun);

    void deleteStatusFile(Container c, User u, boolean deleteExpRuns, Collection<Integer> rowIds) throws PipelineProvider.HandlerException;

    interface PipelineProviderSupplier
    {
        @NotNull Collection<PipelineProvider> getAll();
        @Nullable PipelineProvider findPipelineProvider(String name);
    }
}
