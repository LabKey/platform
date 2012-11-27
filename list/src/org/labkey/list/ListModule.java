/*
 * Copyright (c) 2009-2012 LabKey Corporation
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

import org.labkey.api.admin.FolderSerializationRegistry;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.study.StudySerializationRegistry;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.list.view.ListController;
import org.labkey.list.view.SingleListWebPartFactory;
import org.labkey.list.view.ListWebPart;
import org.labkey.list.model.*;

import java.util.Collection;
import java.util.List;
import java.util.ArrayList;

public class ListModule extends DefaultModule
{
    public String getName()
    {
        return "List";
    }

    // Note: ExperimentModule handles the list schema
    public double getVersion()
    {
        return 12.30;
    }

    // Note: ExperimentModule handles the list schema
    public boolean hasScripts()
    {
        return false;
    }

    protected Collection<WebPartFactory> createWebPartFactories()
    {
        List<WebPartFactory> result = new ArrayList<WebPartFactory>();
        result.add(ListWebPart.FACTORY);
        result.add(new SingleListWebPartFactory());
        return result;
    }

    protected void init()
    {
        addController("list", ListController.class);
        ListService.setInstance(new ListServiceImpl());
        ListSchema.register();
        PropertyService.get().registerDomainKind(new ListDomainType());
    }

    public void doStartup(ModuleContext moduleContext)
    {
        // add a container listener so we'll know when our container is deleted:
        ContainerManager.addContainerListener(new ListContainerListener());
        AuditLogService.get().addAuditViewFactory(ListAuditViewFactory.getInstance());

        FolderSerializationRegistry folderRegistry = ServiceRegistry.get().getService(FolderSerializationRegistry.class);
        if (null != folderRegistry)
        {
            folderRegistry.addFactories(new FolderListWriter.Factory(), new FolderListImporter.Factory());
        }

        // support importing lists from the study archive for backwards compatibility
        StudySerializationRegistry studyRegistry = ServiceRegistry.get().getService(StudySerializationRegistry.class);
        if (null != studyRegistry)
        {
            studyRegistry.addImportFactory(new FolderListImporter.Factory());
        }                  

        if (null != ServiceRegistry.get(SearchService.class))
        {
            ServiceRegistry.get(SearchService.class).addDocumentProvider(ListManager.get());
            ServiceRegistry.get(SearchService.class).addSearchCategory(ListManager.listCategory);
        }
    }

    @Override
    public Collection<String> getSummary(Container c)
    {
        Collection<String> results = new ArrayList<String>();
        ListDef[] lists = ListManager.get().getLists(c);
        if(lists.length > 0)
        {
            results.add(lists.length + " lists");
        }
        return results;
    }

    @Override
    public ActionURL getTabURL(Container c, User user)
    {
        // Don't show full List nav trails to users that aren't admins or developers since they almost certainly don't
        // want to go to those links
        if (c.hasPermission(user, AdminPermission.class) || user.isDeveloper())
        {
            return super.getTabURL(c, user);
        }
        return null;
    }
}
