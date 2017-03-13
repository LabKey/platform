/*
 * Copyright (c) 2007-2016 LabKey Corporation
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
import org.labkey.api.admin.ImportOptions;
import org.labkey.api.attachments.AttachmentDirectory;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.Container;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.files.FileContentService;
import org.labkey.api.files.MissingRootDirectoryException;
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
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.NetworkDrive;
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
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.labkey.api.pipeline.file.AbstractFileAnalysisJob.ANALYSIS_PARAMETERS_ROLE_NAME;

public class PipelineServiceImpl extends PipelineService
{
    public static String PREF_LASTPROTOCOL = "lastprotocol";
    public static String PREF_LASTSEQUENCEDB = "lastsequencedb";
    public static String PREF_LASTSEQUENCEDBPATHS = "lastsequencedbpaths";
    public static String KEY_PREFERENCES = "pipelinePreferences";

    private static Logger _log = Logger.getLogger(PipelineService.class);

    private Map<String, PipelineProvider> _mapPipelineProviders = new TreeMap<>();
    private PipelineQueue _queue = null;

    public static PipelineServiceImpl get()
    {
        return (PipelineServiceImpl) PipelineService.get();
    }

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
            return new PipeRootImpl(pipelineRoot);
        }

        // if we haven't found a 'real' root, default to a root off the site wide default
        return getDefaultPipelineRoot(container, type);
    }

    @Nullable
    public PipeRootImpl findPipelineRoot(Container container)
    {
        return findPipelineRoot(container, PipelineRoot.PRIMARY_ROOT);
    }

    /**
     * Try to locate a default pipeline root from the site file root. Default pipeline roots only
     * extend to the project level and are inherited by sub folders.
     * @return null if there default root is misconfigured or unavailable
     */
    @Nullable
    private PipeRootImpl getDefaultPipelineRoot(Container container, String type)
    {
        try
        {
            if (PipelineRoot.PRIMARY_ROOT.equals(type))
            {
                FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);
                if (svc != null && container != null)
                {
                    if (svc.isUseDefaultRoot(container.getProject()))
                    {
                        File root = svc.getFileRoot(container);
                        if (root != null)
                        {
                            AttachmentDirectory dir = svc.getMappedAttachmentDirectory(container, true);
                            return createDefaultRoot(container, dir.getFileSystemDirectory(), true);
                        }
                    }
                    else
                    {
                        File root = svc.getDefaultRoot(container, true);
                        if (root != null)
                        {
                            File dir = new File(root, svc.getFolderName(FileContentService.ContentType.files));
                            if (!dir.exists())
                                dir.mkdirs();
                            return createDefaultRoot(container, dir, false);
                        }
                    }
                }
            }
        }
        catch (MissingRootDirectoryException e)
        {
            return null;
        }
        return null;
    }

    @NotNull
    private PipeRootImpl createDefaultRoot(Container container, File dir, boolean sameAsFilesRoot)
    {
        PipelineRoot p = new PipelineRoot();

        p.setContainer(container.getId());
        p.setPath(dir.toURI().toString());
        p.setType(PipelineRoot.PRIMARY_ROOT);

        return new PipeRootImpl(p, sameAsFilesRoot);
    }

    public boolean hasSiteDefaultRoot(Container container)
    {
        PipelineRoot pipelineRoot = PipelineManager.findPipelineRoot(container);

        if (pipelineRoot == null)
            return getDefaultPipelineRoot(container, PipelineRoot.PRIMARY_ROOT) != null;
        return false;
    }

    @Override
    public boolean hasValidPipelineRoot(Container container)
    {
        PipeRoot pr = findPipelineRoot(container);
        return pr != null && pr.isValid();
    }


    @NotNull
    public Map<Container, PipeRoot> getAllPipelineRoots()
    {
        PipelineRoot[] pipelines = PipelineManager.getPipelineRoots(PipelineRoot.PRIMARY_ROOT);

        Map<Container, PipeRoot> result = new HashMap<>();
        for (PipelineRoot pipeline : pipelines)
        {
            PipeRoot p = new PipeRootImpl(pipeline);
            if (p.getContainer() != null)
                result.put(p.getContainer(), p);
        }

        return result;
    }


    public PipeRootImpl getPipelineRootSetting(Container container)
    {
        return getPipelineRootSetting(container, PipelineRoot.PRIMARY_ROOT);
    }

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

    public void setPipelineRoot(User user, Container container, String type, boolean searchable, URI... roots) throws SQLException
    {
        if (!canModifyPipelineRoot(user, container))
            throw new UnauthorizedException("You do not have sufficient permissions to set the pipeline root");
        
        PipelineManager.setPipelineRoot(user, container, roots, type, searchable);
    }

    public boolean canModifyPipelineRoot(User user, Container container)
    {
        //per Britt--user must be site admin
        return container != null && !container.isRoot() && user.isSiteAdmin();
    }

    @NotNull
    public List<PipelineProvider> getPipelineProviders()
    {
        // Get a list of unique providers
        return new ArrayList<>(new HashSet<>(_mapPipelineProviders.values()));
    }

    @Nullable
    public PipelineProvider getPipelineProvider(String name)
    {
        if (name == null)
            return null;
        return _mapPipelineProviders.get(name);
    }

    public boolean isEnterprisePipeline()
    {
        return (getPipelineQueue() instanceof EPipelineQueueImpl);
    }

    @NotNull
    public synchronized PipelineQueue getPipelineQueue()
    {
        if (_queue == null)
        {
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
                _queue = new PipelineQueueImpl();
            else
            {
                _log.info("Found JMS queue; running Enterprise Pipeline.");
                _queue = new EPipelineQueueImpl(factory);
            }
        }
        return _queue;
    }

    public void queueJob(PipelineJob job) throws PipelineValidationException
    {
        getPipelineQueue().addJob(job);
    }

    public void setPipelineJobStatus(PipelineJob job, PipelineJob.TaskStatus status) throws PipelineJobException
    {
        job.setActiveTaskStatus(status);

        // Only re-queue the job if status is 'complete' (not 'running' or 'error').
        if (status == PipelineJob.TaskStatus.complete)
        {
            try
            {
                PipelineStatusFileImpl sf = PipelineStatusManager.getStatusFile(job.getLogFile());
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

    public void setPipelineProperty(Container container, String name, String value)
    {
        PipelineManager.setPipelineProperty(container, name, value);
    }

    public String getPipelineProperty(Container container, String name)
    {
        return PipelineManager.getPipelineProperty(container, name);
    }

    public QueryView getPipelineQueryView(ViewContext context, PipelineButtonOption buttonOption)
    {
        return new PipelineQueryView(context, null, null, buttonOption, context.getActionURL());
    }

    public HttpView getSetupView(SetupForm form)
    {
        return new JspView<>("/org/labkey/pipeline/setup.jsp", form, form.getErrors());
    }

    public boolean savePipelineSetup(ViewContext context, SetupForm form, BindException errors) throws Exception
    {
        return PipelineController.savePipelineSetup(context, form, errors);
    }

    private String getLastProtocolKey(PipelineProtocolFactory factory)
    {
        return PREF_LASTPROTOCOL + "-" + factory.getName();
    }

    // TODO: This should be on PipelineProtocolFactory
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
            _log.error("Error", e);
        }
        return "";
    }

    // TODO: This should be on PipelineProtocolFactory
    public void rememberLastProtocolSetting(PipelineProtocolFactory factory, Container container, User user,
                                            String protocolName)
    {
        if (user.isGuest())
            return;
        PropertyManager.PropertyMap map = PropertyManager.getWritableProperties(user, container, PipelineServiceImpl.KEY_PREFERENCES, true);
        map.put(getLastProtocolKey(factory), protocolName);
        map.save();
    }


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
            _log.error("Error", e);
        }
        return "";
    }

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
    public List<String> getLastSequenceDbPathsSetting(PipelineProtocolFactory factory, Container container, User user)
    {
        Map<String, String> props = PropertyManager.getProperties(user, container, PipelineServiceImpl.KEY_PREFERENCES);
        String dbPaths = props.get(PipelineServiceImpl.PREF_LASTSEQUENCEDBPATHS + "-" + factory.getName());

        if (null != dbPaths)
            return parseArray(dbPaths);

        return null;
    }

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


    public PipelineStatusFile getStatusFile(File logFile)
    {
        return PipelineStatusManager.getStatusFile(logFile);
    }

    @Override
    public PipelineStatusFile getStatusFile(int rowId)
    {
        return PipelineStatusManager.getStatusFile(rowId);
    }

    public List<? extends PipelineStatusFile> getQueuedStatusFiles()
    {
        return PipelineStatusManager.getQueuedStatusFiles();
    }

    public List<PipelineStatusFileImpl> getJobsWaitingForFiles(Container c)
    {
        return PipelineStatusManager.getJobsWaitingForFiles(c);
    }

    public List<PipelineStatusFileImpl> getQueuedStatusFiles(Container c)
    {
        return PipelineStatusManager.getQueuedStatusFilesForContainer(c);
    }

    public boolean setStatus(PipelineJob job, String status, String statusInfo, boolean allowInsert)
    {
        return PipelineStatusManager.setStatusFile(job, job.getUser(), status, statusInfo, allowInsert);
    }

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
                    _log.error("Failed to get implementation class from descriptor " + descriptor, e);
                }
                catch (IllegalAccessException | InstantiationException e)
                {
                    _log.error("Failed to resume jobs for descriptor " + descriptor, e);
                }
            }
        }
    }

    public TableInfo getJobsTable(User user, Container container)
    {
        return new PipelineQuerySchema(user, container).getTable(PipelineQuerySchema.JOB_TABLE_NAME);
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

        ViewBackgroundInfo info = new ViewBackgroundInfo(context.getContainer(), context.getUser(), context.getActionURL());

        AbstractFileAnalysisJob job = protocol.createPipelineJob(info, root, filesInputList, fileParameters, variableMap);

        PipelineService.get().queueJob(job);

        return job.getJobGUID();
    }

    @Nullable
    @Override
    public File getProtocolParametersFile(ExpRun expRun)
    {
        return expRun.getInputDatas(ANALYSIS_PARAMETERS_ROLE_NAME, null).get(0).getFile();
    }
}
