/*
 * Copyright (c) 2014 LabKey Corporation
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
package org.labkey.api.data;


import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.audit.query.DefaultAuditTypeTable;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.view.ActionURL;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class SelectQueryAuditProvider extends AbstractAuditTypeProvider
{
    public static final String EVENT_NAME = "SelectQuery";

    static final List<FieldKey> DEFAULT_VISIBLE_COLUMNS = new ArrayList<>();

    static {

        DEFAULT_VISIBLE_COLUMNS.add(FieldKey.fromParts(COLUMN_NAME_CREATED));
        DEFAULT_VISIBLE_COLUMNS.add(FieldKey.fromParts(COLUMN_NAME_CREATED_BY));
        DEFAULT_VISIBLE_COLUMNS.add(FieldKey.fromParts(COLUMN_NAME_IMPERSONATED_BY));
        DEFAULT_VISIBLE_COLUMNS.add(FieldKey.fromParts("LoggedColumns"));
        DEFAULT_VISIBLE_COLUMNS.add(FieldKey.fromParts("IdentifiedData"));
        DEFAULT_VISIBLE_COLUMNS.add(FieldKey.fromParts("QueryId"));
        DEFAULT_VISIBLE_COLUMNS.add(FieldKey.fromParts(COLUMN_NAME_COMMENT));
    }

    private SelectQueryAuditDomainKind _domainKind = new SelectQueryAuditDomainKind();

    @Override
    protected AbstractAuditDomainKind getDomainKind()
    {
        return _domainKind;
    }

    @Override
    public String getEventName()
    {
        return EVENT_NAME;
    }

    @Override
    public String getLabel()
    {
        return "Logged select query events";
    }

    @Override
    public String getDescription()
    {
        return "Information about select queries that have logged columns";
    }

    @Override
    public <K extends AuditTypeEvent> K convertEvent(AuditLogEvent event)
    {
        throw new UnsupportedOperationException("Postdates migration, no need to convert");
    }

    @Override
    public <K extends AuditTypeEvent> Class<K> getEventClass()
    {
        return (Class<K>)SelectQueryAuditEvent.class;
    }

    @Override
    public TableInfo createTableInfo(UserSchema userSchema)
    {
        return new DefaultAuditTypeTable(this, getDomain(), userSchema)
        {
            @Override
            public List<FieldKey> getDefaultVisibleColumns()
            {
                return DEFAULT_VISIBLE_COLUMNS;
            }

            @Override
            protected void initColumn(ColumnInfo col)
            {
                if (col.getName().equalsIgnoreCase("QueryId"))
                {
                    col.setURL(new DetailsURL(getAuditUrl(), "query.RowId~eq", FieldKey.fromParts("QueryId")));
                }
                else
                {
                    super.initColumn(col);
                }
            }
        };
    }

    @Override
    public ActionURL getAuditUrl()
    {
        ActionURL url = super.getAuditUrl();
        url.addParameter("view", "ArgosQuery");
        return url;
    }

    public static class SelectQueryAuditDomainKind extends AbstractAuditDomainKind
    {
        public static final String NAME = "SelectQueryAuditDomain";
        public static String NAMESPACE_PREFIX = "Audit-" + NAME;

        private final Set<PropertyDescriptor> _fields;

        public SelectQueryAuditDomainKind()
        {
            super(EVENT_NAME);

            Set<PropertyDescriptor> fields = new LinkedHashSet<>();
            fields.add(createPropertyDescriptor("LoggedColumns", PropertyType.STRING));
            fields.add(createPropertyDescriptor("IdentifiedData", PropertyType.STRING));
            fields.add(createPropertyDescriptor("QueryId", PropertyType.INTEGER));
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
