/*
 * Copyright (c) 2015-2017 LabKey Corporation
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
package org.labkey.api.security;

import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.query.FieldKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: tgaluhn
 * Date: 3/20/2015
 */
public class AuthenticationProviderConfigAuditTypeProvider extends AbstractAuditTypeProvider
{
    private static final String EVENT_TYPE = "AuthenticationProviderConfiguration";
    private static final String COLUMN_NAME_CHANGES = "Changes";

    static final List<FieldKey> DEFAULT_VISIBLE_COLUMNS = new ArrayList<>();

    static {

        DEFAULT_VISIBLE_COLUMNS.add(FieldKey.fromParts(COLUMN_NAME_CREATED));
        DEFAULT_VISIBLE_COLUMNS.add(FieldKey.fromParts(COLUMN_NAME_CREATED_BY));
        DEFAULT_VISIBLE_COLUMNS.add(FieldKey.fromParts(COLUMN_NAME_IMPERSONATED_BY));
        DEFAULT_VISIBLE_COLUMNS.add(FieldKey.fromParts(COLUMN_NAME_COMMENT));
        DEFAULT_VISIBLE_COLUMNS.add(FieldKey.fromParts(COLUMN_NAME_CHANGES));
    }

    @Override
    protected AbstractAuditDomainKind getDomainKind()
    {
        return new AuthProviderConfigAuditDomainKind();
    }

    @Override
    public String getEventName()
    {
        return EVENT_TYPE;
    }

    @Override
    public String getLabel()
    {
        return "Authentication Provider Configuration events";
    }

    @Override
    public String getDescription()
    {
        return "Data about modifications to the authentication provider configuration";
    }

    @Override
    public <K extends AuditTypeEvent> Class<K> getEventClass()
    {
        return (Class<K>)AuthProviderConfigAuditEvent.class;
    }


    @Override
    public List<FieldKey> getDefaultVisibleColumns()
    {
        return DEFAULT_VISIBLE_COLUMNS;
    }

    public static class AuthProviderConfigAuditEvent extends AuditTypeEvent
    {
        private String _changes;

        public AuthProviderConfigAuditEvent()
        {
            super();
        }

        public AuthProviderConfigAuditEvent(String container, String comment)
        {
            super(EVENT_TYPE, container, comment);
        }

        public String getChanges()
        {
            return _changes;
        }

        public void setChanges(String changes)
        {
            _changes = changes;
        }

        @Override
        public Map<String, Object> getAuditLogMessageElements()
        {
            Map<String, Object> elements =  new LinkedHashMap<>();
            elements.put("changes", getChanges());
            elements.putAll(super.getAuditLogMessageElements());
            return elements;
        }
    }


    public static class AuthProviderConfigAuditDomainKind extends AbstractAuditDomainKind
    {
        public static final String NAME = "AuthenticationProviderConfigAuditDomain";
        public static String NAMESPACE_PREFIX = "Audit-" + NAME;

        private final Set<PropertyDescriptor> _fields;

        public AuthProviderConfigAuditDomainKind()
        {
            super(EVENT_TYPE);

            Set<PropertyDescriptor> fields = new LinkedHashSet<>();
            fields.add(createPropertyDescriptor(COLUMN_NAME_CHANGES, PropertyType.STRING));
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
