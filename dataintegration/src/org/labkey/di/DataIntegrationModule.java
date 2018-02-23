/*
 * Copyright (c) 2013-2017 LabKey Corporation
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
import org.labkey.api.data.ContainerManager.ContainerListener;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.di.DataIntegrationService;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.security.User;
import org.labkey.api.util.ContainerUtil;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.StartupListener;
import org.labkey.api.view.BaseWebPartFactory;
import org.labkey.api.view.JspView;
import org.labkey.api.view.Portal;
import org.labkey.api.view.SimpleWebPartFactory;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;
import org.labkey.di.data.TransformDataType;
import org.labkey.di.data.TransformProperty;
import org.labkey.di.pipeline.ETLPipelineProvider;
import org.labkey.di.pipeline.TransformDescriptor;
import org.labkey.di.pipeline.TransformManager;
import org.labkey.di.steps.RemoteQueryTransformStep;
import org.labkey.di.view.DataIntegrationController;
import org.labkey.di.view.ProcessJobsView;

import javax.servlet.ServletContext;
import java.beans.PropertyChangeEvent;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * User: matthewb
 * Date: 12 Jan 2013
 */
public class DataIntegrationModule extends DefaultModule implements ContainerListener, StartupListener
{
    public static final String NAME = "DataIntegration";

    public String getName()
    {
        return NAME;
    }

    public double getVersion()
    {
        return 18.10;
    }

    protected void init()
    {
        DataIntegrationService.setInstance(TransformManager.get());
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
        return Collections.singleton(DataIntegrationQuerySchema.SCHEMA_NAME);
    }


    @Override
    protected void doStartup(ModuleContext moduleContext)
    {
        PipelineService.get().registerPipelineProvider(new ETLPipelineProvider(this));

        ContainerManager.addContainerListener(this);
        ContextListener.addStartupListener(this);

        DataIntegrationQuerySchema.register(this);
        TransformDataType.register();
        DataIntegrationService.get().registerStepProviders();
        DataIntegrationController.registerAdminConsoleLinks();
    }


    @NotNull
    @Override
    public Set<Class> getUnitTests()
    {
        return new HashSet<>(Arrays.asList(
                TransformManager.TestCase.class,
                VariableMapImpl.TestCase.class,
                TransformDescriptor.TestCase.class
        ));
    }

    @NotNull
    @Override
    public Set<Class> getIntegrationTests()
    {
        return Collections.singleton(RemoteQueryTransformStep.TestCase.class);
    }

    /** web parts **/

    @NotNull
    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return Arrays.asList(
                new SimpleWebPartFactory("Data Transforms", WebPartFactory.LOCATION_BODY, TransFormsWebPart.class, null),
                new BaseWebPartFactory("Data Transform Jobs")
                {
                    @Override
                    public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart)
                    {
                        WebPartView view = new ProcessJobsView(portalCtx.getUser(), portalCtx.getContainer());
                        view.setTitle("Processed Data Transforms");
                        view.setFrame(WebPartView.FrameType.PORTAL);
                        return view;
                    }
                }
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
    // ContainerListener
    //

    @Override
    public void containerCreated(Container c, User user)
    {
    }


    @Override
    public void containerDeleted(Container c, User user)
    {
        DbSchema di = DataIntegrationQuerySchema.getSchema();
        try (DbScope.Transaction transaction = di.getScope().ensureTransaction())
        {
            TransformManager.get().unscheduleAll(c);
            ContainerUtil.purgeTable(di.getTable("TransformRun"), c, null);
            ContainerUtil.purgeTable(di.getTable("TransformConfiguration"), c, null);
            transaction.commit();
        }
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

    @Override
    public void propertyChange(PropertyChangeEvent evt)
    {
    }
}
