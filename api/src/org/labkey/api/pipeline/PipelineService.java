/*
 * Copyright (c) 2005-2016 LabKey Corporation
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
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.springframework.validation.BindException;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 */
abstract public class PipelineService
        implements PipelineStatusFile.StatusReader, PipelineStatusFile.StatusWriter
{
    public static final String MODULE_NAME = "Pipeline";

    public static final String UNZIP_DIR = "unzip";

    static PipelineService instance;

    public static PipelineService get()
    {
        return instance;
    }

    static public void setInstance(PipelineService instance)
    {
        PipelineService.instance = instance;
    }

    abstract public void registerPipelineProvider(PipelineProvider provider, String... aliases);

    /**
     * Looks up the container hierarchy until it finds a pipeline root defined which is being
     * inherited by the specified container
     * @return null if there's no specific pipeline override and the default root is unavailable or misconfigured
     */
    @Nullable
    abstract public PipeRoot findPipelineRoot(Container container);

    /**
     * Looks up the container hierarchy until it finds a pipeline root defined which is being
     * inherited by the specified container
     * @return null if there's no specific pipeline override and the default root is unavailable or misconfigured
     */
    @Nullable
    abstract public PipeRoot findPipelineRoot(Container container, String type);


    /** @return true if this container (or an inherited parent container) has a pipeline root that exists on disk */
    abstract public boolean hasValidPipelineRoot(Container container);

    @NotNull
    abstract public Map<Container, PipeRoot> getAllPipelineRoots();

    @Nullable
    abstract public PipeRoot getPipelineRootSetting(Container container);

    /**
     * Gets the pipeline root that was explicitly configured for this container, or falls back to the default file root.
     * Does NOT look up the container hierarchy for a pipeline override defined in a parent container. In most
     * places where not explicitly doing pipeline configuration, use findPipelineRoot() instead.
     */
    @Nullable
    abstract public PipeRoot getPipelineRootSetting(Container container, String type);

    abstract public void setPipelineRoot(User user, Container container, String type, boolean searchable, URI... roots) throws SQLException;

    abstract public boolean canModifyPipelineRoot(User user, Container container);

    @NotNull
    abstract public List<PipelineProvider> getPipelineProviders();

    @Nullable
    abstract public PipelineProvider getPipelineProvider(String name);

    abstract public boolean isEnterprisePipeline();

    @NotNull
    abstract public PipelineQueue getPipelineQueue();

    /**
     * Add a <code>PipelineJob</code> to this queue to be run.
     *
     * @param job Job to be run
     */
    abstract public void queueJob(PipelineJob job) throws PipelineValidationException;

    /**
     * This will update the active task status of this job and re-queue that job if the task is complete
     */
    abstract public void setPipelineJobStatus(PipelineJob job, PipelineJob.TaskStatus status) throws PipelineJobException;

    abstract public void setPipelineProperty(Container container, String name, String value);

    abstract public String getPipelineProperty(Container container, String name);

    @NotNull
    public abstract String startFileAnalysis(AnalyzeForm form, @Nullable Map<String, String> variableMap, ViewContext viewContext) throws IOException, PipelineValidationException;

    /** Configurations for the pipeline job webpart ButtonBar */
    public enum PipelineButtonOption { Minimal, Assay, Standard }

    abstract public QueryView getPipelineQueryView(ViewContext context, PipelineButtonOption buttonOption);

    abstract public HttpView getSetupView(SetupForm form);

    abstract public boolean savePipelineSetup(ViewContext context, SetupForm form, BindException errors) throws Exception;

    // TODO: This should be on PipelineProtocolFactory
    abstract public String getLastProtocolSetting(PipelineProtocolFactory factory, Container container, User user);

    // TODO: This should be on PipelineProtocolFactory
    abstract public void rememberLastProtocolSetting(PipelineProtocolFactory factory, Container container,
                                                     User user, String protocolName);

    abstract public String getLastSequenceDbSetting(PipelineProtocolFactory factory, Container container, User user);

    abstract public void rememberLastSequenceDbSetting(PipelineProtocolFactory factory, Container container, User user,
                                                       String sequenceDbPath, String sequenceDb);

    abstract public List<String> getLastSequenceDbPathsSetting(PipelineProtocolFactory factory, Container container, User user);

    abstract public void rememberLastSequenceDbPathsSetting(PipelineProtocolFactory factory, Container container,
                                                            User user, List<String> sequenceDbPaths);

    abstract public boolean hasSiteDefaultRoot(Container container);

    abstract public TableInfo getJobsTable(User user, Container container);

    abstract public boolean runFolderImportJob(Container c, User user, ActionURL url, File studyXml, String originalFilename, BindException errors, PipeRoot pipelineRoot, ImportOptions options);

    abstract public Integer getJobId(User u, Container c, String jobGUID);

    abstract public FileAnalysisProperties getFileAnalysisProperties(Container c, String taskId, String path);

    public class FileAnalysisProperties
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

    @Nullable
    public abstract File getProtocolParametersFile(ExpRun expRun);
}
