package org.labkey.api.audit;

import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.audit.query.DefaultAuditTypeTable;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class TransactionAuditProvider extends AbstractAuditTypeProvider implements AuditTypeProvider
{
    public static final String EVENT_TYPE = "TransactionAuditEvent";
    public static final String DB_SEQUENCE_NAME = "TransactionAuditDbSequence";

    public static final String COLUMN_NAME_START_TIME = "StartTime";
    public static final String COLUMN_NAME_TRANSACTION_TYPE = "TransactionType";
    public static final String COLUMN_NAME_TRANSACTION_ID = "TransactionId";

    static final List<FieldKey> defaultVisibleColumns = new ArrayList<>();

    static {
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_TRANSACTION_ID));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_TRANSACTION_TYPE));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED_BY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_IMPERSONATED_BY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_START_TIME));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_PROJECT_ID));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CONTAINER));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_COMMENT));
    }

    @Override
    protected AbstractAuditDomainKind getDomainKind()
    {
        return new TransactionAuditDomainKind();
    }


    @Override
    public String getEventName()
    {
        return EVENT_TYPE;
    }

    @Override
    public String getLabel()
    {
        return "Data Transaction events";
    }

    @Override
    public String getDescription()
    {
        return "Provides basic data about certain types of transactions performed on data.";
    }

    @Override
    public TableInfo createTableInfo(UserSchema userSchema, ContainerFilter cf)
    {
        DefaultAuditTypeTable table = new DefaultAuditTypeTable(this, createStorageTableInfo(), userSchema, cf, getDefaultVisibleColumns())
        {
            @Override
            protected void initColumn(MutableColumnInfo col)
            {
                if (COLUMN_NAME_START_TIME.equalsIgnoreCase(col.getName()))
                    col.setLabel("Start Time");
                else if (COLUMN_NAME_TRANSACTION_ID.equalsIgnoreCase(col.getName()))
                    col.setLabel("Transaction ID");
                else if (COLUMN_NAME_TRANSACTION_TYPE.equalsIgnoreCase(col.getName()))
                    col.setLabel("Transaction Type");
                else if (COLUMN_NAME_CREATED.equalsIgnoreCase(col.getName()))
                    col.setLabel("End Time");
            }
        };
        table.setTitleColumn(COLUMN_NAME_TRANSACTION_ID);
        return table;
    }

    @Override
    public <K extends AuditTypeEvent> Class<K> getEventClass()
    {
        return (Class<K>) TransactionAuditEvent.class;
    }

    @Override
    public List<FieldKey> getDefaultVisibleColumns()
    {
        return defaultVisibleColumns;
    }

    public static class TransactionAuditEvent extends AuditTypeEvent
    {
        private Date _startTime;
        private QueryService.AuditAction _auditAction;
        private String _transactionType;
        private long _transactionId;
        private int rowCount;

        public TransactionAuditEvent()
        {
            super();
        }

        public TransactionAuditEvent(String container, QueryService.AuditAction auditAction, long transactionId)
        {
            super(EVENT_TYPE, container, String.format(auditAction.getCommentSummary(), "Some"));
            _auditAction = auditAction;
            _transactionType = auditAction.name();
            _startTime = new Date();
            _transactionId = transactionId;
        }

        public Date getStartTime()
        {
            return _startTime;
        }

        public void setStartTime(Date startTime)
        {
            _startTime = startTime;
        }

        public String getTransactionType()
        {
            return _transactionType;
        }

        public void setTransactionType(String transactionType)
        {
            _transactionType = transactionType;
        }

        public long getTransactionId()
        {
            return _transactionId;
        }

        public void setTransactionId(long transactionId)
        {
            _transactionId = transactionId;
        }

        public int getRowCount()
        {
            return rowCount;
        }

        public void setRowCount(int rowCount)
        {
            this.rowCount = rowCount;
            setComment(String.format(_auditAction.getCommentSummary(), getRowCount()));
        }
    }

    public static class TransactionAuditDomainKind extends AbstractAuditDomainKind
    {
        public static final String NAME = "TransactionAuditDomain";
        public static String NAMESPACE_PREFIX = "Audit-" + NAME;
        private final Set<PropertyDescriptor> _fields;


        public TransactionAuditDomainKind()
        {
            super(EVENT_TYPE);

            Set<PropertyDescriptor> fields = new LinkedHashSet<>();
            fields.add(createPropertyDescriptor(COLUMN_NAME_TRANSACTION_ID, PropertyType.BIGINT));
            fields.add(createPropertyDescriptor(COLUMN_NAME_START_TIME, PropertyType.DATE_TIME));
            fields.add(createPropertyDescriptor(COLUMN_NAME_TRANSACTION_TYPE, PropertyType.STRING));
            _fields = Collections.unmodifiableSet(fields);
        }

        @Override
        protected String getNamespacePrefix()
        {
            return NAMESPACE_PREFIX;
        }

        @Override
        public Set<PropertyDescriptor> getProperties()
        {
            return _fields;
        }

        @Override
        public String getKindName()
        {
            return NAME;
        }
    }
}
