/*
 * Copyright (c) 2006-2014 LabKey Corporation
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

package org.labkey.study;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.labkey.api.admin.FolderSerializationRegistry;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.EnumConverter;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.data.views.DataViewService;
import org.labkey.api.exp.ExperimentRunType;
import org.labkey.api.exp.ExperimentRunTypeSource;
import org.labkey.api.exp.LsidManager;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.AssayBatchDomainKind;
import org.labkey.api.exp.property.AssayResultDomainKind;
import org.labkey.api.exp.property.AssayRunDomainKind;
import org.labkey.api.exp.property.DefaultAssayDomainKind;
import org.labkey.api.exp.property.PlateBasedAssaySampleSetDomainKind;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.files.FileContentService;
import org.labkey.api.files.TableUpdaterFileListener;
import org.labkey.api.message.digest.DailyMessageDigest;
import org.labkey.api.message.digest.ReportContentDigestProvider;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.ModuleResourceLoader;
import org.labkey.api.module.SpringModule;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.snapshot.QuerySnapshotService;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AdminConsole;
import org.labkey.api.study.PlateService;
import org.labkey.api.study.SpecimenService;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudySerializationRegistry;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.TimepointType;
import org.labkey.api.study.assay.AssayProviderSchema;
import org.labkey.api.study.assay.AssayPublishService;
import org.labkey.api.study.assay.AssayRunType;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.assay.TsvDataHandler;
import org.labkey.api.study.reports.CrosstabReport;
import org.labkey.api.study.reports.CrosstabReportDescriptor;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.StartupListener;
import org.labkey.api.util.SystemMaintenance;
import org.labkey.api.util.emailTemplate.EmailTemplateService;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.BaseWebPartFactory;
import org.labkey.api.view.DefaultWebPartFactory;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;
import org.labkey.api.wiki.WikiService;
import org.labkey.pipeline.xml.AssayImportRunTaskType;
import org.labkey.study.assay.AssayManager;
import org.labkey.study.assay.AssayPublishManager;
import org.labkey.study.assay.FileBasedModuleDataHandler;
import org.labkey.study.assay.ModuleAssayLoader;
import org.labkey.study.assay.TsvAssayProvider;
import org.labkey.study.assay.query.AssayAuditProvider;
import org.labkey.study.assay.query.AssayAuditViewFactory;
import org.labkey.study.assay.query.AssaySchemaImpl;
import org.labkey.study.controllers.CohortController;
import org.labkey.study.controllers.CreateChildStudyAction;
import org.labkey.study.controllers.DatasetController;
import org.labkey.study.controllers.ParticipantGroupController;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.controllers.StudyDefinitionController;
import org.labkey.study.controllers.StudyDesignController;
import org.labkey.study.controllers.StudyPropertiesController;
import org.labkey.study.controllers.assay.AssayController;
import org.labkey.study.controllers.designer.DesignerController;
import org.labkey.study.controllers.plate.PlateController;
import org.labkey.study.controllers.reports.ReportsController;
import org.labkey.study.controllers.samples.SpecimenApiController;
import org.labkey.study.controllers.samples.SpecimenController;
import org.labkey.study.controllers.security.SecurityController;
import org.labkey.study.dataset.DatasetAuditProvider;
import org.labkey.study.dataset.DatasetAuditViewFactory;
import org.labkey.study.dataset.DatasetReportInfoProvider;
import org.labkey.study.dataset.DatasetSnapshotProvider;
import org.labkey.study.dataset.DatasetViewProvider;
import org.labkey.study.designer.view.StudyDesignsWebPart;
import org.labkey.study.importer.DefaultSpecimenImportStrategyFactory;
import org.labkey.study.importer.MissingValueImporterFactory;
import org.labkey.study.importer.SpecimenImporter;
import org.labkey.study.importer.StudyImportProvider;
import org.labkey.study.importer.StudyImporterFactory;
import org.labkey.study.importer.StudyReload;
import org.labkey.study.model.CohortDomainKind;
import org.labkey.study.model.ContinuousDatasetDomainKind;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.DateDatasetDomainKind;
import org.labkey.study.model.ParticipantGroupManager;
import org.labkey.study.model.ParticipantIdImportHelper;
import org.labkey.study.model.SecurityType;
import org.labkey.study.model.SequenceNumImportHelper;
import org.labkey.study.model.SpecimenDomainKind;
import org.labkey.study.model.SpecimenEventDomainKind;
import org.labkey.study.model.StudyDomainKind;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyLsidHandler;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.TestDatasetDomainKind;
import org.labkey.study.model.TreatmentManager;
import org.labkey.study.model.VialDomainKind;
import org.labkey.study.model.VisitDatasetDomainKind;
import org.labkey.study.pipeline.AssayImportRunTask;
import org.labkey.study.pipeline.SampleMindedTransform;
import org.labkey.study.pipeline.SampleMindedTransformTask;
import org.labkey.study.pipeline.StudyPipeline;
import org.labkey.study.plate.PlateManager;
import org.labkey.study.plate.query.PlateSchema;
import org.labkey.study.query.StudyPersonnelDomainKind;
import org.labkey.study.query.StudyQuerySchema;
import org.labkey.study.query.StudySchemaProvider;
import org.labkey.study.query.studydesign.StudyProductAntigenDomainKind;
import org.labkey.study.query.studydesign.StudyProductDomainKind;
import org.labkey.study.query.studydesign.StudyTreatmentDomainKind;
import org.labkey.study.query.studydesign.StudyTreatmentProductDomainKind;
import org.labkey.study.reports.ChartReportView;
import org.labkey.study.reports.EnrollmentReport;
import org.labkey.study.reports.ExportExcelReport;
import org.labkey.study.reports.ExternalReport;
import org.labkey.study.reports.ParticipantReport;
import org.labkey.study.reports.ParticipantReportDescriptor;
import org.labkey.study.reports.ReportViewProvider;
import org.labkey.study.reports.StudyChartQueryReport;
import org.labkey.study.reports.StudyCrosstabReport;
import org.labkey.study.reports.StudyQueryReport;
import org.labkey.study.reports.StudyRReport;
import org.labkey.study.reports.StudyReportUIProvider;
import org.labkey.study.reports.WindowsCommandLineSplitter;
import org.labkey.study.samples.SampleSearchWebPart;
import org.labkey.study.samples.SamplesWebPart;
import org.labkey.study.samples.SpecimenCommentAuditProvider;
import org.labkey.study.samples.SpecimenCommentAuditViewFactory;
import org.labkey.study.security.permissions.ManageStudyPermission;
import org.labkey.study.security.roles.AssayDesignerRole;
import org.labkey.study.security.roles.SpecimenCoordinatorRole;
import org.labkey.study.security.roles.SpecimenRequesterRole;
import org.labkey.study.view.AssayBatchesWebPartFactory;
import org.labkey.study.view.AssayList2WebPartFactory;
import org.labkey.study.view.AssayListWebPartFactory;
import org.labkey.study.view.AssayResultsWebPartFactory;
import org.labkey.study.view.AssayRunsWebPartFactory;
import org.labkey.study.view.studydesign.AssayScheduleWebpartFactory;
import org.labkey.study.view.DatasetsWebPartView;
import org.labkey.study.view.StudyListWebPartFactory;
import org.labkey.study.view.StudySummaryWebPartFactory;
import org.labkey.study.view.StudyToolsWebPartFactory;
import org.labkey.study.view.StudyViewLoader;
import org.labkey.study.view.SubjectDetailsWebPartFactory;
import org.labkey.study.view.SubjectsWebPart;
import org.labkey.study.view.samples.SpecimenRequestNotificationEmailTemplate;
import org.labkey.study.view.studydesign.ImmunizationScheduleWebpartFactory;
import org.labkey.study.view.studydesign.VaccineDesignWebpartFactory;
import org.labkey.study.writer.DatasetWriter;
import org.labkey.study.writer.MissingValueWriterFactory;
import org.labkey.study.writer.SpecimenWriter;
import org.labkey.study.writer.StudySerializationRegistryImpl;
import org.labkey.study.writer.StudyWriterFactory;

import javax.servlet.ServletContext;
import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;


public class StudyModule extends SpringModule implements SearchService.DocumentProvider
{
    public static final String MODULE_NAME = "Study";

    public static final BaseWebPartFactory reportsPartFactory = new ReportsWebPartFactory();
    public static final WebPartFactory reportsWidePartFactory = new ReportsWideWebPartFactory();
    public static final WebPartFactory samplesPartFactory = new SamplesWebPartFactory(WebPartFactory.LOCATION_RIGHT);
    public static final WebPartFactory samplesWidePartFactory = new SamplesWebPartFactory(HttpView.BODY);
    public static final WebPartFactory subjectsWideWebPartFactory = new SubjectsWebPartFactory(HttpView.BODY);
    public static final WebPartFactory subjectsWebPartFactory = new SubjectsWebPartFactory(WebPartFactory.LOCATION_RIGHT);
    public static final WebPartFactory sampleSearchPartFactory = new SampleSearchWebPartFactory(HttpView.BODY);
    public static final WebPartFactory datasetsPartFactory = new DatasetsWebPartFactory();
    public static final WebPartFactory manageStudyPartFactory = new StudySummaryWebPartFactory();
    public static final WebPartFactory enrollmentChartPartFactory = new EnrollmentChartWebPartFactory();
    public static final WebPartFactory studyDesignsWebPartFactory = new StudyDesignsWebPartFactory();
    public static final WebPartFactory studyDesignSummaryWebPartFactory = new StudyDesignSummaryWebPartFactory();
    public static final WebPartFactory assayListWebPartFactory = new AssayListWebPartFactory();
    public static final WebPartFactory assayBatchesWebPartFactory = new AssayBatchesWebPartFactory();
    public static final WebPartFactory assayRunsWebPartFactory = new AssayRunsWebPartFactory();
    public static final WebPartFactory assayResultsWebPartFactory = new AssayResultsWebPartFactory();
    public static final WebPartFactory subjectDetailsWebPartFactory = new SubjectDetailsWebPartFactory();
    public static final WebPartFactory assayList2WebPartFactory = new AssayList2WebPartFactory();
    public static final WebPartFactory studyListWebPartFactory = new StudyListWebPartFactory();
    public static final WebPartFactory dataToolsWideWebPartFactory = new StudyToolsWebPartFactory.Data(HttpView.BODY);
    public static final WebPartFactory studyScheduleWebPartFactory = new StudyScheduleWebPartFactory();
    public static final WebPartFactory dataToolsWebPartFactory = new StudyToolsWebPartFactory.Data(WebPartFactory.LOCATION_RIGHT);
    public static final WebPartFactory specimenToolsWideWebPartFactory = new StudyToolsWebPartFactory.Specimens(HttpView.BODY);
    public static final WebPartFactory specimenToolsWebPartFactory = new StudyToolsWebPartFactory.Specimens(WebPartFactory.LOCATION_RIGHT);
    public static final WebPartFactory specimenReportWebPartFactory = new SpecimenController.SpecimenReportWebPartFactory();
    public static final WebPartFactory assayScheduleWebPartFactory = new AssayScheduleWebpartFactory();
    public static final WebPartFactory vaccineDesignWebPartFactory = new VaccineDesignWebpartFactory();
    public static final WebPartFactory immunizationScheduleWebpartFactory = new ImmunizationScheduleWebpartFactory();

    public String getName()
    {
        return MODULE_NAME;
    }

    public double getVersion()
    {
        return 14.18;
    }

    protected void init()
    {
        addController("study", StudyController.class);
        addController("study-reports", ReportsController.class);
        addController("study-samples", SpecimenController.class);
        addController("study-samples-api", SpecimenApiController.class);
        addController("study-security", SecurityController.class);
        addController("study-designer", DesignerController.class);
        addController("plate", PlateController.class);
        addController("assay", AssayController.class);
        addController("dataset", DatasetController.class);
        addController("study-definition", StudyDefinitionController.class);
        addController("cohort", CohortController.class);
        addController("study-properties", StudyPropertiesController.class);
        addController("participant-group", ParticipantGroupController.class);
        addController("study-design", StudyDesignController.class);

        PlateService.register(new PlateManager());
        AssayService.setInstance(new AssayManager());
        StudyService.register(StudyServiceImpl.INSTANCE);
        DefaultSchema.registerProvider(StudyQuerySchema.SCHEMA_NAME, new StudySchemaProvider(this));
        DefaultSchema.registerProvider(PlateSchema.SCHEMA_NAME, new PlateSchema.Provider(this));
        DefaultSchema.registerProvider(AssaySchemaImpl.NAME, new AssaySchemaImpl.Provider(this));

        PropertyService.get().registerDomainKind(new VisitDatasetDomainKind());
        PropertyService.get().registerDomainKind(new DateDatasetDomainKind());
        PropertyService.get().registerDomainKind(new ContinuousDatasetDomainKind());
        PropertyService.get().registerDomainKind(new TestDatasetDomainKind());
        PropertyService.get().registerDomainKind(new DefaultAssayDomainKind());
        PropertyService.get().registerDomainKind(new AssayBatchDomainKind());
        PropertyService.get().registerDomainKind(new AssayRunDomainKind());
        PropertyService.get().registerDomainKind(new AssayResultDomainKind());
        PropertyService.get().registerDomainKind(new PlateBasedAssaySampleSetDomainKind());
        PropertyService.get().registerDomainKind(new CohortDomainKind());
        PropertyService.get().registerDomainKind(new StudyDomainKind());
        PropertyService.get().registerDomainKind(new SpecimenDomainKind());
        PropertyService.get().registerDomainKind(new VialDomainKind());
        PropertyService.get().registerDomainKind(new SpecimenEventDomainKind());
        PropertyService.get().registerDomainKind(new StudyPersonnelDomainKind());

        // study design domains
        PropertyService.get().registerDomainKind(new StudyProductDomainKind());
        PropertyService.get().registerDomainKind(new StudyProductAntigenDomainKind());
        PropertyService.get().registerDomainKind(new StudyTreatmentProductDomainKind());
        PropertyService.get().registerDomainKind(new StudyTreatmentDomainKind());

        EnumConverter.registerEnum(SecurityType.class);
        EnumConverter.registerEnum(TimepointType.class);
        QuerySnapshotService.registerProvider(StudySchema.getInstance().getSchemaName(), DatasetSnapshotProvider.getInstance());

        ServiceRegistry.get().registerService(StudySerializationRegistry.class, StudySerializationRegistryImpl.get());

        ExperimentService.get().registerExperimentDataHandler(new FileBasedModuleDataHandler());
        DataViewService.get().registerProvider(DatasetViewProvider.TYPE, new DatasetViewProvider());
        DataViewService.get().registerProvider(ReportViewProvider.TYPE, new ReportViewProvider());

        EmailTemplateService.get().registerTemplate(SpecimenRequestNotificationEmailTemplate.class);
    }

    @NotNull
    @Override
    public Set<ModuleResourceLoader> getResourceLoaders()
    {
        return PageFlowUtil.set(new ModuleAssayLoader(), new StudyViewLoader());
    }

    public boolean hasScripts()
    {
        return true;
    }

    @NotNull
    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return new ArrayList<>(Arrays.asList(reportsPartFactory, reportsWidePartFactory, samplesPartFactory,
                samplesWidePartFactory, datasetsPartFactory, manageStudyPartFactory,
                enrollmentChartPartFactory, studyDesignsWebPartFactory, studyDesignSummaryWebPartFactory,
                assayListWebPartFactory, assayBatchesWebPartFactory, assayRunsWebPartFactory, assayResultsWebPartFactory,
                subjectDetailsWebPartFactory, assayList2WebPartFactory, studyListWebPartFactory, sampleSearchPartFactory,
                subjectsWebPartFactory, subjectsWideWebPartFactory, dataToolsWebPartFactory,
                dataToolsWideWebPartFactory, specimenToolsWebPartFactory, specimenToolsWideWebPartFactory,
                specimenReportWebPartFactory, studyScheduleWebPartFactory,
                assayScheduleWebPartFactory, vaccineDesignWebPartFactory, immunizationScheduleWebpartFactory));
    }

    @NotNull
    public Collection<String> getSummary(Container c)
    {
        Study study = StudyManager.getInstance().getStudy(c);

        if (study != null)
        {
            Collection<String> list = new LinkedList<>();
            list.add("Study: " + study.getLabel());
            long participants = StudyManager.getInstance().getParticipantCount(study);
            if (0 < participants)
                list.add("" + participants + " " + StudyService.get().getSubjectNounPlural(c));
            int datasets = study.getDataSets().size();
            if (0 < datasets)
                list.add("" + datasets + " datasets");
            return list;
        }
        else
            return Collections.emptyList();
    }


    @Override
    protected void startupAfterSpringConfig(ModuleContext moduleContext)
    {
        //register roles
        RoleManager.registerRole(new SpecimenCoordinatorRole());
        RoleManager.registerRole(new SpecimenRequesterRole());
        RoleManager.registerRole(new AssayDesignerRole());

        PipelineService.get().registerPipelineProvider(new StudyPipeline(this));
        PipelineService.get().registerPipelineProvider(new StudyImportProvider(this));

        PipelineJobService.get().registerTaskFactoryFactory(AssayImportRunTaskType.type, new AssayImportRunTask.FactoryFactory());

        // This is in the First group because when a container is deleted,
        // the Experiment listener needs to be called after the Study listener,
        // because Study needs the metadata held by Experiment to delete properly.
        ContainerManager.addContainerListener(new StudyContainerListener(), ContainerManager.ContainerListener.Order.First);
        AssayPublishService.register(new AssayPublishManager());
        SpecimenService.register(new SpecimenServiceImpl());
        SpecimenService.get().registerSpecimenImportStrategyFactory(new DefaultSpecimenImportStrategyFactory());
        SpecimenService.get().registerSpecimenTransform(new SampleMindedTransform());

        LsidManager.get().registerHandler("Study", new StudyLsidHandler());
        WikiService wikiService = ServiceRegistry.get().getService(WikiService.class);
        if(null != wikiService)
            wikiService.registerMacroProvider("study", new StudyMacroProvider());
        PlateManager.get().registerLsidHandlers();
        registerFolderTypes();
        SecurityManager.addViewFactory(new SecurityController.StudySecurityViewFactory());
        ExperimentService.get().registerExperimentDataHandler(new TsvDataHandler());
        AssayService.get().registerAssayProvider(new TsvAssayProvider());
        ExperimentService.get().registerExperimentRunTypeSource(new ExperimentRunTypeSource()
        {
            public Set<ExperimentRunType> getExperimentRunTypes(Container container)
            {
                Set<ExperimentRunType> result = new HashSet<>();
                for (final ExpProtocol protocol : AssayService.get().getAssayProtocols(container))
                {
                    result.add(new AssayRunType(protocol, container));
                }
                return result;
            }
        });
        AuditLogService.get().addAuditViewFactory(AssayAuditViewFactory.getInstance());
        AuditLogService.get().addAuditViewFactory(DatasetAuditViewFactory.getInstance());
        AuditLogService.get().addAuditViewFactory(SpecimenCommentAuditViewFactory.getInstance());

        AuditLogService.registerAuditType(new AssayAuditProvider());
        AuditLogService.registerAuditType(new DatasetAuditProvider());
        AuditLogService.registerAuditType(new SpecimenCommentAuditProvider());

        ReportService.get().registerReport(new StudyController.StudyChartReport());
        ReportService.get().registerReport(new EnrollmentReport());
        ReportService.get().registerReport(new StudyQueryReport());
        ReportService.get().registerReport(new ChartReportView.DatasetChartReport());
        ReportService.get().registerReport(new ExternalReport());
        ReportService.get().registerReport(new ExportExcelReport());
        ReportService.get().registerReport(new ChartReportView());
        ReportService.get().registerReport(new StudyChartQueryReport());
        ReportService.get().registerReport(new CrosstabReport());
        ReportService.get().registerReport(new StudyCrosstabReport());
        ReportService.get().registerReport(new StudyRReport());
        ReportService.get().registerReport(new ParticipantReport());

        ReportService.get().registerDescriptor(new ChartReportView.ChartReportViewDescriptor());
        ReportService.get().registerDescriptor(new CrosstabReportDescriptor());
        ReportService.get().registerDescriptor(new ParticipantReportDescriptor());

        ReportService.get().addViewFactory(new ReportsController.StudyRReportViewFactory());
        ReportService.get().addUIProvider(new StudyReportUIProvider());

        StudyReload.initializeAllTimers();

        SearchService ss = ServiceRegistry.get().getService(SearchService.class);

        if (null != ss)
        {
            ss.addSearchCategory(StudyManager.subjectCategory);
            ss.addSearchCategory(StudyManager.datasetCategory);
            ss.addSearchCategory(StudyManager.assayCategory);
            ss.addDocumentProvider(this);
        }

        SystemMaintenance.addTask(new PurgeParticipantsMaintenanceTask());
        SystemMaintenance.addTask(new SpecimenRefreshMaintainanceTask());

        FolderSerializationRegistry folderRegistry = ServiceRegistry.get().getService(FolderSerializationRegistry.class);
        if (null != folderRegistry)
        {
            folderRegistry.addFactories(new MissingValueWriterFactory(), new MissingValueImporterFactory());
            folderRegistry.addFactories(new StudyWriterFactory(), new StudyImporterFactory());
        }

        ServiceRegistry.get(FileContentService.class).addFileListener(new TableUpdaterFileListener(StudySchema.getInstance().getTableInfoUploadLog(), "FilePath", TableUpdaterFileListener.Type.filePath, "RowId"));

        try
        {
            DataSetDefinition.cleanupOrphanedDatasetDomains();
        }
        catch (SQLException sql)
        {
            Logger.getLogger(StudyModule.class).error("Error cleanup orphaned domains", sql);
        }

        AdminConsole.addExperimentalFeatureFlag(CreateChildStudyAction.CREATE_SPECIMEN_STUDY, "Create Specimen Study",
            "Adds a button to the specimen request details page that creates a new child study containing the selected specimens, associated participants, and selected datasets.", false);

        ReportContentDigestProvider reportContentDigestProvider = DailyMessageDigest.getInstance().getReportContentDigestProvider();
        if (null != reportContentDigestProvider)
            reportContentDigestProvider.addReportInfoProvider(new DatasetReportInfoProvider());

    }

    @Override
    public void afterUpdate(final ModuleContext moduleContext)
    {
        if (!moduleContext.isNewInstall() && moduleContext.getOriginalVersion() < 13.11)
        {
            ContextListener.addStartupListener("Study: upgrade dataset labels to names", new StartupListener()
            {
                public void moduleStartupComplete(ServletContext servletContext)
                {
                    StudyUpgradeCode.upgradeDatasetLabelsToNames(moduleContext);
                }
            });
        }
    }

    @Override
    @NotNull
    public Set<String> getSchemaNames()
    {
        HashSet<String> set = new HashSet<>();
        set.add(StudySchema.getInstance().getSchemaName());
        set.addAll(getProvisionedSchemaNames());

        return set;
    }

    @Override
    @NotNull
    public Set<String> getProvisionedSchemaNames()
    {
        return PageFlowUtil.set("studydataset", "assayresult", "studydesign", "specimentables");
    }

    @Override
    @NotNull
    public Set<DbSchema> getSchemasToTest()
    {
        // Don't test studydataset, assayresult, studydesign, and specimentables since they're dynamically generated
        return PageFlowUtil.set(StudySchema.getInstance().getSchema());
    }

    private void registerFolderTypes()
    {
        ModuleLoader.getInstance().registerFolderType(this, new StudyFolderType(this));
        ModuleLoader.getInstance().registerFolderType(this, new AssayFolderType(this));

        if (null != ModuleLoader.getInstance().getModule("Experiment"))
            ModuleLoader.getInstance().registerFolderType(this, new DataspaceStudyFolderType(this));
    }

    private static class ReportsWebPartFactory extends BaseWebPartFactory
    {
        public ReportsWebPartFactory()
        {
            super("Views", WebPartFactory.LOCATION_RIGHT);
            addLegacyNames("Reports", "Reports and Views");
        }

        public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws IllegalAccessException, InvocationTargetException
        {
            if (!portalCtx.hasPermission(ReadPermission.class))
                return new HtmlView("Views", portalCtx.getUser().isGuest() ? "Please log in to see this data." : "You do not have permission to see this data");

            if (null == StudyManager.getInstance().getStudy(portalCtx.getContainer()))
                return new HtmlView("Views", "This folder does not contain a study");
            return new ReportsController.ReportsWebPart(false);
        }
    }

    private static class ReportsWideWebPartFactory extends BaseWebPartFactory
    {
        public ReportsWideWebPartFactory()
        {
            super("Views");
            addLegacyNames("Reports", "Reports and Views");
        }

        public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws IllegalAccessException, InvocationTargetException
        {
            if (!portalCtx.hasPermission(ReadPermission.class))
                return new HtmlView("Views", portalCtx.getUser().isGuest() ? "Please log in to see this data." : "You do not have permission to see this data");

            if (null == StudyManager.getInstance().getStudy(portalCtx.getContainer()))
                return new HtmlView("Views", "This folder does not contain a study");

            return new ReportsController.ReportsWebPart(!WebPartFactory.LOCATION_RIGHT.equalsIgnoreCase(webPart.getLocation()));
        }
    }

    private static class StudyScheduleWebPartFactory extends BaseWebPartFactory
    {
        public StudyScheduleWebPartFactory()
        {
            super("Study Schedule", WebPartFactory.LOCATION_BODY, true, false); // is editable
        }

        @Override
        public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws Exception
        {
            JspView<Portal.WebPart> view = new JspView<>("/org/labkey/study/view/studySchedule.jsp", webPart);
            view.setTitle("Study Schedule");
            view.setFrame(WebPartView.FrameType.PORTAL);
            Container c = portalCtx.getContainer();
            Study study = StudyManager.getInstance().getStudy(c);
            String timepointMenuName;

            if (study != null && study.getTimepointType() == TimepointType.DATE)
            {
                timepointMenuName = "Manage Timepoints";
            }
            else
            {
                timepointMenuName = "Manage Visits";
            }

            if (c.hasPermission(portalCtx.getUser(), ManageStudyPermission.class))
            {
                NavTree menu = new NavTree();
                menu.addChild("Manage Datasets", new ActionURL(StudyController.ManageTypesAction.class, c));
                menu.addChild(timepointMenuName, new ActionURL(StudyController.ManageVisitsAction.class, c));
                view.setNavMenu(menu);
            }

            return view;
        }
    }

    private static class SamplesWebPartFactory extends DefaultWebPartFactory
    {
        public SamplesWebPartFactory(String position)
        {
            super("Specimens", position, SamplesWebPart.class);
            addLegacyNames("Specimen Browse (Experimental)");
        }

        @Override
        public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws IllegalAccessException, InvocationTargetException, InstantiationException
        {
            if (!portalCtx.hasPermission(ReadPermission.class))
                return new HtmlView("Specimens", portalCtx.getUser().isGuest() ? "Please log in to see this data." : "You do not have permission to see this data");

            StudyImpl study = StudyManager.getInstance().getStudy(portalCtx.getContainer());
            if (null == study)
                return new HtmlView("Specimens", "This folder does not contain a study.");
            return new SamplesWebPart(webPart.getLocation().equals(HttpView.BODY), study);
        }
    }

    private static class SampleSearchWebPartFactory extends DefaultWebPartFactory
    {
        public SampleSearchWebPartFactory(String position)
        {
            super("Specimen Search", position, SampleSearchWebPart.class);
            addLegacyNames("Specimen Search (Experimental)");
        }

        @Override
        public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws IllegalAccessException, InvocationTargetException, InstantiationException
        {
            if (!portalCtx.hasPermission(ReadPermission.class))
                return new HtmlView("Specimens", portalCtx.getUser().isGuest() ? "Please log in to see this data." : "You do not have permission to see this data");

            if (null == StudyManager.getInstance().getStudy(portalCtx.getContainer()))
                return new HtmlView("Specimens", "This folder does not contain a study.");
            return new SampleSearchWebPart(true);
        }
    }

    private static class SubjectsWebPartFactory extends DefaultWebPartFactory
    {
        public SubjectsWebPartFactory(String position)
        {
            super("Subject List", position, SubjectsWebPart.class);
        }

        @Override
        public String getDisplayName(Container container, String location)
        {
            return StudyModule.getWebPartSubjectNoun(container) + " List";
        }

        @Override
        public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws IllegalAccessException, InvocationTargetException, InstantiationException
        {
            if (!portalCtx.hasPermission(ReadPermission.class))
                return new HtmlView(getDisplayName(portalCtx.getContainer(),  webPart.getLocation()), portalCtx.getUser().isGuest() ? "Please log in to see this data." : "You do not have permission to see this data");

            if (null == StudyManager.getInstance().getStudy(portalCtx.getContainer()))
                return new HtmlView("Subject List", "This folder does not contain a study.");
            return new SubjectsWebPart(HttpView.BODY.equals(webPart.getLocation()), webPart.getIndex());
        }
    }

    public static String getWebPartSubjectNoun(Container container)
    {
        String subjectNoun = StudyService.get().getSubjectNounSingular(container);
        if (subjectNoun == null)
            subjectNoun = "Subject";
        if (!Character.isUpperCase(subjectNoun.charAt(0)))
            subjectNoun = Character.toUpperCase(subjectNoun.charAt(0)) + subjectNoun.substring(1);
        return subjectNoun;
    }

    private static class DatasetsWebPartFactory extends DefaultWebPartFactory
    {
        public DatasetsWebPartFactory()
        {
            super("Datasets", DatasetsWebPartView.class);
        }


        public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws IllegalAccessException, InvocationTargetException, InstantiationException
        {
            if (!portalCtx.hasPermission(ReadPermission.class))
                return new HtmlView("Datasets", portalCtx.getUser().isGuest() ? "Please log in to see this data." : "You do not have permission to see this data");

            if (null == StudyManager.getInstance().getStudy(portalCtx.getContainer()))
                return new HtmlView("Datasets", "This folder does not contain a study.");

            return new DatasetsWebPartView();
        }
    }


    private static class EnrollmentChartWebPartFactory extends BaseWebPartFactory
    {
        public EnrollmentChartWebPartFactory()
        {
            super("Enrollment Report");
        }

        public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws Exception
        {
            Container c = portalCtx.getContainer();
            Report report = EnrollmentReport.getEnrollmentReport(portalCtx.getUser(), StudyManager.getInstance().getStudy(c), true);
            WebPartView view = new EnrollmentReport.EnrollmentView(report);
            view.setFrame(WebPartView.FrameType.PORTAL);
            return view;
        }
    }

    private static class StudyDesignsWebPartFactory extends BaseWebPartFactory
    {
        public StudyDesignsWebPartFactory()
        {
            super("Vaccine Study Protocols");
            addLegacyNames("Study Designs");
        }
        public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws Exception
        {
            return new StudyDesignsWebPart(portalCtx, true);
        }
    }

    private static class StudyDesignSummaryWebPartFactory extends BaseWebPartFactory
    {
        public StudyDesignSummaryWebPartFactory()
        {
            super("Study Protocol Summary");
        }

        public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws Exception
        {
            JspView view = new JspView("/org/labkey/study/designer/view/studyDesignSummary.jsp");
            view.setTitle("Study Protocol Summary");
            view.setFrame(WebPartView.FrameType.PORTAL);
            return view;
        }
    }

    @Override
    @NotNull
    public Set<Class> getIntegrationTests()
    {
        Set<Class> set = new HashSet<>();
        set.add(SpecimenImporter.TestCase.class);
        set.add(StudyManager.DatasetImportTestCase.class);
        set.add(ParticipantGroupManager.ParticipantGroupTestCase.class);
        set.add(StudyImpl.ProtocolDocumentTestCase.class);
        set.add(DataSetDefinition.TestCleanupOrphanedDatasetDomains.class);
        set.add(StudyManager.VisitCreationTestCase.class);
        set.add(TreatmentManager.TreatmentDataTestCase.class);
        set.add(StudyManager.AssayScheduleTestCase.class);

        return set;
    }

    @Override
    @NotNull
    public Set<Class> getUnitTests()
    {
        Set<Class> set = new HashSet<>();
        set.add(SampleMindedTransformTask.TestCase.class);
        set.add(DatasetWriter.TestCase.class);
        set.add(SpecimenWriter.TestCase.class);
        set.add(TsvAssayProvider.TestCase.class);
        set.add(AssaySchemaImpl.TestCase.class);
        set.add(AssayProviderSchema.TestCase.class);
        set.add(WindowsCommandLineSplitter.TestCase.class);
        set.add(SequenceNumImportHelper.SequenceNumTest.class);
        set.add(ParticipantIdImportHelper.ParticipantIdTest.class);
        return set;
    }

    @Override
    public UpgradeCode getUpgradeCode()
    {
        return new StudyUpgradeCode();
    }

    public void enumerateDocuments(@NotNull final SearchService.IndexTask task, final Container c, final Date modifiedSince)
    {
        StudyManager._enumerateDocuments(task, c);
    }
    

    public void indexDeleted() throws SQLException
    {
    }

    @NotNull
    @Override
    public JSONObject getPageContextJson(ViewContext context)
    {
        Container c = context.getContainer();
        Map<String, String> moduleProperties = getDefaultPageContextJson(c);
        Study study = StudyManager.getInstance().getStudy(c);
        StudyService.Service studyService = StudyService.get();
        JSONObject ret = new JSONObject(moduleProperties);

        if (study != null)
        {
            JSONObject subject = new JSONObject();
            subject.put("tableName", studyService.getSubjectTableName(c));
            subject.put("nounSingular", studyService.getSubjectNounSingular(c));
            subject.put("nounPlural", studyService.getSubjectNounPlural(c));
            subject.put("columnName", studyService.getSubjectColumnName(c));

            ret.put("subject", subject);
            ret.put("timepointType", study.getTimepointType().name());
        }

        return ret;
    }
}
