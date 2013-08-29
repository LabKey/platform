/*
 * Copyright (c) 2013 LabKey Corporation
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
package org.labkey.api.webdav;

import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.AuditTypeProvider;
import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.exp.property.DomainKind;

import java.util.Collections;
import java.util.Set;

/**
 * User: klum
 * Date: 7/21/13
 */
public class FileSystemBatchAuditProvider extends AbstractAuditTypeProvider implements AuditTypeProvider
{
    public static final String EVENT_TYPE = "FileSystemBatch";

    @Override
    protected DomainKind getDomainKind()
    {
        return new FileSystemBatchAuditDomainKind();
    }

    @Override
    public String getEventName()
    {
        return EVENT_TYPE;
    }

    @Override
    public String getLabel()
    {
        return "File batch events";
    }

    @Override
    public String getDescription()
    {
        return "Displays information about file system digest notifications.";
    }

    @Override
    public <K extends AuditTypeEvent> K convertEvent(AuditLogEvent event)
    {
        AuditTypeEvent bean = new AuditTypeEvent();
        copyStandardFields(bean, event);

        return (K)bean;
    }

    @Override
    public <K extends AuditTypeEvent> Class<K> getEventClass()
    {
        return (Class<K>)AuditTypeEvent.class;
    }

    public static class FileSystemBatchAuditDomainKind extends AbstractAuditDomainKind
    {
        public static final String NAME = "FileSystemBatchAuditDomain";
        public static String NAMESPACE_PREFIX = "Audit-" + NAME;

        public FileSystemBatchAuditDomainKind()
        {
            super(EVENT_TYPE);
        }

        @Override
        protected Set<PropertyStorageSpec> getColumns()
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
