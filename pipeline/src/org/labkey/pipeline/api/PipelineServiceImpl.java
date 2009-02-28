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

package org.labkey.pipeline.api;

import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.pipeline.*;
import org.labkey.api.pipeline.browse.BrowseForm;
import org.labkey.api.pipeline.browse.BrowseView;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.pipeline.mule.EPipelineQueueImpl;
import org.labkey.pipeline.mule.ResumableDescriptor;
import org.labkey.pipeline.browse.BrowseViewImpl;
import org.mule.umo.model.UMOModel;
import org.mule.umo.UMODescriptor;
import org.mule.umo.UMOException;
import org.mule.MuleManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.jms.ConnectionFactory;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.*;

public class PipelineServiceImpl extends PipelineService
{
    public static String PARAM_Provider = "provider";
    public static String PARAM_Action = "action";
    public static String PROP_Mirror = "mirror-containers";
    public static String PREF_LASTPATH = "lastpath";
    public static String PREF_LASTPROTOCOL = "lastprotocol";
    public static String PREF_LASTSEQUENCEDB = "lastsequencedb";
    public static String PREF_LASTSEQUENCEDBPATHS = "lastsequencedbpaths";
    public static String KEY_PREFERENCES = "pipelinePreferences";

    private static Logger _log = Logger.getLogger(PipelineService.class);

    private Map<String, PipelineProvider> _mapPipelineProviders = new TreeMap<String, PipelineProvider>();
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
        provider.register();
    }

    public GlobusKeyPair createGlobusKeyPair(byte[] keyBytes, String keyPassword, byte[] certBytes)
    {
        return new GlobusKeyPairImpl(keyBytes, keyPassword, certBytes);
    }


    public PipeRoot findPipelineRoot(Container container)
    {
        PipelineRoot pipelineRoot = PipelineManager.findPipelineRoot(container);
        if (null != pipelineRoot)
        {
            try
            {
                return new PipeRootImpl(pipelineRoot);
            }
            catch (URISyntaxException x)
            {
                _log.error("unexpected error", x);
            }
        }
        return null;
    }


    @NotNull
    public PipeRoot[] getAllPipelineRoots()
    {
        PipelineRoot[] pipelines;
        try
        {
            pipelines = PipelineManager.getPipelineRoots(PipelineRoot.PRIMARY_ROOT);
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
        ArrayList<PipeRoot> pipes = new ArrayList<PipeRoot>(pipelines.length);
        for (PipelineRoot pipeline : pipelines)
        {
            try
            {
                PipeRoot p = new PipeRootImpl(pipeline);
                if (p.getContainer() != null)
                    pipes.add(new PipeRootImpl(pipeline));
            }
            catch (URISyntaxException x)
            {
                _log.error("unexpected error", x);
            }
        }
        return pipes.toArray(new PipeRoot[pipes.size()]);
    }


    public PipeRoot getPipelineRootSetting(Container container)
    {
        try
        {
            return new PipeRootImpl(PipelineManager.getPipelineRootObject(container, PipelineRoot.PRIMARY_ROOT));
        }
        catch (URISyntaxException x)
        {
            _log.error("unexpected error", x);
        }

        return null;
    }



    public URI getPipelineRootSetting(Container container, final String type) throws SQLException
    {
        String root = PipelineManager.getPipelineRoot(container, type);
        if (root == null)
            return null;

        try
        {
            return new URI(root);
        }
        catch (URISyntaxException use)
        {
            _log.error("Invalid pipeline root '" + root + "'.", use);
            return null;
        }
    }

    @NotNull
    public PipeRoot[] getOverlappingRoots(Container c) throws SQLException
    {
        PipelineRoot[] roots = PipelineManager.getOverlappingRoots(c, PipelineRoot.PRIMARY_ROOT);
        List<PipeRoot> rootsList = new ArrayList<PipeRoot>();
        for (PipelineRoot root : roots)
        {
            Container container = ContainerManager.getForId(root.getContainerId());
            if (container == null)
                continue;

            try
            {
                rootsList.add(new PipeRootImpl(root));
            }
            catch (URISyntaxException e)
            {
                _log.error("Invalid pipeline root '" + root + "'.", e);
            }
        }
        return rootsList.toArray(new PipeRoot[rootsList.size()]);
    }

    public void setPipelineRoot(User user, Container container, URI root, String type,
                                GlobusKeyPair globusKeyPair, boolean perlPipeline) throws SQLException
    {
        PipelineManager.setPipelineRoot(user, container, root == null ? "" : root.toString(), type,
                globusKeyPair, perlPipeline);
    }

    public boolean canModifyPipelineRoot(User user, Container container)
    {
        return container != null && !container.isRoot() && user.isAdministrator();
    }

    public boolean usePerlPipeline(Container container) throws SQLException
    {
        if (!AppProps.getInstance().isPerlPipelineEnabled())
            return false;

        PipeRoot pr = findPipelineRoot(container);
        return pr != null && pr.isPerlPipeline();
    }
                                   
    @NotNull
    public File ensureSystemDirectory(URI root)
    {
        return PipeRootImpl.ensureSystemDirectory(root);
    }

    @NotNull
    public List<PipelineProvider> getPipelineProviders()
    {
        // Get a list of unique providers
        return new ArrayList<PipelineProvider>(new HashSet<PipelineProvider>(_mapPipelineProviders.values()));
    }

    @Nullable
    public PipelineProvider getPipelineProvider(String name)
    {
        if (name == null)
            return null;
        return _mapPipelineProviders.get(name);
    }

    public String getButtonHtml(String text, ActionURL href)
    {
        return PageFlowUtil.generateButton(text, href.toString());
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

    public void queueJob(PipelineJob job) throws IOException
    {
        getPipelineQueue().addJob(job);
    }

    public void setPipelineProperty(Container container, String name, String value) throws SQLException
    {
        PipelineManager.setPipelineProperty(container, name, value);
    }

    public String getPipelineProperty(Container container, String name) throws SQLException
    {
        return PipelineManager.getPipelineProperty(container, name);
    }

    public BrowseView getBrowseView(BrowseForm form)
    {
        return new BrowseViewImpl(form);
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
            Map<String, String> props = PropertyManager.getProperties(user.getUserId(), container.getId(), PipelineServiceImpl.KEY_PREFERENCES, false);
            if (props != null)
                return props.get(getLastProtocolKey(factory));
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
        PropertyManager.PropertyMap map = PropertyManager.getWritableProperties(user.getUserId(), container.getId(), PipelineServiceImpl.KEY_PREFERENCES, true);
        map.put(getLastProtocolKey(factory), protocolName);
        PropertyManager.saveProperties(map);
    }


    public String getLastSequenceDbSetting(PipelineProtocolFactory factory, Container container, User user)
    {
        try
        {
            Map<String, String> props = PropertyManager.getProperties(user.getUserId(), container.getId(), PipelineServiceImpl.KEY_PREFERENCES, false);
            if (props != null)
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
        PropertyManager.PropertyMap map = PropertyManager.getWritableProperties(user.getUserId(), container.getId(),
                PipelineServiceImpl.KEY_PREFERENCES, true);
        map.put(PipelineServiceImpl.PREF_LASTSEQUENCEDB + "-" + factory.getName(), fullPath);
        PropertyManager.saveProperties(map);
    }

    public List<String> getLastSequenceDbPathsSetting(PipelineProtocolFactory factory, Container container, User user)
    {
        try
        {
            Map<String, String> props = PropertyManager.getProperties(user.getUserId(), container.getId(), PipelineServiceImpl.KEY_PREFERENCES, false);
            if (props != null)
            {
                String dbPaths = props.get(PipelineServiceImpl.PREF_LASTSEQUENCEDBPATHS + "-" + factory.getName());
                return parseArray(dbPaths);
            }
        }
        catch (Exception e)
        {
            _log.error("Error", e);
        }
        return null;
    }

    public void rememberLastSequenceDbPathsSetting(PipelineProtocolFactory factory, Container container, User user,
                                                   List<String> sequenceDbPathsList)
    {
        if (user.isGuest())
            return;
        String sequenceDbPathsString = list2String(sequenceDbPathsList);
        PropertyManager.PropertyMap map = PropertyManager.getWritableProperties(user.getUserId(), container.getId(),
                PipelineServiceImpl.KEY_PREFERENCES, true);
        if(sequenceDbPathsString == null || sequenceDbPathsString.length() == 0 || sequenceDbPathsString.length() >= 2000)
        {
            map.remove(PipelineServiceImpl.PREF_LASTSEQUENCEDBPATHS + "-" + factory.getName());
        }
        else
        {
            map.put(PipelineServiceImpl.PREF_LASTSEQUENCEDBPATHS + "-" + factory.getName(), sequenceDbPathsString);
        }
        PropertyManager.saveProperties(map);
    }



    public PipelineStatusFile getStatusFile(String path) throws SQLException
    {
        return PipelineStatusManager.getStatusFile(path);
    }

    public PipelineStatusFile[] getQueuedStatusFiles() throws SQLException
    {
        return PipelineStatusManager.getQueuedStatusFiles();
    }

    public PipelineStatusFile[] getQueuedStatusFiles(Container c) throws SQLException
    {
        return PipelineStatusManager.getQueuedStatusFilesForContainer(c);
    }

    public void setStatusFile(ViewBackgroundInfo info, PipelineStatusFile sf) throws Exception
    {
        PipelineStatusManager.setStatusFile(info, (PipelineStatusFileImpl) sf, false);
    }

    public void setStatusFile(PipelineJob job, String status, String statusInfo) throws Exception
    {
        PipelineStatusManager.setStatusFile(job, new PipelineStatusFileImpl(job, status, statusInfo));
    }

    public void ensureError(PipelineJob job) throws Exception
    {
        PipelineStatusManager.ensureError(job);
    }

    private List<String> parseArray(String dbPaths)
    {
        if(dbPaths == null) return null;
        if(dbPaths.length() == 0) return new ArrayList<String>();
        String[] tokens = dbPaths.split("\\|");
        return new ArrayList<String>(Arrays.asList(tokens));
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
                catch (IllegalAccessException e)
                {
                    _log.error("Failed to resume jobs for descriptor " + descriptor, e);
                }
                catch (InstantiationException e)
                {
                    _log.error("Failed to resume jobs for descriptor " + descriptor, e);
                }
            }
        }
    }

}
