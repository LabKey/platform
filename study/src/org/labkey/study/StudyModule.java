/*
 * Copyright (c) 2006-2017 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.labkey.api.admin.FolderSerializationRegistry;
import org.labkey.api.admin.notification.NotificationService;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.data.views.DataViewService;
import org.labkey.api.exp.ExperimentRunType;
import org.labkey.api.exp.LsidManager;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.AssayBatchDomainKind;
import org.labkey.api.exp.property.AssayResultDomainKind;
import org.labkey.api.exp.property.AssayRunDomainKind;
import org.labkey.api.exp.property.DefaultAssayDomainKind;
import org.labkey.api.exp.property.PlateBasedAssaySampleSetDomainKind;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.files.FileContentService;
import org.labkey.api.files.TableUpdaterFileListener;
import org.labkey.api.message.digest.ReportAndDatasetChangeDigestProvider;
import org.labkey.api.module.AdminLinkManager;
import org.labkey.api.module.DefaultFolderType;
import org.labkey.api.module.FolderTypeManager;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleLoader;
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
import org.labkey.api.study.ParticipantCategory;
import org.labkey.api.study.PlateService;
import org.labkey.api.study.SpecimenService;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudySerializationRegistry;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.StudyUrls;
import org.labkey.api.study.TimepointType;
import org.labkey.api.study.assay.AssayProviderSchema;
import org.labkey.api.study.assay.AssayPublishService;
import org.labkey.api.study.assay.AssayRunType;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.assay.AssayUrls;
import org.labkey.api.study.assay.ExperimentListenerImpl;
import org.labkey.api.study.assay.TsvDataHandler;
import org.labkey.api.study.reports.CrosstabReport;
import org.labkey.api.study.reports.CrosstabReportDescriptor;
import org.labkey.api.util.PageFlowUtil;
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
import org.labkey.study.assay.TsvAssayProvider;
import org.labkey.study.assay.query.AssayAuditProvider;
import org.labkey.study.assay.query.AssaySchemaImpl;
import org.labkey.study.audit.StudyAuditProvider;
import org.labkey.study.controllers.CohortController;
import org.labkey.study.controllers.CreateChildStudyAction;
import org.labkey.study.controllers.DatasetController;
import org.labkey.study.controllers.ParticipantGroupController;
import org.labkey.study.controllers.SharedStudyController;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.controllers.StudyDefinitionController;
import org.labkey.study.controllers.StudyDesignController;
import org.labkey.study.controllers.StudyPropertiesController;
import org.labkey.study.controllers.assay.AssayController;
import org.labkey.study.controllers.designer.DesignerController;
import org.labkey.study.controllers.plate.PlateController;
import org.labkey.study.controllers.reports.ReportsController;
import org.labkey.study.controllers.security.SecurityController;
import org.labkey.study.controllers.specimen.SpecimenApiController;
import org.labkey.study.controllers.specimen.SpecimenController;
import org.labkey.study.dataset.DatasetAuditProvider;
import org.labkey.study.dataset.DatasetNotificationInfoProvider;
import org.labkey.study.dataset.DatasetSnapshotProvider;
import org.labkey.study.dataset.DatasetViewProvider;
import org.labkey.study.designer.view.StudyDesignsWebPart;
import org.labkey.study.importer.DefaultSpecimenImportStrategyFactory;
import org.labkey.study.importer.MissingValueImporterFactory;
import org.labkey.study.importer.SpecimenImporter;
import org.labkey.study.importer.StudyImportProvider;
import org.labkey.study.importer.StudyImporterFactory;
import org.labkey.study.importer.StudyReload;
import org.labkey.study.model.*;
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
import org.labkey.study.reports.AssayProgressReport;
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
import org.labkey.study.security.permissions.ManageStudyPermission;
import org.labkey.study.security.roles.AssayDesignerRole;
import org.labkey.study.security.roles.SpecimenCoordinatorRole;
import org.labkey.study.security.roles.SpecimenRequesterRole;
import org.labkey.study.specimen.SpecimenCommentAuditProvider;
import org.labkey.study.specimen.SpecimenSearchWebPart;
import org.labkey.study.specimen.SpecimenWebPart;
import org.labkey.study.view.AssayBatchesWebPartFactory;
import org.labkey.study.view.AssayList2WebPartFactory;
import org.labkey.study.view.AssayListWebPartFactory;
import org.labkey.study.view.AssayResultsWebPartFactory;
import org.labkey.study.view.AssayRunsWebPartFactory;
import org.labkey.study.view.DatasetsWebPartView;
import org.labkey.study.view.StudyListWebPartFactory;
import org.labkey.study.view.StudySummaryWebPartFactory;
import org.labkey.study.view.StudyToolsWebPartFactory;
import org.labkey.study.view.SubjectDetailsWebPartFactory;
import org.labkey.study.view.SubjectsWebPart;
import org.labkey.study.view.specimen.SpecimenRequestNotificationEmailTemplate;
import org.labkey.study.view.studydesign.AssayScheduleWebpartFactory;
import org.labkey.study.view.studydesign.ImmunizationScheduleWebpartFactory;
import org.labkey.study.view.studydesign.VaccineDesignWebpartFactory;
import org.labkey.study.writer.DatasetDataWriter;
import org.labkey.study.writer.DefaultStudyDesignWriter;
import org.labkey.study.writer.MissingValueWriterFactory;
import org.labkey.study.writer.SpecimenWriter;
import org.labkey.study.writer.StudySerializationRegistryImpl;
import org.labkey.study.writer.StudyWriterFactory;

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
import java.util.stream.Collectors;


public class StudyModule extends SpringModule implements SearchService.DocumentProvider
{
    public static final String MODULE_NAME = "Study";

    public static final BaseWebPartFactory reportsPartFactory = new ReportsWebPartFactory();
    public static final WebPartFactory samplesPartFactory = new SamplesWebPartFactory();
    public static final WebPartFactory subjectsWebPartFactory = new SubjectsWebPartFactory();
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
    public static final WebPartFactory studyScheduleWebPartFactory = new StudyScheduleWebPartFactory();
    public static final WebPartFactory dataToolsWebPartFactory = new StudyToolsWebPartFactory.Data();
    public static final WebPartFactory specimenToolsWebPartFactory = new StudyToolsWebPartFactory.Specimens();
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
        return 18.10;
    }

    protected void init()
    {
        addController("study", StudyController.class);
        addController("study-shared", SharedStudyController.class);
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
        ServiceRegistry.get().registerService(StudyService.class, StudyServiceImpl.INSTANCE);
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
        PropertyService.get().registerDomainKind(new LocationDomainKind());
        PropertyService.get().registerDomainKind(new PrimaryTypeDomainKind());
        PropertyService.get().registerDomainKind(new DerivativeTypeDomainKind());
        PropertyService.get().registerDomainKind(new AdditiveTypeDomainKind());
        PropertyService.get().registerDomainKind(new SpecimenDomainKind());
        PropertyService.get().registerDomainKind(new VialDomainKind());
        PropertyService.get().registerDomainKind(new SpecimenEventDomainKind());
        PropertyService.get().registerDomainKind(new StudyPersonnelDomainKind());

        // study design domains
        PropertyService.get().registerDomainKind(new StudyProductDomainKind());
        PropertyService.get().registerDomainKind(new StudyProductAntigenDomainKind());
        PropertyService.get().registerDomainKind(new StudyTreatmentProductDomainKind());
        PropertyService.get().registerDomainKind(new StudyTreatmentDomainKind());

        QuerySnapshotService.registerProvider(StudySchema.getInstance().getSchemaName(), DatasetSnapshotProvider.getInstance());

        ServiceRegistry.get().registerService(StudySerializationRegistry.class, StudySerializationRegistryImpl.get());

        ExperimentService.get().registerExperimentDataHandler(new FileBasedModuleDataHandler());
        ExperimentService.get().addExperimentListener(new ExperimentListenerImpl());
        
        // Register early so file-based assays are available to Java code at upgrade time
        ExperimentService.get().registerExperimentDataHandler(new TsvDataHandler());
        DataViewService.get().registerProvider(DatasetViewProvider.TYPE, new DatasetViewProvider());
        DataViewService.get().registerProvider(ReportViewProvider.TYPE, new ReportViewProvider());

        EmailTemplateService.get().registerTemplate(SpecimenRequestNotificationEmailTemplate.class);

        NotificationService.get().registerNotificationType(ParticipantCategory.SEND_PARTICIPANT_GROUP_TYPE, "Study", "fa-users");

        // Register early so these roles are available to Java code at upgrade time
        RoleManager.registerRole(new SpecimenCoordinatorRole());
        RoleManager.registerRole(new SpecimenRequesterRole());
        RoleManager.registerRole(new AssayDesignerRole());

        AttachmentService.get().registerAttachmentType(ProtocolDocumentType.get());
        AttachmentService.get().registerAttachmentType(SpecimenRequestEventType.get());
    }

    public boolean hasScripts()
    {
        return true;
    }

    @NotNull
    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return new ArrayList<>(Arrays.asList(reportsPartFactory, samplesPartFactory,
                datasetsPartFactory, manageStudyPartFactory,
                enrollmentChartPartFactory, studyDesignsWebPartFactory, studyDesignSummaryWebPartFactory,
                assayListWebPartFactory, assayBatchesWebPartFactory, assayRunsWebPartFactory, assayResultsWebPartFactory,
                subjectDetailsWebPartFactory, assayList2WebPartFactory, studyListWebPartFactory, sampleSearchPartFactory,
                subjectsWebPartFactory, dataToolsWebPartFactory,
                specimenToolsWebPartFactory,
                specimenReportWebPartFactory, studyScheduleWebPartFactory,
                assayScheduleWebPartFactory, vaccineDesignWebPartFactory, immunizationScheduleWebpartFactory,
                new SharedStudyController.StudyFilterWebPartFactory()));
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
            int datasets = study.getDatasets().size();
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
        AssayService.get().registerAssayProvider(new TsvAssayProvider());
        ExperimentService.get().registerExperimentRunTypeSource(container -> {
            Set<ExperimentRunType> result = new HashSet<>();
            if (container != null)
            {
                result.addAll(AssayService.get().getAssayProtocols(container)
                    .stream()
                    .map(protocol -> new AssayRunType(protocol, container))
                    .collect(Collectors.toList()));
            }
            return result;
        });
        AuditLogService.get().registerAuditType(new AssayAuditProvider());
        AuditLogService.get().registerAuditType(new DatasetAuditProvider());
        AuditLogService.get().registerAuditType(new StudyAuditProvider());
        AuditLogService.get().registerAuditType(new SpecimenCommentAuditProvider());

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
        ReportService.get().registerReport(new AssayProgressReport());

        ReportService.get().registerDescriptor(new ChartReportView.ChartReportViewDescriptor());
        ReportService.get().registerDescriptor(new CrosstabReportDescriptor());
        ReportService.get().registerDescriptor(new ParticipantReportDescriptor());

        ReportService.get().addUIProvider(new StudyReportUIProvider());

        SearchService ss = SearchService.get();

        if (null != ss)
        {
            ss.addSearchCategory(StudyManager.subjectCategory);
            ss.addSearchCategory(StudyManager.datasetCategory);
            ss.addSearchCategory(StudyManager.assayCategory);
            ss.addDocumentProvider(this);
        }

        SystemMaintenance.addTask(new PurgeParticipantsMaintenanceTask());
        SystemMaintenance.addTask(new SpecimenRefreshMaintainanceTask());
        SystemMaintenance.addTask(new DefragmentParticipantVisitIndexesTask());

        FolderSerializationRegistry folderRegistry = ServiceRegistry.get().getService(FolderSerializationRegistry.class);
        if (null != folderRegistry)
        {
            folderRegistry.addFactories(new MissingValueWriterFactory(), new MissingValueImporterFactory());
            folderRegistry.addFactories(new StudyWriterFactory(), new StudyImporterFactory());
        }

        FileContentService.get().addFileListener(new TableUpdaterFileListener(StudySchema.getInstance().getTableInfoUploadLog(), "FilePath", TableUpdaterFileListener.Type.filePath, "RowId"));

        DatasetDefinition.cleanupOrphanedDatasetDomains();

        AdminConsole.addExperimentalFeatureFlag(CreateChildStudyAction.CREATE_SPECIMEN_STUDY, "Create Specimen Study",
            "Adds a button to the specimen request details page that creates a new child study containing the selected specimens, associated participants, and selected datasets.", false);
        AdminConsole.addExperimentalFeatureFlag(StudyQuerySchema.EXPERIMENTAL_STUDY_SUBSCHEMAS, "Use sub-schemas in Study",
                "Separate study tables into three groups 'datasets', 'specimens', and 'design'", false);

        ReportAndDatasetChangeDigestProvider.get().addNotificationInfoProvider(new DatasetNotificationInfoProvider());

        AdminLinkManager.getInstance().addListener((adminNavTree, container, user) -> {
            // Need only read permissions to view manage assays page
            if (container.hasPermission(user, ReadPermission.class))
            {
                adminNavTree.addChild(new NavTree("Manage Assays", PageFlowUtil.urlProvider(AssayUrls.class).getAssayListURL(container)));

                if (container.getActiveModules().contains(StudyModule.this) && container.hasPermission(user, ManageStudyPermission.class))
                    adminNavTree.addChild(new NavTree("Manage Study", PageFlowUtil.urlProvider(StudyUrls.class).getManageStudyURL(container)));
            }
        });
    }

    @Override
    public void startBackgroundThreads()
    {
        StudyReload.initializeAllTimers();
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

    private void registerFolderTypes()
    {
        FolderTypeManager.get().registerFolderType(this, new StudyFolderType(this, getActiveModulesForStudyFolder()));
        FolderTypeManager.get().registerFolderType(this, new AssayFolderType(this));
        FolderTypeManager.get().registerFolderType(this, new DataspaceStudyFolderType(this, getActiveModulesForDataspaceFolder()));
    }

    private Set<Module> getActiveModulesForStudyFolder()
    {
        Set<Module> active = DefaultFolderType.getDefaultModuleSet();
        active.add(this);
        Set<String> dependencies = getModuleDependenciesAsSet();
        for (String moduleName : dependencies)
            active.add(ModuleLoader.getInstance().getModule(moduleName));
        return active;
    }

    private Set<Module> getActiveModulesForDataspaceFolder()
    {
        Set<Module> active = getActiveModulesForStudyFolder();
        Module cds = ModuleLoader.getInstance().getModule("CDS");
        if (null != cds)
            active.add(cds);
        return active;
    }

    private static class ReportsWebPartFactory extends BaseWebPartFactory
    {
        public ReportsWebPartFactory()
        {
            super("Views", WebPartFactory.LOCATION_BODY, WebPartFactory.LOCATION_RIGHT);
            addLegacyNames("Reports", "Reports and Views");
        }

        public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart)
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
            super("Study Schedule", false, false, WebPartFactory.LOCATION_BODY);
        }

        @Override
        public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart)
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
        public SamplesWebPartFactory()
        {
            super("Specimens", SpecimenWebPart.class, WebPartFactory.LOCATION_BODY, WebPartFactory.LOCATION_RIGHT);
            addLegacyNames("Specimen Browse (Experimental)");
        }

        @Override
        public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart)
        {
            if (!portalCtx.hasPermission(ReadPermission.class))
                return new HtmlView("Specimens", portalCtx.getUser().isGuest() ? "Please log in to see this data." : "You do not have permission to see this data");

            StudyImpl study = StudyManager.getInstance().getStudy(portalCtx.getContainer());
            if (null == study)
                return new HtmlView("Specimens", "This folder does not contain a study.");
            return new SpecimenWebPart(webPart.getLocation().equals(HttpView.BODY), study);
        }
    }

    private static class SampleSearchWebPartFactory extends DefaultWebPartFactory
    {
        public SampleSearchWebPartFactory(String position)
        {
            super("Specimen Search", SpecimenSearchWebPart.class, position);
            addLegacyNames("Specimen Search (Experimental)");
        }

        @Override
        public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart)
        {
            if (!portalCtx.hasPermission(ReadPermission.class))
                return new HtmlView("Specimens", portalCtx.getUser().isGuest() ? "Please log in to see this data." : "You do not have permission to see this data");

            if (null == StudyManager.getInstance().getStudy(portalCtx.getContainer()))
                return new HtmlView("Specimens", "This folder does not contain a study.");
            return new SpecimenSearchWebPart(true);
        }
    }

    private static class SubjectsWebPartFactory extends DefaultWebPartFactory
    {
        public SubjectsWebPartFactory()
        {
            super("Subject List", SubjectsWebPart.class, WebPartFactory.LOCATION_BODY, WebPartFactory.LOCATION_RIGHT);
        }

        @Override
        public String getDisplayName(Container container, String location)
        {
            return StudyModule.getWebPartSubjectNoun(container) + " List";
        }

        @Override
        public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart)
        {
            if (!portalCtx.hasPermission(ReadPermission.class))
                return new HtmlView(getDisplayName(portalCtx.getContainer(),  webPart.getLocation()), portalCtx.getUser().isGuest() ? "Please log in to see this data." : "You do not have permission to see this data");

            if (null == StudyManager.getInstance().getStudy(portalCtx.getContainer()))
                return new HtmlView("Subject List", "This folder does not contain a study.");
            return new SubjectsWebPart(portalCtx, HttpView.BODY.equals(webPart.getLocation()), webPart.getIndex());
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


        public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart)
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

        public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart)
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
        public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart)
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

        public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart)
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
        set.add(DatasetDefinition.TestCleanupOrphanedDatasetDomains.class);
        set.add(StudyManager.VisitCreationTestCase.class);
        set.add(TreatmentManager.TreatmentDataTestCase.class);
        set.add(StudyManager.AssayScheduleTestCase.class);
        set.add(AssayManager.TestCase.class);
        set.add(VisitImpl.TestCase.class);

        return set;
    }

    @Override
    @NotNull
    public Set<Class> getUnitTests()
    {
        Set<Class> set = new HashSet<>();
        set.add(SampleMindedTransformTask.TestCase.class);
        set.add(DatasetDataWriter.TestCase.class);
        set.add(SpecimenWriter.TestCase.class);
        set.add(TsvAssayProvider.TestCase.class);
        set.add(AssaySchemaImpl.TestCase.class);
        set.add(AssayProviderSchema.TestCase.class);
        set.add(SequenceNumImportHelper.SequenceNumTest.class);
        set.add(ParticipantIdImportHelper.ParticipantIdTest.class);
        set.add(DefaultStudyDesignWriter.TestCase.class);
        return set;
    }

    @Override
    public UpgradeCode getUpgradeCode()
    {
        return new StudyUpgradeCode();
    }

    public void enumerateDocuments(@NotNull SearchService.IndexTask task, @NotNull Container c, Date modifiedSince)
    {
        StudyManager._enumerateDocuments(task, c);
    }
    

    public void indexDeleted()
    {
    }

    @NotNull
    @Override
    public JSONObject getPageContextJson(ViewContext context)
    {
        Container c = context.getContainer();
        Map<String, String> moduleProperties = getDefaultPageContextJson(c);
        Study study = StudyManager.getInstance().getStudy(c);
        StudyService studyService = StudyService.get();
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
