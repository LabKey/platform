package org.labkey.study.dataset;

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
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.study.DataSet;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.ContainerContext;
import org.labkey.api.view.ActionURL;
import org.labkey.study.StudySchema;
import org.labkey.study.assay.AssayPublishManager;
import org.labkey.study.controllers.DatasetController;
import org.labkey.study.model.SecurityType;
import org.labkey.study.model.StudyImpl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
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

    static final List<FieldKey> defaultVisibleColumns = new ArrayList<>();

    static {

        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED_BY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_IMPERSONATED_BY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_PROJECT_ID));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CONTAINER));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_DATASET_ID));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_COMMENT));
    }

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
    public TableInfo createTableInfo(UserSchema userSchema)
    {
        Domain domain = getDomain();
        DbSchema dbSchema =  DbSchema.get(SCHEMA_NAME);

        DefaultAuditTypeTable table = new DefaultAuditTypeTable(this, domain, dbSchema, userSchema)
        {
            @Override
            protected void initColumn(ColumnInfo col)
            {
                if (COLUMN_NAME_DATASET_ID.equalsIgnoreCase(col.getName()))
                {
                    LookupForeignKey fk = new LookupForeignKey("DatasetId", "Label") {
                        public TableInfo getLookupTableInfo()
                        {
                            return StudySchema.getInstance().getTableInfoDataSet();
                        }
                    };
                    col.setLabel("Dataset");
                    col.setFk(fk);
                }
            }

            @Override
            public List<FieldKey> getDefaultVisibleColumns()
            {
                return defaultVisibleColumns;
            }
        };
        DetailsURL url = DetailsURL.fromString("dataset/datasetAuditHistory.view?auditRowId=${rowId}");
        url.setStrictContainerContextEval(true);
        table.setDetailsURL(url);

        restrictDatasetAccess(table, userSchema.getUser());

        return table;
    }

    /**
     * issue 14463 : filter the audit records to those that the user has read access to. For basic
     * study security, the container security policy should suffice, for advanced security we
     * need to check the list of datasets the user can read.
     */
    private void restrictDatasetAccess(FilteredTable table, User user)
    {
        Study study = StudyService.get().getStudy(table.getContainer());

        if (study instanceof StudyImpl)
        {
            SecurityType type = ((StudyImpl)study).getSecurityType();

            // create the dataset in clause if we are configured for advanced security
            if (type == SecurityType.ADVANCED_READ || type == SecurityType.ADVANCED_WRITE)
            {
                List<Integer> readDatasets = new ArrayList<>();
                for (DataSet ds : study.getDataSets())
                {
                    if (ds.canRead(user))
                        readDatasets.add(ds.getDataSetId());
                }

                table.addInClause(table.getRealTable().getColumn(COLUMN_NAME_DATASET_ID), readDatasets);
            }
        }
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
                bean.setOldRecordMap(String.valueOf(dataMap.get(DatasetAuditDomainKind.OLD_RECORD_PROP_NAME)));

            if (dataMap.containsKey(DatasetAuditDomainKind.NEW_RECORD_PROP_NAME))
                bean.setNewRecordMap(String.valueOf(dataMap.get(DatasetAuditDomainKind.NEW_RECORD_PROP_NAME)));
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
        private String _oldRecordMap;
        private String _newRecordMap;

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
