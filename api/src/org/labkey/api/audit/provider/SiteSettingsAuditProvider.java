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
import org.labkey.api.audit.query.DefaultAuditTypeTable;
import org.labkey.api.data.HtmlDisplayColumnFactory;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.UserSchema;

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
public class SiteSettingsAuditProvider extends AbstractAuditTypeProvider implements AuditTypeProvider
{
    public static final  String AUDIT_EVENT_TYPE = "AppPropsEvent";

    private static final String COLUMN_NAME_CHANGES = "Changes";
    private static final String AUDIT_PROP_DIFF = "AppPropsDiff";
    private static final List<FieldKey> defaultVisibleColumns = new ArrayList<>();

    static
    {
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED_BY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_IMPERSONATED_BY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_COMMENT));
    }

    @Override
    protected AbstractAuditDomainKind getDomainKind()
    {
        return new SiteSettingsAuditDomainKind();
    }

    @Override
    public String getEventName()
    {
        return AUDIT_EVENT_TYPE;
    }

    @Override
    public String getLabel()
    {
        return "Site Settings events";
    }

    @Override
    public String getDescription()
    {
        return "Displays information about modifications to the site settings.";
    }

    @Override
    public <K extends AuditTypeEvent> Class<K> getEventClass()
    {
        return (Class<K>)SiteSettingsAuditEvent.class;
    }

    @Override
    public TableInfo createTableInfo(UserSchema userSchema)
    {
        DefaultAuditTypeTable table = new DefaultAuditTypeTable(this, createStorageTableInfo(), userSchema, defaultVisibleColumns);
        table.getColumn("Changes").setDisplayColumnFactory(new HtmlDisplayColumnFactory());
        List<FieldKey> defaultCols = new ArrayList<>(table.getDefaultVisibleColumns());
        defaultCols.add(FieldKey.fromParts("Changes"));
        table.setDefaultVisibleColumns(defaultCols);
        DetailsURL url = DetailsURL.fromString("audit/showSiteSettingsAuditDetails.view?id=${rowId}");
        url.setStrictContainerContextEval(true);
        table.setDetailsURL(url);

        return table;
    }

    @Override
    public List<FieldKey> getDefaultVisibleColumns()
    {
        return defaultVisibleColumns;
    }

    public static class SiteSettingsAuditEvent extends AuditTypeEvent
    {
        private String _changes;

        public SiteSettingsAuditEvent()
        {
            super();
        }

        public SiteSettingsAuditEvent(String container, String comment)
        {
            super(AUDIT_EVENT_TYPE, container, comment);
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
            Map<String, Object> elements = new LinkedHashMap<>();
            elements.put("changes", getChanges());
            elements.putAll(super.getAuditLogMessageElements());
            return elements;
        }
    }

    @Override
    public Map<FieldKey, String> legacyNameMap()
    {
        Map<FieldKey, String> legacyNames = super.legacyNameMap();
        legacyNames.put(FieldKey.fromParts("Property", AUDIT_PROP_DIFF), COLUMN_NAME_CHANGES);
        return legacyNames;
    }

    public static class SiteSettingsAuditDomainKind extends AbstractAuditDomainKind
    {
        public static final String NAME = "SiteSettingsAuditDomain";
        public static String NAMESPACE_PREFIX = "Audit-" + NAME;

        private final Set<PropertyDescriptor> _fields;

        public SiteSettingsAuditDomainKind()
        {
            super(AUDIT_EVENT_TYPE);

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
