/*
 * Copyright (c) 2005-2008 LabKey Corporation
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
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.SpringModule;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.security.User;
import org.labkey.api.util.AppProps;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.WriteableAppProps;
import org.labkey.api.util.emailTemplate.EmailTemplateService;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;
import org.labkey.pipeline.analysis.AnalysisController;
import org.labkey.pipeline.analysis.FileAnalysisPipelineProvider;
import org.labkey.pipeline.api.*;
import org.labkey.pipeline.api.properties.ApplicationPropertiesSiteSettings;
import org.labkey.pipeline.mule.EPipelineContextListener;
import org.labkey.pipeline.status.StatusController;

import java.beans.PropertyChangeEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 */
public class PipelineModule extends SpringModule implements ContainerManager.ContainerListener
{
    private static Logger _log = Logger.getLogger(PipelineModule.class);

    public PipelineModule()
    {
        super(PipelineService.MODULE_NAME, 8.21, "/org/labkey/pipeline", true, new WebPartFactory(PipelineWebPart.getPartName()){
            public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws IllegalAccessException, InvocationTargetException
            {
                return new PipelineWebPart(portalCtx);
            }
        });

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
    }

    @Override
    protected ContextType getContextType()
    {
        return ContextType.config;
    }

    @Override
    public void startup(ModuleContext moduleContext)
    {
        super.startup(moduleContext);

        PipelineService service = PipelineService.get();
        service.registerPipelineProvider(new FileAnalysisPipelineProvider());

        ContainerManager.addContainerListener(this);

        if (PipelineService.get().isEnterprisePipeline())
        {
            EPipelineContextListener listener = new EPipelineContextListener();
            ContextListener.addStartupListener(listener);
            ContextListener.addShutdownListener(listener);
        }
        else
        {
            Thread restarterThread = new Thread(new JobRestarter());
            restarterThread.start();
        }

        PipelineEmailPreferences.get().startNotificationTasks();
    }

    public void afterSchemaUpdate(ModuleContext moduleContext, ViewContext viewContext)
    {
        super.afterSchemaUpdate(moduleContext, viewContext);

        if (StringUtils.trimToNull(AppProps.getInstance().getPipelineToolsDirectory()) == null)
        {
            try
            {
                WriteableAppProps props = AppProps.getWriteableInstance();
                File webappRoot = new File(viewContext.getRequest().getSession(true).getServletContext().getRealPath("/"));     // TODO: change to ModuleLoader.getServletContext()
                props.setPipelineToolsDir(new File(webappRoot.getParentFile(), "bin").toString());
                props.save();
            }
            catch (SQLException e)
            {
                _log.error("Failed to set pipeline tools directory.", e);
            }
        }
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
            PipelineQueueImpl.TestCase.class));
    }

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
                PipelineStatusFileImpl[] incompleteStatusFiles = PipelineStatusManager.getIncompleteStatusFiles();
                for (PipelineStatusFileImpl incompleteStatusFile : incompleteStatusFiles)
                {
                    File logFile = new File(incompleteStatusFile.getFilePath());
                    File serializedJobFile = PipelineJob.getSerializedFile(logFile);
                    if (serializedJobFile != null && serializedJobFile.exists())
                    {
                        FileInputStream fIn = null;
                        ObjectInputStream oIn = null;
                        PipelineJob job = null;
                        try
                        {
                            fIn = new FileInputStream(serializedJobFile);
                            oIn = new ObjectInputStream(fIn);
                            job = (PipelineJob)oIn.readObject();
                        }
                        catch (IOException e)
                        {
                            _log.error("Unable to restart job", e);
                            moveJobToError(incompleteStatusFile);
                        }
                        catch (ClassNotFoundException e)
                        {
                            _log.error("Unable to restart job", e);
                            moveJobToError(incompleteStatusFile);
                        }
                        finally
                        {
                            if (fIn != null) { try { fIn.close(); } catch (IOException e) {} }
                            if (oIn != null) { try { oIn.close(); } catch (IOException e) {} }
                        }
                        if (job != null)
                        {
                            try
                            {
                                PipelineService.get().queueJob(job, PipelineJob.RESTARTED_STATUS);
                            }
                            catch (IOException e)
                            {
                                _log.error("Unable to restart job", e);
                                moveJobToError(incompleteStatusFile);
                            }
                        }
                    }
                    else
                    {
                        moveJobToError(incompleteStatusFile);
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
                PipelineStatusManager.updateStatusFile(incompleteFile);
            }
            catch (SQLException e)
            {
                _log.error("Unable to move job into error - " + incompleteFile.getFilePath(), e);
            }
        }
    }
}
