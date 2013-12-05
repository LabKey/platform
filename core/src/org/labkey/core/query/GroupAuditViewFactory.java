/*
 * Copyright (c) 2007-2013 LabKey Corporation
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

package org.labkey.core.query;

import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.SimpleAuditViewFactory;
import org.labkey.api.audit.query.AuditLogQueryView;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserIdForeignKey;
import org.labkey.api.query.UserIdRenderer;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.Group;
import org.labkey.api.security.GroupManager;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityUrls;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * User: Karl Lum
 * Date: Oct 16, 2007
 *
 * Event field documentation:
 *
 * created - Timestamp
 * createdBy - User who created the record
 * impersonatedBy - user who was impersonating the user (or null)
 * comment - record description
 * projectId - the project id
 * container - container id of the domain event
 * intKey1 - the user id of the principal being modified
 * intKey2 - the group id of the group being modified
 *
 */
public class GroupAuditViewFactory extends SimpleAuditViewFactory
{
    private static final GroupAuditViewFactory _instance = new GroupAuditViewFactory();

    public static GroupAuditViewFactory getInstance()
    {
        return _instance;
    }

    private GroupAuditViewFactory(){}

    public String getEventType()
    {
        return GroupManager.GROUP_AUDIT_EVENT;
    }

    public String getName()
    {
        return "Group events";
    }

    @Override
    public String getDescription()
    {
        return "Information about group modifications and security changes.";
    }

    public QueryView createDefaultQueryView(ViewContext context)
    {
        AuditLogQueryView view = AuditLogService.get().createQueryView(context, null, getEventType());
        view.setSort(new Sort("-Date"));

        return view;
    }

    public List<FieldKey> getDefaultVisibleColumns()
    {
        List<FieldKey> columns = new ArrayList<>();

        columns.add(FieldKey.fromParts("Date"));
        columns.add(FieldKey.fromParts("CreatedBy"));
        columns.add(FieldKey.fromParts("ImpersonatedBy"));
        columns.add(FieldKey.fromParts("ProjectId"));
        columns.add(FieldKey.fromParts("ContainerId"));
        columns.add(FieldKey.fromParts("IntKey2"));
        columns.add(FieldKey.fromParts("IntKey1"));
        columns.add(FieldKey.fromParts("Comment"));

        return columns;
    }

    public void setupTable(FilteredTable table, UserSchema schema)
    {
        super.setupTable(table, schema);
        ColumnInfo col = table.getColumn("IntKey1");
        if (col != null)
        {
            col.setLabel("User");
            UserIdForeignKey.initColumn(col);
            col.setDisplayColumnFactory(new DisplayColumnFactory()
            {
                public DisplayColumn createRenderer(ColumnInfo colInfo)
                {
                    return new UserDisplayColumn(colInfo);
                }
            });
        }
        col = table.getColumn("IntKey2");
        if (col != null)
        {
            col.setLabel("Group");
            col.setFk(new GroupForeignKey());
            col.setDisplayColumnFactory(new DisplayColumnFactory()
            {
                public DisplayColumn createRenderer(ColumnInfo colInfo)
                {
                    return new GroupDisplayColumn(colInfo);
                }
            });
        }
    }

    public AuditLogQueryView createSiteUserView(ViewContext context, int userId)
    {
        return createUserView(context, userId, "Access Modification History:", new String[]{"Date", "CreatedBy", "ProjectId", "IntKey2", "Comment"}, null);
    }

    public AuditLogQueryView createProjectMemberView(ViewContext context, int userId)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("ProjectId"), context.getContainer().getProject());
        return createUserView(context, userId, "Access Modification History For This Project:", new String[]{"Date", "CreatedBy", "IntKey2", "Comment"}, filter);
    }

    private AuditLogQueryView createUserView(ViewContext context, int userId, String title, String[] visibleColumns, SimpleFilter extraFilter)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("IntKey1"), userId);

        if (null != extraFilter)
            filter.addAllClauses(extraFilter);

        AuditLogQueryView view = AuditLogService.get().createQueryView(context, filter, getEventType());
        view.setTitle(title);
        view.setVisibleColumns(visibleColumns);
        view.setSort(new Sort("-Date"));

        return view;
    }

    @Deprecated
    public AuditLogQueryView createGroupView(ViewContext context, int groupId)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("IntKey2"), groupId);

        AuditLogQueryView view = AuditLogService.get().createQueryView(context, filter, getEventType());
        view.setTitle("Group Membership History");
        view.setVisibleColumns(new String[]{"Date", "CreatedBy", "ContainerId", "Comment"});
        view.setSort(new Sort("-Date"));

        return view;
    }

    @Deprecated
    public AuditLogQueryView createFolderView(ViewContext context, Container container)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("ContainerId"), container.getId());
        AuditLogQueryView view = AuditLogService.get().createQueryView(context, filter, getEventType());
        view.setTitle("Access Modification History For This Folder");
        view.setVisibleColumns(new String[]{"Date", "CreatedBy", "Comment"});
        view.setSort(new Sort("-Date"));
        return view;
    }

    public static class GroupForeignKey extends LookupForeignKey
    {
        public GroupForeignKey()
        {
            super("UserId", "Name");
        }

        public TableInfo getLookupTableInfo()
        {
            TableInfo tinfoUsers = CoreSchema.getInstance().getTableInfoPrincipals();
            FilteredTable ret = new FilteredTable(tinfoUsers);
            ret.addWrapColumn(tinfoUsers.getColumn("UserId"));
            ret.addColumn(ret.wrapColumn("Name", tinfoUsers.getColumn("Name")));
            ret.setTitleColumn("Name");
            return ret;
        }
    }

    public static class GroupDisplayColumn extends DataColumn
    {
        private ColumnInfo _groupId;

        public GroupDisplayColumn(ColumnInfo groupId)
        {
            super(groupId);
            _groupId = groupId;
        }

        public String getName()
        {
            return "group";
        }

        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            Integer id = (Integer)getBoundColumn().getValue(ctx);
            if (id != null)
            {
                Group g = SecurityManager.getGroup(id);
                if (g != null)
                {
                    Container groupContainer = g.isAdministrators() ? ContainerManager.getRoot() : ContainerManager.getForId(g.getContainer());
                    if (g.isAdministrators() || g.isProjectGroup())
                    {
                        String groupName = g.isProjectGroup() ? groupContainer.getPath() + "/" + g.getName() : g.getName();
                        ActionURL url = PageFlowUtil.urlProvider(SecurityUrls.class).getManageGroupURL(groupContainer, groupName);

                        out.write("<a href=\"");
                        out.write(PageFlowUtil.filter(url));
                        out.write("\">");
                        out.write(PageFlowUtil.filter(g.getName()));
                        out.write("</a>");
                        return;
                    }
                }
            }
            out.write("&nbsp;");
        }

        public void addQueryColumns(Set<ColumnInfo> set)
        {
            set.add(_groupId);
        }
    }

    private static class UserDisplayColumn extends UserIdRenderer.GuestAsBlank
    {
        public UserDisplayColumn(ColumnInfo col)
        {
            super(col);
        }

        public String getName()
        {
            return "user";
        }
    }
}

