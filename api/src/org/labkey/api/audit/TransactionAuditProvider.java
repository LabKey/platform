package org.labkey.api.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.micrometer.common.util.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.audit.query.DefaultAuditTypeTable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.JsonPrettyPrintDisplayColumnFactory;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.util.JsonUtil;
import org.labkey.api.util.UnexpectedException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class TransactionAuditProvider extends AbstractAuditTypeProvider implements AuditTypeProvider
{
    public static final String EVENT_TYPE = "TransactionAuditEvent";
    public static final String DB_SEQUENCE_NAME = "org.labkey.api.audit.Transaction";

    public static final String COLUMN_NAME_START_TIME = "StartTime";
    public static final String COLUMN_NAME_TRANSACTION_TYPE = "TransactionType";
    public static final String COLUMN_NAME_TRANSACTION_DETAILS = "TransactionDetails";

    static final List<FieldKey> defaultVisibleColumns = new ArrayList<>();

    static {
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_ROW_ID));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_TRANSACTION_TYPE));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_START_TIME));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED_BY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_IMPERSONATED_BY));
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
                else if (COLUMN_NAME_TRANSACTION_DETAILS.equalsIgnoreCase(col.getName()))
                {
                    col.setLabel("Transaction Details");
                    col.setDisplayColumnFactory(new JsonPrettyPrintDisplayColumnFactory());
                }
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

    public static Long getCurrentTransactionAuditId()
    {
        if (null == DbScope.getLabKeyScope().getCurrentTransaction())
            return null;
        return DbScope.getLabKeyScope().getCurrentTransaction().getAuditId();
    }

    public static TransactionAuditEvent getCurrentTransactionAuditEvent()
    {
        if (null == DbScope.getLabKeyScope().getCurrentTransaction())
            return null;
        return DbScope.getLabKeyScope().getCurrentTransaction().getAuditEvent();
    }

    public enum TransactionDetail
    {
        Action(false, "The controller-action for this request"),
        AuditEvents(true, "The types of audit events generated during the transaction"),
        ClientLibrary(false, "The client library (R, Python, etc) used to perform the action"),
        DataIteratorPartitions(false, "The number of partitions rows were processed in via data iterator"),
        DataIteratorUsed(false, "If data iterator was used for insert/update"),
        EditMethod(false, "The method used to insert/update data from the app (e.g., 'DetailEdit', 'GridEdit', etc)"),
        ETL(true, "The ETL process name involved in the transaction"),
        FileWatcher(true, "File watcher source(s) involved in the transaction"),
        ImportFileName(true, "The input filenames used for the import action"),
        ImportOptions(true, "Various import parameters (CrossType, CrossFolder, etc) used during the import action"),
        Product(false, "The product (Sample Manager, etc) this action originated from"),
        QueryCommand(true, "The query commands (insert.update) executed during the transaction"),
        RequestSource(false, "The URL where the request originated from");

        private final boolean multiValue;
        TransactionDetail(boolean multiValue, String description)
        {
            this.multiValue = multiValue;
        }

        public static TransactionDetail fromString(String key)
        {
            for (TransactionDetail detail : values())
            {
                if (detail.name().equalsIgnoreCase(key))
                    return detail;
            }
            return null;
        }

        public static void addAuditDetails(@NotNull Map<TransactionAuditProvider.TransactionDetail, Object> transactionDetails, @NotNull Map<String, Object> auditDetails)
        {
            if (!auditDetails.isEmpty())
            {
                for (Map.Entry<String, Object> entry : auditDetails.entrySet())
                {
                    TransactionAuditProvider.TransactionDetail detail = TransactionAuditProvider.TransactionDetail.fromString(entry.getKey());
                    if (detail != null)
                        detail.add(transactionDetails, entry.getValue());
                }
            }
        }

        public static void addAuditDetails(@NotNull Map<TransactionAuditProvider.TransactionDetail, Object> transactionDetails, @NotNull String auditDetailsJson)
        {
            if (StringUtils.isEmpty(auditDetailsJson))
                return;

            Map<String, Object> auditDetails = new HashMap<>();
            try
            {
                auditDetails = new JSONObject(auditDetailsJson).toMap();
            }
            catch (Exception ignore)
            {
            }

            addAuditDetails(transactionDetails, auditDetails);
        }

        public void add(Map<TransactionDetail, Object> detailMap, Object value)
        {
            if (value == null)
                return;

            if (!this.multiValue)
            {
                detailMap.put(this, value);
                return;
            }
            Object existing = detailMap.get(this);
            Set<String> values;
            if (existing == null)
                values = new HashSet<>();
            else if (existing instanceof Set)
                values = (Set<String>) existing;
            else
                values = new HashSet<>(List.of(existing.toString()));
            if (value instanceof String)
                values.add((String) value);
            else if (value instanceof Collection)
                for (Object v : (Collection<?>) value)
                    values.add(v.toString());
            detailMap.put(this, values);
        }
    }

    public static class TransactionAuditEvent extends AuditTypeEvent
    {
        private Date _startTime;
        private QueryService.AuditAction _auditAction;
        private String _transactionType;

        private int _commentCount = 0; // the audit event comment might have been updated/appended multiple times, for example, the original insert triggers additional insert/update via trigger scripts

        private final Map<TransactionDetail, Object> _detailMap = new HashMap<>();

        public TransactionAuditEvent()
        {
            super();
        }

        public TransactionAuditEvent(Container container, QueryService.AuditAction auditAction, long transactionId)
        {
            super(EVENT_TYPE, container, auditAction.getDefaultCommentSummary());
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

        public String getTransactionDetails()
        {
            try
            {
                return JsonUtil.DEFAULT_MAPPER.writeValueAsString(_detailMap);
            }
            catch (JsonProcessingException e)
            {
                throw UnexpectedException.wrap(e);
            }
        }

        public void addDetail(TransactionDetail key, Object value)
        {
            key.add(_detailMap, value);
        }

        public void addDetails(Map<TransactionDetail, Object> details)
        {
            _detailMap.putAll(details);
        }

        @Override
        public void setComment(String comment)
        {
            _commentCount++;
            super.setComment(comment);
        }

        public void updateCommentRowCount(int rowCount)
        {
            setComment(String.format(_auditAction.getCommentSummary(), rowCount));
        }

        public void addComment(@Nullable QueryService.AuditAction action, int rowCount)
        {
            String existingComment = this.getComment();
            QueryService.AuditAction newAction = action == null ? _auditAction : action;

            String newComment = String.format(newAction.getCommentSummary(), rowCount);

            if (_commentCount == 0) // if empty or default, replace
                updateCommentRowCount(rowCount);
            else // append
                setComment(existingComment + " " + newComment);
        }

        public boolean hasMultiActions()
        {
            return _commentCount > 1;
        }

        public QueryService.AuditAction getAuditAction()
        {
            return _auditAction;
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
            fields.add(createPropertyDescriptor(COLUMN_NAME_TRANSACTION_DETAILS, PropertyType.STRING, -1));
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
