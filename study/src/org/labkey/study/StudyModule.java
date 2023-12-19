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

package org.labkey.study;

import org.apache.commons.collections4.Factory;
import org.apache.commons.lang3.mutable.MutableInt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.admin.FolderSerializationRegistry;
import org.labkey.api.admin.notification.NotificationService;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.PropertySchema;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.data.views.DataViewService;
import org.labkey.api.exp.LsidManager;
import org.labkey.api.exp.api.ExperimentService;
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
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.qc.DataStateManager;
import org.labkey.api.qc.export.DataStateImportExportHelper;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.snapshot.QuerySnapshotService;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportContentEmailManager;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.QueryReport;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AdminConsole;
import org.labkey.api.specimen.SpecimenSampleTypeDomainKind;
import org.labkey.api.specimen.model.AdditiveTypeDomainKind;
import org.labkey.api.specimen.model.DerivativeTypeDomainKind;
import org.labkey.api.specimen.model.LocationDomainKind;
import org.labkey.api.specimen.model.PrimaryTypeDomainKind;
import org.labkey.api.specimen.model.SpecimenDomainKind;
import org.labkey.api.specimen.model.SpecimenEventDomainKind;
import org.labkey.api.specimen.model.VialDomainKind;
import org.labkey.api.study.ParticipantCategory;
import org.labkey.api.study.SpecimenService;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyInternalService;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.StudyUrls;
import org.labkey.api.study.TimepointType;
import org.labkey.api.study.importer.ImportHelperService;
import org.labkey.api.study.model.CohortService;
import org.labkey.api.study.model.ParticipantGroupService;
import org.labkey.api.study.model.VisitService;
import org.labkey.api.study.publish.StudyPublishService;
import org.labkey.api.study.reports.CrosstabReport;
import org.labkey.api.study.reports.CrosstabReportDescriptor;
import org.labkey.api.study.security.StudySecurityEscalationAuditProvider;
import org.labkey.api.study.security.permissions.ManageStudyPermission;
import org.labkey.api.usageMetrics.UsageMetricsService;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.JspTestCase;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.SystemMaintenance;
import org.labkey.api.util.UsageReportingLevel;
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
import org.labkey.api.wiki.WikiRenderingService;
import org.labkey.api.writer.ContainerUser;
import org.labkey.study.assay.ExperimentListenerImpl;
import org.labkey.study.assay.StudyPublishManager;
import org.labkey.study.assay.query.PublishAuditProvider;
import org.labkey.study.audit.ParticipantGroupAuditProvider;
import org.labkey.study.audit.StudyAuditProvider;
import org.labkey.study.controllers.CohortController;
import org.labkey.study.controllers.DatasetController;
import org.labkey.study.controllers.ParticipantGroupController;
import org.labkey.study.controllers.SharedStudyController;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.controllers.StudyDefinitionController;
import org.labkey.study.controllers.StudyDesignController;
import org.labkey.study.controllers.StudyPropertiesController;
import org.labkey.study.controllers.designer.DesignerController;
import org.labkey.study.controllers.publish.PublishController;
import org.labkey.study.controllers.reports.ReportsController;
import org.labkey.study.controllers.security.SecurityController;
import org.labkey.study.dataset.DatasetAuditProvider;
import org.labkey.study.dataset.DatasetNotificationInfoProvider;
import org.labkey.study.dataset.DatasetSnapshotProvider;
import org.labkey.study.dataset.DatasetViewProvider;
import org.labkey.study.designer.view.StudyDesignsWebPart;
import org.labkey.study.importer.StudyImporterFactory;
import org.labkey.study.model.*;
import org.labkey.study.pipeline.StudyPipeline;
import org.labkey.study.qc.StudyQCImportExportHelper;
import org.labkey.study.qc.StudyQCStateHandler;
import org.labkey.study.query.DatasetQueryView;
import org.labkey.study.query.StudyPersonnelDomainKind;
import org.labkey.study.query.StudyQuerySchema;
import org.labkey.study.query.StudySchemaProvider;
import org.labkey.study.query.studydesign.StudyProductAntigenDomainKind;
import org.labkey.study.query.studydesign.StudyProductDomainKind;
import org.labkey.study.query.studydesign.StudyTreatmentDomainKind;
import org.labkey.study.query.studydesign.StudyTreatmentProductDomainKind;
import org.labkey.study.reports.AssayProgressReport;
import org.labkey.study.reports.ExternalReport;
import org.labkey.study.reports.ParticipantReport;
import org.labkey.study.reports.ParticipantReportDescriptor;
import org.labkey.study.reports.StudyCrosstabReport;
import org.labkey.study.reports.StudyQueryReport;
import org.labkey.study.reports.StudyRReport;
import org.labkey.study.reports.StudyReportUIProvider;
import org.labkey.study.view.DatasetsWebPartView;
import org.labkey.study.view.StudyListWebPartFactory;
import org.labkey.study.view.StudySummaryWebPartFactory;
import org.labkey.study.view.StudyToolsWebPartFactory;
import org.labkey.study.view.SubjectDetailsWebPartFactory;
import org.labkey.study.view.SubjectsWebPart;
import org.labkey.study.view.studydesign.AssayScheduleWebpartFactory;
import org.labkey.study.view.studydesign.ImmunizationScheduleWebpartFactory;
import org.labkey.study.view.studydesign.VaccineDesignWebpartFactory;
import org.labkey.study.writer.DatasetDataWriter;
import org.labkey.study.writer.DefaultStudyDesignWriter;
import org.labkey.study.writer.StudyWriterFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.stream.Collectors;


public class StudyModule extends SpringModule implements SearchService.DocumentProvider
{
    public static final String MODULE_NAME = "Study";

    public static final BaseWebPartFactory reportsPartFactory = new ReportsWebPartFactory();
    public static final WebPartFactory assayScheduleWebPartFactory = new AssayScheduleWebpartFactory();
    public static final WebPartFactory dataToolsWebPartFactory = new StudyToolsWebPartFactory();
    public static final WebPartFactory datasetsPartFactory = new DatasetsWebPartFactory();
    public static final WebPartFactory immunizationScheduleWebpartFactory = new ImmunizationScheduleWebpartFactory();
    public static final WebPartFactory manageStudyPartFactory = new StudySummaryWebPartFactory();
    public static final WebPartFactory studyDesignSummaryWebPartFactory = new StudyDesignSummaryWebPartFactory();
    public static final WebPartFactory studyDesignsWebPartFactory = new StudyDesignsWebPartFactory();
    public static final WebPartFactory studyListWebPartFactory = new StudyListWebPartFactory();
    public static final WebPartFactory studyScheduleWebPartFactory = new StudyScheduleWebPartFactory();
    public static final WebPartFactory subjectDetailsWebPartFactory = new SubjectDetailsWebPartFactory();
    public static final WebPartFactory subjectsWebPartFactory = new SubjectsWebPartFactory();
    public static final WebPartFactory vaccineDesignWebPartFactory = new VaccineDesignWebpartFactory();

    @Override
    public String getName()
    {
        return MODULE_NAME;
    }

    @Override
    public Double getSchemaVersion()
    {
        return 23.001;
    }

    @Override
    protected void init()
    {
        addController("study", StudyController.class);  // Default controller for this module -- must be registered first!
        addController("cohort", CohortController.class);
        addController("dataset", DatasetController.class);
        addController("participant-group", ParticipantGroupController.class);
        addController("publish", PublishController.class);
        addController("study-definition", StudyDefinitionController.class);
        addController("study-design", StudyDesignController.class);
        addController("study-designer", DesignerController.class);
        addController("study-properties", StudyPropertiesController.class);
        addController("study-reports", ReportsController.class);
        addController("study-security", SecurityController.class);
        addController("study-shared", SharedStudyController.class);

        ServiceRegistry.get().registerService(StudyService.class, StudyServiceImpl.INSTANCE);
        DefaultSchema.registerProvider(StudyQuerySchema.SCHEMA_NAME, new StudySchemaProvider(this));
        ParticipantGroupService.setInstance(new ParticipantGroupServiceImpl());
        CohortService.setInstance(new CohortServiceImpl());
        VisitService.setInstance(new VisitServiceImpl());
        ImportHelperService.setInstance(new ImportHelperServiceImpl());
        StudyInternalService.setInstance(new StudyInternalServiceImpl());

        PropertyService.get().registerDomainKind(new VisitDatasetDomainKind());
        PropertyService.get().registerDomainKind(new DateDatasetDomainKind());
        PropertyService.get().registerDomainKind(new ContinuousDatasetDomainKind());
        PropertyService.get().registerDomainKind(new TestDatasetDomainKind());
        PropertyService.get().registerDomainKind(new CohortDomainKind());
        PropertyService.get().registerDomainKind(new StudyDomainKind());
        PropertyService.get().registerDomainKind(new StudyPersonnelDomainKind());

        // specimen-related domain kinds
        PropertyService.get().registerDomainKind(new AdditiveTypeDomainKind());
        PropertyService.get().registerDomainKind(new DerivativeTypeDomainKind());
        PropertyService.get().registerDomainKind(new PrimaryTypeDomainKind());
        PropertyService.get().registerDomainKind(new SpecimenDomainKind());
        PropertyService.get().registerDomainKind(new SpecimenEventDomainKind());
        PropertyService.get().registerDomainKind(new VialDomainKind());
        PropertyService.get().registerDomainKind(new LocationDomainKind());
        PropertyService.get().registerDomainKind(new SpecimenSampleTypeDomainKind());

        // study design domains
        PropertyService.get().registerDomainKind(new StudyProductDomainKind());
        PropertyService.get().registerDomainKind(new StudyProductAntigenDomainKind());
        PropertyService.get().registerDomainKind(new StudyTreatmentProductDomainKind());
        PropertyService.get().registerDomainKind(new StudyTreatmentDomainKind());

        QuerySnapshotService.registerProvider(StudySchema.getInstance().getSchemaName(), DatasetSnapshotProvider.getInstance());

        ExperimentService.get().addExperimentListener(new ExperimentListenerImpl());

        DataViewService.get().registerProvider(DatasetViewProvider.TYPE, new DatasetViewProvider());

        NotificationService.get().registerNotificationType(ParticipantCategory.SEND_PARTICIPANT_GROUP_TYPE, "Study", "fa-users");

        AttachmentService.get().registerAttachmentType(ProtocolDocumentType.get());

        // Register so all administrators get this permission
        RoleManager.registerPermission(new ManageStudyPermission());
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
        return List.of(
            assayScheduleWebPartFactory,
            dataToolsWebPartFactory,
            datasetsPartFactory,
            immunizationScheduleWebpartFactory,
            manageStudyPartFactory,
            reportsPartFactory,
            studyDesignSummaryWebPartFactory,
            studyDesignsWebPartFactory,
            studyListWebPartFactory,
            studyScheduleWebPartFactory,
            subjectDetailsWebPartFactory,
            subjectsWebPartFactory,
            vaccineDesignWebPartFactory,
            new SharedStudyController.StudyFilterWebPartFactory()
        );
    }

    @Override
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
                list.add(participants + " " + StudyService.get().getSubjectNounPlural(c));
            int datasets = study.getDatasets().size();
            if (0 < datasets)
                list.add(datasets + " datasets");
            return list;
        }
        else
            return Collections.emptyList();
    }

    @Override
    protected void startupAfterSpringConfig(ModuleContext moduleContext)
    {
        PipelineService.get().registerPipelineProvider(new StudyPipeline(this));

        // This is in the First group because when a container is deleted,
        // the Experiment listener needs to be called after the Study listener,
        // because Study needs the metadata held by Experiment to delete properly.
        ContainerManager.addContainerListener(new StudyContainerListener(), ContainerManager.ContainerListener.Order.First);
        StudyPublishService.setInstance(new StudyPublishManager());

        LsidManager.get().registerHandler("Study", new StudyLsidHandler());
        WikiRenderingService wikiService = WikiRenderingService.get();
        wikiService.registerMacroProvider("study", new StudyMacroProvider());
        registerFolderTypes();
        SecurityManager.addViewFactory(new SecurityController.StudySecurityViewFactory());
        AuditLogService.get().registerAuditType(new PublishAuditProvider());
        AuditLogService.get().registerAuditType(new DatasetAuditProvider());
        AuditLogService.get().registerAuditType(new StudyAuditProvider());
        AuditLogService.get().registerAuditType(new StudySecurityEscalationAuditProvider());
        AuditLogService.get().registerAuditType(new ParticipantGroupAuditProvider());

        ReportService.get().registerReport(new StudyQueryReport());
        ReportService.get().registerReport(new ExternalReport());
        ReportService.get().registerReport(new CrosstabReport());
        ReportService.get().registerReport(new StudyCrosstabReport());
        ReportService.get().registerReport(new StudyRReport());
        ReportService.get().registerReport(new ParticipantReport());
        ReportService.get().registerReport(new AssayProgressReport());

        ReportService.get().registerDescriptor(new CrosstabReportDescriptor());
        ReportService.get().registerDescriptor(new ParticipantReportDescriptor());

        DataStateManager.getInstance().registerDataStateHandler(new StudyQCStateHandler());

        ReportService.get().addUIProvider(new StudyReportUIProvider());
        ReportService.get().addGlobalItemFilterType(QueryReport.TYPE);

        SearchService ss = SearchService.get();

        if (null != ss)
        {
            ss.addSearchCategory(StudyManager.subjectCategory);
            ss.addSearchCategory(StudyManager.datasetCategory);
            ss.addDocumentProvider(this);
        }

        SystemMaintenance.addTask(new PurgeParticipantsMaintenanceTask());
        if (null != SpecimenService.get())
            SystemMaintenance.addTask(new SpecimenRefreshMaintenanceTask());
        SystemMaintenance.addTask(new DefragmentParticipantVisitIndexesTask());
        SystemMaintenance.addTask(new MasterPatientIndexMaintenanceTask());

        FolderSerializationRegistry folderRegistry = FolderSerializationRegistry.get();
        if (null != folderRegistry)
        {
            folderRegistry.addFactories(new StudyWriterFactory(), new StudyImporterFactory());
        }

        FileContentService.get().addFileListener(new TableUpdaterFileListener(StudySchema.getInstance().getTableInfoUploadLog(), "FilePath", TableUpdaterFileListener.Type.filePath, "RowId"));

        DatasetDefinition.cleanupOrphanedDatasetDomains();

        AdminConsole.addExperimentalFeatureFlag(StudyQuerySchema.EXPERIMENTAL_STUDY_SUBSCHEMAS, "Use sub-schemas in Study",
                "Separate study tables into three groups 'datasets', 'specimens', and 'design'", false);

        AdminConsole.addExperimentalFeatureFlag(DatasetQueryView.EXPERIMENTAL_LINKED_DATASET_CHECK,
                "Assay linked to study consistency check",
                "Flags rows in assay linked datasets where the subject and timepoint may be different from the source assay.",
                false);

        AdminConsole.addExperimentalFeatureFlag(DatasetQueryView.EXPERIMENTAL_ALLOW_MERGE_WITH_MANAGED_KEYS,
                "Allow merge of study dataset that uses server-managed additional key fields",
                "Merging of dataset that uses server-managed third key (such as GUID or auto RowId) is not officially supported. Unexpected outcome might be experienced when merge is performed.",
                false);

        ReportAndDatasetChangeDigestProvider.get().addNotificationInfoProvider(new DatasetNotificationInfoProvider());

        AdminLinkManager.getInstance().addListener((adminNavTree, container, user) -> {
            if (container.hasPermission(user, ReadPermission.class) && container.getActiveModules().contains(StudyModule.this) && container.hasPermission(user, ManageStudyPermission.class))
            {
                adminNavTree.addChild(new NavTree("Manage Study", PageFlowUtil.urlProvider(StudyUrls.class).getManageStudyURL(container)));
            }
        });

        UsageMetricsService svc = UsageMetricsService.get();
        if (null != svc)
        {
            svc.registerUsageMetrics(MODULE_NAME, () -> {
                Map<String, Object> metric = new HashMap<>();
                metric.put("studyCount", new SqlSelector(StudySchema.getInstance().getSchema(), "SELECT COUNT(*) FROM study.study").getObject(Long.class));
                metric.put("datasetCount", new SqlSelector(StudySchema.getInstance().getSchema(), "SELECT COUNT(*) FROM study.dataset").getObject(Long.class));

                // Add counts for all reports and visualizations, by type
                metric.put("reportCountsByType", Collections.unmodifiableMap(
                    ContainerManager.getAllChildren(ContainerManager.getRoot()).stream()
                        .flatMap(c->ReportService.get().getReports(null, c).stream())
                        .collect(Collectors.groupingBy(Report::getType, Collectors.counting())))
                );

                metric.put("securityType", new SqlSelector(StudySchema.getInstance().getSchema(), "SELECT SecurityType, COUNT(*) FROM study.Study GROUP BY SecurityType").getValueMap());
                metric.put("timepointType", new SqlSelector(StudySchema.getInstance().getSchema(), "SELECT TimepointType, COUNT(*) FROM study.Study GROUP BY TimepointType").getValueMap());

                metric.put("linkedSampleTypeDatasetCount", new SqlSelector(StudySchema.getInstance().getSchema(), "SELECT COUNT(PublishSourceType) FROM study.dataset WHERE PublishSourceType = 'SampleType'").getObject(Long.class));
                metric.put("linkedAssayDatasetCount", new SqlSelector(StudySchema.getInstance().getSchema(), "SELECT COUNT(PublishSourceType) FROM study.dataset WHERE PublishSourceType = 'Assay'").getObject(Long.class));

                metric.put("redcapCount", new SqlSelector(PropertySchema.getInstance().getSchema(), "SELECT COUNT(*) FROM prop.PropertySets WHERE Category = 'RedcapConfigurationSettings'").getObject(Long.class));
                metric.put("publishStudyCount", new SqlSelector(PropertySchema.getInstance().getSchema(), "SELECT COUNT(DISTINCT(destination)) FROM study.StudySnapshot WHERE Type = 'publish'").getObject(Long.class));
                metric.put("ancillaryStudyCount", new SqlSelector(PropertySchema.getInstance().getSchema(), "SELECT COUNT(DISTINCT(destination)) FROM study.StudySnapshot WHERE Type = 'ancillary'").getObject(Long.class));

                SqlDialect dialect = StudySchema.getInstance().getSqlDialect();
                metric.put("demographicsDatasetCount", new SqlSelector(StudySchema.getInstance().getSchema(), "SELECT COUNT(*) FROM study.Dataset WHERE DemographicData = " + dialect.getBooleanTRUE()).getObject(Long.class));
                metric.put("standardDatasetCount", new SqlSelector(StudySchema.getInstance().getSchema(), "SELECT COUNT(*) FROM study.Dataset WHERE Type = 'Standard'").getObject(Long.class));

                metric.put("managedThirdKeyCount", new SqlSelector(StudySchema.getInstance().getSchema(), "SELECT COUNT(*) FROM study.Dataset WHERE KeyManagementType <> 'None'").getObject(Long.class));
                metric.put("thirdKeyCount", new SqlSelector(StudySchema.getInstance().getSchema(), "SELECT COUNT(*) FROM study.Dataset WHERE (KeyManagementType = 'None' AND KeyPropertyName IS NOT NULL) OR (UseTimeKeyField = " + dialect.getBooleanTRUE() + ")").getObject(Long.class));

                metric.put("datasetsLinkedFromAssays", new SqlSelector(StudySchema.getInstance().getSchema(), "SELECT COUNT(*) FROM study.Dataset WHERE PublishSourceType = 'Assay'").getObject(Long.class));
                metric.put("datasetsLinkedFromSamples", new SqlSelector(StudySchema.getInstance().getSchema(), "SELECT COUNT(*) FROM study.Dataset WHERE PublishSourceType = 'SampleType'").getObject(Long.class));

                metric.put("assayScheduleCount", new SqlSelector(StudySchema.getInstance().getSchema(), "SELECT COUNT(DISTINCT(container)) FROM study.AssaySpecimen").getObject(Long.class));

                metric.put("alternateParticipantIdCount", new SqlSelector(StudySchema.getInstance().getSchema(), "SELECT COUNT(*) FROM study.Study WHERE AlternateIdPrefix IS NOT NULL").getObject(Long.class));
                metric.put("participantIdMappingCount", new SqlSelector(StudySchema.getInstance().getSchema(), "SELECT COUNT(DISTINCT(container)) FROM study.Participant WHERE AlternateId IS NOT NULL").getObject(Long.class));
                metric.put("participantAliasCount", new SqlSelector(StudySchema.getInstance().getSchema(), "SELECT COUNT(*) FROM study.Study WHERE ParticipantAliasDatasetId IS NOT NULL").getObject(Long.class));

                // grab the counts of report and dataset notification settings (by notification option)
                Set<? extends StudyImpl> allStudies = StudyManager.getInstance().getAllStudies();
                Map<ReportContentEmailManager.NotifyOption, Integer> notifyOptionCounts = new HashMap<>();
                allStudies.forEach(study -> {
                    Map<Integer, SortedSet<Integer>> settings = ReportContentEmailManager.getUserCategoryMap(study.getContainer());
                    for (SortedSet<Integer> config : settings.values())
                    {
                        ReportContentEmailManager.NotifyOption option = ReportContentEmailManager.removeNotifyOption(config);
                        notifyOptionCounts.put(option, 1 + notifyOptionCounts.computeIfAbsent(option, (k) -> 0));
                    }
                });
                Map<String, Integer> notificationMap = notifyOptionCounts.entrySet().stream()
                    .collect(Collectors.toMap(e -> e.getKey().name(), Map.Entry::getValue));
                metric.put("reportAndDatasetNotificationOptions", notificationMap);

                long cloudBackedStudies = allStudies.stream()
                    .filter(s -> Objects.requireNonNull(PipelineService.get().findPipelineRoot(s.getContainer())).isCloudRoot())
                    .count();
                metric.put("cloudBackedStudies", cloudBackedStudies);

                // Count the studies that have configured at least one group for per-dataset access control
                long studiesWithAnyPerDatasetGroup = allStudies.stream()
                    .filter(study -> study.getSecurityType().isSupportsPerDatasetPermissions())
                    .filter(study -> SecurityManager.getGroups(study.getContainer().getProject(), true).stream()
                        .anyMatch(group -> GroupSecurityType.getTypeForGroup(group, study) == GroupSecurityType.PER_DATASET))
                    .count();
                metric.put("perDatasetSecurityStudyCount", studiesWithAnyPerDatasetGroup);

                // Count the studies that use products and treatments
                MutableInt hasProducts = new MutableInt(0);
                MutableInt hasTreatments = new MutableInt(0);

                allStudies.stream()
                    .map(study->StudyQuerySchema.createSchema(study, User.getSearchUser(), RoleManager.getRole(ReaderRole.class)))
                    .forEach(schema->{
                        TableInfo products = schema.getTable(StudyQuerySchema.PRODUCT_TABLE_NAME);
                        long productCount = new TableSelector(products).getRowCount();
                        if (productCount > 0)
                            hasProducts.increment();

                        TableInfo treatments = schema.getTable(StudyQuerySchema.TREATMENT_TABLE_NAME);
                        long treatmentCount = new TableSelector(treatments).getRowCount();
                        if (treatmentCount > 0)
                            hasTreatments.increment();
                    });

                metric.put("studyProducts", hasProducts.intValue());
                metric.put("studyTreatments", hasTreatments.intValue());

                return metric;
            });
        }

        AdminConsole.addLink(AdminConsole.SettingsLinkType.Premium, "Master Patient Index", new ActionURL(StudyController.MasterPatientProviderAction.class, ContainerManager.getRoot()), AdminPermission.class);
        DataStateImportExportHelper.registerProvider(new StudyQCImportExportHelper());
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
        return PageFlowUtil.set("studydataset", "studydesign", "specimentables");
    }

    private void registerFolderTypes()
    {
        FolderTypeManager.get().registerFolderType(this, new StudyFolderType(this, getActiveModulesForStudyFolder()));
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

        @Override
        public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart)
        {
            if (!portalCtx.hasPermission(ReadPermission.class))
                return new HtmlView("Views", HtmlString.of(portalCtx.getUser().isGuest() ? "Please log in to see this data." : "You do not have permission to see this data"));

            if (null == StudyManager.getInstance().getStudy(portalCtx.getContainer()))
                return new HtmlView("Views", HtmlString.of("This folder does not contain a study"));
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

    private static class SubjectsWebPartFactory extends DefaultWebPartFactory
    {
        public SubjectsWebPartFactory()
        {
            super(
                "Subject List",
                SubjectsWebPart.class,
                WebPartFactory.LOCATION_BODY,
                WebPartFactory.LOCATION_RIGHT,
                Participant.class.getName() + ":" + WebPartFactory.LOCATION_BODY,
                Participant.class.getName() + ":" + WebPartFactory.LOCATION_RIGHT
            );
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
                return new HtmlView(getDisplayName(portalCtx.getContainer(),  webPart.getLocation()), HtmlString.of(portalCtx.getUser().isGuest() ? "Please log in to see this data." : "You do not have permission to see this data"));

            if (null == StudyManager.getInstance().getStudy(portalCtx.getContainer()))
                return new HtmlView("Subject List", HtmlString.of("This folder does not contain a study."));
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

        @Override
        public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart)
        {
            if (!portalCtx.hasPermission(ReadPermission.class))
                return new HtmlView("Datasets", HtmlString.of(portalCtx.getUser().isGuest() ? "Please log in to see this data." : "You do not have permission to see this data"));

            if (null == StudyManager.getInstance().getStudy(portalCtx.getContainer()))
                return new HtmlView("Datasets", HtmlString.of("This folder does not contain a study."));

            return new DatasetsWebPartView();
        }
    }

    private static class StudyDesignsWebPartFactory extends BaseWebPartFactory
    {
        public StudyDesignsWebPartFactory()
        {
            super("Vaccine Study Protocols");
            addLegacyNames("Study Designs");
        }

        @Override
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

        @Override
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
        return Set.of(
            DatasetDefinition.TestCleanupOrphanedDatasetDomains.class,
            ParticipantGroupManager.ParticipantGroupTestCase.class,
            StudyImpl.ProtocolDocumentTestCase.class,
            StudyManager.AssayScheduleTestCase.class,
            StudyManager.StudySnapshotTestCase.class,
            StudyManager.VisitCreationTestCase.class,
            StudyModule.TestCase.class,
            TreatmentManager.TreatmentDataTestCase.class,
            VisitImpl.TestCase.class
        );
    }

    @Override
    public @NotNull List<Factory<Class<?>>> getIntegrationTestFactories()
    {
        ArrayList<Factory<Class<?>>> list = new ArrayList<>(super.getIntegrationTestFactories());
        list.add(new JspTestCase("/org/labkey/study/model/DatasetImportTestCase.jsp"));
        return list;
    }

    @Override
    @NotNull
    public Set<Class> getUnitTests()
    {
        return Set.of(
            DatasetDataWriter.TestCase.class,
            DefaultStudyDesignWriter.TestCase.class,
            ParticipantIdImportHelper.ParticipantIdTest.class,
            SequenceNumImportHelper.SequenceNumTest.class,
            StudyImpl.DateMathTestCase.class
        );
    }

    @Override
    public void enumerateDocuments(@NotNull SearchService.IndexTask task, @NotNull Container c, Date modifiedSince)
    {
        StudyManager._enumerateDocuments(task, c, modifiedSince);
    }
    
    @Override
    public void indexDeleted()
    {
        new SqlExecutor(StudySchema.getInstance().getSchema()).execute("UPDATE study.Participant SET LastIndexed = NULL");
    }

    @Override
    public JSONObject getPageContextJson(ContainerUser context)
    {
        Container c = context.getContainer();
        Map<String, String> moduleProperties = getDefaultPageContextJson(c);
        StudyImpl study = StudyManager.getInstance().getStudy(c);
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


    @Override
    public @Nullable UpgradeCode getUpgradeCode()
    {
        return new StudyManager.StudyUpgradeCode();
    }


    public static class TestCase extends Assert
    {
        @Test
        public void testUsageMetrics()
        {
            List<String> metricNames = List.of(
                "studyCount",
                "datasetCount",
                "timepointType",
                "reportCountsByType"
            );
            assertTrue("Mothership report missing expected metrics",
                    UsageReportingLevel.MothershipReportTestHelper.getModuleMetrics(UsageReportingLevel.ON, MODULE_NAME)
                    .keySet().containsAll(metricNames));
        }
    }
}
