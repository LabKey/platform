/*
 * Copyright (c) 2005-2010 LabKey Corporation
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

import junit.framework.TestCase;
import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.SpringModule;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.security.User;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.emailTemplate.EmailTemplateService;
import org.labkey.api.view.*;
import org.labkey.api.webdav.WebdavService;
import org.labkey.pipeline.analysis.AnalysisController;
import org.labkey.pipeline.analysis.FileAnalysisPipelineProvider;
import org.labkey.pipeline.api.*;
import org.labkey.pipeline.api.properties.ApplicationPropertiesSiteSettings;
import org.labkey.pipeline.mule.EPipelineContextListener;
import org.labkey.pipeline.status.StatusController;
import org.labkey.pipeline.xstream.PathMapperImpl;
import org.mule.MuleManager;

import java.beans.PropertyChangeEvent;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.util.*;

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
        return 10.10;
    }

    protected void init()
    {
        PipelineServiceImpl ps = new PipelineServiceImpl();
        PipelineService.setInstance(ps);

        // Set up default PipelineJobServiceImpl, which may be overriden by Spring config.
        PipelineJobServiceImpl pjs = PipelineJobServiceImpl.initDefaults();
        pjs.setAppProperties(new ApplicationPropertiesSiteSettings());
        pjs.setStatusWriter(ps);

        addController("pipeline", PipelineController.class);
        addController("pipeline-status", StatusController.class);
        addController("pipeline-analysis", AnalysisController.class);

        EmailTemplateService.get().registerTemplate(PipelineManager.PipelineJobSuccess.class);
        EmailTemplateService.get().registerTemplate(PipelineManager.PipelineJobFailed.class);
        EmailTemplateService.get().registerTemplate(PipelineManager.PipelineDigestJobSuccess.class);
        EmailTemplateService.get().registerTemplate(PipelineManager.PipelineDigestJobFailed.class);

        PipelineQuerySchema.register();
    }

    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return new ArrayList<WebPartFactory>(Arrays.asList(
            new BaseWebPartFactory(PipelineWebPart.getPartName())
            {
                public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws IllegalAccessException, InvocationTargetException
                {
                    return new PipelineWebPart(portalCtx);
                }
            },

            new DefaultWebPartFactory("Pipeline Files", PipelineController.BrowseWebPart.class)
        ));
    }

    public boolean hasScripts()
    {
        return true;
    }

    @Override
    protected ContextType getContextType()
    {
        return ContextType.config;
    }

    public void startup(ModuleContext moduleContext)
    {
        initWebApplicationContext();

        PipelineService service = PipelineService.get();
        service.registerPipelineProvider(new FileAnalysisPipelineProvider(this));

        ContainerManager.addContainerListener(this);

        if (PipelineService.get().isEnterprisePipeline())
        {
            EPipelineContextListener listener = new EPipelineContextListener();
            ContextListener.addStartupListener(listener);
            ContextListener.addShutdownListener(listener);
        }

        // If the queue is in local server memory, then we need to restart all
        // jobs on server restart.  Otherwise, an external JMS queue will retain
        // all jobs between server restarts.
        if (PipelineService.get().getPipelineQueue().isTransient())
        {
            Thread restarterThread = new Thread(new JobRestarter());
            restarterThread.start();
        }

        PipelineEmailPreferences.get().startNotificationTasks();
        PipelineController.registerAdminConsoleLinks();
        StatusController.registerAdminConsoleLinks();
        WebdavService.get().addProvider(new PipelineWebdavProvider());
    }

    @Override
    public Collection<String> getSummary(Container c)
    {
        /* @todo: return running jobs. */
        return super.getSummary(c);
    }

    public void containerCreated(Container c)
    {
    }

    public void containerDeleted(Container c, User user)
    {
        PipelineManager.purge(c);
    }

    public void propertyChange(PropertyChangeEvent evt)
    {
    }


    @Override
    public Set<Class<? extends TestCase>> getJUnitTests()
    {
        return new HashSet<Class<? extends TestCase>>(Arrays.asList(
            PipelineQueueImpl.TestCase.class, PathMapperImpl.TestCase.class));
    }

    @Override
    public Set<String> getSchemaNames()
    {
        return PageFlowUtil.set(PipelineSchema.getInstance().getSchemaName());
    }

    @Override
    public Set<DbSchema> getSchemasToTest()
    {
        return PageFlowUtil.set(PipelineSchema.getInstance().getSchema());
    }


    private static class JobRestarter implements Runnable
    {
        public void run()
        {
            while (!ModuleLoader.getInstance().isStartupComplete())
            {
                try
                {
                    Thread.sleep(5000);
                }
                catch (InterruptedException e) {}
            }
            try
            {
                PipelineStatusFileImpl[] incompleteStatusFiles = PipelineStatusManager.getQueuedStatusFiles();
                for (PipelineStatusFileImpl sf : incompleteStatusFiles)
                {
                    try
                    {
                        PipelineJobServiceImpl.get().getJobStore().retry(sf);
                    }
                    catch (IOException e)
                    {
                        _log.error("Unable to restart job", e);
                        moveJobToError(sf);
                    }
                }
            }
            catch (SQLException e)
            {
                _log.error("SQL problem trying to move jobs to error", e);
            }
        }

        private void moveJobToError(PipelineStatusFileImpl incompleteFile)
        {
            try
            {
                incompleteFile.setStatus(PipelineJob.ERROR_STATUS);
                incompleteFile.setInfo("type=Job restart");
                incompleteFile.beforeUpdate(null, incompleteFile);
                PipelineStatusManager.updateStatusFile(incompleteFile);
            }
            catch (SQLException e)
            {
                _log.error("Unable to move job into error - " + incompleteFile.getFilePath(), e);
            }
        }
    }

    public List<String> getAttributions()
    {
        if (!MuleManager.isInstanciated())
        {
            return Collections.emptyList();
        }
        return Arrays.asList("<a href=\"http://www.mulesource.com\" target=\"top\"><img src=\"http://www.mulesource.com/images/mulesource_license_logo.gif\" alt=\"MuleSource\" width=\"252\" height=\"52\"></a>");
    }

    @Override
    public UpgradeCode getUpgradeCode()
    {
        return new PipelineUpgradeCode();
    }
}
