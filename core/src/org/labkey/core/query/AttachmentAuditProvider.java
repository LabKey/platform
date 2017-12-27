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
package org.labkey.core.query;

import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.AuditTypeProvider;
import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.data.PropertyStorageSpec.Index;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.PageFlowUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: klum
 * Date: 7/19/13
 */
public class AttachmentAuditProvider extends AbstractAuditTypeProvider implements AuditTypeProvider
{
    public static final String COLUMN_NAME_ATTACHMENT_PARENT_ENTITY_ID = "AttachmentParentEntityId";
    public static final String COLUMN_NAME_ATTACHMENT = "Attachment";

    static final List<FieldKey> defaultVisibleColumns = new ArrayList<>();

    static {

        defaultVisibleColumns.add(FieldKey.fromParts("Created"));
        defaultVisibleColumns.add(FieldKey.fromParts("CreatedBy"));
        defaultVisibleColumns.add(FieldKey.fromParts("ImpersonatedBy"));
        defaultVisibleColumns.add(FieldKey.fromParts("ProjectId"));
        defaultVisibleColumns.add(FieldKey.fromParts("Container"));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_ATTACHMENT));
        defaultVisibleColumns.add(FieldKey.fromParts("Comment"));
    }

    @Override
    protected AbstractAuditDomainKind getDomainKind()
    {
        return new AttachmentAuditDomainKind();
    }

    @Override
    public String getEventName()
    {
        return AttachmentService.ATTACHMENT_AUDIT_EVENT;
    }

    @Override
    public String getLabel()
    {
        return "Attachment events";
    }

    @Override
    public String getDescription()
    {
        return "Data about attachment additions, deletions, modifications and downloads";
    }

    @Override
    public Map<FieldKey, String> legacyNameMap()
    {
        Map<FieldKey, String> legacyMap =  super.legacyNameMap();
        legacyMap.put(FieldKey.fromParts("EntityId"), COLUMN_NAME_ATTACHMENT_PARENT_ENTITY_ID);
        legacyMap.put(FieldKey.fromParts("key1"), COLUMN_NAME_ATTACHMENT);
        return legacyMap;
    }

    @Override
    public List<FieldKey> getDefaultVisibleColumns()
    {
        return defaultVisibleColumns;
    }

    @Override
    public <K extends AuditTypeEvent> Class<K> getEventClass()
    {
        return (Class<K>)AttachmentAuditEvent.class;
    }

    public static class AttachmentAuditEvent extends AuditTypeEvent
    {
        private String _attachmentParentEntityId;
        private String _attachment;     // the attachment name

        public AttachmentAuditEvent()
        {
            super();
        }

        public AttachmentAuditEvent(String container, String comment)
        {
            super(AttachmentService.ATTACHMENT_AUDIT_EVENT, container, comment);
        }

        public String getAttachmentParentEntityId()
        {
            return _attachmentParentEntityId;
        }

        public void setAttachmentParentEntityId(String attachmentParentEntityId)
        {
            _attachmentParentEntityId = attachmentParentEntityId;
        }

        public String getAttachment()
        {
            return _attachment;
        }

        public void setAttachment(String attachment)
        {
            _attachment = attachment;
        }

        @Override
        public Map<String, Object> getAuditLogMessageElements()
        {
            Map<String, Object> elements = new LinkedHashMap<>();
            elements.put("attachmentName", getAttachment());
            elements.put("attachmentParentEntityId", getAttachmentParentEntityId());
            elements.putAll(super.getAuditLogMessageElements());
            return elements;
        }
    }

    public static class AttachmentAuditDomainKind extends AbstractAuditDomainKind
    {
        public static final String NAME = "AttachmentAuditDomain";
        public static String NAMESPACE_PREFIX = "Audit-" + NAME;

        private final Set<PropertyDescriptor> _fields;

        public AttachmentAuditDomainKind()
        {
            super(AttachmentService.ATTACHMENT_AUDIT_EVENT);

            Set<PropertyDescriptor> fields = new LinkedHashSet<>();
            fields.add(createPropertyDescriptor(COLUMN_NAME_ATTACHMENT_PARENT_ENTITY_ID, PropertyType.STRING, 36)); // UNDONE: Is needed ? .setEntityId(true));
            fields.add(createPropertyDescriptor(COLUMN_NAME_ATTACHMENT, PropertyType.STRING, null, null, true));
            _fields = Collections.unmodifiableSet(fields);
        }

        @Override
        public Set<PropertyDescriptor> getProperties()
        {
            return _fields;
        }

        @Override
        public Set<Index> getPropertyIndices(Domain domain)
        {
            return PageFlowUtil.set(new Index(false, COLUMN_NAME_ATTACHMENT_PARENT_ENTITY_ID));
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
