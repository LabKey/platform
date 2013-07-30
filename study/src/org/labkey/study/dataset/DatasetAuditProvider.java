package org.labkey.study.dataset;

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
import org.labkey.study.assay.AssayPublishManager;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * User: klum
 * Date: 7/18/13
 */
public class DatasetAuditProvider extends AbstractAuditTypeProvider implements AuditTypeProvider
{
    public static final String DATASET_AUDIT_EVENT = "DatasetAuditEvent";

    public static final String COLUMN_NAME_DATASET_ID = "DatasetId";
    public static final String COLUMN_NAME_HAS_DETAILS = "HasDetails";
    public static final String COLUMN_NAME_UPLOAD_LOG = "UploadLog";

    @Override
    public String getEventName()
    {
        return DATASET_AUDIT_EVENT;
    }

    @Override
    public String getLabel()
    {
        return "Dataset events";
    }

    @Override
    public String getDescription()
    {
        return "Records modifications to dataset records";
    }

    @Override
    protected DomainKind getDomainKind()
    {
        return new DatasetAuditDomainKind();
    }

    @Override
    public <K extends AuditTypeEvent> K convertEvent(AuditLogEvent event)
    {
        DatasetAuditEvent bean = new DatasetAuditEvent();
        copyStandardFields(bean, event);

        if (event.getIntKey1() != null)
            bean.setDatasetId(event.getIntKey1());

        if (event.getIntKey2() != null)
            bean.setHasDetails(event.getIntKey2() == 1);

        bean.setUploadLog(event.getKey1());

        return (K)bean;
    }

    @Override
    public <K extends AuditTypeEvent> K convertEvent(AuditLogEvent event, @Nullable Map<String, Object> dataMap)
    {
        DatasetAuditEvent bean = convertEvent(event);

        if (dataMap != null)
        {
            if (dataMap.containsKey(DatasetAuditDomainKind.OLD_RECORD_PROP_NAME))
                bean.setOldValues(String.valueOf(dataMap.get(DatasetAuditDomainKind.OLD_RECORD_PROP_NAME)));

            if (dataMap.containsKey(DatasetAuditDomainKind.NEW_RECORD_PROP_NAME))
                bean.setNewValues(String.valueOf(dataMap.get(DatasetAuditDomainKind.NEW_RECORD_PROP_NAME)));
        }
        return (K)bean;
    }

    @Override
    public Map<FieldKey, String> legacyNameMap()
    {
        Map<FieldKey, String> legacyNames = super.legacyNameMap();
        legacyNames.put(FieldKey.fromParts("intKey1"), COLUMN_NAME_DATASET_ID);
        legacyNames.put(FieldKey.fromParts("intKey2"), COLUMN_NAME_HAS_DETAILS);
        legacyNames.put(FieldKey.fromParts("key1"), COLUMN_NAME_UPLOAD_LOG);
        legacyNames.put(FieldKey.fromParts("Property", AbstractAuditDomainKind.OLD_RECORD_PROP_NAME), AbstractAuditDomainKind.OLD_RECORD_PROP_NAME);
        legacyNames.put(FieldKey.fromParts("Property", AbstractAuditDomainKind.NEW_RECORD_PROP_NAME), AbstractAuditDomainKind.NEW_RECORD_PROP_NAME);
        return legacyNames;
    }

    @Override
    public <K extends AuditTypeEvent> Class<K> getEventClass()
    {
        return (Class<K>)DatasetAuditEvent.class;
    }

    public static class DatasetAuditEvent extends AuditTypeEvent
    {
        private int _datasetId;
        private boolean _hasDetails;
        private String _uploadLog;
        private String _oldValues;
        private String _newValues;

        public DatasetAuditEvent()
        {
            super();
        }

        public DatasetAuditEvent(String container, String comment)
        {
            super(AssayPublishManager.ASSAY_PUBLISH_AUDIT_EVENT, container, comment);
        }

        public int getDatasetId()
        {
            return _datasetId;
        }

        public void setDatasetId(int datasetId)
        {
            _datasetId = datasetId;
        }

        public boolean isHasDetails()
        {
            return _hasDetails;
        }

        public void setHasDetails(boolean hasDetails)
        {
            _hasDetails = hasDetails;
        }

        public String getUploadLog()
        {
            return _uploadLog;
        }

        public void setUploadLog(String uploadLog)
        {
            _uploadLog = uploadLog;
        }

        public String getOldValues()
        {
            return _oldValues;
        }

        public void setOldValues(String oldValues)
        {
            _oldValues = oldValues;
        }

        public String getNewValues()
        {
            return _newValues;
        }

        public void setNewValues(String newValues)
        {
            _newValues = newValues;
        }
    }

    public static class DatasetAuditDomainKind extends AbstractAuditDomainKind
    {
        public static final String NAME = "DatasetAuditDomain";
        public static String NAMESPACE_PREFIX = "Audit-" + NAME;

        private static final Set<PropertyStorageSpec> _fields = new LinkedHashSet<>();

        static {
            _fields.add(createFieldSpec(COLUMN_NAME_DATASET_ID, JdbcType.INTEGER));
            _fields.add(createFieldSpec(COLUMN_NAME_HAS_DETAILS, JdbcType.BOOLEAN));
            _fields.add(createFieldSpec(COLUMN_NAME_UPLOAD_LOG, JdbcType.VARCHAR));
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
