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
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.SpringModule;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.specimen.SpecimenRequestManager;
import org.labkey.api.specimen.SpecimensPage;
import org.labkey.api.specimen.importer.SpecimenImporter;
import org.labkey.api.specimen.model.AdditiveTypeDomainKind;
import org.labkey.api.specimen.model.DerivativeTypeDomainKind;
import org.labkey.api.specimen.model.PrimaryTypeDomainKind;
import org.labkey.api.specimen.model.SpecimenDomainKind;
import org.labkey.api.specimen.model.SpecimenEventDomainKind;
import org.labkey.api.specimen.model.SpecimenRequestEventType;
import org.labkey.api.specimen.model.VialDomainKind;
import org.labkey.api.study.SpecimenService;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.importer.SimpleStudyImporterRegistry;
import org.labkey.api.study.writer.SimpleStudyWriterRegistry;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.WebPartFactory;
import org.labkey.specimen.action.SpecimenApiController;
import org.labkey.specimen.importer.DefaultSpecimenImportStrategyFactory;
import org.labkey.specimen.importer.SpecimenSchemaImporter;
import org.labkey.specimen.importer.SpecimenSettingsImporter;
import org.labkey.specimen.pipeline.SampleMindedTransform;
import org.labkey.specimen.pipeline.SampleMindedTransformTask;
import org.labkey.specimen.security.roles.SpecimenCoordinatorRole;
import org.labkey.specimen.security.roles.SpecimenRequesterRole;
import org.labkey.specimen.view.SpecimenReportWebPartFactory;
import org.labkey.specimen.view.SpecimenSearchWebPartFactory;
import org.labkey.specimen.view.SpecimenToolsWebPartFactory;
import org.labkey.specimen.view.SpecimenWebPartFactory;
import org.labkey.specimen.writer.SpecimenArchiveWriter;
import org.labkey.specimen.writer.SpecimenSettingsWriter;
import org.labkey.specimen.writer.SpecimenWriter;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class SpecimenModule extends SpringModule
{
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
            new SpecimenSearchWebPartFactory(HttpView.BODY),
            new SpecimenToolsWebPartFactory(),
            new SpecimenWebPartFactory(),
            new SpecimenReportWebPartFactory()
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
        PropertyService.get().registerDomainKind(new AdditiveTypeDomainKind());
        PropertyService.get().registerDomainKind(new DerivativeTypeDomainKind());
        PropertyService.get().registerDomainKind(new PrimaryTypeDomainKind());
        PropertyService.get().registerDomainKind(new SpecimenDomainKind());
        PropertyService.get().registerDomainKind(new SpecimenEventDomainKind());
        PropertyService.get().registerDomainKind(new VialDomainKind());

        // Register early so these roles are available to Java code at upgrade time
        RoleManager.registerRole(new SpecimenCoordinatorRole());
        RoleManager.registerRole(new SpecimenRequesterRole());

        AttachmentService.get().registerAttachmentType(SpecimenRequestEventType.get());

        addController("specimen-api", SpecimenApiController.class, "study-samples-api");

        // Register early -- some modules don't declare a runtime dependency on specimen module, but will use the
        // service if it's available
        SpecimenService.setInstance(new SpecimenServiceImpl());
    }

    @Override
    protected void startupAfterSpringConfig(ModuleContext moduleContext)
    {
        ContainerManager.addContainerListener(SpecimenRequestManager.get());

        StudyService.get().registerStudyTabProvider(tabs->tabs.add(new SpecimensPage("Specimen Data")));
        SpecimenService.get().registerSpecimenImportStrategyFactory(new DefaultSpecimenImportStrategyFactory());
        AuditLogService.get().registerAuditType(new SpecimenCommentAuditProvider());
        SpecimenService.get().registerSpecimenTransform(new SampleMindedTransform());

        SimpleStudyWriterRegistry.registerSimpleStudyWriterProvider(() -> List.of(
            new SpecimenSettingsWriter(),
            new SpecimenArchiveWriter()
        ));

        SimpleStudyImporterRegistry.registerSimpleStudyImporterProvider(() -> List.of(
            new SpecimenSettingsImporter(),
            new SpecimenSchemaImporter()
        ));
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
            SpecimenWriter.TestCase.class,
            SampleMindedTransformTask.TestCase.class
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