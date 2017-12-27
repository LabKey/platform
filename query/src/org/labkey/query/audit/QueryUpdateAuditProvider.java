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
package org.labkey.query.audit;

import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.AuditTypeProvider;
import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.audit.query.DefaultAuditTypeTable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryForm;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.view.ViewContext;
import org.labkey.query.controllers.QueryController;
import org.springframework.validation.BindException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: klum
 * Date: 7/21/13
 */
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
    }

    @Override
    protected AbstractAuditDomainKind getDomainKind()
    {
        return new QueryUpdateAuditDomainKind();
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
    public Map<FieldKey, String> legacyNameMap()
    {
        Map<FieldKey, String> legacyMap =  super.legacyNameMap();
        legacyMap.put(FieldKey.fromParts("key1"), COLUMN_NAME_ROW_PK);
        legacyMap.put(FieldKey.fromParts("key2"), COLUMN_NAME_SCHEMA_NAME);
        legacyMap.put(FieldKey.fromParts("key3"), COLUMN_NAME_QUERY_NAME);
        legacyMap.put(FieldKey.fromParts("Property", AbstractAuditDomainKind.OLD_RECORD_PROP_NAME), AbstractAuditDomainKind.OLD_RECORD_PROP_NAME);
        legacyMap.put(FieldKey.fromParts("Property", AbstractAuditDomainKind.NEW_RECORD_PROP_NAME), AbstractAuditDomainKind.NEW_RECORD_PROP_NAME);
        return legacyMap;
    }

    @Override
    public <K extends AuditTypeEvent> Class<K> getEventClass()
    {
        return (Class<K>)QueryUpdateAuditEvent.class;
    }

    @Override
    public TableInfo createTableInfo(UserSchema userSchema)
    {
        DefaultAuditTypeTable table = new DefaultAuditTypeTable(this, createStorageTableInfo(), userSchema, defaultVisibleColumns)
        {
            @Override
            protected void initColumn(ColumnInfo col)
            {
                if (COLUMN_NAME_SCHEMA_NAME.equalsIgnoreCase(col.getName()))
                {
                    col.setLabel("Schema Name");
                }
                else if (COLUMN_NAME_QUERY_NAME.equalsIgnoreCase(col.getName()))
                {
                    col.setLabel("Query Name");
                }
            }
        };
        appendValueMapColumns(table);

        DetailsURL url = DetailsURL.fromString("query/queryAuditChanges.view?auditRowId=${rowId}");
        url.setStrictContainerContextEval(true);
        table.setDetailsURL(url);

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

    public static class QueryUpdateAuditEvent extends AuditTypeEvent
    {
        private String _rowPk;
        private String _schemaName;
        private String _queryName;
        private String _oldRecordMap;
        private String _newRecordMap;

        public QueryUpdateAuditEvent()
        {
            super();
        }

        public QueryUpdateAuditEvent(String container, String comment)
        {
            super(QUERY_UPDATE_AUDIT_EVENT, container, comment);
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

        public String getOldRecordMap()
        {
            return _oldRecordMap;
        }

        public void setOldRecordMap(String oldRecordMap)
        {
            _oldRecordMap = oldRecordMap;
        }

        public String getNewRecordMap()
        {
            return _newRecordMap;
        }

        public void setNewRecordMap(String newRecordMap)
        {
            _newRecordMap = newRecordMap;
        }

        @Override
        public Map<String, Object> getAuditLogMessageElements()
        {
            Map<String, Object> elements = new LinkedHashMap<>();
            elements.put("rowPk", getRowPk());
            elements.put("schemaName", getSchemaName());
            elements.put("queryName", getQueryName());
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
            fields.add(createPropertyDescriptor(OLD_RECORD_PROP_NAME, PropertyType.STRING, -1));        // varchar max
            fields.add(createPropertyDescriptor(NEW_RECORD_PROP_NAME, PropertyType.STRING, -1));        // varchar max
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
