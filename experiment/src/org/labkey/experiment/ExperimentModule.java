/*
 * Copyright (c) 2005-2009 LabKey Corporation
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

import junit.framework.TestCase;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.defaults.DefaultValueService;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.ExperimentRunType;
import org.labkey.api.exp.ExperimentRunTypeSource;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.list.ListImporterViewGetter;
import org.labkey.api.exp.api.DefaultExperimentDataHandler;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.ExperimentProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.property.SystemProperty;
import org.labkey.api.exp.property.DomainAuditViewFactory;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.exp.xar.LsidUtils;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.SpringModule;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.*;
import org.labkey.api.search.SearchService;
import org.labkey.experiment.api.ExperimentServiceImpl;
import org.labkey.experiment.api.LogDataType;
import org.labkey.experiment.api.SampleSetDomainType;
import org.labkey.experiment.api.property.PropertyServiceImpl;
import org.labkey.experiment.api.property.RangeValidator;
import org.labkey.experiment.api.property.RegExValidator;
import org.labkey.experiment.controllers.exp.ExperimentController;
import org.labkey.experiment.controllers.property.PropertyController;
import org.labkey.experiment.defaults.DefaultValueServiceImpl;
import org.labkey.experiment.pipeline.ExperimentPipelineProvider;
import org.labkey.experiment.types.TypesController;
import org.labkey.experiment.xar.DefaultRunExpansionHandler;
import org.labkey.experiment.list.client.ListImporter;
import org.jetbrains.annotations.NotNull;

import java.beans.PropertyChangeEvent;
import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.util.*;

/**
 * User: phussey (Peter Hussey)
 * Date: Jul 18, 2005
 * Time: 3:25:52 PM
 */
public class ExperimentModule extends SpringModule
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
        return 9.30;
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

    @Override
    protected ContextType getContextType()
    {
        return ContextType.config;
    }

    protected Collection<? extends WebPartFactory> createWebPartFactories()
    {
        List<BaseWebPartFactory> result = new ArrayList<BaseWebPartFactory>();

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

        result.add(new BaseWebPartFactory(ExperimentModule.EXPERIMENT_RUN_WEB_PART_NAME){
            public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart)
            {
                return ExperimentService.get().createExperimentRunWebPart(new ViewContext(portalCtx), ExperimentRunType.ALL_RUNS_TYPE, true);
            }
        });
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


    public void startup(ModuleContext context)
    {
        SearchService ss = ServiceRegistry.get().getService(SearchService.class);
        if (null != ss)
            ss.addSearchCategory(OntologyManager.conceptCategory);

        PipelineService.get().registerPipelineProvider(new ExperimentPipelineProvider(this));
        ExperimentService.get().registerExperimentRunTypeSource(new ExperimentRunTypeSource()
        {
            public Set<ExperimentRunType> getExperimentRunTypes(Container container)
            {
                return Collections.singleton(ExperimentRunType.ALL_RUNS_TYPE);
            }
        });
        ExperimentService.get().registerRunExpansionHandler(new DefaultRunExpansionHandler());
        ExperimentService.get().registerExperimentDataHandler(new DefaultExperimentDataHandler());
        ExperimentService.get().registerDataType(new LogDataType());
        AuditLogService.get().addAuditViewFactory(DomainAuditViewFactory.getInstance());
        AuditLogService.get().addAuditViewFactory(ExperimentAuditViewFactory.getInstance());

        ContainerManager.addContainerListener(new ContainerManager.ContainerListener()
        {
            public void containerCreated(Container c)
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
                    catch (SQLException e)
                    {
                        throw new RuntimeException(e);
                    }
                    catch (ExperimentException e)
                    {
                        throw new RuntimeException(e);
                    }

                }
            }
        });
        SystemProperty.registerProperties();
        TypesController.registerAdminConsoleLinks();

        PropertyService.get().registerValidatorKind(new RegExValidator());
        PropertyService.get().registerValidatorKind(new RangeValidator());

        // TODO: Remove this hack
        ListImporterViewGetter.setClass(ListImporter.class);

        initWebApplicationContext();
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
    public Set<Class<? extends TestCase>> getJUnitTests()
    {
        return new HashSet<Class<? extends TestCase>>(Arrays.asList(
            LSIDRelativizer.TestCase.class,
            OntologyManager.TestCase.class,
            LsidUtils.TestCase.class));
    }


    @Override
    public Set<String> getSchemaNames()
    {
        return PageFlowUtil.set(ExpSchema.SCHEMA_NAME);
    }


    @Override
    public Set<DbSchema> getSchemasToTest()
    {
        return PageFlowUtil.set(ExperimentService.get().getSchema());
    }

    @Override
    public UpgradeCode getUpgradeCode()
    {
        return new ExperimentUpgradeCode();
    }


    @Override
    public void enumerateDocuments(@NotNull SearchService.IndexTask task, Container c, Date modifiedSince)
    {
        if (c == null)
            OntologyManager.indexConcepts(task);
    }
}
