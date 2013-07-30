package org.labkey.search.audit;

import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.AuditTypeProvider;
import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.query.FieldKey;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * User: klum
 * Date: 7/21/13
 */
public class SearchAuditProvider extends AbstractAuditTypeProvider implements AuditTypeProvider
{
    public static final String EVENT_TYPE = "SearchAuditEvent";

    public static final String COLUMN_NAME_QUERY = "Query";

    @Override
    protected DomainKind getDomainKind()
    {
        return new SearchAuditDomainKind();
    }

    @Override
    public String getEventName()
    {
        return EVENT_TYPE;
    }

    @Override
    public String getLabel()
    {
        return "Search";
    }

    @Override
    public String getDescription()
    {
        return "Search queries";
    }

    @Override
    public <K extends AuditTypeEvent> K convertEvent(AuditLogEvent event)
    {
        SearchAuditEvent bean = new SearchAuditEvent();
        copyStandardFields(bean, event);

        bean.setQuery(event.getKey1());

        return (K)bean;
    }

    @Override
    public Map<FieldKey, String> legacyNameMap()
    {
        Map<FieldKey, String> legacyNames = super.legacyNameMap();
        legacyNames.put(FieldKey.fromParts("key1"), COLUMN_NAME_QUERY);
        return legacyNames;
    }

    @Override
    public <K extends AuditTypeEvent> Class<K> getEventClass()
    {
        return (Class<K>)SearchAuditEvent.class;
    }

    public static class SearchAuditEvent extends AuditTypeEvent
    {
        private String _query;

        public SearchAuditEvent()
        {
            super();
        }

        public SearchAuditEvent(String container, String comment)
        {
            super(EVENT_TYPE, container, comment);
        }

        public String getQuery()
        {
            return _query;
        }

        public void setQuery(String query)
        {
            _query = query;
        }
    }

    public static class SearchAuditDomainKind extends AbstractAuditDomainKind
    {
        public static final String NAME = "SearchAuditDomain";
        public static String NAMESPACE_PREFIX = "Audit-" + NAME;
        private static final Set<PropertyStorageSpec> _fields = new LinkedHashSet<>();

        static {
            _fields.add(createFieldSpec(COLUMN_NAME_QUERY, JdbcType.VARCHAR));
        }

        @Override
        protected Set<PropertyStorageSpec> getColumns()
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
