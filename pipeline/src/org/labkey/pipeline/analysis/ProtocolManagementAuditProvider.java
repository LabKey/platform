/*
 * Copyright (c) 2016-2017 LabKey Corporation
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
package org.labkey.pipeline.analysis;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.data.Container;
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
 * Audit logger for the creation, deletion, or archival of pipeline protocols.
 * User: tgaluhn
 * Date: 11/9/2016
 */
public class ProtocolManagementAuditProvider extends AbstractAuditTypeProvider
{
    public static final String EVENT = "pipelineProtocolEvent";
    static final List<FieldKey> defaultVisibleColumns = new ArrayList<>();
    private static final String COLUMNNAME_ACTION = "action";
    private static final String COLUMNNAME_FACTORY = "factory";
    private static final String COLUMNNAME_PROTOCOL = "protocol";

    static {

        defaultVisibleColumns.add(FieldKey.fromParts("Created"));
        defaultVisibleColumns.add(FieldKey.fromParts("CreatedBy"));
        defaultVisibleColumns.add(FieldKey.fromParts("ImpersonatedBy"));
        defaultVisibleColumns.add(FieldKey.fromParts("ProjectId"));
        defaultVisibleColumns.add(FieldKey.fromParts("Container"));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMNNAME_ACTION));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMNNAME_FACTORY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMNNAME_PROTOCOL));
        defaultVisibleColumns.add(FieldKey.fromParts("Comment"));
    }


    @Override
    public String getEventName()
    {
        return EVENT;
    }

    @Override
    public String getLabel()
    {
        return "Pipeline protocol events";
    }

    @Override
    public String getDescription()
    {
        return "Information about pipeline protocol creation, deletion, or archival.";
    }

    @Override
    public <K extends AuditTypeEvent> Class<K> getEventClass()
    {
        return (Class<K>)ProtocolManagementEvent.class;
    }

    @Override
    protected AbstractAuditDomainKind getDomainKind()
    {
        return new ProtocolManagementDomainKind();
    }

    @Override
    public List<FieldKey> getDefaultVisibleColumns()
    {
        return defaultVisibleColumns;
    }

    public static class ProtocolManagementEvent extends AuditTypeEvent
    {
        private String _protocol;
        private String _action;
        private String _factory;

        public ProtocolManagementEvent()
        {
            super();
        }

        public ProtocolManagementEvent(String eventType, Container container, String factory, String protocol, String action)
        {
            super(eventType, container, makeComment(factory, protocol, action));
            _protocol = protocol;
            _action = action.toUpperCase();
            _factory = factory;
        }

        public String getProtocol()
        {
            return _protocol;
        }

        public void setProtocol(String protocol)
        {
            _protocol = protocol;
        }

        public String getAction()
        {
            return _action;
        }

        public void setAction(String action)
        {
            _action = action;
        }

        public String getFactory()
        {
            return _factory;
        }

        public void setFactory(String factory)
        {
            _factory = factory;
        }

        private static String makeComment(String factory, String protocol, String action)
        {
            return StringUtils.capitalize(action) + " protocol file " + protocol + " for factory " + factory + ".";
        }

        @Override
        public Map<String, Object> getAuditLogMessageElements()
        {
            Map<String, Object> elements = new LinkedHashMap<>();
            elements.put("protocol", getProtocol());
            elements.put("action", getAction());
            elements.put("factory", getFactory());
            elements.putAll(super.getAuditLogMessageElements());
            return elements;
        }
    }

    public static class ProtocolManagementDomainKind extends AbstractAuditDomainKind
    {
        public static final String NAME = "PipelineProtocolDomain";
        public static String NAMESPACE_PREFIX = "Audit-" + NAME;

        private final Set<PropertyDescriptor> _fields;
        public ProtocolManagementDomainKind()
        {
            super(EVENT);
            Set<PropertyDescriptor> fields = new LinkedHashSet<>();
            fields.add(createPropertyDescriptor(COLUMNNAME_ACTION, PropertyType.STRING, null, null, true));
            fields.add(createPropertyDescriptor(COLUMNNAME_FACTORY, PropertyType.STRING, null, null, true));
            fields.add(createPropertyDescriptor(COLUMNNAME_PROTOCOL, PropertyType.STRING, null, null, true));
            _fields = Collections.unmodifiableSet(fields);
        }

        @Override
        protected String getNamespacePrefix()
        {
            return NAMESPACE_PREFIX;
        }

        @Override
        public Set<PropertyDescriptor> getProperties()
        {
            return _fields;
        }

        @Override
        public String getKindName()
        {
            return NAME;
        }
    }
}
