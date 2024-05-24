/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import org.apache.commons.collections4.Factory;
import org.apache.commons.lang3.math.NumberUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.FolderSerializationRegistry;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.assay.AssayService;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.NameGenerator;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.defaults.DefaultValueService;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.ExperimentRunType;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.DefaultExperimentDataHandler;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpDataClass;
import org.labkey.api.exp.api.ExpLineageOptions;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpProtocolAttachmentType;
import org.labkey.api.exp.api.ExpRunAttachmentType;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.ExperimentJSONConverter;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.FilterProtocolInputCriteria;
import org.labkey.api.exp.api.SampleTypeDomainKind;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.property.DomainAuditProvider;
import org.labkey.api.exp.property.DomainPropertyAuditProvider;
import org.labkey.api.exp.property.ExperimentProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.property.SystemProperty;
import org.labkey.api.exp.query.ExpMaterialTable;
import org.labkey.api.exp.query.ExpSampleTypeTable;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.exp.xar.LSIDRelativizer;
import org.labkey.api.exp.xar.LsidUtils;
import org.labkey.api.files.FileContentService;
import org.labkey.api.files.TableUpdaterFileListener;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleProperty;
import org.labkey.api.module.SpringModule;
import org.labkey.api.module.Summary;
import org.labkey.api.ontology.OntologyService;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.settings.AdminConsole;
import org.labkey.api.settings.AppProps;
import org.labkey.api.usageMetrics.UsageMetricsService;
import org.labkey.api.util.JspTestCase;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.AlwaysAvailableWebPartFactory;
import org.labkey.api.view.BaseWebPartFactory;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;
import org.labkey.api.vocabulary.security.DesignVocabularyPermission;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.api.webdav.WebdavService;
import org.labkey.experiment.api.DataClassDomainKind;
import org.labkey.experiment.api.ExpDataClassImpl;
import org.labkey.experiment.api.ExpDataClassTableImpl;
import org.labkey.experiment.api.ExpDataClassType;
import org.labkey.experiment.api.ExpDataImpl;
import org.labkey.experiment.api.ExpDataTableImpl;
import org.labkey.experiment.api.ExpMaterialImpl;
import org.labkey.experiment.api.ExpProtocolImpl;
import org.labkey.experiment.api.ExpSampleTypeImpl;
import org.labkey.experiment.api.ExperimentServiceImpl;
import org.labkey.experiment.api.ExperimentStressTest;
import org.labkey.experiment.api.GraphAlgorithms;
import org.labkey.experiment.api.LineagePerfTest;
import org.labkey.experiment.api.LineageTest;
import org.labkey.experiment.api.LogDataType;
import org.labkey.experiment.api.Protocol;
import org.labkey.experiment.api.SampleTypeServiceImpl;
import org.labkey.experiment.api.UniqueValueCounterTestCase;
import org.labkey.experiment.api.VocabularyDomainKind;
import org.labkey.experiment.api.data.ChildOfCompareType;
import org.labkey.experiment.api.data.ChildOfMethod;
import org.labkey.experiment.api.data.LineageCompareType;
import org.labkey.experiment.api.data.ParentOfCompareType;
import org.labkey.experiment.api.data.ParentOfMethod;
import org.labkey.experiment.api.property.DomainPropertyImpl;
import org.labkey.experiment.api.property.LengthValidator;
import org.labkey.experiment.api.property.LookupValidator;
import org.labkey.experiment.api.property.PropertyServiceImpl;
import org.labkey.experiment.api.property.RangeValidator;
import org.labkey.experiment.api.property.RegExValidator;
import org.labkey.experiment.api.property.StorageProvisionerImpl;
import org.labkey.experiment.api.property.TextChoiceValidator;
import org.labkey.experiment.controllers.exp.ExperimentController;
import org.labkey.experiment.controllers.property.PropertyController;
import org.labkey.experiment.defaults.DefaultValueServiceImpl;
import org.labkey.experiment.pipeline.ExperimentPipelineProvider;
import org.labkey.experiment.samples.DataClassFolderImporter;
import org.labkey.experiment.samples.DataClassFolderWriter;
import org.labkey.experiment.samples.ExperimentQueryChangeListener;
import org.labkey.experiment.samples.SampleStatusFolderImporter;
import org.labkey.experiment.samples.SampleTimelineAuditProvider;
import org.labkey.experiment.samples.SampleTypeFolderImporter;
import org.labkey.experiment.samples.SampleTypeFolderWriter;
import org.labkey.experiment.types.TypesController;
import org.labkey.experiment.xar.FolderXarImporterFactory;
import org.labkey.experiment.xar.FolderXarWriterFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.labkey.api.data.ColumnRenderPropertiesImpl.STORAGE_UNIQUE_ID_CONCEPT_URI;
import static org.labkey.api.data.ColumnRenderPropertiesImpl.TEXT_CHOICE_CONCEPT_URI;
import static org.labkey.api.exp.api.ExperimentService.MODULE_NAME;

public class ExperimentModule extends SpringModule
{
    private static final String SAMPLE_TYPE_WEB_PART_NAME = "Sample Types";
    private static final String PROTOCOL_WEB_PART_NAME = "Protocols";

    public static final String EXPERIMENT_RUN_WEB_PART_NAME = "Experiment Runs";

    @Override
    public String getName()
    {
        return MODULE_NAME;
    }

    @Override
    public Double getSchemaVersion()
    {
        return 24.002;
    }

    @Nullable
    @Override
    public UpgradeCode getUpgradeCode()
    {
        return new ExperimentUpgradeCode();
    }

    @Override
    protected void init()
    {
        addController("experiment", ExperimentController.class);
        addController("experiment-types", TypesController.class);
        addController("property", PropertyController.class);
        ExperimentService.setInstance(new ExperimentServiceImpl());
        SampleTypeService.setInstance(new SampleTypeServiceImpl());
        DefaultValueService.setInstance(new DefaultValueServiceImpl());
        StorageProvisioner.setInstance(StorageProvisionerImpl.get());

        PropertyServiceImpl propertyServiceImpl = new PropertyServiceImpl();
        PropertyService.setInstance(propertyServiceImpl);
        UsageMetricsService.get().registerUsageMetrics(getName(), propertyServiceImpl);

        ExperimentProperty.register();
        SamplesSchema.register(this);
        ExpSchema.register(this);

        PropertyService.get().registerDomainKind(new SampleTypeDomainKind());
        PropertyService.get().registerDomainKind(new DataClassDomainKind());
        PropertyService.get().registerDomainKind(new VocabularyDomainKind());

        QueryService.get().addCompareType(new ChildOfCompareType());
        QueryService.get().addCompareType(new ParentOfCompareType());
        QueryService.get().addCompareType(new LineageCompareType());
        QueryService.get().registerMethod(ChildOfMethod.NAME, new ChildOfMethod(), JdbcType.BOOLEAN, 2, 3);
        QueryService.get().registerMethod(ParentOfMethod.NAME, new ParentOfMethod(), JdbcType.BOOLEAN, 2, 3);
        QueryService.get().addQueryListener(new ExperimentQueryChangeListener());

        PropertyService.get().registerValidatorKind(new RegExValidator());
        PropertyService.get().registerValidatorKind(new RangeValidator());
        PropertyService.get().registerValidatorKind(new LookupValidator());
        PropertyService.get().registerValidatorKind(new LengthValidator());
        PropertyService.get().registerValidatorKind(new TextChoiceValidator());

        ExperimentService.get().registerExperimentDataHandler(new DefaultExperimentDataHandler());
        ExperimentService.get().registerProtocolInputCriteria(new FilterProtocolInputCriteria.Factory());

        AdminConsole.addExperimentalFeatureFlag(AppProps.EXPERIMENTAL_RESOLVE_PROPERTY_URI_COLUMNS, "Resolve property URIs as columns on experiment tables",
                "If a column is not found on an experiment table, attempt to resolve the column name as a Property URI and add it as a property column", false);
        if (CoreSchema.getInstance().getSqlDialect().isSqlServer())
        {
            AdminConsole.addExperimentalFeatureFlag(NameGenerator.EXPERIMENTAL_WITH_COUNTER, "Use strict incremental withCounter and rootSampleCount expression",
                    "When withCounter or rootSampleCount is used in name expression, make sure the count increments one-by-one and does not jump.", true);
        }
        else
        {
            AdminConsole.addExperimentalFeatureFlag(NameGenerator.EXPERIMENTAL_ALLOW_GAP_COUNTER, "Allow gap with withCounter and rootSampleCount expression",
                    "Check this option if gaps in the count generated by withCounter or rootSampleCount name expression are allowed.", true);

        }

        RoleManager.registerPermission(new DesignVocabularyPermission(), true);

        AttachmentService.get().registerAttachmentType(ExpRunAttachmentType.get());
        AttachmentService.get().registerAttachmentType(ExpProtocolAttachmentType.get());

        WebdavService.get().addExpDataProvider((path, container) -> ExperimentService.get().getAllExpDataByURL(path, container));
        ExperimentService.get().registerObjectReferencer(ExperimentServiceImpl.get());

        addModuleProperty(new LineageMaximumDepthModuleProperty(this));
    }

    @Override
    public boolean hasScripts()
    {
        return true;
    }

    @Override
    @NotNull
    protected Collection<WebPartFactory> createWebPartFactories()
    {
        List<WebPartFactory> result = new ArrayList<>();

        BaseWebPartFactory runGroupsFactory = new BaseWebPartFactory(RunGroupWebPart.WEB_PART_NAME, WebPartFactory.LOCATION_BODY, WebPartFactory.LOCATION_RIGHT)
        {
            @Override
            public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart)
            {
                return new RunGroupWebPart(portalCtx, WebPartFactory.LOCATION_RIGHT.equalsIgnoreCase(webPart.getLocation()), webPart);
            }
        };
        runGroupsFactory.addLegacyNames("Experiments", "Experiment", "Experiment Navigator", "Narrow Experiments");
        result.add(runGroupsFactory);

        BaseWebPartFactory runTypesFactory = new BaseWebPartFactory(RunTypeWebPart.WEB_PART_NAME, WebPartFactory.LOCATION_BODY, WebPartFactory.LOCATION_RIGHT)
        {
            @Override
            public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart)
            {
                return new RunTypeWebPart();
            }
        };
        result.add(runTypesFactory);

        result.add(new ExperimentRunWebPartFactory());
        BaseWebPartFactory sampleTypeFactory = new BaseWebPartFactory(SAMPLE_TYPE_WEB_PART_NAME, WebPartFactory.LOCATION_BODY, WebPartFactory.LOCATION_RIGHT)
        {
            @Override
            public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart)
            {
                return new SampleTypeWebPart(WebPartFactory.LOCATION_RIGHT.equalsIgnoreCase(webPart.getLocation()), portalCtx);
            }
        };
        sampleTypeFactory.addLegacyNames("Narrow Sample Sets", "Sample Sets");
        result.add(sampleTypeFactory);
        result.add(new AlwaysAvailableWebPartFactory("Samples Menu", false, false, WebPartFactory.LOCATION_MENUBAR) {
            @Override
            public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart)
            {
                WebPartView view = new JspView<>("/org/labkey/experiment/samplesAndAnalytes.jsp", webPart);
                view.setTitle("Samples");
                return view;
            }
        });

        result.add(new AlwaysAvailableWebPartFactory("Data Classes", false, false, WebPartFactory.LOCATION_BODY, WebPartFactory.LOCATION_RIGHT) {
            @Override
            public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart)
            {
                return new DataClassWebPart(WebPartFactory.LOCATION_RIGHT.equalsIgnoreCase(webPart.getLocation()), portalCtx, webPart);
            }
        });

        BaseWebPartFactory narrowProtocolFactory = new BaseWebPartFactory(PROTOCOL_WEB_PART_NAME, WebPartFactory.LOCATION_RIGHT)
        {
            @Override
            public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart)
            {
                return new ProtocolWebPart(WebPartFactory.LOCATION_RIGHT.equalsIgnoreCase(webPart.getLocation()), portalCtx);
            }
        };
        narrowProtocolFactory.addLegacyNames("Narrow Protocols");
        result.add(narrowProtocolFactory);

        return result;
    }

    private void addDataResourceResolver(String categoryName)
    {
        SearchService ss = SearchService.get();
        if (null == ss)
            return;

        ss.addResourceResolver(categoryName, new SearchService.ResourceResolver()
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

                return ExperimentJSONConverter.serializeData(data, user, ExperimentJSONConverter.DEFAULT_SETTINGS).toMap();
            }

            @Override
            public Map<String, Map<String, Object>> getCustomSearchJsonMap(User user, @NotNull Collection<String> resourceIdentifiers)
            {
                Map<String, ExpData> idDataMap = ExpDataImpl.fromDocumentIds(resourceIdentifiers);
                if (idDataMap == null)
                    return null;

                Map<String, Map<String, Object>> searchJsonMap = new HashMap<>();
                for (String resourceIdentifier : idDataMap.keySet())
                    searchJsonMap.put(resourceIdentifier, ExperimentJSONConverter.serializeData(idDataMap.get(resourceIdentifier), user, ExperimentJSONConverter.DEFAULT_SETTINGS).toMap());
                return searchJsonMap;
            }
        });
    }

    private void addDataClassResourceResolver(String categoryName)
    {
        SearchService ss = SearchService.get();
        if (null == ss)
            return;

        ss.addResourceResolver(categoryName, new SearchService.ResourceResolver(){
            @Override
            public Map<String, Object> getCustomSearchJson(User user, @NotNull String resourceIdentifier)
            {
                int rowId = NumberUtils.toInt(resourceIdentifier.replace(categoryName + ":", ""));
                if (rowId == 0)
                    return null;

                ExpDataClass dataClass = ExperimentService.get().getDataClass(rowId);
                if (dataClass == null)
                    return null;

                Map<String, Object> properties = ExperimentJSONConverter.serializeExpObject(dataClass, null, ExperimentJSONConverter.DEFAULT_SETTINGS, user).toMap();

                //Need to map to proper Icon
                properties.put("type", "dataClass" + (dataClass.getCategory() != null ? ":" + dataClass.getCategory() : ""));

                return properties;
            }
        });
    }

    private void addSampleTypeResourceResolver(String categoryName)
    {
        SearchService ss = SearchService.get();
        if (null == ss)
            return;

        ss.addResourceResolver(categoryName, new SearchService.ResourceResolver(){
            @Override
            public Map<String, Object> getCustomSearchJson(User user, @NotNull String resourceIdentifier)
            {
                int rowId = NumberUtils.toInt(resourceIdentifier.replace(categoryName + ":", ""));
                if (rowId == 0)
                    return null;

                ExpSampleType sampleType = SampleTypeService.get().getSampleType(rowId);
                if (sampleType == null)
                    return null;

                Map<String, Object> properties = ExperimentJSONConverter.serializeExpObject(sampleType, null, ExperimentJSONConverter.DEFAULT_SETTINGS, user).toMap();

                //Need to map to proper Icon
                properties.put("type", "sampleSet");

                return properties;
            }
        });
    }

    private void addSampleResourceResolver(String categoryName)
    {
        SearchService ss = SearchService.get();
        if (null == ss)
            return;

        ss.addResourceResolver(categoryName, new SearchService.ResourceResolver(){
            @Override
            public Map<String, Object> getCustomSearchJson(User user, @NotNull String resourceIdentifier)
            {
                int rowId = NumberUtils.toInt(resourceIdentifier.replace(categoryName + ":", ""));
                if (rowId == 0)
                    return null;

                ExpMaterial material = ExperimentService.get().getExpMaterial(rowId);
                if (material == null)
                    return null;

                return ExperimentJSONConverter.serializeMaterial(material, user, ExperimentJSONConverter.DEFAULT_SETTINGS).toMap();
            }

            @Override
            public Map<String, Map<String, Object>> getCustomSearchJsonMap(User user, @NotNull Collection<String> resourceIdentifiers)
            {
                Set<Integer> rowIds = new HashSet<>();
                Map<Integer, String> rowIdIdentifierMap = new HashMap<>();
                for (String resourceIdentifier : resourceIdentifiers)
                {
                    int rowId = NumberUtils.toInt(resourceIdentifier.replace(categoryName + ":", ""));
                    if (rowId != 0)
                    {
                        rowIds.add(rowId);
                        rowIdIdentifierMap.put(rowId, resourceIdentifier);
                    }

                }

                Map<String, Map<String, Object>> searchJsonMap = new HashMap<>();
                for (ExpMaterial material : ExperimentService.get().getExpMaterials(rowIds))
                {
                    searchJsonMap.put(
                        rowIdIdentifierMap.get(material.getRowId()),
                        ExperimentJSONConverter.serializeMaterial(material, user, ExperimentJSONConverter.DEFAULT_SETTINGS).toMap()
                    );
                }

                return searchJsonMap;
            }

        });
    }

    @Override
    protected void startupAfterSpringConfig(ModuleContext moduleContext)
    {
        SearchService ss = SearchService.get();
        if (null != ss)
        {
//            ss.addSearchCategory(OntologyManager.conceptCategory);
            ss.addSearchCategory(ExpSampleTypeImpl.searchCategory);
            ss.addSearchCategory(ExpSampleTypeImpl.mediaSearchCategory);
            ss.addSearchCategory(ExpMaterialImpl.searchCategory);
            ss.addSearchCategory(ExpMaterialImpl.mediaSearchCategory);
            ss.addSearchCategory(ExpDataClassImpl.SEARCH_CATEGORY);
            ss.addSearchCategory(ExpDataClassImpl.MEDIA_SEARCH_CATEGORY);
            ss.addSearchCategory(ExpDataImpl.expDataCategory);
            ss.addSearchCategory(ExpDataImpl.expMediaDataCategory);
            ss.addSearchResultTemplate(new ExpDataImpl.DataSearchResultTemplate());
            addDataResourceResolver(ExpDataImpl.expDataCategory.getName());
            addDataResourceResolver(ExpDataImpl.expMediaDataCategory.getName());
            addDataClassResourceResolver(ExpDataClassImpl.SEARCH_CATEGORY.getName());
            addDataClassResourceResolver(ExpDataClassImpl.MEDIA_SEARCH_CATEGORY.getName());
            addSampleTypeResourceResolver(ExpSampleTypeImpl.searchCategory.getName());
            addSampleTypeResourceResolver(ExpSampleTypeImpl.mediaSearchCategory.getName());
            addSampleResourceResolver(ExpMaterialImpl.searchCategory.getName());
            addSampleResourceResolver(ExpMaterialImpl.mediaSearchCategory.getName());
            ss.addDocumentProvider(ExperimentServiceImpl.get());
        }

        PipelineService.get().registerPipelineProvider(new ExperimentPipelineProvider(this));
        ExperimentService.get().registerExperimentRunTypeSource(container -> Collections.singleton(ExperimentRunType.ALL_RUNS_TYPE));
        ExperimentService.get().registerDataType(new LogDataType());

        AuditLogService.get().registerAuditType(new DomainAuditProvider());
        AuditLogService.get().registerAuditType(new DomainPropertyAuditProvider());
        AuditLogService.get().registerAuditType(new ExperimentAuditProvider());
        AuditLogService.get().registerAuditType(new SampleTypeAuditProvider());
        AuditLogService.get().registerAuditType(new SampleTimelineAuditProvider());

        FileContentService fileContentService = FileContentService.get();
        if (null != fileContentService)
        {
            fileContentService.addFileListener(new ExpDataFileListener());
            fileContentService.addFileListener(new TableUpdaterFileListener(ExperimentService.get().getTinfoExperimentRun(), "FilePathRoot", TableUpdaterFileListener.Type.fileRootPath, "RowId"));
            fileContentService.addFileListener(new FileLinkFileListener());
        }
        ContainerManager.addContainerListener(
                new ContainerManager.AbstractContainerListener()
                {
                    @Override
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

        FolderSerializationRegistry folderRegistry = FolderSerializationRegistry.get();
        if (null != folderRegistry)
        {
            folderRegistry.addFactories(new FolderXarWriterFactory(), new FolderXarImporterFactory());
            folderRegistry.addWriterFactory(new SampleTypeFolderWriter.SampleTypeDesignWriter.Factory());
            folderRegistry.addWriterFactory(new SampleTypeFolderWriter.SampleTypeDataWriter.Factory());
            folderRegistry.addWriterFactory(new DataClassFolderWriter.DataClassDesignWriter.Factory());
            folderRegistry.addWriterFactory(new DataClassFolderWriter.DataClassDataWriter.Factory());
            folderRegistry.addImportFactory(new SampleTypeFolderImporter.Factory());
            folderRegistry.addImportFactory(new DataClassFolderImporter.Factory());
            folderRegistry.addImportFactory(new SampleStatusFolderImporter.Factory());
        }

        AttachmentService.get().registerAttachmentType(ExpDataClassType.get());

        WebdavService.get().addProvider(new ScriptsResourceProvider());

        UsageMetricsService svc = UsageMetricsService.get();
        if (null != svc)
        {
            svc.registerUsageMetrics(getName(), () -> {
                Map<String, Object> results = new HashMap<>();
                if (AssayService.get() != null)
                {
                    Map<String, Object> assayMetrics = new HashMap<>();
                    SQLFragment baseRunSQL = new SQLFragment("SELECT COUNT(*) FROM ").append(ExperimentService.get().getTinfoExperimentRun(), "r").append(" WHERE lsid LIKE ?");
                    SQLFragment baseProtocolSQL = new SQLFragment("SELECT * FROM ").append(ExperimentService.get().getTinfoProtocol(), "p").append(" WHERE lsid LIKE ? AND ApplicationType = ?");
                    for (AssayProvider assayProvider : AssayService.get().getAssayProviders())
                    {
                        Map<String, Object> protocolMetrics = new HashMap<>();

                        // Run count across all assay designs of this type
                        SQLFragment runSQL = new SQLFragment(baseRunSQL);
                        runSQL.add(Lsid.namespaceLikeString(assayProvider.getRunLSIDPrefix()));
                        protocolMetrics.put("runCount", new SqlSelector(ExperimentService.get().getSchema(), runSQL).getObject(Long.class));

                        // Number of assay designs of this type
                        SQLFragment protocolSQL = new SQLFragment(baseProtocolSQL);
                        protocolSQL.add(assayProvider.getProtocolPattern());
                        protocolSQL.add(ExpProtocol.ApplicationType.ExperimentRun.toString());
                        List<Protocol> protocols = new SqlSelector(ExperimentService.get().getSchema(), protocolSQL).getArrayList(Protocol.class);
                        protocolMetrics.put("protocolCount", protocols.size());

                        List<? extends ExpProtocol> wrappedProtocols = protocols.stream().map(ExpProtocolImpl::new).collect(Collectors.toList());

                        protocolMetrics.put("resultRowCount", assayProvider.getResultRowCount(wrappedProtocols));

                        // Primary implementation class
                        protocolMetrics.put("implementingClass", assayProvider.getClass());

                        assayMetrics.put(assayProvider.getName(), protocolMetrics);
                    }
                    assayMetrics.put("autoLinkedAssayCount", new SqlSelector(ExperimentService.get().getSchema(), "SELECT COUNT(*) FROM exp.protocol EP JOIN exp.objectPropertiesView OP ON EP.lsid = OP.objecturi WHERE OP.propertyuri = 'terms.labkey.org#AutoCopyTargetContainer'").getObject(Long.class));
                    assayMetrics.put("protocolsWithTransformScriptCount", new SqlSelector(ExperimentService.get().getSchema(), "SELECT COUNT(*) FROM exp.protocol EP JOIN exp.objectPropertiesView OP ON EP.lsid = OP.objecturi WHERE OP.name = 'TransformScript' AND status = 'Active'").getObject(Long.class));

                    assayMetrics.put("standardAssayWithPlateSupportCount", new SqlSelector(ExperimentService.get().getSchema(), "SELECT COUNT(*) FROM exp.protocol EP JOIN exp.objectPropertiesView OP ON EP.lsid = OP.objecturi WHERE OP.name = 'PlateMetadata' AND floatValue = 1").getObject(Long.class));
                    SQLFragment runsWithPlateSQL = new SQLFragment("""
                        SELECT COUNT(*) FROM exp.experimentrun r
                            INNER JOIN exp.object o ON o.objectUri = r.lsid
                            INNER JOIN exp.objectproperty op ON op.objectId = o.objectId
                        WHERE op.propertyid IN (
                            SELECT propertyid FROM exp.propertydescriptor WHERE name = ? AND lookupquery = ?
                    )""");
                    assayMetrics.put("standardAssayRunsWithPlateTemplate", new SqlSelector(ExperimentService.get().getSchema(), new SQLFragment(runsWithPlateSQL).add("PlateTemplate").add("PlateTemplate")).getObject(Long.class));
                    assayMetrics.put("standardAssayRunsWithPlateSet", new SqlSelector(ExperimentService.get().getSchema(), new SQLFragment(runsWithPlateSQL).add("PlateSet").add("PlateSet")).getObject(Long.class));

                    Map<String, Object> sampleLookupCountMetrics = new HashMap<>();
                    SQLFragment baseAssaySampleLookupSQL = new SQLFragment("SELECT COUNT(*) FROM exp.propertydescriptor WHERE (lookupschema = 'samples' OR (lookupschema = 'exp' AND lookupquery =  'Materials')) AND propertyuri LIKE ?");

                    SQLFragment batchAssaySampleLookupSQL = new SQLFragment(baseAssaySampleLookupSQL);
                    batchAssaySampleLookupSQL.add("urn:lsid:%:" + ExpProtocol.AssayDomainTypes.Batch.getPrefix() + ".%");
                    sampleLookupCountMetrics.put("batchDomain", new SqlSelector(ExperimentService.get().getSchema(), batchAssaySampleLookupSQL).getObject(Long.class));

                    SQLFragment runAssaySampleLookupSQL = new SQLFragment(baseAssaySampleLookupSQL);
                    runAssaySampleLookupSQL.add("urn:lsid:%:" + ExpProtocol.AssayDomainTypes.Run.getPrefix() + ".%");
                    sampleLookupCountMetrics.put("runDomain", new SqlSelector(ExperimentService.get().getSchema(), runAssaySampleLookupSQL).getObject(Long.class));

                    SQLFragment resultAssaySampleLookupSQL = new SQLFragment(baseAssaySampleLookupSQL);
                    resultAssaySampleLookupSQL.add("urn:lsid:%:" + ExpProtocol.AssayDomainTypes.Result.getPrefix() + ".%");
                    sampleLookupCountMetrics.put("resultDomain", new SqlSelector(ExperimentService.get().getSchema(), resultAssaySampleLookupSQL).getObject(Long.class));

                    SQLFragment resultAssayMultipleSampleLookupSQL = new SQLFragment(
                    "SELECT COUNT(*) FROM (\n" +
                        "    SELECT PD.domainid, COUNT(*) AS PropCount\n" +
                        "    FROM exp.propertydescriptor D\n" +
                        "        JOIN exp.PropertyDomain PD ON D.propertyId = PD.propertyid\n" +
                        "    WHERE (lookupschema = 'samples' OR (lookupschema = 'exp' AND lookupquery = 'Materials'))\n" +
                        "        AND propertyuri LIKE ?\n" +
                        "    GROUP BY PD.domainid\n" +
                        ") X WHERE X.PropCount > 1"
                    );
                    resultAssayMultipleSampleLookupSQL.add("urn:lsid:%:" + ExpProtocol.AssayDomainTypes.Result.getPrefix() + ".%");
                    sampleLookupCountMetrics.put("resultDomainWithMultiple", new SqlSelector(ExperimentService.get().getSchema(), resultAssayMultipleSampleLookupSQL).getObject(Long.class));

                    assayMetrics.put("sampleLookupCount", sampleLookupCountMetrics);

                    results.put("assay", assayMetrics);
                }

                results.put("autoLinkedSampleSetCount", new SqlSelector(ExperimentService.get().getSchema(), "SELECT COUNT(*) FROM exp.materialsource WHERE autoLinkTargetContainer IS NOT NULL").getObject(Long.class));
                results.put("sampleSetCount", new SqlSelector(ExperimentService.get().getSchema(), "SELECT COUNT(*) FROM exp.materialsource").getObject(Long.class));
                results.put("sampleCount", new SqlSelector(ExperimentService.get().getSchema(), "SELECT COUNT(*) FROM exp.material").getObject(Long.class));
                results.put("aliquotCount", new SqlSelector(ExperimentService.get().getSchema(), "SELECT COUNT(*) FROM exp.material where aliquotedfromlsid IS NOT NULL").getObject(Long.class));

                results.put("dataClassCount", new SqlSelector(ExperimentService.get().getSchema(), "SELECT COUNT(*) FROM exp.dataclass").getObject(Long.class));
                results.put("dataClassRowCount", new SqlSelector(ExperimentService.get().getSchema(), "SELECT COUNT(*) FROM exp.data WHERE classid IN (SELECT rowid FROM exp.dataclass)").getObject(Long.class));
                results.put("dataWithDataParentsCount", new SqlSelector(ExperimentService.get().getSchema(), "SELECT COUNT(DISTINCT d.sourceApplicationId) FROM exp.data d\n" +
                        "JOIN exp.datainput di ON di.targetapplicationid = d.sourceapplicationid").getObject(Long.class));

                results.put("ontologyPrincipalConceptCodeCount", new SqlSelector(ExperimentService.get().getSchema(), "SELECT COUNT(*) FROM exp.propertydescriptor WHERE principalconceptcode IS NOT NULL").getObject(Long.class));
                results.put("ontologyLookupColumnCount", new SqlSelector(ExperimentService.get().getSchema(), "SELECT COUNT(*) FROM exp.propertydescriptor WHERE concepturi = ?", OntologyService.conceptCodeConceptURI).getObject(Long.class));
                results.put("ontologyConceptSubtreeCount", new SqlSelector(ExperimentService.get().getSchema(), "SELECT COUNT(*) FROM exp.propertydescriptor WHERE conceptsubtree IS NOT NULL").getObject(Long.class));
                results.put("ontologyConceptImportColumnCount", new SqlSelector(ExperimentService.get().getSchema(), "SELECT COUNT(*) FROM exp.propertydescriptor WHERE conceptimportcolumn IS NOT NULL").getObject(Long.class));
                results.put("ontologyConceptLabelColumnCount", new SqlSelector(ExperimentService.get().getSchema(), "SELECT COUNT(*) FROM exp.propertydescriptor WHERE conceptlabelcolumn IS NOT NULL").getObject(Long.class));

                results.put("scannableColumnCount", new SqlSelector(ExperimentService.get().getSchema(), "SELECT COUNT(*) FROM exp.propertydescriptor WHERE scannable = ?", true).getObject(Long.class));
                results.put("uniqueIdColumnCount", new SqlSelector(ExperimentService.get().getSchema(), "SELECT COUNT(*) FROM exp.propertydescriptor WHERE concepturi = ?", STORAGE_UNIQUE_ID_CONCEPT_URI).getObject(Long.class));
                results.put("sampleTypeWithUniqueIdCount", new SqlSelector(ExperimentService.get().getSchema(), "SELECT COUNT(DISTINCT DD.DomainURI) FROM\n" +
                        "     exp.PropertyDescriptor D \n" +
                        "         JOIN exp.PropertyDomain PD ON D.propertyId = PD.propertyid\n" +
                        "         JOIN exp.DomainDescriptor DD on PD.domainID = DD.domainId\n" +
                        "WHERE D.conceptURI = ?", STORAGE_UNIQUE_ID_CONCEPT_URI).getObject(Long.class));

                results.put("fileColumnCount", new SqlSelector(ExperimentService.get().getSchema(), "SELECT COUNT(*) FROM exp.propertydescriptor WHERE rangeURI = ?", PropertyType.FILE_LINK.getTypeUri()).getObject(Long.class));
                results.put("sampleTypeWithFileColumnCount", new SqlSelector(ExperimentService.get().getSchema(), "SELECT COUNT(DISTINCT DD.DomainURI) FROM\n" +
                        "     exp.PropertyDescriptor D \n" +
                        "         JOIN exp.PropertyDomain PD ON D.propertyId = PD.propertyid\n" +
                        "         JOIN exp.DomainDescriptor DD on PD.domainID = DD.domainId\n" +
                        "WHERE DD.storageSchemaName = ? AND D.rangeURI = ?", SampleTypeDomainKind.PROVISIONED_SCHEMA_NAME, PropertyType.FILE_LINK.getTypeUri()).getObject(Long.class));

                results.put("sampleTypeAliquotSpecificField", new SqlSelector(ExperimentService.get().getSchema(), "SELECT COUNT(DISTINCT D.PropertyURI) FROM\n" +
                        "     exp.PropertyDescriptor D \n" +
                        "         JOIN exp.PropertyDomain PD ON D.propertyId = PD.propertyid\n" +
                        "         JOIN exp.DomainDescriptor DD on PD.domainID = DD.domainId\n" +
                        "WHERE DD.storageSchemaName = ? AND D.derivationDataScope = ?", SampleTypeDomainKind.PROVISIONED_SCHEMA_NAME, ExpSchema.DerivationDataScopeType.ChildOnly.name()).getObject(Long.class));
                results.put("sampleTypeParentOnlyField", new SqlSelector(ExperimentService.get().getSchema(), "SELECT COUNT(DISTINCT D.PropertyURI) FROM\n" +
                        "     exp.PropertyDescriptor D \n" +
                        "         JOIN exp.PropertyDomain PD ON D.propertyId = PD.propertyid\n" +
                        "         JOIN exp.DomainDescriptor DD on PD.domainID = DD.domainId\n" +
                        "WHERE DD.storageSchemaName = ? AND (D.derivationDataScope = ? OR D.derivationDataScope IS NULL)", SampleTypeDomainKind.PROVISIONED_SCHEMA_NAME, ExpSchema.DerivationDataScopeType.ParentOnly.name()).getObject(Long.class));
                results.put("sampleTypeParentAndAliquotField", new SqlSelector(ExperimentService.get().getSchema(), "SELECT COUNT(DISTINCT D.PropertyURI) FROM\n" +
                        "     exp.PropertyDescriptor D \n" +
                        "         JOIN exp.PropertyDomain PD ON D.propertyId = PD.propertyid\n" +
                        "         JOIN exp.DomainDescriptor DD on PD.domainID = DD.domainId\n" +
                        "WHERE DD.storageSchemaName = ? AND D.derivationDataScope = ?", SampleTypeDomainKind.PROVISIONED_SCHEMA_NAME, ExpSchema.DerivationDataScopeType.All.name()).getObject(Long.class));

                results.put("attachmentColumnCount", new SqlSelector(ExperimentService.get().getSchema(), "SELECT COUNT(*) FROM exp.propertydescriptor WHERE rangeURI = ?", PropertyType.ATTACHMENT.getTypeUri()).getObject(Long.class));
                results.put("dataClassWithAttachmentColumnCount", new SqlSelector(ExperimentService.get().getSchema(), "SELECT COUNT(DISTINCT DD.DomainURI) FROM\n" +
                        "     exp.PropertyDescriptor D \n" +
                        "         JOIN exp.PropertyDomain PD ON D.propertyId = PD.propertyid\n" +
                        "         JOIN exp.DomainDescriptor DD on PD.domainID = DD.domainId\n" +
                        "WHERE DD.storageSchemaName = ? AND D.rangeURI = ?", DataClassDomainKind.PROVISIONED_SCHEMA_NAME, PropertyType.ATTACHMENT.getTypeUri()).getObject(Long.class));

                results.put("textChoiceColumnCount", new SqlSelector(ExperimentService.get().getSchema(), "SELECT COUNT(*) FROM exp.propertydescriptor WHERE concepturi = ?", TEXT_CHOICE_CONCEPT_URI).getObject(Long.class));

                results.put("domainsWithDateTimeColumnCount", new SqlSelector(ExperimentService.get().getSchema(), "SELECT COUNT(DISTINCT DD.DomainURI) FROM\n" +
                        "     exp.PropertyDescriptor D \n" +
                        "         JOIN exp.PropertyDomain PD ON D.propertyId = PD.propertyid\n" +
                        "         JOIN exp.DomainDescriptor DD on PD.domainID = DD.domainId\n" +
                        "WHERE D.rangeURI = ?", PropertyType.DATE_TIME.getTypeUri()).getObject(Long.class));

                results.put("domainsWithDateColumnCount", new SqlSelector(ExperimentService.get().getSchema(), "SELECT COUNT(DISTINCT DD.DomainURI) FROM\n" +
                        "     exp.PropertyDescriptor D \n" +
                        "         JOIN exp.PropertyDomain PD ON D.propertyId = PD.propertyid\n" +
                        "         JOIN exp.DomainDescriptor DD on PD.domainID = DD.domainId\n" +
                        "WHERE D.rangeURI = ?", PropertyType.DATE.getTypeUri()).getObject(Long.class));

                results.put("domainsWithTimeColumnCount", new SqlSelector(ExperimentService.get().getSchema(), "SELECT COUNT(DISTINCT DD.DomainURI) FROM\n" +
                        "     exp.PropertyDescriptor D \n" +
                        "         JOIN exp.PropertyDomain PD ON D.propertyId = PD.propertyid\n" +
                        "         JOIN exp.DomainDescriptor DD on PD.domainID = DD.domainId\n" +
                        "WHERE D.rangeURI = ?", PropertyType.TIME.getTypeUri()).getObject(Long.class));

                results.put("maxObjectObjectId", new SqlSelector(ExperimentService.get().getSchema(), "SELECT MAX(ObjectId) FROM exp.Object").getObject(Long.class));
                results.put("maxMaterialRowId", new SqlSelector(ExperimentService.get().getSchema(), "SELECT MAX(RowId) FROM exp.Material").getObject(Long.class));

                return results;
            });
        }
    }

    @Override
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

        int sampleTypeCount = SampleTypeService.get().getSampleTypes(c, null, false).size();
        if (sampleTypeCount > 0)
            list.add(sampleTypeCount + " Sample Type" + (sampleTypeCount > 1 ? "s" : ""));

        return list;
    }

    @Override
    public ArrayList<Summary> getDetailedSummary(Container c)
    {
        ArrayList<Summary> summaries = new ArrayList<>();
        User user = HttpView.currentContext().getUser();

        // Assay types
        int assayTypeCount = 0;
        for (ExpProtocol protocol : AssayService.get().getAssayProtocols(c))
        {
            if (protocol.getContainer().equals(c))
                assayTypeCount++;
        }
        if (assayTypeCount > 0)
            summaries.add(new Summary(assayTypeCount, "Assay Type"));

        // Run count
        int runGroupCount = ExperimentService.get().getExperiments(c, user, false, true).size();
        if (runGroupCount > 0)
            summaries.add(new Summary(runGroupCount, "Assay run"));

        // Number of Data Classes
        List<? extends ExpDataClass> dataClasses = ExperimentService.get().getDataClasses(c, user, false);
        int dataClassCount = dataClasses.size();
        if (dataClassCount > 0)
            summaries.add(new Summary(dataClassCount, "Data Class", "Data Classes"));

        // Individual Data Class row counts
        ExpSchema expSchema = new ExpSchema(user, c);

        // The table-level container filter is set to ensure data class types are included
        // that may not be defined in the target container but may have rows of data in the target container
        TableInfo dataClassesTable = ExpSchema.TableType.DataClasses.createTable(expSchema, null, ContainerFilter.Type.CurrentPlusProjectAndShared.create(c, user));

        // Issue 47919: The "DataCounts" column is filtered to only count data in the target container
        if (dataClassesTable instanceof ExpDataClassTableImpl)
            ((ExpDataClassTableImpl) dataClassesTable).setDataCountContainerFilter(ContainerFilter.Type.Current.create(c, user));

        Map<String, Object> dataClassResults = new TableSelector(dataClassesTable, dataClassesTable.getColumns("Name,DataCount"), null, null).getValueMap();
        for (String k : dataClassResults.keySet())
        {
            int count = ((Long) dataClassResults.get(k)).intValue();
            if (count != 0)
                summaries.add(new Summary(count, k));
        }

        // Sample Types
        int sampleTypeCount = SampleTypeService.get().getSampleTypes(c, null, false).size();
        if (sampleTypeCount > 0)
            summaries.add(new Summary(sampleTypeCount, "Sample Type"));

        // Individual Sample Type row counts
        UserSchema userSchema = QueryService.get().getUserSchema(user, c, SchemaKey.fromParts(ExpSchema.SCHEMA_NAME));
        ExpSampleTypeTable sampleTypeTable = ExperimentService.get().createSampleTypeTable(ExpSchema.TableType.SampleSets.toString(), userSchema, ContainerFilter.Type.CurrentPlusProjectAndShared.create(c, user));
        sampleTypeTable.populate();
        Map<String, Object> tsSamplesResults = new TableSelector(sampleTypeTable, sampleTypeTable.getColumns("Name,SampleCount"), null, null).getValueMap();
        for (String k : tsSamplesResults.keySet())
        {
            int count = ((Number) tsSamplesResults.get(k)).intValue();
            if (count != 0)
            {
                Summary s = k.equals("MixtureBatches")
                        ? new Summary(count, "Batch", "Batches") // Special handling for name replacement + pluralization
                        : new Summary(count, k);
                summaries.add(s);
            }
        }

        return summaries;
    }

    @Override
    @NotNull
    public Set<Class> getIntegrationTests()
    {
        return Set.of(
            DomainPropertyImpl.TestCase.class,
            ExpDataTableImpl.TestCase.class,
            ExperimentServiceImpl.TestCase.class,
            ExperimentServiceImpl.LineageQueryTestCase.class,
            ExperimentServiceImpl.ParseInputOutputAliasTestCase.class,
            ExperimentStressTest.class,
            LineagePerfTest.class,
            LineageTest.class,
            OntologyManager.TestCase.class,
            StorageProvisionerImpl.TestCase.class,
            UniqueValueCounterTestCase.class,
            PropertyServiceImpl.TestCase.class
        );
    }

    @Override
    public @NotNull List<Factory<Class<?>>> getIntegrationTestFactories()
    {
        ArrayList<Factory<Class<?>>> list = new ArrayList<>(super.getIntegrationTestFactories());
        list.add(new JspTestCase("/org/labkey/experiment/api/ExpDataClassDataTestCase.jsp"));
        list.add(new JspTestCase("/org/labkey/experiment/api/ExpSampleTypeTestCase.jsp"));
        return list;
    }

    @NotNull
    @Override
    public Set<Class> getUnitTests()
    {
        return Set.of(
            GraphAlgorithms.TestCase.class,
            LSIDRelativizer.TestCase.class,
            Lsid.TestCase.class,
            LsidUtils.TestCase.class,
            PropertyController.TestCase.class
        );
    }

    @Override
    @NotNull
    public Set<String> getSchemaNames()
    {
        return Set.of(
            ExpSchema.SCHEMA_NAME,
            DataClassDomainKind.PROVISIONED_SCHEMA_NAME,
            SampleTypeDomainKind.PROVISIONED_SCHEMA_NAME
        );
    }

    @NotNull
    @Override
    public Collection<String> getProvisionedSchemaNames()
    {
        return PageFlowUtil.set(DataClassDomainKind.PROVISIONED_SCHEMA_NAME, SampleTypeDomainKind.PROVISIONED_SCHEMA_NAME);
    }
}
