/*
 * Copyright (c) 2013 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.AuditTypeProvider;
import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.audit.query.DefaultAuditTypeTable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.PropertyStorageSpec.Index;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserIdForeignKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.GroupManager;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ViewContext;
import org.springframework.validation.BindException;

import java.util.ArrayList;
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
    protected DomainKind getDomainKind()
    {
        return new GroupAuditDomainKind();
    }

    @Override
    public <K extends AuditTypeEvent> K convertEvent(AuditLogEvent event)
    {
        GroupAuditEvent bean = new GroupAuditEvent();
        copyStandardFields(bean, event);

        if (event.getIntKey1() != null)
            bean.setUser(event.getIntKey1());

        if (event.getIntKey2() != null)
            bean.setGroup(event.getIntKey2());

        if (event.getEntityId() != null)
            bean.setResourceEntityId(event.getEntityId());

        return (K)bean;
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
    public TableInfo createTableInfo(UserSchema userSchema)
    {
        Domain domain = getDomain();
        DbSchema dbSchema =  DbSchema.get(SCHEMA_NAME);

        DefaultAuditTypeTable table = new DefaultAuditTypeTable(this, domain, dbSchema, userSchema)
        {
            @Override
            protected void initColumn(ColumnInfo col)
            {
                if (COLUMN_NAME_GROUP.equalsIgnoreCase(col.getName()))
                {
                    col.setLabel("Group");
                    col.setFk(new GroupAuditViewFactory.GroupForeignKey());
                    col.setDisplayColumnFactory(new DisplayColumnFactory()
                    {
                        public DisplayColumn createRenderer(ColumnInfo colInfo)
                        {
                            return new GroupAuditViewFactory.GroupDisplayColumn(colInfo);
                        }
                    });
                }
                else if (COLUMN_NAME_USER.equalsIgnoreCase(col.getName()))
                {
                    UserIdForeignKey.initColumn(col);
                }
            }

            @Override
            public List<FieldKey> getDefaultVisibleColumns()
            {
                return defaultVisibleColumns;
            }
        };

        return table;
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

        return createUserView(context, filter, "Access Modification History:", columns, errors);
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

        return createUserView(context, filter, "Access Modification History For This Project:", columns, errors);
    }

    @Nullable
    private static QueryView createUserView(ViewContext context, SimpleFilter filter, String title, List<FieldKey> columns, BindException errors)
    {
        if (AuditLogService.get().isMigrateComplete() || AuditLogService.get().hasEventTypeMigrated(GroupManager.GROUP_AUDIT_EVENT))
        {
            UserSchema schema = AuditLogService.getAuditLogSchema(context.getUser(), context.getContainer());
            if (schema != null)
            {
                QuerySettings settings = new QuerySettings(context, QueryView.DATAREGIONNAME_DEFAULT);

                settings.setBaseFilter(filter);
                settings.setQueryName(GroupManager.GROUP_AUDIT_EVENT);
                settings.setFieldKeys(columns);
                QueryView auditView = schema.createView(context, settings, errors);
                auditView.setTitle(title);

                return auditView;
            }
            return null;
        }
        else
            throw new IllegalArgumentException("Hard table logging is not enabled for this audit event type");
    }

    public static class GroupAuditEvent extends AuditTypeEvent
    {
        int _user;
        int _group;
        String _resourceEntityId;

        public GroupAuditEvent()
        {
            super();
        }

        public GroupAuditEvent(String container, String comment)
        {
            super(GroupManager.GROUP_AUDIT_EVENT, container, comment);
        }

        public int getUser()
        {
            return _user;
        }

        public void setUser(int user)
        {
            _user = user;
        }

        public int getGroup()
        {
            return _group;
        }

        public void setGroup(int group)
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

    }

    public static class GroupAuditDomainKind extends AbstractAuditDomainKind
    {
        public static final String NAME = "GroupAuditDomain";
        public static String NAMESPACE_PREFIX = "Audit-" + NAME;

        private static final Set<PropertyStorageSpec> _fields = new LinkedHashSet<>();

        static {
            _fields.add(createFieldSpec(COLUMN_NAME_USER, JdbcType.INTEGER));
            _fields.add(createFieldSpec(COLUMN_NAME_GROUP, JdbcType.INTEGER));
            _fields.add(createFieldSpec(COLUMN_NAME_RESOURCE_ENTITY_ID, JdbcType.VARCHAR).setEntityId(true));
        }

        public GroupAuditDomainKind()
        {
            super(GroupManager.GROUP_AUDIT_EVENT);
        }

        @Override
        protected Set<PropertyStorageSpec> getColumns()
        {
            return _fields;
        }

        @Override
        public Set<Index> getPropertyIndices()
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
}
