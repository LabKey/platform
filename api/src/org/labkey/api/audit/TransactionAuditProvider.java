package org.labkey.api.audit;

import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.audit.query.DefaultAuditTypeTable;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class TransactionAuditProvider extends AbstractAuditTypeProvider implements AuditTypeProvider
{
    public static final String EVENT_TYPE = "TransactionAuditEvent";
    public static final String DB_SEQUENCE_NAME = "org.labkey.api.audit.Transaction";

    public static final String COLUMN_NAME_START_TIME = "StartTime";
    public static final String COLUMN_NAME_TRANSACTION_TYPE = "TransactionType";

    static final List<FieldKey> defaultVisibleColumns = new ArrayList<>();

    static {
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_TRANSACTION_TYPE));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED_BY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_IMPERSONATED_BY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_START_TIME));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_PROJECT_ID));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CONTAINER));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_COMMENT));
    }

    public TransactionAuditProvider()
    {
        super(new TransactionAuditDomainKind());
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
        return new DefaultAuditTypeTable(this, createStorageTableInfo(), userSchema, cf, getDefaultVisibleColumns())
        {
            @Override
            protected void initColumn(MutableColumnInfo col)
            {
                if (COLUMN_NAME_START_TIME.equalsIgnoreCase(col.getName()))
                    col.setLabel("Start Time");
                else if (COLUMN_NAME_TRANSACTION_TYPE.equalsIgnoreCase(col.getName()))
                    col.setLabel("Transaction Type");
                else if (COLUMN_NAME_CREATED.equalsIgnoreCase(col.getName()))
                    col.setLabel("End Time");
            }
        };
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
            setRowId(transactionId);
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
        private final Set<PropertyStorageSpec> _baseFields;


        public TransactionAuditDomainKind()
        {
            super(EVENT_TYPE);

            Set<PropertyDescriptor> fields = new LinkedHashSet<>();
            fields.add(createPropertyDescriptor(COLUMN_NAME_START_TIME, PropertyType.DATE_TIME));
            fields.add(createPropertyDescriptor(COLUMN_NAME_TRANSACTION_TYPE, PropertyType.STRING));
            _fields = Collections.unmodifiableSet(fields);

            // We override the base fields so we can use a DbSequence as the RowId and make it available
            // throughout the transaction for use in other audit logs that are created.
            Set<PropertyStorageSpec> baseFields = super.getBaseProperties(null).stream()
                    .filter(field -> !field.getName().equalsIgnoreCase("RowId"))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            baseFields.add(createFieldSpec("RowId", JdbcType.BIGINT, true, false));
            _baseFields = Collections.unmodifiableSet(baseFields);
        }

        @Override
        protected String getNamespacePrefix()
        {
            return NAMESPACE_PREFIX;
        }

        @Override
        public Set<PropertyStorageSpec> getBaseProperties(Domain domain)
        {
            return _baseFields;
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
