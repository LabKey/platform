package org.labkey.query.audit;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.AuditTypeProvider;
import org.labkey.api.audit.DetailedAuditTypeEvent;
import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.audit.query.DefaultAuditTypeTable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.MutableColumnInfo;
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

public class GridViewAuditProvider extends AbstractAuditTypeProvider implements AuditTypeProvider
{
    public static final String EVENT_TYPE = "GridViewAuditEvent";

    public static final String COLUMN_NAME_CUSTOM_VIEW_ID = "CustomViewId";
    public static final String COLUMN_NAME_VIEW_NAME = "ViewName";
    public static final String COLUMN_NAME_SCHEMA_NAME = "SchemaName";
    public static final String COLUMN_NAME_QUERY_NAME = "QueryName";
    public static final String COLUMN_NAME_CUSTOM_VIEW_OWNER = "CustomViewOwner";
    public static final String COLUMN_NAME_INHERITABLE = "Inheritable";
    public static final String COLUMN_NAME_HIDDEN = "Hidden";

    static final List<FieldKey> defaultVisibleColumns = new ArrayList<>();

    static
    {
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED_BY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_COMMENT));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_VIEW_NAME));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_SCHEMA_NAME));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_QUERY_NAME));
    }

    public GridViewAuditProvider()
    {
        super(new GridViewAuditDomainKind());
    }

    @Override
    public String getEventName()
    {
        return EVENT_TYPE;
    }

    @Override
    public String getLabel()
    {
        return "Grid view events";
    }

    @Override
    public String getDescription()
    {
        return "Events about grid view creation, modification, and deletion.";
    }

    @Override
    public <K extends AuditTypeEvent> Class<K> getEventClass()
    {
        return (Class<K>) GridViewAuditEvent.class;
    }

    @Override
    public TableInfo createTableInfo(UserSchema userSchema, ContainerFilter cf)
    {
        DefaultAuditTypeTable table = new DefaultAuditTypeTable(this, createStorageTableInfo(), userSchema, cf, defaultVisibleColumns)
        {
            @Override
            protected void initColumn(MutableColumnInfo col)
            {
                if (COLUMN_NAME_VIEW_NAME.equalsIgnoreCase(col.getName()))
                {
                    col.setLabel("View Name");
                }
                else if (COLUMN_NAME_SCHEMA_NAME.equalsIgnoreCase(col.getName()))
                {
                    col.setLabel("Schema Name");
                }
                else if (COLUMN_NAME_QUERY_NAME.equalsIgnoreCase(col.getName()))
                {
                    col.setLabel("Query Name");
                }
                else if (COLUMN_NAME_CUSTOM_VIEW_OWNER.equalsIgnoreCase(col.getName()))
                {
                    col.setLabel("View Owner");
                }
            }
        };
        appendValueMapColumns(table, EVENT_TYPE);

        return table;
    }

    @Override
    public List<FieldKey> getDefaultVisibleColumns()
    {
        return defaultVisibleColumns;
    }

    public static class GridViewAuditEvent extends DetailedAuditTypeEvent
    {
        private int _customViewId;
        private String _viewName;
        private String _schemaName;
        private String _queryName;
        private Integer _customViewOwner;
        private boolean _inheritable;
        private boolean _hidden;

        /** Important for reflection-based instantiation */
        @SuppressWarnings("unused")
        public GridViewAuditEvent()
        {
            super();
        }

        public GridViewAuditEvent(Container container, String comment)
        {
            super(EVENT_TYPE, container, comment);
        }

        public int getCustomViewId()
        {
            return _customViewId;
        }

        public void setCustomViewId(int customViewId)
        {
            _customViewId = customViewId;
        }

        public String getViewName()
        {
            return _viewName;
        }

        public void setViewName(String viewName)
        {
            _viewName = viewName;
        }

        public String getSchemaName()
        {
            return _schemaName;
        }

        public void setSchemaName(String schemaName)
        {
            _schemaName = schemaName;
        }

        public String getQueryName()
        {
            return _queryName;
        }

        public void setQueryName(String queryName)
        {
            _queryName = queryName;
        }

        public Integer getCustomViewOwner()
        {
            return _customViewOwner;
        }

        public void setCustomViewOwner(Integer customViewOwner)
        {
            _customViewOwner = customViewOwner;
        }

        public boolean isInheritable()
        {
            return _inheritable;
        }

        public void setInheritable(boolean inheritable)
        {
            _inheritable = inheritable;
        }

        public boolean isHidden()
        {
            return _hidden;
        }

        public void setHidden(boolean hidden)
        {
            _hidden = hidden;
        }

        @Override
        public Map<String, Object> getAuditLogMessageElements()
        {
            Map<String, Object> elements = new LinkedHashMap<>();
            elements.put("customViewId", getCustomViewId());
            elements.put("viewName", StringUtils.isEmpty(getViewName()) ? "default" : getViewName());
            elements.put("schemaName", getSchemaName());
            elements.put("queryName", getQueryName());
            elements.put("customViewOwner", getCustomViewOwner());
            elements.put("inheritable", isInheritable());
            elements.put("hidden", isHidden());
            elements.putAll(super.getAuditLogMessageElements());
            return elements;
        }
    }

    public static class GridViewAuditDomainKind extends AbstractAuditDomainKind
    {
        public static final String NAME = "GridViewAuditDomain";
        public static String NAMESPACE_PREFIX = "Audit-" + NAME;

        private final Set<PropertyDescriptor> _fields;

        public GridViewAuditDomainKind()
        {
            super(EVENT_TYPE);

            Set<PropertyDescriptor> fields = new LinkedHashSet<>();
            fields.add(createPropertyDescriptor(COLUMN_NAME_CUSTOM_VIEW_ID, PropertyType.INTEGER));
            fields.add(createPropertyDescriptor(COLUMN_NAME_VIEW_NAME, PropertyType.STRING));
            fields.add(createPropertyDescriptor(COLUMN_NAME_SCHEMA_NAME, PropertyType.STRING));
            fields.add(createPropertyDescriptor(COLUMN_NAME_QUERY_NAME, PropertyType.STRING));
            fields.add(createPropertyDescriptor(COLUMN_NAME_CUSTOM_VIEW_OWNER, PropertyType.INTEGER));
            fields.add(createPropertyDescriptor(COLUMN_NAME_INHERITABLE, PropertyType.BOOLEAN));
            fields.add(createPropertyDescriptor(COLUMN_NAME_HIDDEN, PropertyType.BOOLEAN));
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
