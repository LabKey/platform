package org.labkey.api.exp.property;

import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.audit.query.DefaultAuditTypeTable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerFilterable;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.UserSchema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by davebradlee on 11/16/17.
 */
public class DomainPropertyAuditProvider extends AbstractAuditTypeProvider
{
    public static final String EVENT_NAME = "DomainPropertyAuditEvent";

    private static final String COLUMN_NAME_PROPERTY_URI = "PropertyUri";
    private static final String COLUMN_NAME_PROPERTY_NAME = "PropertyName";
    private static final String COLUMN_NAME_DOMAIN_EVENT_ID = "DomainEventId";
    private static final String COLUMN_NAME_ACTION = "Action";
    private static final String COLUMN_NAME_DOMAIN_NAME = "DomainName";

    protected static final List<FieldKey> DEFAULT_VISIBLE_COLUMNS = new ArrayList<>();

    static {

        DEFAULT_VISIBLE_COLUMNS.add(FieldKey.fromParts(COLUMN_NAME_CREATED));
        DEFAULT_VISIBLE_COLUMNS.add(FieldKey.fromParts(COLUMN_NAME_CREATED_BY));
        DEFAULT_VISIBLE_COLUMNS.add(FieldKey.fromParts(COLUMN_NAME_IMPERSONATED_BY));
        DEFAULT_VISIBLE_COLUMNS.add(FieldKey.fromParts(COLUMN_NAME_PROPERTY_NAME));
        DEFAULT_VISIBLE_COLUMNS.add(FieldKey.fromParts(COLUMN_NAME_ACTION));
        DEFAULT_VISIBLE_COLUMNS.add(FieldKey.fromParts(COLUMN_NAME_DOMAIN_NAME));
        DEFAULT_VISIBLE_COLUMNS.add(FieldKey.fromParts(COLUMN_NAME_DOMAIN_EVENT_ID));
        DEFAULT_VISIBLE_COLUMNS.add(FieldKey.fromParts(COLUMN_NAME_COMMENT));
    }

    @Override
    protected AbstractAuditDomainKind getDomainKind()
    {
        return new DomainPropertyAuditDomainKind();
    }

    @Override
    public String getEventName()
    {
        return EVENT_NAME;
    }

    @Override
    public String getLabel()
    {
        return "Domain property events";
    }

    @Override
    public String getDescription()
    {
        return "Information about creation, deletion and modification of domain properties";
    }

    @Override
    @SuppressWarnings("unchecked")
    public <K extends AuditTypeEvent> Class<K> getEventClass()
    {
        return (Class<K>)DomainPropertyAuditEvent.class;
    }

    @Override
    public List<FieldKey> getDefaultVisibleColumns()
    {
        return DEFAULT_VISIBLE_COLUMNS;
    }

    @Override
    public TableInfo createTableInfo(UserSchema userSchema)
    {
        return new DefaultAuditTypeTable(this, createStorageTableInfo(), userSchema, DEFAULT_VISIBLE_COLUMNS)
        {
            @Override
            protected void initColumn(ColumnInfo col)
            {
                if (COLUMN_NAME_DOMAIN_EVENT_ID.equalsIgnoreCase(col.getName()))
                {
                    DetailsURL url = new DetailsURL(getAuditUrl().addParameter("view", DomainAuditProvider.EVENT_TYPE),
                                                    "query.RowId~eq",
                                                    FieldKey.fromParts(COLUMN_NAME_DOMAIN_EVENT_ID));
                    url.setContainerContext(ContainerManager.getRoot());
                    col.setURL(url);
                    col.setLabel("Domain Event");
                    col.setFk(new LookupForeignKey("RowId", "RowId")
                    {
                        @Override
                        public TableInfo getLookupTableInfo()
                        {
                            DomainAuditProvider provider = new DomainAuditProvider();
                            ContainerFilterable table = (ContainerFilterable)provider.createTableInfo(getUserSchema());
                            table.setContainerFilter(ContainerFilter.EVERYTHING);
                            return table;
                        }
                    });
                }
                if (COLUMN_NAME_PROPERTY_URI.equalsIgnoreCase(col.getName()))
                    col.setLabel("Property Uri");
                if (COLUMN_NAME_PROPERTY_NAME.equalsIgnoreCase(col.getName()))
                    col.setLabel("Property Name");
                if (COLUMN_NAME_DOMAIN_NAME.equalsIgnoreCase(col.getName()))
                    col.setLabel("Domain Name");
            }
        };
    }

    public static class DomainPropertyAuditDomainKind extends AbstractAuditDomainKind
    {
        private static final String NAME = "DomainPropertyAuditDomain";
        private static String NAMESPACE_PREFIX = "Audit-" + NAME;

        private final Set<PropertyDescriptor> _fields;

        public DomainPropertyAuditDomainKind()
        {
            super(EVENT_NAME);

            Set<PropertyDescriptor> fields = new LinkedHashSet<>();
            fields.add(createPropertyDescriptor(COLUMN_NAME_PROPERTY_URI, PropertyType.STRING));
            fields.add(createPropertyDescriptor(COLUMN_NAME_PROPERTY_NAME, PropertyType.STRING));
            fields.add(createPropertyDescriptor(COLUMN_NAME_ACTION, PropertyType.STRING));
            fields.add(createPropertyDescriptor(COLUMN_NAME_DOMAIN_NAME, PropertyType.STRING));
            fields.add(createPropertyDescriptor(COLUMN_NAME_DOMAIN_EVENT_ID, PropertyType.INTEGER));
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

    public static class DomainPropertyAuditEvent extends AuditTypeEvent
    {
        private String _propertyUri;
        private String _propertyName;
        private String _action;
        private Integer _domainEventId;
        private String _domainName;

        public DomainPropertyAuditEvent()
        {
            super();
        }

        public DomainPropertyAuditEvent(String container, String propertyUri, String propertyName, String action,
                                        Integer domainEventId, String domainName, String comment)
        {
            super(EVENT_NAME, container, comment);
            _propertyName = propertyName;
            _propertyUri = propertyUri;
            _action = action;
            _domainEventId = domainEventId;
            _domainName = domainName;
        }

        public String getPropertyUri()
        {
            return _propertyUri;
        }

        public void setPropertyUri(String propertyUri)
        {
            _propertyUri = propertyUri;
        }

        public String getPropertyName()
        {
            return _propertyName;
        }

        public void setPropertyName(String propertyName)
        {
            _propertyName = propertyName;
        }

        public String getAction()
        {
            return _action;
        }

        public void setAction(String action)
        {
            _action = action;
        }

        public Integer getDomainEventId()
        {
            return _domainEventId;
        }

        public void setDomainEventId(Integer domainEventId)
        {
            _domainEventId = domainEventId;
        }

        public String getDomainName()
        {
            return _domainName;
        }

        public void setDomainName(String domainName)
        {
            _domainName = domainName;
        }

        @Override
        public Map<String, Object> getAuditLogMessageElements()
        {
            Map<String, Object> elements = new LinkedHashMap<>();
            elements.put("propertyUri", getPropertyUri());
            elements.put("propertyName", getPropertyName());
            elements.put("action", getAction());
            elements.put("domainEventId", getDomainEventId());
            elements.put("domainName", getDomainName());
            elements.putAll(super.getAuditLogMessageElements());
            return elements;
        }
    }

}
