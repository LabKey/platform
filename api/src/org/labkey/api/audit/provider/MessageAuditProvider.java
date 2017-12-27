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

import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.AuditTypeProvider;
import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.MailHelper;

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
public class MessageAuditProvider extends AbstractAuditTypeProvider implements AuditTypeProvider
{
    public static final String COLUMN_NAME_FROM = "From";
    public static final String COLUMN_NAME_TO = "To";
    public static final String COLUMN_NAME_CONTENT_TYPE = "ContentType";

    static final List<FieldKey> defaultVisibleColumns = new ArrayList<>();

    static {

        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED_BY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_IMPERSONATED_BY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_FROM));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_TO));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_COMMENT));
    }

    @Override
    protected AbstractAuditDomainKind getDomainKind()
    {
        return new MessageAuditDomainKind();
    }

    @Override
    public String getEventName()
    {
        return MailHelper.MESSAGE_AUDIT_EVENT;
    }

    @Override
    public String getLabel()
    {
        return "Message events";
    }

    @Override
    public String getDescription()
    {
        return "Data about messages sent from the server.";
    }

    @Override
    public Map<FieldKey, String> legacyNameMap()
    {
        Map<FieldKey, String> legacyNames = super.legacyNameMap();
        legacyNames.put(FieldKey.fromParts("key1"), COLUMN_NAME_FROM);
        legacyNames.put(FieldKey.fromParts("key2"), COLUMN_NAME_TO);
        legacyNames.put(FieldKey.fromParts("key3"), COLUMN_NAME_CONTENT_TYPE);
        return legacyNames;
    }

    @Override
    public <K extends AuditTypeEvent> Class<K> getEventClass()
    {
        return (Class<K>)MessageAuditEvent.class;
    }

    @Override
    public List<FieldKey> getDefaultVisibleColumns()
    {
        return defaultVisibleColumns;
    }


    public static class MessageAuditEvent extends AuditTypeEvent
    {
        private String _from;
        private String _to;
        private String _contentType;

        public MessageAuditEvent()
        {
            super();
        }

        public MessageAuditEvent(String container, String comment)
        {
            super(MailHelper.MESSAGE_AUDIT_EVENT, container, comment);
        }

        public String getFrom()
        {
            return _from;
        }

        public void setFrom(String from)
        {
            _from = from;
        }

        public String getTo()
        {
            return _to;
        }

        public void setTo(String to)
        {
            _to = to;
        }

        public String getContentType()
        {
            return _contentType;
        }

        public void setContentType(String contentType)
        {
            _contentType = contentType;
        }

        @Override
        public Map<String, Object> getAuditLogMessageElements()
        {
            Map<String, Object> elements = new LinkedHashMap<>();
            elements.put("from", getFrom());
            elements.put("to", getTo());
            elements.put("contentType", getContentType());
            elements.putAll(super.getAuditLogMessageElements());
            return elements;
        }
    }

    public static class MessageAuditDomainKind extends AbstractAuditDomainKind
    {
        public static final String NAME = "MessageAuditDomain";
        public static String NAMESPACE_PREFIX = "Audit-" + NAME;

        private final Set<PropertyDescriptor> _fields;

        public MessageAuditDomainKind()
        {
            super(MailHelper.MESSAGE_AUDIT_EVENT);

            Set<PropertyDescriptor> fields = new LinkedHashSet<>();
            fields.add(createPropertyDescriptor("From", PropertyType.STRING));
            fields.add(createPropertyDescriptor("To", PropertyType.STRING));
            fields.add(createPropertyDescriptor("ContentType", PropertyType.STRING));
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
