/*
 * Copyright (c) 2013 LabKey Corporation
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

package org.labkey.di;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.di.DataIntegrationService;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.ContainerUtil;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.ShutdownListener;
import org.labkey.api.util.StartupListener;
import org.labkey.api.view.JspView;
import org.labkey.api.view.SimpleWebPartFactory;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;
import org.labkey.di.data.TransformDataType;
import org.labkey.di.data.TransformProperty;
import org.labkey.di.pipeline.BaseQueryTransformDescriptor;
import org.labkey.di.pipeline.TransformManager;
import org.labkey.di.pipeline.ETLPipelineProvider;
import org.labkey.di.view.DataIntegrationController;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import java.beans.PropertyChangeEvent;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * User: matthewb
 * Date: 12 Jan 2013
 */
public class DataIntegrationModule extends DefaultModule implements ContainerManager.ContainerListener, StartupListener, ShutdownListener
{
    public static final String NAME = "DataIntegration";


    public DataIntegrationModule()
    {
        ServiceRegistry.get().registerService(DataIntegrationService.class, TransformManager.get());
    }


    public String getName()
    {
        return NAME;
    }

    public double getVersion()
    {
        return 13.13;
    }

    protected void init()
    {
        addController("dataintegration", DataIntegrationController.class);
        TransformProperty.register();
    }

    public boolean hasScripts()
    {
        return true;
    }



    @NotNull
    @Override
    public Set<String> getSchemaNames()
    {
        return Collections.singleton(DataIntegrationDbSchema.SCHEMA_NAME);
    }

    @Override
    public void afterUpdate(ModuleContext moduleContext)
    {
    }


    @Override
    protected void doStartup(ModuleContext moduleContext)
    {
        PipelineService.get().registerPipelineProvider(new ETLPipelineProvider(this));

        ContainerManager.addContainerListener(this);
        ContextListener.addShutdownListener(this);

        DataIntegrationDbSchema.register(this);
        TransformDataType.register();
    }


    @NotNull
    @Override
    public Set<Class> getUnitTests()
    {
        return new HashSet<Class>(Arrays.asList(
                TransformManager.TestCase.class,
                VariableMapImpl.TestCase.class,
                BaseQueryTransformDescriptor.TestCase.class
        ));
    }


    /** web parts **/

    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return Arrays.asList(
                (WebPartFactory)new SimpleWebPartFactory("Data Transforms", WebPartFactory.LOCATION_BODY, TransFormsWebPart.class, null)
        );
    }

    @SuppressWarnings("UnusedDeclaration")
    public static class TransFormsWebPart extends JspView<Object>
    {
        public TransFormsWebPart(ViewContext portalCtx) throws Exception
        {
            this(portalCtx.getContainer());
        }

        public TransFormsWebPart(Container c)
        {
            super(DataIntegrationController.class, "transformConfiguration.jsp", null);
            setTitle("Data Transforms");
            setFrame(WebPartView.FrameType.PORTAL);
            setModelBean(this);
        }
    }

    //
    // StartupListener
    //


    @Override
    public void moduleStartupComplete(ServletContext servletContext)
    {
        TransformManager.get().startAllConfigurations();
    }


    //
    // ShutdownListener
    //

    public void shutdownPre(ServletContextEvent servletContextEvent)
    {
        TransformManager.get().shutdownPre();
    }


    public void shutdownStarted(ServletContextEvent servletContextEvent)
    {
        TransformManager.get().shutdownStarted();
    }


    //
    // ContainerListener
    //

    @Override
    public void containerCreated(Container c, User user)
    {
    }


    @Override
    public void containerDeleted(Container c, User user)
    {
        DbSchema di = null;
        try
        {
            TransformManager.get().unscheduleAll(c);

            di = DbSchema.get("dataintegration");
            di.getScope().ensureTransaction();
            ContainerUtil.purgeTable(di.getTable("TransformRun"), c, null);
            ContainerUtil.purgeTable(di.getTable("TransformConfiguration"), c, null);
            di.getScope().commitTransaction();
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
        finally
        {
            if (null != di)
                di.getScope().closeConnection();
        }
    }

    @Override
    public void containerMoved(Container c, Container oldParent, User user)
    {
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt)
    {
    }
}
