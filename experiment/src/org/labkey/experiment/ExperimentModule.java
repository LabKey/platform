/*
 * Copyright (c) 2005-2015 LabKey Corporation
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

import com.drew.lang.annotations.Nullable;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.admin.FolderSerializationRegistry;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.defaults.DefaultValueService;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.ExperimentRunType;
import org.labkey.api.exp.ExperimentRunTypeSource;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.api.DefaultExperimentDataHandler;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.DomainAuditProvider;
import org.labkey.api.exp.property.DomainAuditViewFactory;
import org.labkey.api.exp.property.ExperimentProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.property.SystemProperty;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.exp.xar.LsidUtils;
import org.labkey.api.files.FileContentService;
import org.labkey.api.files.TableUpdaterFileListener;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.SpringModule;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.AlwaysAvailableWebPartFactory;
import org.labkey.api.view.BaseWebPartFactory;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;
import org.labkey.experiment.api.ExpMaterialImpl;
import org.labkey.experiment.api.ExperimentServiceImpl;
import org.labkey.experiment.api.LogDataType;
import org.labkey.experiment.api.SampleSetDomainType;
import org.labkey.experiment.api.property.DomainPropertyImpl;
import org.labkey.experiment.api.property.LengthValidator;
import org.labkey.experiment.api.property.LookupValidator;
import org.labkey.experiment.api.property.PropertyServiceImpl;
import org.labkey.experiment.api.property.RangeValidator;
import org.labkey.experiment.api.property.RegExValidator;
import org.labkey.experiment.controllers.exp.ExperimentController;
import org.labkey.experiment.controllers.property.PropertyController;
import org.labkey.experiment.defaults.DefaultValueServiceImpl;
import org.labkey.experiment.pipeline.ExperimentPipelineProvider;
import org.labkey.experiment.types.TypesController;
import org.labkey.experiment.xar.FolderXarImporterFactory;
import org.labkey.experiment.xar.FolderXarWriterFactory;

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
        return 15.30;
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
        SamplesSchema.register(this);
        ExpSchema.register(this);
        PropertyService.get().registerDomainKind(new SampleSetDomainType());
    }

    public boolean hasScripts()
    {
        return true;
    }

    @Override
    public UpgradeCode getUpgradeCode()
    {
        return new ExperimentUpgradeCode();
    }

    @NotNull
    protected Collection<WebPartFactory> createWebPartFactories()
    {
        List<WebPartFactory> result = new ArrayList<>();

        BaseWebPartFactory runGroupsFactory = new BaseWebPartFactory(RunGroupWebPart.WEB_PART_NAME, WebPartFactory.LOCATION_BODY, WebPartFactory.LOCATION_RIGHT)
        {
            public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart)
            {
                return new RunGroupWebPart(portalCtx, WebPartFactory.LOCATION_RIGHT.equalsIgnoreCase(webPart.getLocation()), webPart);
            }
        };
        runGroupsFactory.addLegacyNames("Experiments", "Experiment", "Experiment Navigator", "Narrow Experiments");
        result.add(runGroupsFactory);

        BaseWebPartFactory runTypesFactory = new BaseWebPartFactory(RunTypeWebPart.WEB_PART_NAME, WebPartFactory.LOCATION_BODY, WebPartFactory.LOCATION_RIGHT)
        {
            public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart)
            {
                return new RunTypeWebPart();
            }
        };
        result.add(runTypesFactory);

        result.add(new ExperimentRunWebPartFactory());
        BaseWebPartFactory sampleSetFactory = new BaseWebPartFactory(SAMPLE_SET_WEB_PART_NAME, WebPartFactory.LOCATION_BODY, WebPartFactory.LOCATION_RIGHT)
        {
            public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart)
            {
                return new SampleSetWebPart(WebPartFactory.LOCATION_RIGHT.equalsIgnoreCase(webPart.getLocation()), portalCtx);
            }
        };
        sampleSetFactory.addLegacyNames("Narrow Sample Sets");
        result.add(sampleSetFactory);
        result.add(new AlwaysAvailableWebPartFactory("Samples Menu", false, false, WebPartFactory.LOCATION_MENUBAR) {
            public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart)
            {
                WebPartView view = new JspView<>(ExperimentModule.class, "samplesAndAnalytes.jsp", webPart);
                view.setTitle("Samples");
                return view;
            }
        });
        BaseWebPartFactory narrowProtocolFactory = new BaseWebPartFactory(PROTOCOL_WEB_PART_NAME, WebPartFactory.LOCATION_RIGHT)
        {
            public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart)
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
            @NotNull
            public Set<ExperimentRunType> getExperimentRunTypes(@Nullable Container container)
            {
                return Collections.singleton(ExperimentRunType.ALL_RUNS_TYPE);
            }
        });
        ExperimentService.get().registerExperimentDataHandler(new DefaultExperimentDataHandler());
        ExperimentService.get().registerDataType(new LogDataType());
        AuditLogService.get().addAuditViewFactory(DomainAuditViewFactory.getInstance());
        AuditLogService.get().addAuditViewFactory(ExperimentAuditViewFactory.getInstance());
        AuditLogService.get().addAuditViewFactory(SampleSetAuditViewFactory.getInstance());

        AuditLogService.registerAuditType(new DomainAuditProvider());
        AuditLogService.registerAuditType(new ExperimentAuditProvider());
        AuditLogService.registerAuditType(new SampleSetAuditProvider());

        ServiceRegistry.get(FileContentService.class).addFileListener(new ExpDataFileListener());
        ServiceRegistry.get(FileContentService.class).addFileListener(new TableUpdaterFileListener(ExperimentService.get().getTinfoExperimentRun(), "FilePathRoot", TableUpdaterFileListener.Type.filePath, "RowId"));
        ServiceRegistry.get(FileContentService.class).addFileListener(new FileLinkFileListener());

        ContainerManager.addContainerListener(new ContainerManager.AbstractContainerListener()
                                              {
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
                                              },
                // This is in the Last group because when a container is deleted,
                // the Experiment listener needs to be called after the Study listener,
                // because Study needs the metadata held by Experiment to delete properly.
                // but it should be before the CoreContainerListener
                ContainerManager.ContainerListener.Order.Last);

        SystemProperty.registerProperties();
        TypesController.registerAdminConsoleLinks();

        PropertyService.get().registerValidatorKind(new RegExValidator());
        PropertyService.get().registerValidatorKind(new RangeValidator());
        PropertyService.get().registerValidatorKind(new LookupValidator());
        PropertyService.get().registerValidatorKind(new LengthValidator());

        FolderSerializationRegistry folderRegistry = ServiceRegistry.get().getService(FolderSerializationRegistry.class);
        if (null != folderRegistry)
        {
            folderRegistry.addFactories(new FolderXarWriterFactory(), new FolderXarImporterFactory());
        }
    }

    @NotNull
    public Collection<String> getSummary(Container c)
    {
        Collection<String> list = new LinkedList<>();
        int runGroupCount = ExperimentService.get().getExperiments(c, null, false, true).size();
        if (runGroupCount > 0)
            list.add("" + runGroupCount + " Run Group" + (runGroupCount > 1 ? "s" : ""));

        User user = HttpView.currentContext().getUser();

        Set<ExperimentRunType> runTypes = ExperimentService.get().getExperimentRunTypes(c);
        for (ExperimentRunType runType : runTypes)
        {
            if (runType == ExperimentRunType.ALL_RUNS_TYPE)
                continue;

            long runCount = runType.getRunCount(user, c);
            if (runCount > 0)
                list.add(runCount + " runs of type " + runType.getDescription());
        }

        /*
        ExpProtocol[] protocols = ExperimentService.get().getExpProtocols(c);
        for (ExpProtocol protocol : protocols)
        {
            List<? extends ExpRun> runs = ExperimentService.get().getExpRunsForProtocolIds(true, protocol.getRowId());
            if (runs != null && runs.size() > 0)
                list.add(runs.size() + " runs of type " + protocol.getName());
        }
        */

        int sampleSetCount = ExperimentService.get().getSampleSets(c, null, false).size();
        if (sampleSetCount > 0)
            list.add(sampleSetCount + " Sample Set" + (sampleSetCount > 1 ? "s" : ""));

        return list;
    }


    @Override
    @NotNull
    public Set<Class> getIntegrationTests()
    {
        return new HashSet<Class>(Arrays.asList(
            OntologyManager.TestCase.class,
            DomainPropertyImpl.TestCase.class));
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


    public void enumerateDocuments(final @NotNull SearchService.IndexTask task, final @NotNull Container c, final Date modifiedSince)
    {
//        if (c == ContainerManager.getSharedContainer())
//            OntologyManager.indexConcepts(task);

        Runnable r = new Runnable()
        {
            public void run()
            {
                for (ExpMaterialImpl material : ExperimentServiceImpl.get().getIndexableMaterials(c, modifiedSince))
                {
                    material.index(task);
                }
            }
        };
        task.addRunnable(r, SearchService.PRIORITY.bulk);

    }

    public void indexDeleted()
    {
        // Clear the last indexed time on all materials
        new SqlExecutor(ExperimentService.get().getSchema()).execute("UPDATE " + ExperimentService.get().getTinfoMaterial() +
                " SET LastIndexed = NULL WHERE LastIndexed IS NOT NULL");
    }
}
