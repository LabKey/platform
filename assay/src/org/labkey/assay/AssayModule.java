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

import org.apache.commons.collections4.Factory;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.assay.AssayBatchDomainKind;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.assay.AssayProviderSchema;
import org.labkey.api.assay.AssayResultDomainKind;
import org.labkey.api.assay.AssayRunDomainKind;
import org.labkey.api.assay.AssayRunType;
import org.labkey.api.assay.AssayService;
import org.labkey.api.assay.AssayUrls;
import org.labkey.api.assay.TsvDataHandler;
import org.labkey.api.assay.plate.AssayPlateMetadataService;
import org.labkey.api.assay.plate.PlateMetadataDataHandler;
import org.labkey.api.assay.plate.PlateService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ContainerType;
import org.labkey.api.exp.ExperimentRunType;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.module.AdminLinkManager;
import org.labkey.api.module.FolderTypeManager;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.SpringModule;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reports.report.r.ParamReplacementSvc;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.settings.AdminConsole;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.JspTestCase;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.StartupListener;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.WebPartFactory;
import org.labkey.assay.pipeline.AssayImportRunTask;
import org.labkey.assay.plate.AssayPlateDataDomainKind;
import org.labkey.assay.plate.AssayPlateMetadataServiceImpl;
import org.labkey.assay.plate.PlateManager;
import org.labkey.assay.plate.TsvPlateTypeHandler;
import org.labkey.assay.plate.query.PlateSchema;
import org.labkey.assay.query.AssayDbSchema;
import org.labkey.assay.query.AssaySchemaImpl;
import org.labkey.assay.security.AssayDesignerRole;
import org.labkey.assay.view.AssayBatchesWebPartFactory;
import org.labkey.assay.view.AssayList2WebPartFactory;
import org.labkey.assay.view.AssayListWebPartFactory;
import org.labkey.assay.view.AssayResultsWebPartFactory;
import org.labkey.assay.view.AssayRunsWebPartFactory;
import org.labkey.pipeline.xml.AssayImportRunTaskType;

import javax.servlet.ServletContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.labkey.api.assay.DefaultDataTransformer.LEGACY_SESSION_COOKIE_NAME_REPLACEMENT;
import static org.labkey.api.assay.DefaultDataTransformer.LEGACY_SESSION_ID_REPLACEMENT;

public class AssayModule extends SpringModule
{
    private static final String NAME = "Assay";

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public Double getSchemaVersion()
    {
        return 22.000;
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
        return List.of(
            assayListWebPartFactory, assayBatchesWebPartFactory, assayRunsWebPartFactory, assayResultsWebPartFactory, assayList2WebPartFactory
        );
    }

    @Override
    protected void init()
    {
        AssayService.setInstance(new AssayManager());
        PlateService.setInstance(new PlateManager());
        addController("assay", AssayController.class);
        addController("plate", PlateController.class);
        DefaultSchema.registerProvider(PlateSchema.SCHEMA_NAME, new PlateSchema.Provider(this));
        DefaultSchema.registerProvider(AssaySchemaImpl.NAME, new AssaySchemaImpl.Provider(this));

        PropertyService.get().registerDomainKind(new PlateBasedAssaySampleTypeDomainKind());

        // Register early so file-based assays are available to Java code at upgrade time
        ExperimentService.get().registerExperimentDataHandler(new TsvDataHandler());
        ExperimentService.get().registerExperimentDataHandler(new FileBasedModuleDataHandler());
        ExperimentService.get().registerExperimentDataHandler(new PlateMetadataDataHandler());
        AssayPlateMetadataService.registerService(PlateMetadataDataHandler.DATA_TYPE, new AssayPlateMetadataServiceImpl());
        PropertyService.get().registerDomainKind(new AssayPlateDataDomainKind());
        PlateService.get().registerPlateTypeHandler(new TsvPlateTypeHandler());

        PropertyService.get().registerDomainKind(new DefaultAssayDomainKind());
        PropertyService.get().registerDomainKind(new AssayBatchDomainKind());
        PropertyService.get().registerDomainKind(new AssayRunDomainKind());
        PropertyService.get().registerDomainKind(new AssayResultDomainKind());

        ParamReplacementSvc.get().registerDeprecated(LEGACY_SESSION_COOKIE_NAME_REPLACEMENT, ValidationException.SEVERITY.WARN, "Use '" + SecurityManager.API_KEY + "' instead");
        ParamReplacementSvc.get().registerDeprecated(LEGACY_SESSION_ID_REPLACEMENT, ValidationException.SEVERITY.WARN, "Use '" + SecurityManager.API_KEY + "' instead");

        RoleManager.registerRole(new AssayDesignerRole());
    }

    @Override
    protected void startupAfterSpringConfig(ModuleContext moduleContext)
    {
        FolderTypeManager.get().registerFolderType(this, new AssayFolderType(this));

        PipelineJobService.get().registerTaskFactoryFactory(AssayImportRunTaskType.type, new AssayImportRunTask.FactoryFactory());
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
            ss.addResourceResolver(AssayManager.ASSAY_CATEGORY.getName(), AssayDocumentProvider.getSearchResolver());
            ss.addDocumentProvider(new AssayDocumentProvider());
        }

        AdminConsole.addExperimentalFeatureFlag(AssayManager.EXPERIMENTAL_ASSAY_DATA_IMPORT, "UX Assay Data Import",
                "Adds an 'Import Data' button (using plus icon) to the 'Assay List' query view to get to the new UX Assay Data Import page.", false);

        // add a container listener so we'll know when our container is deleted:
        ContainerManager.addContainerListener(new AssayContainerListener());

        AdminLinkManager.getInstance().addListener((adminNavTree, container, user) -> {
            // Need only read permissions to view manage assays page
            if (container.hasPermission(user, ReadPermission.class))
            {
                Container targetContainer = container.getContainerFor(ContainerType.DataType.folderManagement);
                adminNavTree.addChild(new NavTree("Manage Assays", PageFlowUtil.urlProvider(AssayUrls.class).getAssayListURL(targetContainer)));
            }
        });

        // Issue 24850: add modules associated with assays that have an active definition
        // Register ModuleDependencyProvider in a StartupListener to make sure the expected schemas exist.
        ContextListener.addStartupListener(new StartupListener()
        {
            @Override
            public String getName()
            {
                return "Assay";
            }

            @Override
            public void moduleStartupComplete(ServletContext servletContext)
            {
                ContainerManager.registerModuleDependencyProvider((dependencies, c) -> {
                    AssayService svc = AssayService.get();
                    List<ExpProtocol> activeProtocols = svc.getAssayProtocols(c);
                    for (ExpProtocol p : activeProtocols)
                    {
                        AssayProvider ap = svc.getProvider(p);
                        if (ap != null)
                        {
                            for (Module m : ap.getRequiredModules())
                                dependencies.add(m);
                        }
                    }
                });
            }
        });
    }

    @Override
    public ActionURL getTabURL(Container c, User user)
    {
        return new ActionURL(AssayController.TabAction.class, c);
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
        return Set.of(
            ModuleAssayCache.TestCase.class
        );
    }

    @Override
    public @NotNull Collection<Factory<Class<?>>> getIntegrationTestFactories()
    {
        ArrayList<Factory<Class<?>>> list = new ArrayList<>(super.getIntegrationTestFactories());
        list.add(new JspTestCase("/org/labkey/assay/AssayIntegrationTestCase.jsp"));
        return list;
    }

    @Override
    public @NotNull Set<Class> getUnitTests()
    {
        return Set.of(
            TsvAssayProvider.TestCase.class,
            AssaySchemaImpl.TestCase.class,
            AssayProviderSchema.TestCase.class,
            PlateManager.TestCase.class
        );
    }
}