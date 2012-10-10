/*
 * Copyright (c) 2005-2012 LabKey Corporation
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
package org.labkey.experiment;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.Table;
import org.labkey.api.defaults.DefaultValueService;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.ExperimentRunType;
import org.labkey.api.exp.ExperimentRunTypeSource;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.api.DefaultExperimentDataHandler;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.DomainAuditViewFactory;
import org.labkey.api.exp.property.ExperimentProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.property.SystemProperty;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.exp.xar.LsidUtils;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.SpringModule;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.AlwaysAvailableWebPartFactory;
import org.labkey.api.view.BaseWebPartFactory;
import org.labkey.api.view.JspView;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;
import org.labkey.experiment.api.ExpMaterialImpl;
import org.labkey.experiment.api.ExperimentServiceImpl;
import org.labkey.experiment.api.LogDataType;
import org.labkey.experiment.api.SampleSetDomainType;
import org.labkey.experiment.api.property.LookupValidator;
import org.labkey.experiment.api.property.PropertyServiceImpl;
import org.labkey.experiment.api.property.RangeValidator;
import org.labkey.experiment.api.property.RegExValidator;
import org.labkey.experiment.controllers.exp.ExperimentController;
import org.labkey.experiment.controllers.property.PropertyController;
import org.labkey.experiment.defaults.DefaultValueServiceImpl;
import org.labkey.experiment.pipeline.ExperimentPipelineProvider;
import org.labkey.experiment.types.TypesController;

import java.beans.PropertyChangeEvent;
import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * User: phussey (Peter Hussey)
 * Date: Jul 18, 2005
 * Time: 3:25:52 PM
 */
public class ExperimentModule extends SpringModule implements SearchService.DocumentProvider
{
    private static final String SAMPLE_SET_WEB_PART_NAME = "Sample Sets";
    private static final String PROTOCOL_WEB_PART_NAME = "Protocols";
    public static final String EXPERIMENT_RUN_WEB_PART_NAME = "Experiment Runs";

    public String getName()
    {
        return ExperimentService.MODULE_NAME;
    }

    public double getVersion()
    {
        return 12.24;
    }

    protected void init()
    {
        addController("experiment", ExperimentController.class);
        addController("experiment-types", TypesController.class);
        addController("property", PropertyController.class);
        ExperimentService.setInstance(new ExperimentServiceImpl());
        PropertyService.setInstance(new PropertyServiceImpl());
        DefaultValueService.setInstance(new DefaultValueServiceImpl());

        ExperimentProperty.register();
        SamplesSchema.register();
        ExpSchema.register();
        PropertyService.get().registerDomainKind(new SampleSetDomainType());
    }

    public boolean hasScripts()
    {
        return true;
    }

    protected Collection<WebPartFactory> createWebPartFactories()
    {
        List<WebPartFactory> result = new ArrayList<WebPartFactory>();

        BaseWebPartFactory runGroupsFactory = new BaseWebPartFactory(RunGroupWebPart.WEB_PART_NAME)
        {
            public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws IllegalAccessException, InvocationTargetException
            {
                return new RunGroupWebPart(portalCtx, WebPartFactory.LOCATION_RIGHT.equalsIgnoreCase(webPart.getLocation()), webPart);
            }
        };
        runGroupsFactory.addLegacyNames("Experiments", "Experiment", "Experiment Navigator");
        result.add(runGroupsFactory);
        BaseWebPartFactory narrowRunGroupsFactory = new BaseWebPartFactory(RunGroupWebPart.WEB_PART_NAME, WebPartFactory.LOCATION_RIGHT)
        {
            public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws IllegalAccessException, InvocationTargetException
            {
                return new RunGroupWebPart(portalCtx, WebPartFactory.LOCATION_RIGHT.equalsIgnoreCase(webPart.getLocation()), webPart);
            }
        };
        narrowRunGroupsFactory.addLegacyNames("Experiments", "Narrow Experiments");
        result.add(narrowRunGroupsFactory);

        BaseWebPartFactory runTypesFactory = new BaseWebPartFactory(RunTypeWebPart.WEB_PART_NAME)
        {
            public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws IllegalAccessException, InvocationTargetException
            {
                return new RunTypeWebPart();
            }
        };
        result.add(runTypesFactory);

        BaseWebPartFactory runTypesNarrowFactory = new BaseWebPartFactory(RunTypeWebPart.WEB_PART_NAME, WebPartFactory.LOCATION_RIGHT)
        {
            public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws IllegalAccessException, InvocationTargetException
            {
                return new RunTypeWebPart();
            }
        };
        result.add(runTypesNarrowFactory);

        result.add(new ExperimentRunWebPartFactory());
        result.add(new BaseWebPartFactory(SAMPLE_SET_WEB_PART_NAME){
            public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws IllegalAccessException, InvocationTargetException
            {
                return new SampleSetWebPart(WebPartFactory.LOCATION_RIGHT.equalsIgnoreCase(webPart.getLocation()), portalCtx);
            }
        });
        BaseWebPartFactory narrowSampleSetFactory = new BaseWebPartFactory(SAMPLE_SET_WEB_PART_NAME, WebPartFactory.LOCATION_RIGHT)
        {
            public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws IllegalAccessException, InvocationTargetException
            {
                return new SampleSetWebPart(WebPartFactory.LOCATION_RIGHT.equalsIgnoreCase(webPart.getLocation()), portalCtx);
            }
        };
        narrowSampleSetFactory.addLegacyNames("Narrow Sample Sets");
        result.add(narrowSampleSetFactory);
        result.add(new AlwaysAvailableWebPartFactory("Samples Menu", WebPartFactory.LOCATION_MENUBAR, false, false) {
            public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws Exception
            {
                WebPartView view = new JspView<Portal.WebPart>(ExperimentModule.class, "samplesAndAnalytes.jsp", webPart);
                view.setTitle("Samples");
                return view;
            }
        });
        BaseWebPartFactory narrowProtocolFactory = new BaseWebPartFactory(PROTOCOL_WEB_PART_NAME, WebPartFactory.LOCATION_RIGHT)
        {
            public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws IllegalAccessException, InvocationTargetException
            {
                return new ProtocolWebPart(WebPartFactory.LOCATION_RIGHT.equalsIgnoreCase(webPart.getLocation()), portalCtx);
            }
        };
        narrowProtocolFactory.addLegacyNames("Narrow Protocols");
        result.add(narrowProtocolFactory);

        return result;
    }

    @Override
    protected void startupAfterSpringConfig(ModuleContext moduleContext)
    {
        SearchService ss = ServiceRegistry.get().getService(SearchService.class);
        if (null != ss)
        {
//            ss.addSearchCategory(OntologyManager.conceptCategory);
            ss.addSearchCategory(ExpMaterialImpl.searchCategory);
            ss.addDocumentProvider(this);
        }

        PipelineService.get().registerPipelineProvider(new ExperimentPipelineProvider(this));
        ExperimentService.get().registerExperimentRunTypeSource(new ExperimentRunTypeSource()
        {
            public Set<ExperimentRunType> getExperimentRunTypes(Container container)
            {
                return Collections.singleton(ExperimentRunType.ALL_RUNS_TYPE);
            }
        });
        ExperimentService.get().registerExperimentDataHandler(new DefaultExperimentDataHandler());
        ExperimentService.get().registerDataType(new LogDataType());
        AuditLogService.get().addAuditViewFactory(DomainAuditViewFactory.getInstance());
        AuditLogService.get().addAuditViewFactory(ExperimentAuditViewFactory.getInstance());
        AuditLogService.get().addAuditViewFactory(SampleSetAuditViewFactory.getInstance());

        ContainerManager.addContainerListener(new ContainerManager.ContainerListener()
        {
            public void containerCreated(Container c, User user)
            {
            }

            public void containerDeleted(Container c, User user)
            {
                try
                {
                    ExperimentService.get().deleteAllExpObjInContainer(c, user);
                }
                catch (ExperimentException ee)
                {
                    throw new RuntimeException(ee);
                }
            }

            @Override
            public void containerMoved(Container c, Container oldParent, User user)
            {                
            }

            public void propertyChange(PropertyChangeEvent evt)
            {
                if (evt.getPropertyName().equals("Parent"))
                {
                    Container c = (Container) evt.getSource();
                    Container cOldParent = (Container) evt.getOldValue();
                    Container cNewParent = (Container) evt.getNewValue();
                    try
                    {
                        ExperimentService.get().moveContainer(c, cOldParent, cNewParent);
                    }
                    catch (ExperimentException e)
                    {
                        throw new RuntimeException(e);
                    }

                }
            }
        },
                // This is in the Last group because when a container is deleted,
                // the Experiment listener needs to be called after the Study listener,
                // because Study needs the metadata held by Experiment to delete properly.
                ContainerManager.ContainerListener.Order.Last);

        SystemProperty.registerProperties();
        TypesController.registerAdminConsoleLinks();

        PropertyService.get().registerValidatorKind(new RegExValidator());
        PropertyService.get().registerValidatorKind(new RangeValidator());
        PropertyService.get().registerValidatorKind(new LookupValidator());
    }

    public Collection<String> getSummary(Container c)
    {
        Collection<String> list = new LinkedList<String>();
        int count = ExperimentService.get().getExperiments(c, null, false, true).length;
        if (count > 0)
            list.add("" + count + " Run Group" + (count > 1 ? "s" : ""));
        return list;
    }


    @Override
    @NotNull
    public Set<Class> getIntegrationTests()
    {
        return new HashSet<Class>(Arrays.asList(
            OntologyManager.TestCase.class));
    }

    @NotNull
    @Override
    public Set<Class> getUnitTests()
    {
        return new HashSet<Class>(Arrays.asList(
            LSIDRelativizer.TestCase.class,
            LsidUtils.TestCase.class));
    }

    @Override
    @NotNull
    public Set<String> getSchemaNames()
    {
        return PageFlowUtil.set(ExpSchema.SCHEMA_NAME);
    }


    @Override
    @NotNull
    public Set<DbSchema> getSchemasToTest()
    {
        return PageFlowUtil.set(ExperimentService.get().getSchema());
    }

    public void enumerateDocuments(final @NotNull SearchService.IndexTask task, final @NotNull Container c, final Date modifiedSince)
    {
//        if (c == ContainerManager.getSharedContainer())
//            OntologyManager.indexConcepts(task);

        Runnable r = new Runnable()
        {
            public void run()
            {
                ExpMaterialImpl[] materials = ExperimentServiceImpl.get().getIndexableMaterials(c, modifiedSince);
                for (ExpMaterialImpl material : materials)
                {
                    material.index(task);
                }
            }
        };
        task.addRunnable(r, SearchService.PRIORITY.bulk);

    }

    public void indexDeleted() throws SQLException
    {
        // Clear the last indexed time on all materials
        Table.execute(ExperimentService.get().getSchema(), "UPDATE " + ExperimentService.get().getTinfoMaterial() +
                " SET LastIndexed = NULL WHERE LastIndexed IS NOT NULL");
    }
}
