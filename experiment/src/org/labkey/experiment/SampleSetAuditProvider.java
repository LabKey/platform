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
package org.labkey.experiment;

import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.AuditTypeProvider;
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
 * User: klum
 * Date: 7/21/13
 */
public class SampleSetAuditProvider extends AbstractAuditTypeProvider implements AuditTypeProvider
{
    public static final String EVENT_TYPE = "SampleSetAuditEvent";

    public static final String COLUMN_NAME_SOURCE_LSID = "SourceLsid";
    public static final String COLUMN_NAME_SAMPLE_SET_NAME = "SampleSetName";
    public static final String COLUMN_NAME_INSERT_UPDATE_CHOICE = "InsertUpdateChoice";

    static final List<FieldKey> defaultVisibleColumns = new ArrayList<>();

    static {

        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED_BY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_IMPERSONATED_BY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_SAMPLE_SET_NAME));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_PROJECT_ID));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CONTAINER));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_COMMENT));
    }

    @Override
    protected AbstractAuditDomainKind getDomainKind()
    {
        return new SampleSetAuditDomainKind();
    }

    @Override
    public String getEventName()
    {
        return EVENT_TYPE;
    }

    @Override
    public String getLabel()
    {
        return "Sample Set events";
    }

    @Override
    public String getDescription()
    {
        return "Summarizes events from sample set inserts or updates";
    }

    @Override
    public <K extends AuditTypeEvent> Class<K> getEventClass()
    {
        return (Class<K>)SampleSetAuditEvent.class;
    }

    @Override
    public List<FieldKey> getDefaultVisibleColumns()
    {
        return defaultVisibleColumns;
    }

    public static class SampleSetAuditEvent extends AuditTypeEvent
    {
        private String _sourceLsid;
        private String _sampleSetName;
        private String _insertUpdateChoice;

        public SampleSetAuditEvent()
        {
            super();
        }

        public SampleSetAuditEvent(String container, String comment)
        {
            super(EVENT_TYPE, container, comment);
        }

        public String getSourceLsid()
        {
            return _sourceLsid;
        }

        public void setSourceLsid(String sourceLsid)
        {
            _sourceLsid = sourceLsid;
        }

        public String getSampleSetName()
        {
            return _sampleSetName;
        }

        public void setSampleSetName(String sampleSetName)
        {
            _sampleSetName = sampleSetName;
        }

        public String getInsertUpdateChoice()
        {
            return _insertUpdateChoice;
        }

        public void setInsertUpdateChoice(String insertUpdateChoice)
        {
            _insertUpdateChoice = insertUpdateChoice;
        }

        @Override
        public Map<String, Object> getAuditLogMessageElements()
        {
            Map<String, Object> elements = new LinkedHashMap<>();
            elements.put("sourceLsid", getSourceLsid());
            elements.put("sampleSetName", getSampleSetName());
            elements.put("insertUpdateChoice", getInsertUpdateChoice());
            elements.putAll(super.getAuditLogMessageElements());
            return elements;
        }
    }

    public static class SampleSetAuditDomainKind extends AbstractAuditDomainKind
    {
        public static final String NAME = "SampleSetAuditDomain";
        public static String NAMESPACE_PREFIX = "Audit-" + NAME;
        private final Set<PropertyDescriptor> _fields;

        public SampleSetAuditDomainKind()
        {
            super(EVENT_TYPE);

            Set<PropertyDescriptor> fields = new LinkedHashSet<>();
            fields.add(createPropertyDescriptor(COLUMN_NAME_SOURCE_LSID, PropertyType.STRING));
            fields.add(createPropertyDescriptor(COLUMN_NAME_SAMPLE_SET_NAME, PropertyType.STRING));
            fields.add(createPropertyDescriptor(COLUMN_NAME_INSERT_UPDATE_CHOICE, PropertyType.STRING));
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
