/*
 * Copyright (c) 2016-2017 LabKey Corporation
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
package org.labkey.issue.actions;

import org.labkey.api.action.GWTServiceAction;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.Sort;
import org.labkey.api.exp.property.DomainEditorServiceBase;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.server.BaseRemoteService;
import org.labkey.api.issues.IssuesSchema;
import org.labkey.api.security.Group;
import org.labkey.api.security.MemberType;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ViewContext;
import org.labkey.issue.model.IssueManager;
import org.labkey.issues.client.GWTIssueDefinition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Created by klum on 5/11/2016.
 */
@RequiresPermission(ReadPermission.class)
public class IssueServiceAction extends GWTServiceAction
{
    protected BaseRemoteService createService()
    {
        return new IssueServiceImpl(getViewContext());
    }

    private class IssueServiceImpl extends DomainEditorServiceBase implements org.labkey.issues.client.IssueService
    {
        public IssueServiceImpl(ViewContext context)
        {
            super(context);
        }

        @Override
        public GWTDomain getDomainDescriptor(String typeURI)
        {
            GWTDomain domain = super.getDomainDescriptor(typeURI);
            domain.setDefaultValueOptions(new DefaultValueType[]
                    { DefaultValueType.FIXED_EDITABLE, DefaultValueType.LAST_ENTERED, DefaultValueType.FIXED_NON_EDITABLE }, DefaultValueType.FIXED_EDITABLE);
            return domain;
        }

        @Override
        public GWTIssueDefinition getIssueDefinition(String defName)
        {
            GWTIssueDefinition def = new GWTIssueDefinition();
            Container c = getContainer();

            IssueManager.EntryTypeNames typeNames = IssueManager.getEntryTypeNames(c, defName);
            Group assignedToGroup = IssueManager.getAssignedToGroup(c, defName);
            User defaultUser = IssueManager.getDefaultAssignedToUser(c, defName);

            def.setIssueDefName(defName);
            def.setSingularItemName(typeNames.singularName);
            def.setPluralItemName(typeNames.pluralName);
            def.setCommentSortDirection(IssueManager.getCommentSortDirection(c, defName).name());
            if (assignedToGroup != null)
                def.setAssignedToGroup(assignedToGroup.getUserId());
            if (defaultUser != null)
                def.setAssignedToUser(defaultUser.getUserId());

            return def;
        }

        @Override
        public List<String> updateIssueDefinition(GWTIssueDefinition def, GWTDomain orig, GWTDomain dd)
        {
            IssueManager.EntryTypeNames names = new IssueManager.EntryTypeNames();

            try (DbScope.Transaction transaction = IssuesSchema.getInstance().getSchema().getScope().ensureTransaction())
            {
                names.singularName = def.getSingularItemName();
                names.pluralName = def.getPluralItemName();

                IssueManager.saveEntryTypeNames(getContainer(), def.getIssueDefName(), names);
                IssueManager.saveCommentSortDirection(getContainer(), def.getIssueDefName(), Sort.SortDirection.fromString(def.getCommentSortDirection()));

                Group group = null;
                if (def.getAssignedToGroup() != null)
                    group = SecurityManager.getGroup(def.getAssignedToGroup());
                IssueManager.saveAssignedToGroup(getContainer(), def.getIssueDefName(), group);

                User user = null;
                if (def.getAssignedToUser() != null)
                    user = UserManager.getUser(def.getAssignedToUser());
                IssueManager.saveDefaultAssignedToUser(getContainer(), def.getIssueDefName(), user);

                List<String> errors = new ArrayList<>();
                if (orig != null && dd != null)
                    errors = super.updateDomainDescriptor(orig, dd);
                if (errors.isEmpty())
                    transaction.commit();

                return errors;
            }
        }

        @Override
        public List<Map<String, String>> getProjectGroups()
        {
            List<Map<String, String>> groups = new ArrayList<>();

            SecurityManager.getGroups(getContainer().getProject(), true).stream().filter(group -> !group.isGuests() && (!group.isUsers() || getUser().hasRootAdminPermission())).forEach(group -> {
                String displayText = (group.isProjectGroup() ? "" : "Site:") + group.getName();
                groups.add(PageFlowUtil.map("name", displayText, "value", String.valueOf(group.getUserId())));
            });

            return groups;
        }

        @Override
        public List<Map<String, String>> getUsersForGroup(Integer groupId)
        {
            List<Map<String, String>> users = new ArrayList<>();

            if (groupId != null)
            {
                Group group = SecurityManager.getGroup(groupId);
                if (group != null)
                {
                    for (User user : SecurityManager.getAllGroupMembers(group, MemberType.ACTIVE_USERS, group.isUsers()))
                    {
                        if (getContainer().hasPermission(user, UpdatePermission.class))
                        {
                            users.add(PageFlowUtil.map("name", user.getDisplayName(getUser()), "value", String.valueOf(user.getUserId())));
                        }
                    }
                }
            }
            else
            {
                // all project users
                for (User user : SecurityManager.getProjectUsers(getContainer()))
                {
                    if (getContainer().hasPermission(user, UpdatePermission.class))
                    {
                        users.add(PageFlowUtil.map("name", user.getDisplayName(getUser()), "value", String.valueOf(user.getUserId())));
                    }
                }
            }

            users.sort(Comparator.comparing(map -> map.get("name"), String.CASE_INSENSITIVE_ORDER));
            return users;
        }

        @Override
        public List<Map<String, String>> getFolderMoveContainers()
        {
            List<Map<String, String>> containers = new ArrayList<>();
            Container root = ContainerManager.getRoot();
            List<Container> allContainers = ContainerManager.getAllChildren(root, getUser(), AdminPermission.class, false);

            // remove current container
            allContainers.remove(getContainer());
            allContainers.remove(root);

            for (Container container : allContainers)
            {
                // remove containers that start with underscore
                if (container.getName().startsWith("_"))
                    continue;

                containers.add(PageFlowUtil.map("name", container.getPath(), "value", container.getId()));
            }
            return containers;
        }
    }
}
