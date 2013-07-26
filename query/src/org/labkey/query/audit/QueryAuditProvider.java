package org.labkey.query.audit;

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
public class QueryAuditProvider extends AbstractAuditTypeProvider implements AuditTypeProvider
{
    public static final String QUERY_AUDIT_EVENT = "QueryExportAuditEvent";
    public static final String COLUMN_NAME_SCHEMA_NAME = "SchemaName";
    public static final String COLUMN_NAME_QUERY_NAME = "QueryName";
    public static final String COLUMN_NAME_DETAILS_URL = "DetailsUrl";
    public static final String COLUMN_NAME_DATA_ROW_COUNT = "DataRowCount";

    @Override
    protected DomainKind getDomainKind()
    {
        return new QueryAuditDomainKind();
    }

    @Override
    public String getEventName()
    {
        return QUERY_AUDIT_EVENT;
    }

    @Override
    public String getLabel()
    {
        return "Query events";
    }

    @Override
    public String getDescription()
    {
        return "Query events";
    }

    @Override
    public <K extends AuditTypeEvent> K convertEvent(AuditLogEvent event)
    {
        QueryAuditEvent bean = new QueryAuditEvent();
        copyStandardFields(bean, event);

        bean.setSchemaName(event.getKey1());
        bean.setQueryName(event.getKey2());
        bean.setDetailsUrl(event.getKey3());

        if (event.getIntKey1() != null)
            bean.setDataRowCount(event.getIntKey1());

        return (K)bean;
    }

    @Override
    public Map<FieldKey, String> legacyNameMap()
    {
        Map<FieldKey, String> legacyMap =  super.legacyNameMap();
        legacyMap.put(FieldKey.fromParts("key1"), COLUMN_NAME_SCHEMA_NAME);
        legacyMap.put(FieldKey.fromParts("key2"), COLUMN_NAME_QUERY_NAME);
        legacyMap.put(FieldKey.fromParts("key3"), COLUMN_NAME_DETAILS_URL);
        legacyMap.put(FieldKey.fromParts("intKey1"), COLUMN_NAME_DATA_ROW_COUNT);
        return legacyMap;
    }

    public static class QueryAuditEvent extends AuditTypeEvent
    {
        private String _schemaName;
        private String _queryName;
        private String _detailsUrl;
        private int _dataRowCount;

        public QueryAuditEvent()
        {
            super();
        }

        public QueryAuditEvent(String container, String comment)
        {
            super(QUERY_AUDIT_EVENT, container, comment);
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

        public String getDetailsUrl()
        {
            return _detailsUrl;
        }

        public void setDetailsUrl(String detailsUrl)
        {
            _detailsUrl = detailsUrl;
        }

        public int getDataRowCount()
        {
            return _dataRowCount;
        }

        public void setDataRowCount(int dataRowCount)
        {
            _dataRowCount = dataRowCount;
        }
    }

    public static class QueryAuditDomainKind extends AbstractAuditDomainKind
    {
        public static final String NAME = "QueryAuditDomain";
        public static String NAMESPACE_PREFIX = "Audit-" + NAME;
        private static final Set<PropertyStorageSpec> _fields = new LinkedHashSet<>();

        static {
            _fields.add(createFieldSpec(COLUMN_NAME_SCHEMA_NAME, JdbcType.VARCHAR));
            _fields.add(createFieldSpec(COLUMN_NAME_QUERY_NAME, JdbcType.VARCHAR));
            _fields.add(createFieldSpec(COLUMN_NAME_DETAILS_URL, JdbcType.VARCHAR));
            _fields.add(createFieldSpec(COLUMN_NAME_DATA_ROW_COUNT, JdbcType.INTEGER));
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
