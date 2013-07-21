package org.labkey.query.audit;

import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.AuditTypeProvider;
import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.exp.property.DomainKind;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: 7/21/13
 */
public class QueryUpdateAuditProvider extends AbstractAuditTypeProvider implements AuditTypeProvider
{
    public static final String QUERY_UPDATE_AUDIT_EVENT = "QueryUpdateAuditEvent";

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
            _fields.add(createFieldSpec("RowPk", JdbcType.VARCHAR));
            _fields.add(createFieldSpec("SchemaName", JdbcType.VARCHAR));
            _fields.add(createFieldSpec("QueryName", JdbcType.VARCHAR));
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
