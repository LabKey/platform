/*
 * Copyright (c) 2009-2019 LabKey Corporation
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

package org.labkey.list;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.admin.FolderSerializationRegistry;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.lists.permissions.DesignListPermission;
import org.labkey.api.module.AdminLinkManager;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.SpringModule;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.PlatformDeveloperPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.settings.AdminConsole;
import org.labkey.api.study.StudySerializationRegistry;
import org.labkey.api.usageMetrics.UsageMetricsService;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.UsageReportingLevel;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.WebPartFactory;
import org.labkey.list.controllers.ListController;
import org.labkey.list.model.FolderListImporter;
import org.labkey.list.model.FolderListWriter;
import org.labkey.list.model.IntegerListDomainKind;
import org.labkey.list.model.ListAuditProvider;
import org.labkey.list.model.ListDef;
import org.labkey.list.model.ListManager;
import org.labkey.list.model.ListManagerSchema;
import org.labkey.list.model.ListQuerySchema;
import org.labkey.list.model.ListSchema;
import org.labkey.list.model.ListServiceImpl;
import org.labkey.list.model.ListWriter;
import org.labkey.list.model.VarcharListDomainKind;
import org.labkey.list.view.ListItemType;
import org.labkey.list.view.ListsWebPart;
import org.labkey.list.view.SingleListWebPartFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ListModule extends SpringModule
{
    @Override
    public String getName()
    {
        return "List";
    }

    // Note: ExperimentModule handles the list schema
    @Override
    public Double getSchemaVersion()
    {
        return 20.000;
    }

    // Note: ExperimentModule handles the list schema
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
        result.add(ListsWebPart.FACTORY);
        result.add(new SingleListWebPartFactory());
        return result;
    }

    @Override
    protected void init()
    {
        addController("list", ListController.class);
        ListService.setInstance(new ListServiceImpl());
        ListQuerySchema.register(this);
        ListManagerSchema.register(this);

        PropertyService.get().registerDomainKind(new IntegerListDomainKind());
        PropertyService.get().registerDomainKind(new VarcharListDomainKind());

        RoleManager.registerPermission(new DesignListPermission());

        AttachmentService.get().registerAttachmentType(ListItemType.get());
    }

    @Override
    public void startupAfterSpringConfig(ModuleContext moduleContext)
    {
        AuditLogService.get().registerAuditType(new ListAuditProvider());

        FolderSerializationRegistry folderRegistry = FolderSerializationRegistry.get();
        if (null != folderRegistry)
        {
            folderRegistry.addFactories(new FolderListWriter.Factory(), new FolderListImporter.Factory());
        }

        // support importing lists from the study archive for backwards compatibility
        StudySerializationRegistry studyRegistry = StudySerializationRegistry.get();
        if (null != studyRegistry)
        {
            studyRegistry.addImportFactory(new FolderListImporter.Factory());
        }                  

        SearchService ss = SearchService.get();
        if (null != ss)
        {
            ss.addDocumentProvider(ListManager.get());
            ss.addSearchCategory(ListManager.listCategory);
        }

        AdminLinkManager.getInstance().addListener((adminNavTree, container, user) ->
        {
            // Only need read permissions to view manage lists page
            if (container.hasPermission(user, ReadPermission.class))
                adminNavTree.addChild(new NavTree("Manage Lists", ListService.get().getManageListsURL(container)));
        });

        UsageMetricsService svc = UsageMetricsService.get();
        if (null != svc)
        {
            svc.registerUsageMetrics(UsageReportingLevel.MEDIUM, getName(), () -> {
                Map<String, Object> metric = new HashMap<>();
                metric.put("listCount", new SqlSelector(DbSchema.get("exp", DbSchemaType.Module), "SELECT COUNT(*) FROM exp.list").getObject(Long.class));
                return metric;
            });
        }
    }

    @NotNull
    @Override
    public Collection<String> getSummary(Container c)
    {
        Collection<String> results = new ArrayList<>();
        Collection<ListDef> lists = ListManager.get().getLists(c);
        if(lists.size() > 0)
        {
            results.add(lists.size() + " lists");
        }
        return results;
    }

    @Override
    public ActionURL getTabURL(Container c, User user)
    {
        // Don't show full List nav trails to users that aren't admins or developers since they almost certainly don't
        // want to go to those links
        if (c.hasOneOf(user, AdminPermission.class, PlatformDeveloperPermission.class))
        {
            return super.getTabURL(c, user);
        }
        return null;
    }

    @NotNull
    @Override
    public Set<String> getSchemaNames()
    {
        return PageFlowUtil.set(ListSchema.getInstance().getSchemaName());
    }

    @NotNull
    @Override
    public Collection<String> getProvisionedSchemaNames()
    {
        return PageFlowUtil.set(ListSchema.getInstance().getSchemaName());
    }

    @NotNull
    @Override
    public Set<Class> getUnitTests()
    {
        return Set.of(
            ListManager.TestCase.class,
            ListWriter.TestCase.class
        );
    }
}
