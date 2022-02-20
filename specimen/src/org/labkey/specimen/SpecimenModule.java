/*
 * Copyright (c) 2021 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.TableInfo;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.SpringModule;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.QueryParam;
import org.labkey.api.query.QueryView;
import org.labkey.api.security.User;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.settings.AdminConsole;
import org.labkey.api.specimen.SpecimenMigrationService;
import org.labkey.api.specimen.SpecimensPage;
import org.labkey.api.specimen.importer.SpecimenImporter;
import org.labkey.api.specimen.model.SpecimenRequestEventType;
import org.labkey.api.specimen.requirements.SpecimenRequest;
import org.labkey.api.study.SpecimenService;
import org.labkey.api.study.StudyInternalService;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.importer.SimpleStudyImportContext;
import org.labkey.api.study.importer.SimpleStudyImporterRegistry;
import org.labkey.api.study.writer.SimpleStudyWriterRegistry;
import org.labkey.api.util.emailTemplate.EmailTemplateService;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.WebPartFactory;
import org.labkey.specimen.actions.SpecimenApiController;
import org.labkey.specimen.actions.SpecimenController;
import org.labkey.specimen.importer.AbstractSpecimenTask;
import org.labkey.specimen.importer.DefaultSpecimenImportStrategyFactory;
import org.labkey.specimen.importer.SpecimenSchemaImporter;
import org.labkey.specimen.importer.SpecimenSettingsImporter;
import org.labkey.specimen.pipeline.SampleMindedTransform;
import org.labkey.specimen.pipeline.SampleMindedTransformTask;
import org.labkey.specimen.pipeline.SpecimenPipeline;
import org.labkey.specimen.security.roles.SpecimenCoordinatorRole;
import org.labkey.specimen.security.roles.SpecimenRequesterRole;
import org.labkey.specimen.view.ManageSpecimenView;
import org.labkey.specimen.view.SpecimenReportWebPartFactory;
import org.labkey.specimen.view.SpecimenRequestNotificationEmailTemplate;
import org.labkey.specimen.view.SpecimenSearchWebPartFactory;
import org.labkey.specimen.view.SpecimenToolsWebPartFactory;
import org.labkey.specimen.view.SpecimenWebPartFactory;
import org.labkey.specimen.writer.SpecimenArchiveWriter;
import org.labkey.specimen.writer.SpecimenSettingsWriter;
import org.labkey.specimen.writer.SpecimenWriter;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class SpecimenModule extends SpringModule
{
    public static final String NAME = "Specimen";
    // TODO: Delete this and all associated code
    public static final String CREATE_SPECIMEN_STUDY = "CreateSpecimenStudy";

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

        AttachmentService.get().registerAttachmentType(SpecimenRequestEventType.get());

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
            public ActionURL getSpecimenEventsURL(Container c, ActionURL returnURL)
            {
                return new ActionURL(SpecimenController.SpecimenEventsAction.class, c).addReturnURL(returnURL);
            }

            @Override
            public ActionURL getInsertSpecimenQueryRowURL(Container c, String schemaName, TableInfo table)
            {
                ActionURL url = new ActionURL(SpecimenController.InsertSpecimenQueryRowAction.class, c);
                url.addParameter("schemaName", schemaName);
                url.addParameter(QueryView.DATAREGIONNAME_DEFAULT + "." + QueryParam.queryName, table.getName());

                return url;
            }

            @Override
            public ActionURL getUpdateSpecimenQueryRowURL(Container c, String schemaName, TableInfo table)
            {
                ActionURL url = new ActionURL(SpecimenController.UpdateSpecimenQueryRowAction.class, c);
                url.addParameter("schemaName", schemaName);
                url.addParameter(QueryView.DATAREGIONNAME_DEFAULT + "." + QueryParam.queryName, table.getName());

                return url;
            }

            @Override
            public void importSpecimenArchive(@Nullable Path inputFile, PipelineJob job, SimpleStudyImportContext ctx, boolean merge, boolean syncParticipantVisit) throws PipelineJobException
            {
                AbstractSpecimenTask.doImport(inputFile, job, ctx, merge, syncParticipantVisit);
            }

            @Override
            public void clearRequestCaches(Container c)
            {
                SpecimenRequestManager.get().clearCaches(c);
            }

            @Override
            public SpecimenRequest getRequest(Container c, int rowId)
            {
                return SpecimenRequestManager.get().getRequest(c, rowId);
            }

            @Override
            public void clearGroupedValuesForColumn(Container container)
            {
                SpecimenRequestManager.get().clearGroupedValuesForColumn(container);
            }

            @Override
            public void updateVialCounts(Container container, User user)
            {
                SpecimenRequestManager.get().updateVialCounts(container, user);
            }
        });
     }

    @Override
    protected void startupAfterSpringConfig(ModuleContext moduleContext)
    {
        ContainerManager.addContainerListener(new SpecimenRequestContainerListener());

        StudyService.get().registerStudyTabProvider(tabs->tabs.add(new SpecimensPage("Specimen Data")));
        SpecimenService.get().registerSpecimenImportStrategyFactory(new DefaultSpecimenImportStrategyFactory());
        AuditLogService.get().registerAuditType(new SpecimenCommentAuditProvider());
        SpecimenService.get().registerSpecimenTransform(new SampleMindedTransform());
        PipelineService.get().registerPipelineProvider(new SpecimenPipeline(this));
        StudyInternalService.get().registerManageStudyViewFactory(ctx->ctx.getContainer().hasActiveModuleByName("specimen") ? new ManageSpecimenView() : null);

        SimpleStudyWriterRegistry.registerSimpleStudyWriterProvider(() -> List.of(
            new SpecimenArchiveWriter(),
            new SpecimenSettingsWriter()
        ));

        SimpleStudyImporterRegistry.registerSimpleStudyImporterProvider(() -> List.of(
            new SpecimenSchemaImporter(),
            new SpecimenSettingsImporter()
        ));

        // TODO: Remove this... we're no longer respecting this flag
        AdminConsole.addExperimentalFeatureFlag(CREATE_SPECIMEN_STUDY, "Create Specimen Study",
    "Adds a button to the specimen request details page that creates a new child study containing the selected specimens, associated participants, and selected datasets.", false);
    }

    @Override
    @NotNull
    public Collection<String> getSummary(Container c)
    {
        return Collections.emptyList();
    }

    @Override
    @NotNull
    public Set<Class> getUnitTests()
    {
        return Set.of(
            SampleMindedTransformTask.TestCase.class,
            SpecimenWriter.TestCase.class
        );
    }

    @Override
    public @NotNull Collection<String> getSchemaNames()
    {
        return Set.of();
    }

    @Override
    public @NotNull Set<Class> getIntegrationTests()
    {
        return Set.of(
            SpecimenImporter.TestCase.class
        );
    }
}