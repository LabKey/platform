/*
 * Copyright (c) 2016 LabKey Corporation
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
package org.labkey.study.audit;

import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.AuditTypeProvider;
import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.query.FieldKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * User: jeckels
 * Date: 5/3/16
 */
public class StudyAuditProvider extends AbstractAuditTypeProvider implements AuditTypeProvider
{
    public static final String STUDY_AUDIT_EVENT = "StudyAuditEvent";
    static final List<FieldKey> defaultVisibleColumns = new ArrayList<>();

    static {

        defaultVisibleColumns.add(FieldKey.fromParts("Created"));
        defaultVisibleColumns.add(FieldKey.fromParts("CreatedBy"));
        defaultVisibleColumns.add(FieldKey.fromParts("ImpersonatedBy"));
        defaultVisibleColumns.add(FieldKey.fromParts("ProjectId"));
        defaultVisibleColumns.add(FieldKey.fromParts("Container"));
        defaultVisibleColumns.add(FieldKey.fromParts("Comment"));
    }

    @Override
    protected AbstractAuditDomainKind getDomainKind()
    {
        return new StudyAuditDomainKind();
    }

    @Override
    public String getEventName()
    {
        return STUDY_AUDIT_EVENT;
    }

    @Override
    public String getLabel()
    {
        return "Study events";
    }

    @Override
    public String getDescription()
    {
        return "Information about general changes to studies";
    }

    @Override
    public List<FieldKey> getDefaultVisibleColumns()
    {
        return defaultVisibleColumns;
    }

    @Override
    public <K extends AuditTypeEvent> Class<K> getEventClass()
    {
        return (Class<K>)AuditTypeEvent.class;
    }

    public static class StudyAuditDomainKind  extends AbstractAuditDomainKind
    {
        public static final String NAME = "StudyAuditDomain";
        public static String NAMESPACE_PREFIX = "Audit-" + NAME;

        public StudyAuditDomainKind()
        {
            super(STUDY_AUDIT_EVENT);
        }

        @Override
        public Set<PropertyDescriptor> getProperties()
        {
            return Collections.emptySet();
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
