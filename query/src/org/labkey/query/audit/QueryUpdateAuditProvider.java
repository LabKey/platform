package org.labkey.query.audit;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.AuditTypeProvider;
import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.audit.query.DefaultAuditTypeTable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.UserSchema;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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

    static final List<FieldKey> defaultVisibleColumns = new ArrayList<>();

    static {

        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED_BY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_IMPERSONATED_BY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_PROJECT_ID));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CONTAINER));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_SCHEMA_NAME));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_QUERY_NAME));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_COMMENT));
    }

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
                bean.setOldRecordMap(String.valueOf(dataMap.get(QueryUpdateAuditDomainKind.OLD_RECORD_PROP_NAME)));
            if (dataMap.containsKey(QueryUpdateAuditDomainKind.NEW_RECORD_PROP_NAME))
                bean.setNewRecordMap(String.valueOf(dataMap.get(QueryUpdateAuditDomainKind.NEW_RECORD_PROP_NAME)));
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

    @Override
    public TableInfo createTableInfo(UserSchema userSchema)
    {
        Domain domain = getDomain();
        DbSchema dbSchema =  DbSchema.get(SCHEMA_NAME);

        DefaultAuditTypeTable table = new DefaultAuditTypeTable(this, domain, dbSchema, userSchema)
        {
            @Override
            protected void initColumn(ColumnInfo col)
            {
                if (COLUMN_NAME_SCHEMA_NAME.equalsIgnoreCase(col.getName()))
                {
                    col.setLabel("Schema Name");
                }
                else if (COLUMN_NAME_QUERY_NAME.equalsIgnoreCase(col.getName()))
                {
                    col.setLabel("Query Name");
                }
            }

            @Override
            public List<FieldKey> getDefaultVisibleColumns()
            {
                return defaultVisibleColumns;
            }
        };
        DetailsURL url = DetailsURL.fromString("query/queryAuditChanges.view?auditRowId=${rowId}");
        url.setStrictContainerContextEval(true);
        table.setDetailsURL(url);

        return table;
    }

    public static class QueryUpdateAuditEvent extends AuditTypeEvent
    {
        private String _rowPk;
        private String _schemaName;
        private String _queryName;
        private String _oldRecordMap;
        private String _newRecordMap;

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

        public String getOldRecordMap()
        {
            return _oldRecordMap;
        }

        public void setOldRecordMap(String oldRecordMap)
        {
            _oldRecordMap = oldRecordMap;
        }

        public String getNewRecordMap()
        {
            return _newRecordMap;
        }

        public void setNewRecordMap(String newRecordMap)
        {
            _newRecordMap = newRecordMap;
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
