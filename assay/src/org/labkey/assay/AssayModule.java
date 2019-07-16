/*
 * Copyright (c) 2019 LabKey Corporation
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

package org.labkey.assay;

import org.jetbrains.annotations.NotNull;
import org.labkey.assay.query.AssayDbSchema;
import org.labkey.api.assay.AssayToStudyMigrationService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.exp.ExperimentRunType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.module.FolderTypeManager;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.SpringModule;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.search.SearchService;
import org.labkey.api.settings.AdminConsole;
import org.labkey.api.study.PlateService;
import org.labkey.api.study.assay.AssayProviderSchema;
import org.labkey.api.study.assay.AssayRunType;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.WebPartFactory;
import org.labkey.assay.view.AssayBatchesWebPartFactory;
import org.labkey.assay.view.AssayList2WebPartFactory;
import org.labkey.assay.view.AssayListWebPartFactory;
import org.labkey.assay.view.AssayResultsWebPartFactory;
import org.labkey.assay.view.AssayRunsWebPartFactory;
import org.labkey.assay.query.AssaySchemaImpl;
import org.labkey.assay.plate.PlateManager;
import org.labkey.assay.plate.query.PlateSchema;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class AssayModule extends SpringModule
{
    public static final String NAME = "Assay";

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public double getVersion()
    {
        return 19.21;
    }

    @Override
    public boolean hasScripts()
    {
        return true;
    }

    public static final WebPartFactory assayListWebPartFactory = new AssayListWebPartFactory();
    public static final WebPartFactory assayBatchesWebPartFactory = new AssayBatchesWebPartFactory();
    public static final WebPartFactory assayRunsWebPartFactory = new AssayRunsWebPartFactory();
    public static final WebPartFactory assayResultsWebPartFactory = new AssayResultsWebPartFactory();
    public static final WebPartFactory assayList2WebPartFactory = new AssayList2WebPartFactory();

    @Override
    @NotNull
    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return Arrays.asList(
            assayListWebPartFactory, assayBatchesWebPartFactory, assayRunsWebPartFactory, assayResultsWebPartFactory, assayList2WebPartFactory
        );
    }

    @Override
    protected void init()
    {
        AssayService.setInstance(new AssayManager());
        addController("assay", AssayController.class);
        PropertyService.get().registerDomainKind(new PlateBasedAssaySampleSetDomainKind());
        ExperimentService.get().registerExperimentDataHandler(new FileBasedModuleDataHandler());
        DefaultSchema.registerProvider(PlateSchema.SCHEMA_NAME, new PlateSchema.Provider(this));
        DefaultSchema.registerProvider(AssaySchemaImpl.NAME, new AssaySchemaImpl.Provider(this));
        FolderTypeManager.get().registerFolderType(this, new AssayFolderType(this));
        addController("plate", PlateController.class);
        PlateService.setInstance(new PlateManager());
        AssayToStudyMigrationService.setInstance(new AssayToStudyMigrationServiceImpl());
    }

    @Override
    protected void startupAfterSpringConfig(ModuleContext moduleContext)
    {
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
        PlateManager.get().registerLsidHandlers();
        SearchService ss = SearchService.get();

        if (null != ss)
        {
            ss.addSearchCategory(AssayManager.ASSAY_CATEGORY);
            ss.addDocumentProvider(new AssayDocumentProvider());
        }

        AdminConsole.addExperimentalFeatureFlag(AssayManager.EXPERIMENTAL_ASSAY_DATA_IMPORT, "UX Assay Data Import",
                "Adds an 'Import Data' button (using plus icon) to the 'Assay List' query view to get to the new UX Assay Data Import page.", false);

        // add a container listener so we'll know when our container is deleted:
        ContainerManager.addContainerListener(new AssayContainerListener());
    }

    @Override
    @NotNull
    public Collection<String> getSummary(Container c)
    {
        return Collections.emptyList();
    }

    @Override
    @NotNull
    public Set<String> getSchemaNames()
    {
        HashSet<String> set = new HashSet<>();
        set.add(AssayDbSchema.getInstance().getSchemaName());
        set.addAll(getProvisionedSchemaNames());

        return set;
    }

    @Override
    @NotNull
    public Set<String> getProvisionedSchemaNames()
    {
        return PageFlowUtil.set("assayresult");
    }

    @Override
    public @NotNull Set<Class> getIntegrationTests()
    {
        return Collections.singleton(ModuleAssayCache.TestCase.class);
    }

    @Override
    public @NotNull Set<Class> getUnitTests()
    {
        Set<Class> set = new HashSet<>();
        set.add(TsvAssayProvider.TestCase.class);
        set.add(AssaySchemaImpl.TestCase.class);
        set.add(AssayProviderSchema.TestCase.class);

        return set;
    }
}