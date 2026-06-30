/*
 * Copyright (c) 2021-2026 LabKey Corporation
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

package org.labkey.specimen;

import org.apache.commons.collections4.bag.HashBag;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.SpringModule;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.specimen.SpecimenMigrationService;
import org.labkey.api.specimen.SpecimenQuerySchema;
import org.labkey.api.specimen.SpecimenSchema;
import org.labkey.api.specimen.SpecimensPage;
import org.labkey.api.specimen.model.SpecimenTablesProvider;
import org.labkey.api.study.SpecimenService;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyInternalService;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.importer.SimpleStudyImportContext;
import org.labkey.api.study.importer.SimpleStudyImporterRegistry;
import org.labkey.api.study.writer.SimpleStudyWriterRegistry;
import org.labkey.api.usageMetrics.UsageMetricsService;
import org.labkey.api.util.SystemMaintenance;
import org.labkey.api.util.emailTemplate.EmailTemplateService;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.specimen.actions.SpecimenApiController;
import org.labkey.specimen.actions.SpecimenController;
import org.labkey.specimen.importer.AbstractSpecimenTask;
import org.labkey.specimen.importer.QueryBasedSpecimenImportUploadTask;
import org.labkey.specimen.importer.QueryBasedSpecimenTransform;
import org.labkey.specimen.importer.RequestabilityManager;
import org.labkey.specimen.importer.SpecimenImporter;
import org.labkey.specimen.importer.SpecimenSchemaImporter;
import org.labkey.specimen.importer.SpecimenSettingsImporter;
import org.labkey.specimen.model.SpecimenRequestEventType;
import org.labkey.specimen.pipeline.SpecimenPipeline;
import org.labkey.specimen.requirements.SpecimenRequestRequirementProvider;
import org.labkey.specimen.query.SpecimenPivotByDerivativeType;
import org.labkey.specimen.query.SpecimenPivotByPrimaryType;
import org.labkey.specimen.query.SpecimenPivotByRequestingLocation;
import org.labkey.specimen.query.SpecimenQueryView;
import org.labkey.specimen.query.SpecimenUpdateService;
import org.labkey.specimen.security.roles.SpecimenCoordinatorRole;
import org.labkey.specimen.security.roles.SpecimenRequesterRole;
import org.labkey.specimen.settings.RepositorySettings;
import org.labkey.specimen.settings.SettingsManager;
import org.labkey.specimen.view.ManageSpecimenView;
import org.labkey.specimen.view.SpecimenReportWebPartFactory;
import org.labkey.specimen.view.SpecimenRequestNotificationEmailTemplate;
import org.labkey.specimen.view.SpecimenSearchWebPartFactory;
import org.labkey.specimen.view.SpecimenToolsWebPartFactory;
import org.labkey.specimen.view.SpecimenWebPartFactory;
import org.labkey.specimen.writer.SpecimenArchiveWriter;
import org.labkey.specimen.writer.SpecimenSettingsWriter;
import org.labkey.specimen.writer.SpecimenWriter;
import org.labkey.vfs.FileLike;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class SpecimenModule extends SpringModule
{
    private static final Logger LOG = LogHelper.getLogger(SpecimenModule.class, "Specimen metrics");
    public static final String NAME = "Specimen";

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    @NotNull
    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return List.of(
            new SpecimenReportWebPartFactory(),
            new SpecimenSearchWebPartFactory(HttpView.BODY),
            new SpecimenToolsWebPartFactory(),
            new SpecimenWebPartFactory()
        );
    }

    @Override
    public @Nullable Double getSchemaVersion()
    {
        return null;
    }

    @Override
    public boolean hasScripts()
    {
        return false;
    }

    @Override
    protected void init()
    {
        // Register early so these roles are available to Java code at upgrade time
        RoleManager.registerRole(new SpecimenCoordinatorRole());
        RoleManager.registerRole(new SpecimenRequesterRole());

        AttachmentService.get().registerAttachmentParentType(SpecimenRequestEventType.get());

        addController("specimen", SpecimenController.class, "study-samples");
        addController("specimen-api", SpecimenApiController.class, "study-samples-api", "specimens-api");

        // Register early -- some modules don't declare a runtime dependency on specimen module, but will use the
        // service if it's available
        SpecimenService.setInstance(new SpecimenServiceImpl());
        EmailTemplateService.get().registerTemplate(SpecimenRequestNotificationEmailTemplate.class);

        SpecimenMigrationService.setInstance(new SpecimenMigrationService()
        {
            @Override
            public ActionURL getBeginURL(Container c)
            {
                return new ActionURL(SpecimenController.BeginAction.class, c);
            }

            @Override
            public ActionURL getSelectedSpecimensURL(Container c)
            {
                return new ActionURL(SpecimenController.SelectedSpecimensAction.class, c);
            }

            @Override
            public ActionURL getSpecimensURL(Container c)
            {
                return SpecimenController.getSpecimensURL(c);
            }

            @Override
            public void importSpecimenArchive(@Nullable FileLike inputFile, PipelineJob job, SimpleStudyImportContext ctx, boolean merge, boolean syncParticipantVisit) throws PipelineJobException
            {
                AbstractSpecimenTask.doImport(inputFile, job, ctx, merge, syncParticipantVisit);
            }

            @Override
            public void clearRequestCaches(Container c)
            {
                SpecimenRequestManager.get().clearCaches(c);
            }

            @Override
            public @Nullable QueryUpdateService getSpecimenQueryUpdateService(Container c, TableInfo queryTable)
            {
                return SettingsManager.get().getRepositorySettings(c).isSpecimenDataEditable() ? new SpecimenUpdateService(queryTable) : null;
            }

            @Override
            public QueryView getSpecimenQueryView(ViewContext context, QuerySettings settings)
            {
                return SpecimenQueryView.createView(context, settings, SpecimenQueryView.ViewType.VIALS);
            }

            @Override
            public void setDefaultRequestabilityRules(Container container, User user)
            {
                RequestabilityManager.getInstance().setDefaultRules(container, user);
            }

            @Override
            public boolean isEnableRequests(Container c)
            {
                return SettingsManager.get().getRepositorySettings(c).isEnableRequests();
            }

            @Override
            public void addSpecimenPivotTableNames(Set<String> names)
            {
                names.add(SpecimenPivotByPrimaryType.PIVOT_BY_PRIMARY_TYPE);
                names.add(SpecimenPivotByDerivativeType.PIVOT_BY_DERIVATIVE_TYPE);
                names.add(SpecimenPivotByRequestingLocation.PIVOT_BY_REQUESTING_LOCATION);
            }

            @Override
            public @Nullable TableInfo getSpecimenPivotTable(UserSchema schema, String name, Study study, ContainerFilter cf)
            {
                if (SpecimenPivotByPrimaryType.PIVOT_BY_PRIMARY_TYPE.equalsIgnoreCase(name))
                {
                    return new SpecimenPivotByPrimaryType(schema, study, cf);
                }
                if (SpecimenPivotByDerivativeType.PIVOT_BY_DERIVATIVE_TYPE.equalsIgnoreCase(name))
                {
                    return new SpecimenPivotByDerivativeType(schema, study, cf);
                }
                if (SpecimenPivotByRequestingLocation.PIVOT_BY_REQUESTING_LOCATION.equalsIgnoreCase(name))
                {
                    return new SpecimenPivotByRequestingLocation(schema, study, cf);
                }
                return null;
            }
        });

        SystemMaintenance.addTask(new QueryBasedSpecimenImportUploadTask());
    }

    @Override
    protected void startupAfterSpringConfig(ModuleContext moduleContext)
    {
        ContainerManager.addContainerListener(new SpecimenRequestContainerListener());

        StudyService.get().registerStudyTabProvider(tabs -> tabs.add(new SpecimensPage("Specimen Data")));
        AuditLogService.get().registerAuditType(new SpecimenCommentAuditProvider());
        PipelineService.get().registerPipelineProvider(new SpecimenPipeline(this));
        StudyInternalService.get().registerManageStudyViewFactory(ctx -> ctx.getContainer().hasActiveModuleByName("specimen") ? new ManageSpecimenView() : null);

        SimpleStudyWriterRegistry.registerSimpleStudyWriterProvider(() -> List.of(
            new SpecimenArchiveWriter(),
            new SpecimenSettingsWriter()
        ));

        SimpleStudyImporterRegistry.registerSimpleStudyImporterProvider(() -> List.of(
            new SpecimenSchemaImporter(),
            new SpecimenSettingsImporter()
        ));

        SpecimenService specimenService = SpecimenService.get();
        if (null != specimenService)
            specimenService.registerSpecimenTransform(new QueryBasedSpecimenTransform());

        UsageMetricsService svc = UsageMetricsService.get();
        if (null != svc)
        {
            StudyService ss = StudyService.get();

            if (null != ss)
            {
                svc.registerUsageMetrics(NAME, () -> {
                    // Collect and add specimen repository statistics: simple vs. advanced study count, event/vial/specimen count, count of studies with requests enabled, request count by status
                    HashBag<String> specimenBag = new HashBag<>();
                    MutableInt requestsEnabled = new MutableInt(0);
                    MutableInt hasLocations = new MutableInt(0);

                    ss.getAllStudies(ContainerManager.getRoot()).stream()
                        .map(study -> SpecimenQuerySchema.get(study, User.getAdminServiceUser()))
                        .forEach(schema -> {
                            RepositorySettings settings = SettingsManager.get().getRepositorySettings(schema.getContainer());

                            if (settings.isSimple())
                            {
                                specimenBag.add("simple");
                                TableInfo simpleSpecimens = schema.getTable(SpecimenTablesProvider.SIMPLE_SPECIMEN_TABLE_NAME);
                                specimenBag.add("simpleSpecimens", (int) new TableSelector(simpleSpecimens).getRowCount());
                            }
                            else
                            {
                                specimenBag.add("advanced");
                                TableInfo events = schema.getTable(SpecimenTablesProvider.SPECIMEN_EVENT_TABLE_NAME);
                                TableInfo vials = schema.getTable(SpecimenTablesProvider.SPECIMEN_DETAIL_TABLE_NAME);
                                TableInfo specimens = schema.getTable(SpecimenTablesProvider.SPECIMEN_SUMMARY_TABLE_NAME);
                                specimenBag.add("events", (int) new TableSelector(events).getRowCount());
                                specimenBag.add("vials", (int) new TableSelector(vials).getRowCount());
                                specimenBag.add("specimens", (int) new TableSelector(specimens).getRowCount());
                            }

                            if (settings.isEnableRequests())
                                requestsEnabled.increment();

                            TableInfo locations = schema.getTable(SpecimenQuerySchema.LOCATION_TABLE_NAME);
                            long locationCount = new TableSelector(locations).getRowCount();
                            specimenBag.add("locations", (int) locationCount);
                            specimenBag.add("locationsInUse", (int) new TableSelector(locations, new SimpleFilter(FieldKey.fromParts("In Use"), true), null).getRowCount());
                            if (locationCount > 0)
                                hasLocations.increment();

                            LOG.debug(specimenBag.toString());
                        });

                    Map<String, Object> specimensMap = specimenBag.uniqueSet().stream().collect(Collectors.toMap(s -> s, specimenBag::getCount));
                    Map<String, Object> requestsMap = new SqlSelector(SpecimenSchema.get().getSchema(), new SQLFragment("SELECT Label, COUNT(*) FROM study.SampleRequest INNER JOIN study.SampleRequestStatus srs ON StatusId = srs.RowId GROUP BY Label")).getValueMap(String.class);
                    requestsMap.put("enabled", requestsEnabled);
                    specimensMap.put("requests", requestsMap);
                    specimensMap.put("hasLocations", hasLocations.intValue());

                    return specimensMap;
                });
            }
        }
    }

    @Override
    public @NotNull Set<Class<?>> getUnitTests()
    {
        return Set.of(
            SpecimenWriter.TestCase.class
        );
    }

    @Override
    public @NotNull Collection<String> getSchemaNames()
    {
        return Set.of();
    }

    @Override
    public @NotNull Set<Class<?>> getIntegrationTests()
    {
        return Set.of(
            SpecimenImporter.TestCase.class,
            SpecimenRequestRequirementProvider.ContainerScopingTestCase.class,
            SpecimenController.ContainerScopingTestCase.class
        );
    }
}