package org.labkey.study.assay.query;

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
 * Date: 7/17/13
 */
public class AssayAuditProvider extends AbstractAuditTypeProvider implements AuditTypeProvider
{

    public static final String COLUMN_NAME_PROTOCOL = "Protocol";
    public static final String COLUMN_NAME_TARGET_STUDY = "TargetStudy";
    public static final String COLUMN_NAME_DATASET_ID = "DatasetId";
    public static final String COLUMN_NAME_SOURCE_LSID = "SourceLsid";
    public static final String COLUMN_NAME_RECORD_COUNT = "RecordCount";

    @Override
    public String getEventName()
    {
        return AssayPublishManager.ASSAY_PUBLISH_AUDIT_EVENT;
    }

    @Override
    public String getLabel()
    {
        return "Copy-to-Study Assay events";
    }

    @Override
    public String getDescription()
    {
        return "Information about copy-to-study Assay events.";
    }

    @Override
    protected DomainKind getDomainKind()
    {
        return new AssayAuditDomainKind();
    }

    @Override
    public <K extends AuditTypeEvent> K convertEvent(AuditLogEvent event)
    {
        AssayAuditEvent bean = new AssayAuditEvent();
        copyStandardFields(bean, event);

        if (event.getIntKey1() != null)
            bean.setAssayProtocol(event.getIntKey1());

        bean.setTargetStudy(event.getKey1());

        return (K)bean;
    }

    @Override
    public <K extends AuditTypeEvent> K convertEvent(AuditLogEvent event, @Nullable Map<String, Object> dataMap)
    {
        AssayAuditEvent bean = convertEvent(event);

        if (dataMap != null)
        {
            if (dataMap.containsKey("datasetid"))
                bean.setDatasetId((Integer)dataMap.get("datasetid"));

            if (dataMap.containsKey("sourceLsid"))
                bean.setSourceLsid(String.valueOf(dataMap.get("sourceLsid")));

            if (dataMap.containsKey("recordCount"))
                bean.setRecordCount((Integer)dataMap.get("recordCount"));
        }
        return (K)bean;
    }

    @Override
    public Map<FieldKey, String> legacyNameMap()
    {
        Map<FieldKey, String> legacyMap =  super.legacyNameMap();
        legacyMap.put(FieldKey.fromParts("intKey1"), COLUMN_NAME_PROTOCOL);
        legacyMap.put(FieldKey.fromParts("key1"), COLUMN_NAME_TARGET_STUDY);

        legacyMap.put(FieldKey.fromParts("Property", "sourceLsid"), COLUMN_NAME_SOURCE_LSID);
        legacyMap.put(FieldKey.fromParts("Property", "datasetId"), COLUMN_NAME_DATASET_ID);
        legacyMap.put(FieldKey.fromParts("Property", "recordCount"), COLUMN_NAME_RECORD_COUNT);
        return legacyMap;
    }

    public static class AssayAuditEvent extends AuditTypeEvent
    {
        private int _assayProtocol;
        private String _targetStudy;
        private int _datasetId;
        private String _sourceLsid;
        private int _recordCount;

        public AssayAuditEvent()
        {
            super();
        }

        public AssayAuditEvent(String container, String comment)
        {
            super(AssayPublishManager.ASSAY_PUBLISH_AUDIT_EVENT, container, comment);
        }

        public int getAssayProtocol()
        {
            return _assayProtocol;
        }

        public void setAssayProtocol(int assayProtocol)
        {
            _assayProtocol = assayProtocol;
        }

        public String getTargetStudy()
        {
            return _targetStudy;
        }

        public void setTargetStudy(String targetStudy)
        {
            _targetStudy = targetStudy;
        }

        public int getDatasetId()
        {
            return _datasetId;
        }

        public void setDatasetId(int datasetId)
        {
            _datasetId = datasetId;
        }

        public String getSourceLsid()
        {
            return _sourceLsid;
        }

        public void setSourceLsid(String sourceLsid)
        {
            _sourceLsid = sourceLsid;
        }

        public int getRecordCount()
        {
            return _recordCount;
        }

        public void setRecordCount(int recordCount)
        {
            _recordCount = recordCount;
        }
    }

    public static class AssayAuditDomainKind extends AbstractAuditDomainKind
    {
        public static final String NAME = "AssayAuditDomain";
        public static String NAMESPACE_PREFIX = "Audit-" + NAME;

        private static final Set<PropertyStorageSpec> _fields = new LinkedHashSet<>();

        static {
            _fields.add(createFieldSpec(COLUMN_NAME_PROTOCOL, JdbcType.INTEGER));
            _fields.add(createFieldSpec(COLUMN_NAME_TARGET_STUDY, JdbcType.VARCHAR));
            _fields.add(createFieldSpec(COLUMN_NAME_DATASET_ID, JdbcType.INTEGER));
            _fields.add(createFieldSpec(COLUMN_NAME_SOURCE_LSID, JdbcType.VARCHAR));
            _fields.add(createFieldSpec(COLUMN_NAME_RECORD_COUNT, JdbcType.INTEGER));
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

