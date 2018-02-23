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
package org.labkey.pipeline;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.admin.sitevalidation.SiteValidationService;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.files.FileContentService;
import org.labkey.api.files.TableUpdaterFileListener;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.SpringModule;
import org.labkey.api.pipeline.CancelledException;
import org.labkey.api.pipeline.NoSuchJobException;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineQueue;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.RemoteExecutionEngine;
import org.labkey.api.pipeline.file.PathMapperImpl;
import org.labkey.api.pipeline.trigger.PipelineTriggerRegistry;
import org.labkey.api.pipeline.trigger.PipelineTriggerType;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.emailTemplate.EmailTemplateService;
import org.labkey.api.view.BaseWebPartFactory;
import org.labkey.api.view.DefaultWebPartFactory;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartConfigurationException;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;
import org.labkey.api.webdav.WebdavService;
import org.labkey.pipeline.analysis.AnalysisController;
import org.labkey.pipeline.analysis.CommandTaskImpl;
import org.labkey.pipeline.analysis.FileAnalysisPipelineProvider;
import org.labkey.pipeline.analysis.ProtocolManagementAuditProvider;
import org.labkey.pipeline.analysis.ProtocolManagementWebPart;
import org.labkey.pipeline.api.ExecTaskFactory;
import org.labkey.pipeline.api.PipelineEmailPreferences;
import org.labkey.pipeline.api.PipelineJobServiceImpl;
import org.labkey.pipeline.api.PipelineManager;
import org.labkey.pipeline.api.PipelineQuerySchema;
import org.labkey.pipeline.api.PipelineQueueImpl;
import org.labkey.pipeline.api.PipelineSchema;
import org.labkey.pipeline.api.PipelineServiceImpl;
import org.labkey.pipeline.api.PipelineStatusFileImpl;
import org.labkey.pipeline.api.PipelineStatusManager;
import org.labkey.pipeline.api.ScriptTaskFactory;
import org.labkey.pipeline.api.properties.ApplicationPropertiesSiteSettings;
import org.labkey.pipeline.importer.FolderImportProvider;
import org.labkey.pipeline.mule.EPipelineContextListener;
import org.labkey.pipeline.mule.EPipelineQueueImpl;
import org.labkey.pipeline.mule.RemoteServerStartup;
import org.labkey.pipeline.mule.filters.TaskJmsSelectorFilter;
import org.labkey.pipeline.status.StatusController;
import org.labkey.pipeline.trigger.PipelineTriggerRegistryImpl;
import org.labkey.pipeline.validators.PipelineSetupValidator;
import org.labkey.pipeline.xml.ExecTaskType;
import org.labkey.pipeline.xml.ScriptTaskType;

import java.beans.PropertyChangeEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 */
public class PipelineModule extends SpringModule implements ContainerManager.ContainerListener
{
    private static Logger _log = Logger.getLogger(PipelineModule.class);

    public String getName()
    {
        return PipelineService.MODULE_NAME;
    }

    public double getVersion()
    {
        return 18.10;
    }

    protected void init()
    {
        PipelineServiceImpl ps = new PipelineServiceImpl();
        PipelineService.setInstance(ps);

        // Set up default PipelineJobServiceImpl, which may be overridden by Spring config.
        PipelineJobServiceImpl pjs = PipelineJobServiceImpl.initDefaults(PipelineJobService.LocationType.WebServer);
        pjs.setAppProperties(new ApplicationPropertiesSiteSettings());

        pjs.registerTaskFactoryFactory(ScriptTaskType.type, new ScriptTaskFactory.FactoryFactory());
        pjs.registerTaskFactoryFactory(ExecTaskType.type, new ExecTaskFactory.FactoryFactory());

        addController("pipeline", PipelineController.class);
        addController("pipeline-status", StatusController.class);
        addController("pipeline-analysis", AnalysisController.class);

        EmailTemplateService.get().registerTemplate(PipelineManager.PipelineJobSuccess.class);
        EmailTemplateService.get().registerTemplate(PipelineManager.PipelineJobFailed.class);
        EmailTemplateService.get().registerTemplate(PipelineManager.PipelineDigestJobSuccess.class);
        EmailTemplateService.get().registerTemplate(PipelineManager.PipelineDigestJobFailed.class);

        PipelineQuerySchema.register(this);

        PipelineTriggerRegistry.setInstance(new PipelineTriggerRegistryImpl());
    }

    @NotNull
    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return new ArrayList<>(Arrays.asList(
            new BaseWebPartFactory(PipelineWebPart.getPartName())
            {
                public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart)
                {
                    return new PipelineWebPart(portalCtx);
                }
            },

            new DefaultWebPartFactory("Pipeline Files", PipelineController.BrowseWebPart.class),
            new BaseWebPartFactory(ProtocolManagementWebPart.getName())
            {
                @Override
                public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart) throws WebPartConfigurationException
                {
                    return new ProtocolManagementWebPart(portalCtx);
                }
            }

        ));
    }

    public boolean hasScripts()
    {
        return true;
    }

    @Override
    protected void startupAfterSpringConfig(ModuleContext moduleContext)
    {
        PipelineService service = PipelineService.get();
        service.registerPipelineProvider(new FileAnalysisPipelineProvider(this));
        service.registerPipelineProvider(new FolderImportProvider(this));

        ContainerManager.addContainerListener(this);

        if (PipelineService.get().isEnterprisePipeline())
        {
            EPipelineContextListener listener = new EPipelineContextListener();
            ContextListener.addStartupListener(listener);
            ContextListener.addShutdownListener(listener);
        }

        PipelineController.registerAdminConsoleLinks();
        StatusController.registerAdminConsoleLinks();
        WebdavService.get().addProvider(new PipelineWebdavProvider());

        if (null != FileContentService.get())
            FileContentService.get().addFileListener(new TableUpdaterFileListener(PipelineSchema.getInstance().getTableInfoStatusFiles(), "FilePath", TableUpdaterFileListener.Type.filePathForwardSlash, "RowId"));
        SiteValidationService svc = ServiceRegistry.get().getService(SiteValidationService.class);
        if (null != svc)
        {
            svc.registerProvider(getName(), new PipelineSetupValidator());
        }

        AuditLogService.get().registerAuditType(new ProtocolManagementAuditProvider());
    }

    @Override
    public void startBackgroundThreads()
    {
        PipelineEmailPreferences.get().startNotificationTasks();

        // Issue 19407. Need to delay restarting jobs until after all modules have started up, or we may try to restart
        // a job whose pipeline hasn't yet been registered. Invoking from startBackgroundThreads() ensures that startup
        // is complete and the base URL has been set.

        // Restart any jobs that were in process or in the queue when the server shut down
        Thread restarterThread = new Thread(new JobRestarter());
        restarterThread.start();
    }

    @NotNull
    @Override
    public Collection<String> getSummary(Container c)
    {
        /* @todo: return running jobs. */
        return super.getSummary(c);
    }

    public void containerCreated(Container c, User user)
    {
    }

    public void containerDeleted(Container c, User user)
    {
        PipelineManager.purge(c);
    }

    @Override
    public void containerMoved(Container c, Container oldParent, User user)
    {        
    }

    @NotNull
    @Override
    public Collection<String> canMove(Container c, Container newParent, User user)
    {
        return Collections.emptyList();
    }

    public void propertyChange(PropertyChangeEvent evt)
    {
    }


    @Override
    @NotNull
    public Set<Class> getIntegrationTests()
    {
        return new HashSet<>(Arrays.asList(
            PipelineQueueImpl.TestCase.class,
            PipelineController.TestCase.class,
            StatusController.TestCase.class
        ));
    }

    @Override
    @NotNull
    public Set<Class> getUnitTests()
    {
        return new HashSet<>(Arrays.asList(
            PathMapperImpl.TestCase.class,
            PipelineCommandTestCase.class,
            PipelineJobServiceImpl.TestCase.class,
            CommandTaskImpl.TestCase.class,
            PipelineServiceImpl.TestCase.class
        ));
    }

    @Override
    @NotNull
    public Set<String> getSchemaNames()
    {
        return PageFlowUtil.set(PipelineSchema.getInstance().getSchemaName());
    }

    private static class JobRestarter implements Runnable
    {
        public void run()
        {
            assert ModuleLoader.getInstance().isStartupComplete();

            // If the queue is in local server memory, then we need to restart all
            // jobs on server restart.
            PipelineQueue queue = PipelineService.get().getPipelineQueue();
            if (queue.isTransient())
            {
                requeueAllPendingJobs();
            }
            else if (!queue.isLocal() && queue instanceof EPipelineQueueImpl)
            {
                // Restart jobs that have been dropped from the queue and are supposed to run on the web server
                // Ignore RemoteExecutionEngines
                Set<String> locations = new CaseInsensitiveHashSet(TaskJmsSelectorFilter.getAllLocalLocations());
                for (RemoteExecutionEngine<?> engine : PipelineJobService.get().getRemoteExecutionEngines())
                {
                    locations.remove(engine.getConfig().getLocation());
                }
                new RemoteServerStartup().getRequeueRequest(((EPipelineQueueImpl) queue).getJMSFactory(), locations, null).performRequest();
            }

            // TODO: is this the correct spot to start all of the trigger configs?
            for (PipelineTriggerType triggerType : PipelineTriggerRegistry.get().getTypes())
            {
                triggerType.startAll();
            }
        }

        private void requeueAllPendingJobs()
        {
            for (PipelineStatusFileImpl sf : PipelineStatusManager.getQueuedStatusFiles())
            {
                try
                {
                    PipelineJobServiceImpl.get().getJobStore().retry(sf);
                }
                catch (CancelledException ignored) { /* Job has already been set to CANCELLED */ }
                catch (IOException | NoSuchJobException | RuntimeException e)
                {
                    _log.error("Unable to restart job", e);
                    moveJobToError(sf);
                }
            }
        }

        private void moveJobToError(PipelineStatusFileImpl incompleteFile)
        {
            try
            {
                incompleteFile.setStatus(PipelineJob.TaskStatus.error.toString());
                incompleteFile.setInfo("type=Job restart");
                incompleteFile.beforeUpdate(null, incompleteFile);
                PipelineStatusManager.updateStatusFile(incompleteFile);
            }
            catch (RuntimeSQLException e)
            {
                _log.error("Unable to move job into error - " + incompleteFile.getFilePath(), e);
            }
        }
    }
}
