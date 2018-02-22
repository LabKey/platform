/*
 * Copyright (c) 2014-2016 LabKey Corporation
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
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.query.FieldKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class SelectQueryAuditProvider extends AbstractAuditTypeProvider
{
    public static final String EVENT_NAME = "SelectQuery";

    protected static final List<FieldKey> DEFAULT_VISIBLE_COLUMNS = new ArrayList<>();

    static {

        DEFAULT_VISIBLE_COLUMNS.add(FieldKey.fromParts(COLUMN_NAME_CREATED));
        DEFAULT_VISIBLE_COLUMNS.add(FieldKey.fromParts(COLUMN_NAME_CREATED_BY));
        DEFAULT_VISIBLE_COLUMNS.add(FieldKey.fromParts(COLUMN_NAME_IMPERSONATED_BY));
        DEFAULT_VISIBLE_COLUMNS.add(FieldKey.fromParts("LoggedColumns"));
        DEFAULT_VISIBLE_COLUMNS.add(FieldKey.fromParts("IdentifiedData"));
        DEFAULT_VISIBLE_COLUMNS.add(FieldKey.fromParts("QueryId"));
        DEFAULT_VISIBLE_COLUMNS.add(FieldKey.fromParts(COLUMN_NAME_COMMENT));
    }

    @Override
    protected AbstractAuditDomainKind getDomainKind()
    {
        return new SelectQueryAuditDomainKind();
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
        return "Lists specific columns and identified data relating to explicitly logged queries";
    }

    @Override
    public <K extends AuditTypeEvent> Class<K> getEventClass()
    {
        return (Class<K>)SelectQueryAuditEvent.class;
    }

    @Override
    public List<FieldKey> getDefaultVisibleColumns()
    {
        return DEFAULT_VISIBLE_COLUMNS;
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
            fields.add(createPropertyDescriptor("IdentifiedData", PropertyType.STRING, -1));
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

    public List<String> getCustomizedDataLoggingValues(QueryLogging queryLogging, Set<Object> dataLoggingValues)
    {
        return null;
    }

    protected boolean isLogEmptyResults()
    {
        return true;
    }

    // if true, ignore compliance logging setting and always log
    public boolean forceQueryLogging()
    {
        return false;
    }
}
