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
package org.labkey.experiment;

import org.apache.commons.lang3.math.NumberUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.FolderSerializationRegistry;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.defaults.DefaultValueService;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.ExperimentRunType;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.api.DefaultExperimentDataHandler;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExperimentJSONConverter;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.DomainAuditProvider;
import org.labkey.api.exp.property.DomainPropertyAuditProvider;
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
import org.labkey.api.query.QueryService;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AdminConsole;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.AlwaysAvailableWebPartFactory;
import org.labkey.api.view.BaseWebPartFactory;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.experiment.api.DataClassDomainKind;
import org.labkey.experiment.api.ExpDataClassDataTestCase;
import org.labkey.experiment.api.ExpDataClassType;
import org.labkey.experiment.api.ExpDataImpl;
import org.labkey.experiment.api.ExpMaterialImpl;
import org.labkey.experiment.api.ExpSampleSetTestCase;
import org.labkey.experiment.api.ExperimentServiceImpl;
import org.labkey.experiment.api.LogDataType;
import org.labkey.experiment.api.SampleSetDomainKind;
import org.labkey.experiment.api.data.ChildOfCompareType;
import org.labkey.experiment.api.data.ParentOfCompareType;
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
import java.util.Map;
import java.util.Set;

/**
 * User: phussey (Peter Hussey)
 * Date: Jul 18, 2005
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
        return 18.10;
    }

    @Nullable
    @Override
    public UpgradeCode getUpgradeCode()
    {
        return new ExperimentUpgradeCode();
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
        PropertyService.get().registerDomainKind(new SampleSetDomainKind());
        PropertyService.get().registerDomainKind(new DataClassDomainKind());

        QueryService.get().addCompareType(new ChildOfCompareType());
        QueryService.get().addCompareType(new ParentOfCompareType());

        PropertyService.get().registerValidatorKind(new RegExValidator());
        PropertyService.get().registerValidatorKind(new RangeValidator());
        PropertyService.get().registerValidatorKind(new LookupValidator());
        PropertyService.get().registerValidatorKind(new LengthValidator());

        ExperimentService.get().registerExperimentDataHandler(new DefaultExperimentDataHandler());

        AdminConsole.addExperimentalFeatureFlag(ExperimentServiceImpl.EXPERIMENTAL_LEGACY_LINEAGE, "Legacy lineage query",
                "This feature will restore the legacy lineage queries used on the Material and Data details pages", false);

        AdminConsole.addExperimentalFeatureFlag(ExperimentService.EXPERIMENTAL_LINEAGE_PERFORMANCE, "Lineage performance improvements",
                "This feature will turn on the lineage performance improvements", false);

        AdminConsole.addExperimentalFeatureFlag(AppProps.EXPERIMENTAL_RESOLVE_PROPERTY_URI_COLUMNS, "Resolve property URIs as columns on experiment tables",
                "If a column is not found on an experiment table, attempt to resolve the column name as a Property URI and add it as a property column", false);
    }

    public boolean hasScripts()
    {
        return true;
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

        result.add(new AlwaysAvailableWebPartFactory("Data Classes", false, false, WebPartFactory.LOCATION_BODY, WebPartFactory.LOCATION_RIGHT) {
            public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart)
            {
                return new DataClassWebPart(WebPartFactory.LOCATION_RIGHT.equalsIgnoreCase(webPart.getLocation()), portalCtx, webPart);
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
        SearchService ss = SearchService.get();
        if (null != ss)
        {
//            ss.addSearchCategory(OntologyManager.conceptCategory);
            ss.addSearchCategory(ExpMaterialImpl.searchCategory);
            ss.addSearchCategory(ExpDataImpl.expDataCategory);
            ss.addSearchResultTemplate(new ExpDataImpl.DataSearchResultTemplate());
            ss.addResourceResolver("data", new SearchService.ResourceResolver()
            {
                @Override
                public WebdavResource resolve(@NotNull String resourceIdentifier)
                {
                    ExpDataImpl data = ExpDataImpl.fromDocumentId(resourceIdentifier);
                    if (data == null)
                        return null;

                    return data.createDocument();
                }

                @Override
                public Map<String, Object> getCustomSearchJson(User user, @NotNull String resourceIdentifier)
                {
                    ExpDataImpl data = ExpDataImpl.fromDocumentId(resourceIdentifier);
                    if (data == null)
                        return null;

                    return ExperimentJSONConverter.serializeData(data);
                }
            });
            ss.addResourceResolver("material", new SearchService.ResourceResolver(){
                @Override
                public Map<String, Object> getCustomSearchJson(User user, @NotNull String resourceIdentifier)
                {
                    int rowId = NumberUtils.toInt(resourceIdentifier.replace("material:", ""));
                    if (rowId == 0)
                        return null;

                    ExpMaterial material = ExperimentService.get().getExpMaterial(rowId);
                    if (material == null)
                        return null;

                    return ExperimentJSONConverter.serializeMaterial(material);
                }
            });
            ss.addDocumentProvider(this);
        }

        PipelineService.get().registerPipelineProvider(new ExperimentPipelineProvider(this));
        ExperimentService.get().registerExperimentRunTypeSource(container -> Collections.singleton(ExperimentRunType.ALL_RUNS_TYPE));
        ExperimentService.get().registerDataType(new LogDataType());

        AuditLogService.get().registerAuditType(new DomainAuditProvider());
        AuditLogService.get().registerAuditType(new DomainPropertyAuditProvider());
        AuditLogService.get().registerAuditType(new ExperimentAuditProvider());
        AuditLogService.get().registerAuditType(new SampleSetAuditProvider());

        if (null != FileContentService.get())
        {
            FileContentService.get().addFileListener(new ExpDataFileListener());
            FileContentService.get().addFileListener(new TableUpdaterFileListener(ExperimentService.get().getTinfoExperimentRun(), "FilePathRoot", TableUpdaterFileListener.Type.filePath, "RowId"));
            FileContentService.get().addFileListener(new FileLinkFileListener());
        }
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

        FolderSerializationRegistry folderRegistry = ServiceRegistry.get().getService(FolderSerializationRegistry.class);
        if (null != folderRegistry)
        {
            folderRegistry.addFactories(new FolderXarWriterFactory(), new FolderXarImporterFactory());
        }

        AttachmentService.get().registerAttachmentType(ExpDataClassType.get());
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

        int dataClassCount = ExperimentService.get().getDataClasses(c, null, false).size();
        if (dataClassCount > 0)
            list.add(dataClassCount + " Data Class" + (dataClassCount > 1 ? "es" : ""));

        int sampleSetCount = ExperimentService.get().getSampleSets(c, null, false).size();
        if (sampleSetCount > 0)
            list.add(sampleSetCount + " Sample Set" + (sampleSetCount > 1 ? "s" : ""));

        return list;
    }


    @Override
    @NotNull
    public Set<Class> getIntegrationTests()
    {
        return new HashSet<>(Arrays.asList(
                OntologyManager.TestCase.class,
                DomainPropertyImpl.TestCase.class,
                ExpDataClassDataTestCase.class,
                ExpSampleSetTestCase.class,
                ExperimentServiceImpl.TestCase.class
                /*,LineagePerfTest.class*/));
    }

    @NotNull
    @Override
    public Set<Class> getUnitTests()
    {
        return new HashSet<>(Arrays.asList(
            Lsid.TestCase.class,
            LSIDRelativizer.TestCase.class,
            LsidUtils.TestCase.class));
    }

    @Override
    @NotNull
    public Set<String> getSchemaNames()
    {
        return PageFlowUtil.set(
                ExpSchema.SCHEMA_NAME,
                DataClassDomainKind.PROVISIONED_SCHEMA_NAME
        );
    }

    @NotNull
    @Override
    public Collection<String> getProvisionedSchemaNames()
    {
        return PageFlowUtil.set(DataClassDomainKind.PROVISIONED_SCHEMA_NAME);
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

                for (ExpDataImpl data : ExperimentServiceImpl.get().getIndexableData(c, modifiedSince))
                {
                    data.index(task);
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

        // Clear the last indexed time on all data
        new SqlExecutor(ExperimentService.get().getSchema()).execute("UPDATE " + ExperimentService.get().getTinfoData() +
                " SET LastIndexed = NULL WHERE LastIndexed IS NOT NULL");
    }
}
