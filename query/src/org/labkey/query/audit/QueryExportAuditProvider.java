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
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.AuditTypeProvider;
import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.audit.query.DefaultAuditTypeTable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.view.ActionURL;
import org.labkey.query.controllers.QueryController.QueryExportAuditRedirectAction;

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
 *
 * UNDONE: Fancy QueryAuditViewFactory.QueryDetailsColumn
 */
public class QueryExportAuditProvider extends AbstractAuditTypeProvider implements AuditTypeProvider
{
    public static final String QUERY_AUDIT_EVENT = "QueryExportAuditEvent";
    public static final String COLUMN_NAME_SCHEMA_NAME = "SchemaName";
    public static final String COLUMN_NAME_QUERY_NAME = "QueryName";
    public static final String COLUMN_NAME_DETAILS_URL = "DetailsUrl";
    public static final String COLUMN_NAME_DATA_ROW_COUNT = "DataRowCount";

    static final List<FieldKey> defaultVisibleColumns = new ArrayList<>();

    static {

        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED_BY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_IMPERSONATED_BY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_PROJECT_ID));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CONTAINER));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_SCHEMA_NAME));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_QUERY_NAME));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_DATA_ROW_COUNT));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_COMMENT));
    }

    @Override
    protected AbstractAuditDomainKind getDomainKind()
    {
        return new QueryExportAuditDomainKind();
    }

    @Override
    public String getEventName()
    {
        return QUERY_AUDIT_EVENT;
    }

    @Override
    public String getLabel()
    {
        return "Query export events";
    }

    @Override
    public String getDescription()
    {
        return "Data about queries used for exporting data.";
    }

    @Override
    public Map<FieldKey, String> legacyNameMap()
    {
        Map<FieldKey, String> legacyMap =  super.legacyNameMap();
        legacyMap.put(FieldKey.fromParts("key1"), COLUMN_NAME_SCHEMA_NAME);
        legacyMap.put(FieldKey.fromParts("key2"), COLUMN_NAME_QUERY_NAME);
        legacyMap.put(FieldKey.fromParts("key3"), COLUMN_NAME_DETAILS_URL);
        legacyMap.put(FieldKey.fromParts("intKey1"), COLUMN_NAME_DATA_ROW_COUNT);
        return legacyMap;
    }

    @Override
    public <K extends AuditTypeEvent> Class<K> getEventClass()
    {
        return (Class<K>)QueryExportAuditEvent.class;
    }

    @Override
    public TableInfo createTableInfo(UserSchema userSchema)
    {
        DefaultAuditTypeTable table = new DefaultAuditTypeTable(this, createStorageTableInfo(), userSchema, defaultVisibleColumns)
        {
            @Override
            protected void initColumn(ColumnInfo col)
            {
                if (COLUMN_NAME_DATA_ROW_COUNT.equalsIgnoreCase(col.getName()))
                {
                    col.setLabel("Data Row Count");
                }
                else if (COLUMN_NAME_SCHEMA_NAME.equalsIgnoreCase(col.getName()))
                {
                    col.setLabel("Schema Name");
                }
                else if (COLUMN_NAME_QUERY_NAME.equalsIgnoreCase(col.getName()))
                {
                    col.setLabel("Query Name");
                }
            }
        };

        // Query details redirect action
        DetailsURL url = new DetailsURL(new ActionURL(QueryExportAuditRedirectAction.class, null), "rowId", FieldKey.fromParts(COLUMN_NAME_ROW_ID));
        url.setStrictContainerContextEval(true);
        table.setDetailsURL(url);

        return table;
    }


    @Override
    public List<FieldKey> getDefaultVisibleColumns()
    {
        return defaultVisibleColumns;
    }


    public static class QueryExportAuditEvent extends AuditTypeEvent
    {
        private String _schemaName;
        private String _queryName;
        private String _detailsUrl;
        private int _dataRowCount;

        public QueryExportAuditEvent()
        {
            super();
        }

        public QueryExportAuditEvent(String container, String comment)
        {
            super(QUERY_AUDIT_EVENT, container, comment);
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

        public String getDetailsUrl()
        {
            return _detailsUrl;
        }

        public void setDetailsUrl(String detailsUrl)
        {
            _detailsUrl = detailsUrl;
        }

        public int getDataRowCount()
        {
            return _dataRowCount;
        }

        public void setDataRowCount(int dataRowCount)
        {
            _dataRowCount = dataRowCount;
        }

        @Override
        public Map<String, Object> getAuditLogMessageElements()
        {
            Map<String, Object> elements = new LinkedHashMap<>();
            elements.put("schemaName", getSchemaName());
            elements.put("queryName", getQueryName());
            elements.put("detailsUrl", getDetailsUrl());
            elements.put("dataRowCount", getDataRowCount());
            elements.putAll(super.getAuditLogMessageElements());
            return elements;
        }
    }

    public static class QueryExportAuditDomainKind extends AbstractAuditDomainKind
    {
        public static final String NAME = "QueryAuditDomain";
        public static String NAMESPACE_PREFIX = "Audit-" + NAME;

        private final Set<PropertyDescriptor> _fields;

        public QueryExportAuditDomainKind()
        {
            super(QUERY_AUDIT_EVENT);

            Set<PropertyDescriptor> fields = new LinkedHashSet<>();
            fields.add(createPropertyDescriptor(COLUMN_NAME_SCHEMA_NAME, PropertyType.STRING));
            fields.add(createPropertyDescriptor(COLUMN_NAME_QUERY_NAME, PropertyType.STRING));
            fields.add(createPropertyDescriptor(COLUMN_NAME_DETAILS_URL, PropertyType.STRING, -1)); // max size
            fields.add(createPropertyDescriptor(COLUMN_NAME_DATA_ROW_COUNT, PropertyType.INTEGER));
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
