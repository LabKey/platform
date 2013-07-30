package org.labkey.query.audit;

import org.jetbrains.annotations.Nullable;
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
public class QueryUpdateAuditProvider extends AbstractAuditTypeProvider implements AuditTypeProvider
{
    public static final String QUERY_UPDATE_AUDIT_EVENT = "QueryUpdateAuditEvent";

    public static final String COLUMN_NAME_ROW_PK = "RowPk";
    public static final String COLUMN_NAME_SCHEMA_NAME = "SchemaName";
    public static final String COLUMN_NAME_QUERY_NAME = "QueryName";

    @Override
    protected DomainKind getDomainKind()
    {
        return new QueryUpdateAuditDomainKind();
    }

    @Override
    public String getEventName()
    {
        return QUERY_UPDATE_AUDIT_EVENT;
    }

    @Override
    public String getLabel()
    {
        return "Query update events";
    }

    @Override
    public String getDescription()
    {
        return "Query update events";
    }

    @Override
    public <K extends AuditTypeEvent> K convertEvent(AuditLogEvent event)
    {
        QueryUpdateAuditEvent bean = new QueryUpdateAuditEvent();
        copyStandardFields(bean, event);

        bean.setRowPk(event.getKey1());
        bean.setSchemaName(event.getKey2());
        bean.setQueryName(event.getKey3());

        return (K)bean;
    }

    @Override
    public <K extends AuditTypeEvent> K convertEvent(AuditLogEvent event, @Nullable Map<String, Object> dataMap)
    {
        QueryUpdateAuditEvent bean = convertEvent(event);

        if (dataMap != null)
        {
            if (dataMap.containsKey(QueryUpdateAuditDomainKind.OLD_RECORD_PROP_NAME))
                bean.setOldRecord(String.valueOf(dataMap.get(QueryUpdateAuditDomainKind.OLD_RECORD_PROP_NAME)));
            if (dataMap.containsKey(QueryUpdateAuditDomainKind.NEW_RECORD_PROP_NAME))
                bean.setNewRecord(String.valueOf(dataMap.get(QueryUpdateAuditDomainKind.NEW_RECORD_PROP_NAME)));
        }
        return (K)bean;
    }

    @Override
    public Map<FieldKey, String> legacyNameMap()
    {
        Map<FieldKey, String> legacyMap =  super.legacyNameMap();
        legacyMap.put(FieldKey.fromParts("key1"), COLUMN_NAME_ROW_PK);
        legacyMap.put(FieldKey.fromParts("key2"), COLUMN_NAME_SCHEMA_NAME);
        legacyMap.put(FieldKey.fromParts("key3"), COLUMN_NAME_QUERY_NAME);
        legacyMap.put(FieldKey.fromParts("Property", AbstractAuditDomainKind.OLD_RECORD_PROP_NAME), AbstractAuditDomainKind.OLD_RECORD_PROP_NAME);
        legacyMap.put(FieldKey.fromParts("Property", AbstractAuditDomainKind.NEW_RECORD_PROP_NAME), AbstractAuditDomainKind.NEW_RECORD_PROP_NAME);
        return legacyMap;
    }

    @Override
    public <K extends AuditTypeEvent> Class<K> getEventClass()
    {
        return (Class<K>)QueryUpdateAuditEvent.class;
    }

    public static class QueryUpdateAuditEvent extends AuditTypeEvent
    {
        private String _rowPk;
        private String _schemaName;
        private String _queryName;
        private String _oldRecord;
        private String _newRecord;

        public QueryUpdateAuditEvent()
        {
            super();
        }

        public QueryUpdateAuditEvent(String container, String comment)
        {
            super(QUERY_UPDATE_AUDIT_EVENT, container, comment);
        }

        public String getRowPk()
        {
            return _rowPk;
        }

        public void setRowPk(String rowPk)
        {
            _rowPk = rowPk;
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

        public String getOldRecord()
        {
            return _oldRecord;
        }

        public void setOldRecord(String oldRecord)
        {
            _oldRecord = oldRecord;
        }

        public String getNewRecord()
        {
            return _newRecord;
        }

        public void setNewRecord(String newRecord)
        {
            _newRecord = newRecord;
        }
    }

    public static class QueryUpdateAuditDomainKind extends AbstractAuditDomainKind
    {
        public static final String NAME = "QueryUpdateAuditDomain";
        public static String NAMESPACE_PREFIX = "Audit-" + NAME;
        private static final Set<PropertyStorageSpec> _fields = new LinkedHashSet<>();

        static {
            _fields.add(createFieldSpec(COLUMN_NAME_ROW_PK, JdbcType.VARCHAR));
            _fields.add(createFieldSpec(COLUMN_NAME_SCHEMA_NAME, JdbcType.VARCHAR));
            _fields.add(createFieldSpec(COLUMN_NAME_QUERY_NAME, JdbcType.VARCHAR));
            _fields.add(createFieldSpec(OLD_RECORD_PROP_NAME, JdbcType.VARCHAR));
            _fields.add(createFieldSpec(NEW_RECORD_PROP_NAME, JdbcType.VARCHAR));
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
