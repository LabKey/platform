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

package org.labkey.pipeline.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.admin.ImportOptions;
import org.labkey.api.attachments.AttachmentDirectory;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.files.FileContentService;
import org.labkey.api.pipeline.AnalyzeForm;
import org.labkey.api.pipeline.ParamParser;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineProtocolFactory;
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.pipeline.PipelineQueue;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineStatusFile;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.pipeline.file.AbstractFileAnalysisJob;
import org.labkey.api.pipeline.file.AbstractFileAnalysisProtocol;
import org.labkey.api.pipeline.file.AbstractFileAnalysisProtocolFactory;
import org.labkey.api.pipeline.view.SetupForm;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryView;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminOperationsPermission;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.TestContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.view.ViewContext;
import org.labkey.pipeline.PipelineController;
import org.labkey.pipeline.analysis.ProtocolManagementAuditProvider;
import org.labkey.pipeline.importer.FolderImportJob;
import org.labkey.pipeline.mule.EPipelineQueueImpl;
import org.labkey.pipeline.mule.ResumableDescriptor;
import org.labkey.pipeline.status.PipelineQueryView;
import org.mule.MuleManager;
import org.mule.umo.UMODescriptor;
import org.mule.umo.UMOException;
import org.mule.umo.model.UMOModel;
import org.springframework.validation.BindException;

import javax.jms.ConnectionFactory;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.labkey.api.pipeline.file.AbstractFileAnalysisJob.ANALYSIS_PARAMETERS_ROLE_NAME;

public class PipelineServiceImpl implements PipelineService
{
    private static final Logger LOG = Logger.getLogger(PipelineService.class);

    private static final String PREF_LASTPROTOCOL = "lastprotocol";
    private static final String PREF_LASTSEQUENCEDB = "lastsequencedb";
    private static final String PREF_LASTSEQUENCEDBPATHS = "lastsequencedbpaths";
    private static final String KEY_PREFERENCES = "pipelinePreferences";

    private final Map<String, PipelineProvider> _mapPipelineProviders = new ConcurrentSkipListMap<>();
    private final List<PipelineProviderSupplier> _suppliers = new CopyOnWriteArrayList<>();
    private final PipelineQueue _queue;

    public static PipelineServiceImpl get()
    {
        return (PipelineServiceImpl) PipelineService.get();
    }

    public PipelineServiceImpl()
    {
        registerPipelineProviderSupplier(new StandardPipelineProviderSupplier());

        ConnectionFactory factory = null;
        try
        {
            Context initCtx = new InitialContext();
            Context env = (Context) initCtx.lookup("java:comp/env");
            factory = (ConnectionFactory) env.lookup("jms/ConnectionFactory");
        }
        catch (NamingException e)
        {
        }

        if (factory == null)
        {
            _queue = new PipelineQueueImpl();
        }
        else
        {
            LOG.info("Found JMS queue; running Enterprise Pipeline.");
            _queue = new EPipelineQueueImpl(factory);
        }
    }

    @Override
    public void registerPipelineProviderSupplier(PipelineProviderSupplier supplier)
    {
        _suppliers.add(supplier);
    }

    @Override
    public void registerPipelineProvider(PipelineProvider provider, String... aliases)
    {
        _mapPipelineProviders.put(provider.getName(), provider);
        for (String alias : aliases)
            _mapPipelineProviders.put(alias, provider);
    }

    @Nullable
    @Override
    public PipeRootImpl findPipelineRoot(Container container, String type)
    {
        PipelineRoot pipelineRoot = PipelineManager.findPipelineRoot(container, type);
        if (null != pipelineRoot)
        {
            PipeRootImpl pipeRoot = new PipeRootImpl(pipelineRoot);
            if (pipeRoot.isValid())
                return pipeRoot;
        }

        // if we haven't found a 'real' pipeline root, default to pipeline root same as file root for this container
        return getDefaultPipelineRoot(container, type);
    }

    @Nullable
    @Override
    public PipeRootImpl findPipelineRoot(Container container)
    {
        return findPipelineRoot(container, PRIMARY_ROOT);
    }

    /**
     * Try to locate a default pipeline root from the default file root. However, if the file root for this container
     * has been customized, then set the default pipeline root for this container to be the same as the customized file root.
     * Or in other words: unless the pipeline root has been customized, set it the same as the file root.
     * Default pipeline roots only extend to the project level and are inherited by sub folders.
     * @return null if there default root is misconfigured or unavailable
     */
    @Nullable
    private PipeRootImpl getDefaultPipelineRoot(Container container, String type)
    {
        try
        {
            if (PRIMARY_ROOT.equals(type))
            {
                FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);
                if (svc != null && container != null)
                {
                    if (svc.isCloudRoot(container))
                    {
                        // File root is in the cloud
                        return new PipeRootImpl(createPipelineRoot(container, FileContentService.CLOUD_ROOT_PREFIX + "/" + svc.getCloudRootName(container)));
                    }
                    else if (!svc.isUseDefaultRoot(container.getProject()))
                    {
                        // File root has been overridden, so set the pipeline root to the same place
                        Path root = svc.getFileRootPath(container);
                        if (root != null)
                        {
                            AttachmentDirectory dir = svc.getMappedAttachmentDirectory(container, true);
                            return new PipeRootImpl(createPipelineRoot(container, FileUtil.pathToString(dir.getFileSystemDirectoryPath())), false);
                        }
                    }
                    else
                    {
                        Path root = svc.getDefaultRootPath(container, true);
                        if (root != null)
                        {
                            Path dir = root.resolve(svc.getFolderName(FileContentService.ContentType.files));
                            if (!Files.exists(dir))
                                Files.createDirectories(dir);
                            return new PipeRootImpl(createPipelineRoot(container, FileUtil.pathToString(dir)), true);
                        }
                    }
                }
            }
        }
        catch (IOException e)
        {
            return null;
        }
        return null;
    }

    @NotNull
    private PipelineRoot createPipelineRoot(Container container, String dir)
    {
        PipelineRoot p = new PipelineRoot();
        p.setContainer(container.getId());
        p.setPath(dir);
        p.setType(PRIMARY_ROOT);
        return p;
    }

    @Override
    public boolean hasSiteDefaultRoot(Container container)
    {
        PipelineRoot pipelineRoot = PipelineManager.findPipelineRoot(container);

        if (pipelineRoot == null)
            return getDefaultPipelineRoot(container, PRIMARY_ROOT) != null;
        return false;
    }

    @Override
    public boolean hasValidPipelineRoot(Container container)
    {
        PipeRoot pr = findPipelineRoot(container);
        return pr != null && pr.isValid();
    }


    @NotNull
    @Override
    public Map<Container, PipeRoot> getAllPipelineRoots()
    {
        PipelineRoot[] pipelines = PipelineManager.getPipelineRoots(PRIMARY_ROOT);

        Map<Container, PipeRoot> result = new HashMap<>();
        for (PipelineRoot pipeline : pipelines)
        {
            PipeRoot p = new PipeRootImpl(pipeline);
            if (p.getContainer() != null)
                result.put(p.getContainer(), p);
        }

        return result;
    }


    @Override
    public PipeRootImpl getPipelineRootSetting(Container container)
    {
        return getPipelineRootSetting(container, PRIMARY_ROOT);
    }

    @Override
    public PipeRootImpl getPipelineRootSetting(Container container, final String type)
    {
        PipelineRoot r = PipelineManager.getPipelineRootObject(container, type);
        if (null == r)
        {
            if (container != null)
                return getDefaultPipelineRoot(container, type);
            return null;
        }
        return new PipeRootImpl(r);
    }

    @Override
    public void setPipelineRoot(User user, Container container, String type, boolean searchable, URI... roots) throws SQLException
    {
        if (!canModifyPipelineRoot(user, container))
            throw new UnauthorizedException("You do not have sufficient permissions to set the pipeline root");
        
        PipelineManager.setPipelineRoot(user, container, roots, type, searchable);
    }

    @Override
    public boolean canModifyPipelineRoot(User user, Container container)
    {
        //per Britt--user must be site admin
        return container != null && !container.isRoot() && container.hasPermission(user, AdminOperationsPermission.class);
    }

    @Override
    @NotNull
    public List<PipelineProvider> getPipelineProviders()
    {
        LinkedList<PipelineProvider> providers = new LinkedList<>();

        // This mechanism allows PipelineProviders to come and go during a server session, e.g., as file-based assay definitions change
        for (PipelineProviderSupplier supplier : _suppliers)
            providers.addAll(supplier.getAll());

        return Collections.unmodifiableList(providers);
    }

    private class StandardPipelineProviderSupplier implements PipelineProviderSupplier
    {
        @NotNull
        @Override
        public Collection<PipelineProvider> getAll()
        {
            // Get a set of unique providers
            return new HashSet<>(_mapPipelineProviders.values());
        }

        @Nullable
        @Override
        public PipelineProvider findPipelineProvider(String name)
        {
            return _mapPipelineProviders.get(name);
        }
    }

    @Nullable
    @Override
    public PipelineProvider getPipelineProvider(String name)
    {
        if (name == null)
            return null;

        // This mechanism allows PipelineProviders to come and go during a server session, e.g., as file-based assay definitions change
        for (PipelineProviderSupplier supplier : _suppliers)
        {
            PipelineProvider provider = supplier.findPipelineProvider(name);

            if (null != provider)
                return provider;
        }

        return null;
    }

    @Override
    public boolean isEnterprisePipeline()
    {
        return (getPipelineQueue() instanceof EPipelineQueueImpl);
    }

    @NotNull
    @Override
    public PipelineQueue getPipelineQueue()
    {
        return _queue;
    }

    @Override
    public void queueJob(PipelineJob job) throws PipelineValidationException
    {
        getPipelineQueue().addJob(job);
    }

    @Override
    public void setPipelineJobStatus(PipelineJob job, PipelineJob.TaskStatus status) throws PipelineJobException
    {
        job.setActiveTaskStatus(status);

        // Only re-queue the job if status is 'complete' (not 'running' or 'error').
        if (status == PipelineJob.TaskStatus.complete)
        {
            try
            {
                PipelineStatusFileImpl sf = PipelineStatusManager.getStatusFile(job.getContainer(), job.getLogFilePath());
                if (sf != null)
                {
                    sf.setActiveHostName(null);  //indicates previous task was complete
                    PipelineStatusManager.updateStatusFile(sf);
                }

                EPipelineQueueImpl.dispatchJob(job);
            }
            catch (UMOException e)
            {
                throw new PipelineJobException(e);
            }
        }
    }

    @Override
    public void setPipelineProperty(Container container, String name, String value)
    {
        PipelineManager.setPipelineProperty(container, name, value);
    }

    @Override
    public String getPipelineProperty(Container container, String name)
    {
        return PipelineManager.getPipelineProperty(container, name);
    }

    @Override
    public QueryView getPipelineQueryView(ViewContext context, PipelineButtonOption buttonOption)
    {
        return new PipelineQueryView(context, null, null, buttonOption, context.getActionURL());
    }

    @Override
    public HttpView getSetupView(SetupForm form)
    {
        return new JspView<>("/org/labkey/pipeline/setup.jsp", form, form.getErrors());
    }

    @Override
    public boolean savePipelineSetup(ViewContext context, SetupForm form, BindException errors) throws Exception
    {
        return PipelineController.savePipelineSetup(context, form, errors);
    }

    private String getLastProtocolKey(PipelineProtocolFactory factory)
    {
        return PREF_LASTPROTOCOL + "-" + factory.getName();
    }

    // TODO: This should be on PipelineProtocolFactory
    @Override
    public String getLastProtocolSetting(PipelineProtocolFactory factory, Container container, User user)
    {
        try
        {
            Map<String, String> props = PropertyManager.getProperties(user, container, PipelineServiceImpl.KEY_PREFERENCES);
            String lastProtocolkey = props.get(getLastProtocolKey(factory));
            if (lastProtocolkey != null)
                return lastProtocolkey;
        }
        catch (Exception e)
        {
            LOG.error("Error", e);
        }
        return "";
    }

    // TODO: This should be on PipelineProtocolFactory
    @Override
    public void rememberLastProtocolSetting(PipelineProtocolFactory factory, Container container, User user,
                                            String protocolName)
    {
        if (user.isGuest())
            return;
        PropertyManager.PropertyMap map = PropertyManager.getWritableProperties(user, container, PipelineServiceImpl.KEY_PREFERENCES, true);
        map.put(getLastProtocolKey(factory), protocolName);
        map.save();
    }


    @Override
    public String getLastSequenceDbSetting(PipelineProtocolFactory factory, Container container, User user)
    {
        try
        {
            Map<String, String> props = PropertyManager.getProperties(user, container, PipelineServiceImpl.KEY_PREFERENCES);
            String lastSequenceDbSetting = props.get(PipelineServiceImpl.PREF_LASTSEQUENCEDB + "-" + factory.getName());
            if (lastSequenceDbSetting != null)
                return props.get(PipelineServiceImpl.PREF_LASTSEQUENCEDB + "-" + factory.getName());
        }
        catch (Exception e)
        {
            LOG.error("Error", e);
        }
        return "";
    }

    @Override
    public void rememberLastSequenceDbSetting(PipelineProtocolFactory factory, Container container, User user,
                                              String sequenceDbPath,String sequenceDb)
    {
        if (user.isGuest())
            return;
        if (sequenceDbPath == null || sequenceDbPath.equals("/")) 
            sequenceDbPath = "";
        String fullPath = sequenceDbPath + sequenceDb;
        PropertyManager.PropertyMap map = PropertyManager.getWritableProperties(user, container,
                PipelineServiceImpl.KEY_PREFERENCES, true);
        map.put(PipelineServiceImpl.PREF_LASTSEQUENCEDB + "-" + factory.getName(), fullPath);
        map.save();
    }

    @Nullable
    @Override
    public List<String> getLastSequenceDbPathsSetting(PipelineProtocolFactory factory, Container container, User user)
    {
        Map<String, String> props = PropertyManager.getProperties(user, container, PipelineServiceImpl.KEY_PREFERENCES);
        String dbPaths = props.get(PipelineServiceImpl.PREF_LASTSEQUENCEDBPATHS + "-" + factory.getName());

        if (null != dbPaths)
            return parseArray(dbPaths);

        return null;
    }

    @Override
    public void rememberLastSequenceDbPathsSetting(PipelineProtocolFactory factory, Container container, User user,
                                                   List<String> sequenceDbPathsList)
    {
        if (user.isGuest())
            return;
        String sequenceDbPathsString = list2String(sequenceDbPathsList);
        PropertyManager.PropertyMap map = PropertyManager.getWritableProperties(user, container,
                PipelineServiceImpl.KEY_PREFERENCES, true);
        if(sequenceDbPathsString == null || sequenceDbPathsString.length() == 0 || sequenceDbPathsString.length() >= 2000)
        {
            map.remove(PipelineServiceImpl.PREF_LASTSEQUENCEDBPATHS + "-" + factory.getName());
        }
        else
        {
            map.put(PipelineServiceImpl.PREF_LASTSEQUENCEDBPATHS + "-" + factory.getName(), sequenceDbPathsString);
        }
        map.save();
    }

    @Override
    public PipelineStatusFile getStatusFile(File logFile)
    {
        return PipelineStatusManager.getStatusFile(logFile);
    }

    @Override
    public PipelineStatusFile getStatusFile(Container container, Path logFile)
    {
        return PipelineStatusManager.getStatusFile(container, logFile);
    }

    @Override
    public PipelineStatusFile getStatusFile(int rowId)
    {
        return PipelineStatusManager.getStatusFile(rowId);
    }

    @Override
    public PipelineStatusFile getStatusFile(String jobGuid)
    {
        return PipelineStatusManager.getJobStatusFile(jobGuid);
    }

    @Override
    public List<? extends PipelineStatusFile> getQueuedStatusFiles()
    {
        return PipelineStatusManager.getQueuedStatusFiles();
    }

    @Override
    public List<PipelineStatusFileImpl> getJobsWaitingForFiles(Container c)
    {
        return PipelineStatusManager.getJobsWaitingForFiles(c);
    }

    @Override
    public List<PipelineStatusFileImpl> getQueuedStatusFiles(Container c)
    {
        return PipelineStatusManager.getQueuedStatusFilesForContainer(c);
    }

    @Override
    public boolean setStatus(PipelineJob job, String status, String statusInfo, boolean allowInsert)
    {
        return PipelineStatusManager.setStatusFile(job, job.getUser(), status, statusInfo, allowInsert);
    }

    @Override
    public void ensureError(PipelineJob job) throws Exception
    {
        PipelineStatusManager.ensureError(job);
    }

    @Override
    public void setHostName(String hostName)
    {
        throw new UnsupportedOperationException("Method supported only on remote server");
    }

    private List<String> parseArray(String dbPaths)
    {
        if(dbPaths == null) return null;
        if(dbPaths.length() == 0) return new ArrayList<>();
        String[] tokens = dbPaths.split("\\|");
        return new ArrayList<>(Arrays.asList(tokens));
    }

    private String list2String(List<String> sequenceDbPathsList)
    {
        if(sequenceDbPathsList == null) return null;
        StringBuilder temp = new StringBuilder();
        for(String path:sequenceDbPathsList)
        {
            if(temp.length() > 0)
                temp.append("|");
            temp.append(path);
        }
        return temp.toString();
    }

    /**
     * Recheck the status of the jobs that may or may not have been started already
     */
    public void refreshLocalJobs()
    {
        // Spin through the Mule config to be sure that we get all the different descriptors that
        // have been registered
        for (UMOModel model : (Collection<UMOModel>) MuleManager.getInstance().getModels().values())
        {
            for (Iterator<String> i = model.getComponentNames(); i.hasNext(); )
            {
                String name = i.next();
                UMODescriptor descriptor = model.getDescriptor(name);

                try
                {
                    Class c = descriptor.getImplementationClass();
                    if (ResumableDescriptor.class.isAssignableFrom(c))
                    {
                        ResumableDescriptor resumable = ((Class<ResumableDescriptor>)c).newInstance();
                        resumable.resume(descriptor);
                    }
                }
                catch (UMOException e)
                {
                    LOG.error("Failed to get implementation class from descriptor " + descriptor, e);
                }
                catch (IllegalAccessException | InstantiationException e)
                {
                    LOG.error("Failed to resume jobs for descriptor " + descriptor, e);
                }
            }
        }
    }

    @Override
    public TableInfo getJobsTable(User user, Container container)
    {
        return new PipelineQuerySchema(user, container).getTable(PipelineQuerySchema.JOB_TABLE_NAME);
    }

    @Override
    public TableInfo getTriggersTable(User user, Container container)
    {
        return new PipelineQuerySchema(user, container).getTable(PipelineQuerySchema.TRIGGER_CONFIGURATIONS_TABLE_NAME);
    }

    @Override
    public boolean runFolderImportJob(Container c, User user, ActionURL url, File studyXml, String originalFilename, BindException errors, PipeRoot pipelineRoot, ImportOptions options)
    {
        try{
            PipelineService.get().queueJob(new FolderImportJob(c, user, url, studyXml, originalFilename, pipelineRoot, options));
            return true;
        }
        catch (PipelineValidationException e){
            return false;
        }
    }

    @Override
    public Integer getJobId(User u, Container c, String jobGUID)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("job"), jobGUID);
        Collection<Map<String, Object>> selectResults = new TableSelector(PipelineService.get().getJobsTable(u, c), Collections.singleton("RowId"), filter, null).getMapCollection();
        Integer rowId = null;

        for (Map<String, Object> m : selectResults)
        {
            rowId = (Integer)m.get("RowId");
        }
        return rowId;
    }

    @Override
    public FileAnalysisProperties getFileAnalysisProperties(Container c, String taskId, @Nullable String path)
    {
        PipeRoot pr = PipelineService.get().findPipelineRoot(c);
        if (pr == null || !pr.isValid())
            throw new NotFoundException();

        File dirData = null;
        if (path != null)
        {
            dirData = pr.resolvePath(path);
            if (dirData == null || !NetworkDrive.exists(dirData))
                throw new NotFoundException("Could not resolve path: " + path);
        }

        TaskPipeline taskPipeline = PipelineJobService.get().getTaskPipeline(taskId);
        AbstractFileAnalysisProtocolFactory factory = PipelineJobService.get().getProtocolFactory(taskPipeline);
        return new FileAnalysisProperties(pr, dirData, factory);
    }

    @Override
    @NotNull
    public String startFileAnalysis(AnalyzeForm form, @Nullable Map<String, String> variableMap, ViewContext context) throws IOException, PipelineValidationException
    {
        ViewBackgroundInfo info = new ViewBackgroundInfo(context.getContainer(), context.getUser(), context.getActionURL());
        return startFileAnalysis(form, variableMap, info);
    }

    @Override
    @NotNull
    public String startFileAnalysis(AnalyzeForm form, @Nullable Map<String, String> variableMap, ViewBackgroundInfo context) throws IOException, PipelineValidationException
    {
        if (form.getProtocolName() == null)
        {
            throw new IllegalArgumentException("Must specify a protocol name");
        }
        TaskPipeline taskPipeline = PipelineJobService.get().getTaskPipeline(form.getTaskId());
        FileAnalysisProperties props = getFileAnalysisProperties(context.getContainer(), form.getTaskId(), form.getPath());
        PipeRoot root = props.getPipeRoot();
        File dirData = props.getDirData();
        AbstractFileAnalysisProtocolFactory factory = props.getFactory();

        if (taskPipeline.isUseUniqueAnalysisDirectory())
        {
            dirData = new File(dirData, form.getProtocolName() + "_" + FileUtil.getTimestamp());
            if (!dirData.mkdir())
            {
                throw new IOException("Failed to create unique analysis directory: " + FileUtil.getAbsoluteCaseSensitiveFile(dirData).getAbsolutePath());
            }
        }
        AbstractFileAnalysisProtocol protocol = factory.getProtocol(root, dirData, form.getProtocolName(), false);
        if (protocol == null || form.isAllowProtocolRedefinition())
        {
            String xml;
            if (StringUtils.isNotBlank(form.getConfigureXml()))
            {
                if (StringUtils.isNotBlank(form.getConfigureJson()))
                {
                    throw new IllegalArgumentException("The parameters should be defined as XML or JSON, not both");
                }
                xml = form.getConfigureXml();
            }
            else
            {
                if (StringUtils.isBlank(form.getConfigureJson()))
                {
                    throw new IllegalArgumentException("Parameters must be defined, either as XML or JSON");
                }
                ParamParser parser = PipelineJobService.get().createParamParser();
                Map<String, String> params = new HashMap<>();
                Map<String, Object> parsedMap = new ObjectMapper().readValue(form.getConfigureJson(), new TypeReference<Map<String, Object>>(){});
                for (Map.Entry<String, Object> entry : parsedMap.entrySet())
                {
                    params.put(entry.getKey(), entry.getValue() == null ? null : entry.getValue().toString());
                }
                xml = parser.getXMLFromMap(params);
            }

            protocol = PipelineJobService.get().getProtocolFactory(taskPipeline).createProtocolInstance(
                    form.getProtocolName(),
                    form.getProtocolDescription(),
                    xml);

            protocol.setEmail(context.getUser().getEmail());
            protocol.validate(root);
            if (form.isSaveProtocol())
            {
                protocol.validateToSave(root, false, !form.isAllowProtocolRedefinition());
                protocol.saveDefinition(root);
                PipelineService.get().rememberLastProtocolSetting(protocol.getFactory(),
                        context.getContainer(), context.getUser(), protocol.getName());
                AuditLogService.get().addEvent(context.getUser(),
                        new ProtocolManagementAuditProvider.ProtocolManagementEvent(ProtocolManagementAuditProvider.EVENT,
                                context.getContainer(), factory.getName(), protocol.getName(), "created"));
            }
        }
        else
        {
            if (StringUtils.isNotBlank(form.getConfigureXml()) || StringUtils.isNotBlank(form.getConfigureJson()))
            {
                throw new IllegalArgumentException("Cannot redefine an existing protocol");
            }
            PipelineService.get().rememberLastProtocolSetting(protocol.getFactory(),
                    context.getContainer(), context.getUser(), protocol.getName());
        }

        protocol.getFactory().ensureDefaultParameters(root);

        File fileParameters = protocol.getParametersFile(dirData, root);
        // Make sure configure.xml file exists for the job when it runs.
        if (fileParameters != null && !fileParameters.exists())
        {
            protocol.setEmail(context.getUser().getEmail());
            protocol.saveInstance(fileParameters, context.getContainer());
        }

        Boolean allowNonExistentFiles = form.isAllowNonExistentFiles() != null ? form.isAllowNonExistentFiles() : false;
        List<File> filesInputList = form.getValidatedFiles(context.getContainer(), allowNonExistentFiles);

        if (form.isActiveJobs())
        {
            throw new IllegalArgumentException("Active jobs already exist for this protocol.");
        }

        if (taskPipeline.isUseUniqueAnalysisDirectory())
        {
            for (File inputFile : filesInputList)
            {
                if (!(inputFile.renameTo(new File(dirData, inputFile.getName())) || allowNonExistentFiles))
                {
                    throw new IOException("Failed to move input file into unique directory: " + FileUtil.getAbsoluteCaseSensitiveFile(inputFile).getAbsolutePath());
                }
            }
        }

        String pipelineDescription = form.getPipelineDescription();
        if(pipelineDescription != null)
        {
            if(variableMap == null)
                variableMap = new HashMap<>();

            variableMap.put("pipelineDescription", pipelineDescription);
        }

        AbstractFileAnalysisJob job = protocol.createPipelineJob(context, root, filesInputList, fileParameters, variableMap);
        PipelineService.get().queueJob(job);
        return job.getJobGUID();
    }

    @Override
    public boolean isProtocolDefined(AnalyzeForm form)
    {
        FileAnalysisProperties props = getFileAnalysisProperties(form.getContainer(), form.getTaskId(), form.getPath());
        AbstractFileAnalysisProtocolFactory factory = props.getFactory();
        return factory.getProtocol(props.getPipeRoot(), props.getDirData(), form.getProtocolName(), false) != null;
    }

    @Nullable
    @Override
    public File getProtocolParametersFile(ExpRun expRun)
    {
        return expRun.getInputDatas(ANALYSIS_PARAMETERS_ROLE_NAME, null).get(0).getFile();
    }

    @Override
    public void deleteStatusFile(Container c, User u, boolean deleteExpRuns, Collection<Integer> rowIds) throws PipelineProvider.HandlerException
    {
        PipelineStatusManager.deleteStatus(c, u, deleteExpRuns, rowIds);
    }

    public static class TestCase extends Assert
    {
        private static String PROJECT_NAME = "__PipelineRootTestProject";
        private static String FOLDER_NAME = "subfolder";
        private static String DEFAULT_ROOT_URI = "build/deploy/files/__PipelineRootTestProject/@files";
        private static String FILE_ROOT_SUFFIX = "_FileRootTest";
        private static String PIPELINE_ROOT_SUFFIX = "_PipelineRootTest";

        private User _user;
        private Container _project;
        private Container _subFolder;

        @Before
        public void setUp()
        {
            TestContext ctx = TestContext.get();
            User loggedIn = ctx.getUser();
            assertTrue("login before running this test", null != loggedIn);
            assertFalse("login before running this test", loggedIn.isGuest());
            _user = ctx.getUser().cloneUser();
        }

        /**
         * Verify pipeline root and file root default values at the project level
         */
        @Test
        public void testPipelineRootDefaultsInProject() throws Exception
        {
            // make sure the project doesnt already exist and create a new project that will have pipeline root and file root set to their defaults
            if (ContainerManager.getForPath(PROJECT_NAME) != null)
            {
                ContainerManager.deleteAll(ContainerManager.getForPath(PROJECT_NAME), _user);
            }
            _project = ContainerManager.createContainer(ContainerManager.getRoot(), PROJECT_NAME, null, null, Container.TYPE.normal, _user);

            // obtain the pipeline root
            PipeRoot pipelineRootSetting = PipelineService.get().getPipelineRootSetting(_project);
            File pipelineRoot = pipelineRootSetting.getRootPath();

            // obtain the file root
            FileContentService fileService = ServiceRegistry.get().getService(FileContentService.class);
            File fileRoot = fileService.getFileRoot(_project, FileContentService.ContentType.files);

            // verify pipeline root and file root are set to defaults and they they point to the same place
            assertEquals("The pipeline root isDefault flag was not set correctly.", true, pipelineRootSetting.isDefault());
            assertEquals("The default pipeline root was not set the same as the default file root.", pipelineRoot, fileRoot);
            assertTrue("The pipeline root uri was: " + FileUtil.uriToString(pipelineRootSetting.getUri()) + ", but expected: " + DEFAULT_ROOT_URI, FileUtil.uriToString(pipelineRootSetting.getUri()).contains(DEFAULT_ROOT_URI));

            // ensure everything back to the way it was before test ran
            ContainerManager.deleteAll(_project, _user);
        }

        /**
         * Verify when project level file root is customized that the previously defautl project level pipeline root is implicitly customized as well
         * and points to the customized file root location.
         */
        @Test
        public void testPipelineRootWithCustomizedFileRootInProject() throws Exception
        {
            // make sure the project doesnt already exist and create a new project that will have pipeline root and file root set to their defaults
            if (ContainerManager.getForPath(PROJECT_NAME) != null)
            {
                ContainerManager.deleteAll(ContainerManager.getForPath(PROJECT_NAME), _user);
            }
            _project = ContainerManager.createContainer(ContainerManager.getRoot(), PROJECT_NAME, null, null, Container.TYPE.normal, _user);

            // customize the file root to point to a new location off the site root
            FileContentService fileService = ServiceRegistry.get().getService(FileContentService.class);
            fileService.setFileRoot(_project, getTestRoot(FILE_ROOT_SUFFIX));

            // obtain the pipeline root (customized implicitly because the file root was customized)
            PipeRoot pipelineRootSetting = PipelineService.get().getPipelineRootSetting(_project);
            File pipelineRoot = pipelineRootSetting.getRootPath();

            // obtain the customized file root
            File fileRoot = fileService.getFileRoot(_project, FileContentService.ContentType.files);

            // verify pipeline root and file root are now both customized and set to the same customized location
            assertEquals("The pipeline root isDefault flag was not set correctly.",false, pipelineRootSetting.isDefault());
            assertEquals("The default pipeline root was not set the same as the customized file root.",pipelineRoot, fileRoot);
            assertEquals("The pipeline root was not set to the customized file root location.", pipelineRoot.getParentFile(), getTestRoot(FILE_ROOT_SUFFIX));

            ContainerManager.deleteAll(_project, _user);
        }

        /**
         * Verify project level pipeline root and project level file root can be customized to point to different locations
         */
        @Test
        public void testBothPipelineRootAndFileRootCustomizedInProject() throws Exception
        {
            // make sure the project doesnt already exist and create a new project that will have pipeline root and file root set to their defaults
            if (ContainerManager.getForPath(PROJECT_NAME) != null)
            {
                ContainerManager.deleteAll(ContainerManager.getForPath(PROJECT_NAME), _user);
            }
            _project = ContainerManager.createContainer(ContainerManager.getRoot(), PROJECT_NAME, null, null, Container.TYPE.normal, _user);

            // customize the file root to point to a new location off the site root
            FileContentService fileService = ServiceRegistry.get().getService(FileContentService.class);
            fileService.setFileRoot(_project, getTestRoot(FILE_ROOT_SUFFIX));

            // customize the pipeline root to point to a new location off the site root
            PipelineService.get().setPipelineRoot(_user, _project, PRIMARY_ROOT, false, getTestRoot(PIPELINE_ROOT_SUFFIX).toURI());

            // obtain the customized pipeline root
            PipeRoot pipelineRootSetting = PipelineService.get().getPipelineRootSetting(_project);
            File pipelineRoot = pipelineRootSetting.getRootPath();

            // obtain the customized file root
            File fileRoot = fileService.getFileRoot(_project, FileContentService.ContentType.files);

            // verify pipeline root and file root are now both customized and set to different customized location
            assertEquals("The pipeline root isDefault flag was not set correctly.",false, pipelineRootSetting.isDefault());
            assertNotEquals("The customized pipeline root was not set different than the customized file root.",pipelineRoot, fileRoot);
            assertEquals("The file root was not set to the customized file root location.", fileRoot.getParentFile(), getTestRoot(FILE_ROOT_SUFFIX));
            assertEquals("The pipeline root was not set to the customized pipeline root location.", pipelineRoot, getTestRoot(PIPELINE_ROOT_SUFFIX));

            ContainerManager.deleteAll(_project, _user);
        }

        /**
         * Verify when project level file root is customized that the subfolder of that project has the file root
         * point to a subfolder of the customized project level file root location, and the pipeline root point to the
         * same location as the subfolder file root.
         */
        @Test
        public void testSubfolderWhenCustomizedFileRootInProject() throws Exception
        {
            // make sure the project doesnt already exist and create a new project that will have pipeline root and file root set to their defaults
            if (ContainerManager.getForPath(PROJECT_NAME) != null)
            {
                ContainerManager.deleteAll(ContainerManager.getForPath(PROJECT_NAME), _user);
            }
            _project = ContainerManager.createContainer(ContainerManager.getRoot(), PROJECT_NAME, null, null, Container.TYPE.normal, _user);

            // customize the file root to point to a new location off the site root
            FileContentService fileService = ServiceRegistry.get().getService(FileContentService.class);
            fileService.setFileRoot(_project, getTestRoot(FILE_ROOT_SUFFIX));

            // create a subfolder of this project
            _subFolder = ContainerManager.createContainer(_project, FOLDER_NAME);

            // obtain the customized file root of the project
            File projectFileRoot = fileService.getFileRoot(_project, FileContentService.ContentType.files);

            // obtain the customized file root of the subfolder
            File subFolderFileRoot = fileService.getFileRoot(_subFolder, FileContentService.ContentType.files);

            // obtain the pipeline root of the subfolder
            // uses findPipelineRoot() because it will walk the folder hierarchy up to the project looking for custom
            // pipeline root settings but getPipelineRootSetting will not.
            PipeRoot subfolderPipelineRootSetting = PipelineService.get().findPipelineRoot(_subFolder);
            File subfolderPipelineRoot = subfolderPipelineRootSetting.getRootPath();

            // verify subfolder pipeline root and file root are now both customized and set to the same subfolder of the project file root
            assertEquals("The pipeline root isDefault flag was not set correctly.",false, subfolderPipelineRootSetting.isDefault());
            assertEquals("The pipeline root of this subfolder was not set the same as the file root of the subfolder.",subFolderFileRoot, subfolderPipelineRoot);
            assertEquals("The file root of this subfolder was not set to a subfolder of the file root of the parent project.",projectFileRoot.getParentFile(), subFolderFileRoot.getParentFile().getParentFile());

            ContainerManager.deleteAll(_project, _user);
        }

        /**
         * Verify when both the project level file root and pipeline root are customized that a subfolder of the
         * project has the file root point to a subfolder of the customized project level file root, but the pipeline root
         * point to the same location as its parent project pipeline root.
         *
         * This a test of Issue 30795: Pipeline Files path for project subfolder issues when project has a files root override
         */
        @Test
        public void testSubfolderWhenBothPipelineRootAndFileRootCustomizedInProject() throws Exception
        {
            // make sure the project doesnt already exist and create a new project that will have pipeline root and file root set to their defaults
            if (ContainerManager.getForPath(PROJECT_NAME) != null)
            {
                ContainerManager.deleteAll(ContainerManager.getForPath(PROJECT_NAME), _user);
            }
            _project = ContainerManager.createContainer(ContainerManager.getRoot(), PROJECT_NAME, null, null, Container.TYPE.normal, _user);

            // customize the file root to point to a new location off the site root
            FileContentService fileService = ServiceRegistry.get().getService(FileContentService.class);
            fileService.setFileRoot(_project, getTestRoot(FILE_ROOT_SUFFIX));

            // customize the project pipeline root to point to a new location off the site root
            PipelineService.get().setPipelineRoot(_user, _project, PRIMARY_ROOT, false, getTestRoot(PIPELINE_ROOT_SUFFIX).toURI());

            // create a subfolder of this project
            _subFolder = ContainerManager.createContainer(_project, FOLDER_NAME);

            // obtain the customized file root of the project
            File projectFileRoot = fileService.getFileRoot(_project, FileContentService.ContentType.files);

            // obtain the customized file root of the subfolder
            File subFolderFileRoot = fileService.getFileRoot(_subFolder, FileContentService.ContentType.files);

            // obtain the customized pipeline root of the project
            PipeRoot projectPipelineRootSetting = PipelineService.get().getPipelineRootSetting(_project);
            File projectPipelineRoot = projectPipelineRootSetting.getRootPath();

            // obtain the pipeline root of the subfolder
            // uses findPipelineRoot() because it will walk the folder hierarchy up to the project looking for custom
            // pipeline root settings but getPipelineRootSetting will not.
            PipeRoot subfolderPipelineRootSetting = PipelineService.get().findPipelineRoot(_subFolder);
            File subfolderPipelineRoot = subfolderPipelineRootSetting.getRootPath();

            // verify subfolder pipeline root and project pipeline root are now both customized and set to the same location
            assertEquals("The project pipeline root isDefault flag was not set correctly.",false, projectPipelineRootSetting.isDefault());
            assertEquals("The subfolder pipeline root isDefault flag was not set correctly.",false, subfolderPipelineRootSetting.isDefault());
            assertEquals("The file root of this subfolder was not set to a subfolder of the file root of the parent project.",projectFileRoot.getParentFile(), subFolderFileRoot.getParentFile().getParentFile());
            assertEquals("The pipeline root of this subfolder was not set the same as the pipeline root of the parent project.",projectPipelineRoot, subfolderPipelineRoot);

            ContainerManager.deleteAll(_project, _user);
        }

        private File getTestRoot(String rootSufix)
        {
            FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);
            File siteRoot = svc.getSiteDefaultRoot();
            File testRoot = new File(siteRoot, rootSufix);
            testRoot.mkdirs();
            Assert.assertTrue("Unable to create test root", testRoot.exists());
            return testRoot;
        }
    }
}
