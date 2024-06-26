package org.labkey.api.audit.provider;

import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.AuditTypeProvider;
import org.labkey.api.audit.DetailedAuditTypeEvent;
import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.audit.query.DefaultAuditTypeTable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.UserSchema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.labkey.api.audit.query.AbstractAuditDomainKind.NEW_RECORD_PROP_NAME;
import static org.labkey.api.audit.query.AbstractAuditDomainKind.OLD_RECORD_PROP_NAME;

public class ModulePropertiesAuditProvider extends AbstractAuditTypeProvider implements AuditTypeProvider
{
    public static final  String AUDIT_EVENT_TYPE = "ModulePropertyEvents";

    private static final String COLUMN_NAME_MODULE = "Module";
    private static final List<FieldKey> defaultVisibleColumns = new ArrayList<>();

    static
    {
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED_BY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_IMPERSONATED_BY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CONTAINER));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_MODULE));
        defaultVisibleColumns.add(FieldKey.fromParts(OLD_RECORD_PROP_NAME));
        defaultVisibleColumns.add(FieldKey.fromParts(NEW_RECORD_PROP_NAME));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_DATA_CHANGES));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_COMMENT));
    }

    public ModulePropertiesAuditProvider()
    {
        super(new ModulePropertiesAuditDomainKind());
    }

    @Override
    public String getEventName()
    {
        return AUDIT_EVENT_TYPE;
    }

    @Override
    public String getLabel()
    {
        return "Module Property events";
    }

    @Override
    public String getDescription()
    {
        return "Displays information about modifications to module properties.";
    }

    @Override
    public <K extends AuditTypeEvent> Class<K> getEventClass()
    {
        return (Class<K>) ModulePropertiesAuditEvent.class;
    }

    @Override
    public TableInfo createTableInfo(UserSchema userSchema, ContainerFilter cf)
    {
        DefaultAuditTypeTable table = new DefaultAuditTypeTable(this, createStorageTableInfo(), userSchema, cf, defaultVisibleColumns);
        appendValueMapColumns(table);
        return table;
    }

    @Override
    public List<FieldKey> getDefaultVisibleColumns()
    {
        return defaultVisibleColumns;
    }

    public static class ModulePropertiesAuditEvent extends DetailedAuditTypeEvent
    {
        private String _module;

        public ModulePropertiesAuditEvent()
        {

        }

        public ModulePropertiesAuditEvent(Container container, String comment)
        {
            super(AUDIT_EVENT_TYPE, container, comment);
        }

        public String getModule()
        {
            return _module;
        }

        public void setModule(String module)
        {
            _module = module;
        }

        @Override
        public Map<String, Object> getAuditLogMessageElements()
        {
            Map<String, Object> elements = new LinkedHashMap<>();

            elements.put("module", getModule());
            elements.putAll(super.getAuditLogMessageElements());
            return elements;
        }
    }


    public static class ModulePropertiesAuditDomainKind extends AbstractAuditDomainKind
    {
        public static final String NAME = "ModulePropertiesAuditDomain";
        public static String NAMESPACE_PREFIX = "Audit-" + NAME;

        private final Set<PropertyDescriptor> _fields;

        public ModulePropertiesAuditDomainKind()
        {
            super(AUDIT_EVENT_TYPE);

            Set<PropertyDescriptor> fields = new LinkedHashSet<>();
            fields.add(createPropertyDescriptor(COLUMN_NAME_MODULE, PropertyType.STRING));
            fields.add(createOldDataMapPropertyDescriptor());
            fields.add(createNewDataMapPropertyDescriptor());
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
