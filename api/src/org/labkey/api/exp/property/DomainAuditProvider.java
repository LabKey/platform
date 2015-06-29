/*
 * Copyright (c) 2013-2014 LabKey Corporation
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
package org.labkey.api.exp.property;

import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.AuditTypeProvider;
import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.audit.query.DefaultAuditTypeTable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.query.FieldKey;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.UserSchema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;

/**
 * User: klum
 * Date: 7/21/13
 */
public class DomainAuditProvider extends AbstractAuditTypeProvider implements AuditTypeProvider
{
    public static final String EVENT_TYPE = "DomainAuditEvent";

    public static final String COLUMN_NAME_DOMAIN_URI = "DomainUri";
    public static final String COLUMN_NAME_DOMAIN_NAME = "DomainName";

    static final List<FieldKey> defaultVisibleColumns = new ArrayList<>();

    static {

        defaultVisibleColumns.add(FieldKey.fromParts("Created"));
        defaultVisibleColumns.add(FieldKey.fromParts("CreatedBy"));
        defaultVisibleColumns.add(FieldKey.fromParts("ImpersonatedBy"));
        defaultVisibleColumns.add(FieldKey.fromParts("ProjectId"));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_DOMAIN_URI));
        defaultVisibleColumns.add(FieldKey.fromParts("Comment"));
    }

    @Override
    protected AbstractAuditDomainKind getDomainKind()
    {
        return new DomainAuditDomainKind();
    }

    @Override
    public String getEventName()
    {
        return EVENT_TYPE;
    }

    @Override
    public String getLabel()
    {
        return "Domain events";
    }

    @Override
    public String getDescription()
    {
        return "Domain events";
    }

    @Override
    public TableInfo createTableInfo(UserSchema userSchema)
    {
        return new DefaultAuditTypeTable(this, createStorageTableInfo(), userSchema, defaultVisibleColumns)
        {
            @Override
            protected void initColumn(ColumnInfo col)
            {
                if (COLUMN_NAME_DOMAIN_URI.equalsIgnoreCase(col.getName()))
                {
                    final ColumnInfo container = getColumn(FieldKey.fromParts("Container"));
                    final ColumnInfo name = getColumn(FieldKey.fromParts("DomainName"));

                    col.setLabel("Domain");
                    col.setDisplayColumnFactory(new DisplayColumnFactory()
                    {
                        public DisplayColumn createRenderer(ColumnInfo colInfo)
                        {
                            return new DomainAuditViewFactory.DomainColumn(colInfo, container, name);
                        }
                    });
                }
            }
        };
    }

    @Override
    public List<FieldKey> getDefaultVisibleColumns()
    {
        return defaultVisibleColumns;
    }

    @Override
    public <K extends AuditTypeEvent> K convertEvent(AuditLogEvent event)
    {
        DomainAuditEvent bean = new DomainAuditEvent();
        copyStandardFields(bean, event);

        bean.setDomainUri(event.getKey1());
        bean.setDomainName(event.getKey3());

        return (K)bean;
    }

    @Override
    public Map<FieldKey, String> legacyNameMap()
    {
        Map<FieldKey, String> legacyNames = super.legacyNameMap();
        legacyNames.put(FieldKey.fromParts("key1"), COLUMN_NAME_DOMAIN_URI);
        legacyNames.put(FieldKey.fromParts("key3"), COLUMN_NAME_DOMAIN_NAME);
        return legacyNames;
    }

    @Override
    public <K extends AuditTypeEvent> Class<K> getEventClass()
    {
        return (Class<K>)DomainAuditEvent.class;
    }

    public static class DomainAuditEvent extends AuditTypeEvent
    {
        private String _domainUri;
        private String _domainName;

        public DomainAuditEvent()
        {
            super();
        }

        public DomainAuditEvent(String container, String comment)
        {
            super(EVENT_TYPE, container, comment);
        }

        public String getDomainUri()
        {
            return _domainUri;
        }

        public void setDomainUri(String domainUri)
        {
            _domainUri = domainUri;
        }

        public String getDomainName()
        {
            return _domainName;
        }

        public void setDomainName(String domainName)
        {
            _domainName = domainName;
        }
    }

    public static class DomainAuditDomainKind extends AbstractAuditDomainKind
    {
        public static final String NAME = "DomainAuditDomain";
        public static String NAMESPACE_PREFIX = "Audit-" + NAME;

        private final Set<PropertyDescriptor> _fields;

        public DomainAuditDomainKind()
        {
            super(EVENT_TYPE);

            Set<PropertyDescriptor> fields = new LinkedHashSet<>();
            fields.add(createPropertyDescriptor(COLUMN_NAME_DOMAIN_URI, PropertyType.STRING));
            fields.add(createPropertyDescriptor(COLUMN_NAME_DOMAIN_NAME, PropertyType.STRING));
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
