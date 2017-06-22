/*
 * Copyright (c) 2013-2017 LabKey Corporation
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
package org.labkey.api.audit.provider;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.AuditTypeProvider;
import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.audit.query.DefaultAuditTypeTable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.PropertyStorageSpec.Index;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.PrincipalIdForeignKey;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.Group;
import org.labkey.api.security.GroupManager;
import org.labkey.api.security.PrincipalType;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityUrls;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.springframework.validation.BindException;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: klum
 * Date: 7/17/13
 */
public class GroupAuditProvider extends AbstractAuditTypeProvider implements AuditTypeProvider
{
    public static final String COLUMN_NAME_RESOURCE_ENTITY_ID = "ResourceEntityId";
    public static final String COLUMN_NAME_USER = "User";
    public static final String COLUMN_NAME_GROUP = "Group";

    static final List<FieldKey> defaultVisibleColumns = new ArrayList<>();

    static {

        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED_BY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_IMPERSONATED_BY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_PROJECT_ID));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CONTAINER));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_GROUP));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_USER));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_COMMENT));
    }

    @Override
    public String getEventName()
    {
        return GroupManager.GROUP_AUDIT_EVENT;
    }

    @Override
    public String getLabel()
    {
        return "Group events";
    }

    @Override
    public String getDescription()
    {
        return "Information about group modifications and security changes.";
    }

    @Override
    protected AbstractAuditDomainKind getDomainKind()
    {
        return new GroupAuditDomainKind();
    }

    @Override
    public Map<FieldKey, String> legacyNameMap()
    {
        Map<FieldKey, String> legacyNames = super.legacyNameMap();
        legacyNames.put(FieldKey.fromParts("EntityId"), COLUMN_NAME_RESOURCE_ENTITY_ID);
        legacyNames.put(FieldKey.fromParts("intKey1"), COLUMN_NAME_USER);
        legacyNames.put(FieldKey.fromParts("intKey2"), COLUMN_NAME_GROUP);
        return legacyNames;
    }

    @Override
    public <K extends AuditTypeEvent> Class<K> getEventClass()
    {
        return (Class<K>)GroupAuditEvent.class;
    }

    @Override
    public TableInfo createTableInfo(final UserSchema userSchema)
    {
        return new DefaultAuditTypeTable(GroupAuditProvider.this, createStorageTableInfo(), userSchema, defaultVisibleColumns)
        {
            @Override
            protected void initColumn(ColumnInfo col)
            {
                if (COLUMN_NAME_GROUP.equalsIgnoreCase(col.getName()))
                {
                    col.setLabel("Group");
                    col.setFk(new GroupForeignKey(userSchema));
                    col.setDisplayColumnFactory(GroupDisplayColumn::new);
                }
                else if (COLUMN_NAME_USER.equalsIgnoreCase(col.getName()))
                {
                    col.setLabel("Member");
                    PrincipalIdForeignKey.initColumn(col);
                }
            }
        };
    }

    @Override
    public List<FieldKey> getDefaultVisibleColumns()
    {
        return defaultVisibleColumns;
    }

    @Nullable
    public static QueryView createSiteUserView(ViewContext context, int userId, BindException errors)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts(GroupAuditProvider.COLUMN_NAME_USER), userId);

        List<FieldKey> columns = new ArrayList<>();

        columns.add(FieldKey.fromParts(GroupAuditProvider.COLUMN_NAME_CREATED));
        columns.add(FieldKey.fromParts(GroupAuditProvider.COLUMN_NAME_CREATED_BY));
        columns.add(FieldKey.fromParts(GroupAuditProvider.COLUMN_NAME_PROJECT_ID));
        columns.add(FieldKey.fromParts(GroupAuditProvider.COLUMN_NAME_GROUP));
        columns.add(FieldKey.fromParts(GroupAuditProvider.COLUMN_NAME_COMMENT));

        return createUserView(context, filter, "Access Modification History:", columns, errors, ContainerFilter.Type.AllFolders);
    }

    @Nullable
    public static QueryView createProjectMemberView(ViewContext context, int userId, Container project, BindException errors)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts(GroupAuditProvider.COLUMN_NAME_USER), userId);
        filter.addCondition(FieldKey.fromParts(GroupAuditProvider.COLUMN_NAME_PROJECT_ID), project.getId());

        List<FieldKey> columns = new ArrayList<>();

        columns.add(FieldKey.fromParts(GroupAuditProvider.COLUMN_NAME_CREATED));
        columns.add(FieldKey.fromParts(GroupAuditProvider.COLUMN_NAME_CREATED_BY));
        columns.add(FieldKey.fromParts(GroupAuditProvider.COLUMN_NAME_GROUP));
        columns.add(FieldKey.fromParts(GroupAuditProvider.COLUMN_NAME_COMMENT));

        return createUserView(context, filter, "Access Modification History For This Project:", columns, errors, null);
    }

    @Nullable
    private static QueryView createUserView(ViewContext context, SimpleFilter filter, String title, List<FieldKey> columns, BindException errors, ContainerFilter.Type filterType)
    {
        UserSchema schema = AuditLogService.getAuditLogSchema(context.getUser(), context.getContainer());
        if (schema != null)
        {
            QuerySettings settings = new QuerySettings(context, QueryView.DATAREGIONNAME_DEFAULT);

            settings.setBaseFilter(filter);
            settings.setQueryName(GroupManager.GROUP_AUDIT_EVENT);
            settings.setFieldKeys(columns);

            if (filterType != null)
                settings.setContainerFilterName(filterType.name());

            QueryView auditView = schema.createView(context, settings, errors);
            auditView.setTitle(title);

            return auditView;
        }
        return null;
    }

    public static class GroupAuditEvent extends AuditTypeEvent
    {
        Integer _user;
        Integer _group;
        String _resourceEntityId;

        public GroupAuditEvent()
        {
            super();
        }

        public GroupAuditEvent(String container, String comment)
        {
            super(GroupManager.GROUP_AUDIT_EVENT, container, comment);
        }

        public Integer getUser()
        {
            return _user;
        }

        public void setUser(Integer user)
        {
            _user = user;
        }

        public Integer getGroup()
        {
            return _group;
        }

        public void setGroup(Integer group)
        {
            _group = group;
        }

        public String getResourceEntityId()
        {
            return _resourceEntityId;
        }

        public void setResourceEntityId(String resourceEntityId)
        {
            _resourceEntityId = resourceEntityId;
        }

        @Override
        public Map<String, Object> getAuditLogMessageElements()
        {
            Map<String, Object> elements = new LinkedHashMap<>();
            if (getUser() != null)
                elements.put("user", getUserMessageElement(getUser()));
            if (getGroup() != null)
                elements.put("group", getGroupMessageElement(getGroup()));
            elements.putAll(super.getAuditLogMessageElements());
            return elements;
        }
    }

    public static class GroupAuditDomainKind extends AbstractAuditDomainKind
    {
        public static final String NAME = "GroupAuditDomain";
        public static final String NAMESPACE_PREFIX = "Audit-" + NAME;

        private static Set<PropertyDescriptor> _fields;  // TODO: Seems wrong... static + set in constructor?

        public GroupAuditDomainKind()
        {
            super(GroupManager.GROUP_AUDIT_EVENT);

            Set<PropertyDescriptor> fields = new LinkedHashSet<>();
            fields.add(createPropertyDescriptor(COLUMN_NAME_USER, PropertyType.INTEGER));
            fields.add(createPropertyDescriptor(COLUMN_NAME_GROUP, PropertyType.INTEGER));
            fields.add(createPropertyDescriptor(COLUMN_NAME_RESOURCE_ENTITY_ID, PropertyType.STRING)); // UNDONE: is needed? .setEntityId(true));
            _fields = Collections.unmodifiableSet(fields);
        }

        @Override
        public Set<PropertyDescriptor> getProperties()
        {
            return _fields;
        }

        @Override
        public Set<Index> getPropertyIndices(Domain domain)
        {
            return PageFlowUtil.set(
                    new Index(false, COLUMN_NAME_USER),
                    new Index(false, COLUMN_NAME_GROUP)
            );
        }

        @Override
        protected String getNamespacePrefix()
        {
            return NAMESPACE_PREFIX;
        }

        @Override
        public String getKindName()
        {
            return NAME;
        }
    }

    public static class GroupForeignKey extends LookupForeignKey
    {
        private final UserSchema _userSchema;

        public GroupForeignKey(UserSchema userSchema)
        {
            super("UserId", "Name");
            _userSchema = userSchema;
        }

        public TableInfo getLookupTableInfo()
        {
            TableInfo tinfoUsers = CoreSchema.getInstance().getTableInfoPrincipals();
            FilteredTable ret = new FilteredTable<>(tinfoUsers, _userSchema);
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
                        String displayText = PageFlowUtil.filter(g.getName());

                        // Link to security-group action ONLY for standard security groups (not module groups, like actors). See #26351.
                        if (g.getPrincipalType() == PrincipalType.GROUP)
                        {
                            String groupName = g.isProjectGroup() ? groupContainer.getPath() + "/" + g.getName() : g.getName();
                            ActionURL url = PageFlowUtil.urlProvider(SecurityUrls.class).getManageGroupURL(groupContainer, groupName);

                            out.write("<a href=\"");
                            out.write(PageFlowUtil.filter(url));
                            out.write("\">");
                            out.write(displayText);
                            out.write("</a>");
                        }
                        else
                        {
                            out.write(displayText);
                        }
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
}
