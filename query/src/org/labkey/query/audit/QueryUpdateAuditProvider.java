/*
 * Copyright (c) 2013-2019 LabKey Corporation
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
package org.labkey.query.audit;

import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.AuditTypeProvider;
import org.labkey.api.audit.DetailedAuditTypeEvent;
import org.labkey.api.audit.TransactionAuditProvider;
import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.audit.query.DefaultAuditTypeTable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryForm;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.view.ViewContext;
import org.labkey.query.controllers.QueryController;
import org.springframework.validation.BindException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class QueryUpdateAuditProvider extends AbstractAuditTypeProvider implements AuditTypeProvider
{
    public static final String QUERY_UPDATE_AUDIT_EVENT = "QueryUpdateAuditEvent";

    public static final String COLUMN_NAME_ROW_PK = "RowPk";
    public static final String COLUMN_NAME_SCHEMA_NAME = "SchemaName";
    public static final String COLUMN_NAME_QUERY_NAME = "QueryName";

    static final List<FieldKey> defaultVisibleColumns = new ArrayList<>();

    static {

        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED_BY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_IMPERSONATED_BY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_PROJECT_ID));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CONTAINER));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_SCHEMA_NAME));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_QUERY_NAME));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_COMMENT));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_USER_COMMENT));
    }

    public QueryUpdateAuditProvider()
    {
        super(new QueryUpdateAuditDomainKind());
    }

    @Override
    public String getEventName()
    {
        return QUERY_UPDATE_AUDIT_EVENT;
    }

    @Override
    public String getLabel()
    {
        return "Query update events";
    }

    @Override
    public String getDescription()
    {
        return "Data about insert and update queries.";
    }

    @Override
    public <K extends AuditTypeEvent> Class<K> getEventClass()
    {
        return (Class<K>)QueryUpdateAuditEvent.class;
    }

    @Override
    public TableInfo createTableInfo(UserSchema userSchema, ContainerFilter cf)
    {
        DefaultAuditTypeTable table = new DefaultAuditTypeTable(this, createStorageTableInfo(), userSchema, cf, defaultVisibleColumns)
        {
            @Override
            protected void initColumn(MutableColumnInfo col)
            {
                if (COLUMN_NAME_SCHEMA_NAME.equalsIgnoreCase(col.getName()))
                {
                    col.setLabel("Schema Name");
                }
                else if (COLUMN_NAME_QUERY_NAME.equalsIgnoreCase(col.getName()))
                {
                    col.setLabel("Query Name");
                }
                else if (COLUMN_NAME_USER_COMMENT.equalsIgnoreCase(col.getName()))
                {
                    col.setLabel("User Comment");
                }
            }
        };
        appendValueMapColumns(table, QUERY_UPDATE_AUDIT_EVENT);

        return table;
    }

    @Override
    public List<FieldKey> getDefaultVisibleColumns()
    {
        return defaultVisibleColumns;
    }


    public static QueryView createHistoryQueryView(ViewContext context, QueryForm form, BindException errors)
    {
        UserSchema schema = AuditLogService.getAuditLogSchema(context.getUser(), context.getContainer());
        if (schema != null)
        {
            QuerySettings settings = new QuerySettings(context, QueryView.DATAREGIONNAME_DEFAULT);

            SimpleFilter filter = new SimpleFilter(FieldKey.fromParts(QueryUpdateAuditProvider.COLUMN_NAME_SCHEMA_NAME), form.getSchemaName());
            filter.addCondition(FieldKey.fromParts(QueryUpdateAuditProvider.COLUMN_NAME_QUERY_NAME), form.getQueryName());

            settings.setBaseFilter(filter);
            settings.setQueryName(QUERY_UPDATE_AUDIT_EVENT);
            return schema.createView(context, settings, errors);
        }
        return null;
    }

    public static QueryView createDetailsQueryView(ViewContext context, QueryController.QueryDetailsForm form, BindException errors)
    {
        return createDetailsQueryView(context, form.getSchemaName(), form.getQueryName(), form.getKeyValue(), errors);
    }

    public static QueryView createDetailsQueryView(ViewContext context, String schemaName, String queryName, String keyValue, BindException errors)
    {
        UserSchema schema = AuditLogService.getAuditLogSchema(context.getUser(), context.getContainer());
        if (schema != null)
        {
            QuerySettings settings = new QuerySettings(context, QueryView.DATAREGIONNAME_DEFAULT);

            SimpleFilter filter = new SimpleFilter(FieldKey.fromParts(QueryUpdateAuditProvider.COLUMN_NAME_SCHEMA_NAME), schemaName);
            filter.addCondition(FieldKey.fromParts(QueryUpdateAuditProvider.COLUMN_NAME_QUERY_NAME), queryName);
            filter.addCondition(FieldKey.fromParts(QueryUpdateAuditProvider.COLUMN_NAME_ROW_PK), keyValue);

            settings.setBaseFilter(filter);
            settings.setQueryName(QUERY_UPDATE_AUDIT_EVENT);
            return schema.createView(context, settings, errors);
        }
        return null;
    }

    public int moveEvents(Container targetContainer, Collection<?> rowPks, String schemaName, String queryName)
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addInClause(FieldKey.fromParts(COLUMN_NAME_ROW_PK), rowPks.stream().map(Object::toString).toList());
        filter.addCondition(FieldKey.fromParts(COLUMN_NAME_SCHEMA_NAME), schemaName);
        filter.addCondition(FieldKey.fromParts(COLUMN_NAME_QUERY_NAME), queryName);

        return Table.updateContainer(createStorageTableInfo(), targetContainer, filter, null, false);
    }

    public static class QueryUpdateAuditEvent extends DetailedAuditTypeEvent
    {
        private String _rowPk;
        private String _schemaName;
        private String _queryName;

        /** Important for reflection-based instantiation */
        @SuppressWarnings("unused")
        public QueryUpdateAuditEvent()
        {
            super();
        }

        public QueryUpdateAuditEvent(Container container, String comment)
        {
            super(QUERY_UPDATE_AUDIT_EVENT, container, comment);
            setTransactionEvent(TransactionAuditProvider.getCurrentTransactionAuditEvent(), QUERY_UPDATE_AUDIT_EVENT);
        }

        public String getRowPk()
        {
            return _rowPk;
        }

        public void setRowPk(String rowPk)
        {
            _rowPk = rowPk;
        }

        public String getSchemaName()
        {
            return _schemaName;
        }

        public void setSchemaName(String schemaName)
        {
            _schemaName = schemaName;
        }

        public String getQueryName()
        {
            return _queryName;
        }

        public void setQueryName(String queryName)
        {
            _queryName = queryName;
        }

        @Override
        public Map<String, Object> getAuditLogMessageElements()
        {
            Map<String, Object> elements = new LinkedHashMap<>();
            elements.put("rowPk", getRowPk());
            elements.put("schemaName", getSchemaName());
            elements.put("queryName", getQueryName());
            elements.put("transactionId", getTransactionId());
            elements.put("userComment", getUserComment());
            // N.B. oldRecordMap and newRecordMap are potentially very large (and are not displayed in the default grid view)
            elements.putAll(super.getAuditLogMessageElements());
            return elements;
        }
    }

    public static class QueryUpdateAuditDomainKind extends AbstractAuditDomainKind
    {
        public static final String NAME = "QueryUpdateAuditDomain";
        public static String NAMESPACE_PREFIX = "Audit-" + NAME;

        private final Set<PropertyDescriptor> _fields;

        public QueryUpdateAuditDomainKind()
        {
            super(QUERY_UPDATE_AUDIT_EVENT);

            Set<PropertyDescriptor> fields = new LinkedHashSet<>();
            fields.add(createPropertyDescriptor(COLUMN_NAME_ROW_PK, PropertyType.STRING));
            fields.add(createPropertyDescriptor(COLUMN_NAME_SCHEMA_NAME, PropertyType.STRING));
            fields.add(createPropertyDescriptor(COLUMN_NAME_QUERY_NAME, PropertyType.STRING));
            fields.add(createOldDataMapPropertyDescriptor());
            fields.add(createNewDataMapPropertyDescriptor());
            fields.add(createPropertyDescriptor(COLUMN_NAME_TRANSACTION_ID, PropertyType.BIGINT));
            fields.add(createPropertyDescriptor(COLUMN_NAME_USER_COMMENT, PropertyType.STRING));
            _fields = Collections.unmodifiableSet(fields);
        }

        @Override
        public Set<PropertyDescriptor> getProperties()
        {
            return _fields;
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
