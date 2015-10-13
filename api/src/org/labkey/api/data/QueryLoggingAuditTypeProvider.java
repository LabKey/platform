package org.labkey.api.data;

import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.query.FieldKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * User: tgaluhn
 * Date: 10/8/2015
 */
public class QueryLoggingAuditTypeProvider extends AbstractAuditTypeProvider
{
    public static final String EVENT_NAME = "Logged Query SQL";

    static final List<FieldKey> DEFAULT_VISIBLE_COLUMNS = new ArrayList<>();

    static
    {
        DEFAULT_VISIBLE_COLUMNS.add(FieldKey.fromParts(COLUMN_NAME_CREATED));
        DEFAULT_VISIBLE_COLUMNS.add(FieldKey.fromParts(COLUMN_NAME_CONTAINER));
        DEFAULT_VISIBLE_COLUMNS.add(FieldKey.fromParts(COLUMN_NAME_CREATED_BY));
        DEFAULT_VISIBLE_COLUMNS.add(FieldKey.fromParts(COLUMN_NAME_IMPERSONATED_BY));
        DEFAULT_VISIBLE_COLUMNS.add(FieldKey.fromParts("SQL"));
    }

    @Override
    protected AbstractAuditDomainKind getDomainKind()
    {
        return new QueryLoggingAuditDomainKind();
    }

    @Override
    public String getEventName()
    {
        return EVENT_NAME;
    }

    @Override
    public String getLabel()
    {
        return "Logged sql queries";
    }

    @Override
    public String getDescription()
    {
        return "Sql queries to external datasources configured for logging";
    }

    @Override
    public <K extends AuditTypeEvent> Class<K> getEventClass()
    {
        return (Class<K>) QueryLoggingAuditTypeEvent.class;
    }

    @Override
    public List<FieldKey> getDefaultVisibleColumns()
    {
        return DEFAULT_VISIBLE_COLUMNS;
    }

    public static class QueryLoggingAuditDomainKind extends AbstractAuditDomainKind
    {
        public static final String NAME = "QueryLoggingAuditDomain";
        public static String NAMESPACE_PREFIX = "Audit-" + NAME;

        private final Set<PropertyDescriptor> _fields;

        public QueryLoggingAuditDomainKind()
        {
            super(EVENT_NAME);

            Set<PropertyDescriptor> fields = new LinkedHashSet<>();
            PropertyDescriptor sql = createPropertyDescriptor("SQL", PropertyType.STRING);
            sql.setScale(Integer.MAX_VALUE);
            fields.add(sql);
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
