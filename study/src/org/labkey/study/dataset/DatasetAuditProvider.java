/*
 * Copyright (c) 2013-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.study.dataset;

import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.AuditTypeProvider;
import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.audit.query.DefaultAuditTypeTable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.study.StudySchema;
import org.labkey.study.model.SecurityType;
import org.labkey.study.model.StudyImpl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
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
    public static final String COLUMN_NAME_LSID = "Lsid";

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
        return "Data about dataset creation, deletion, and modification";
    }

    @Override
    protected AbstractAuditDomainKind getDomainKind()
    {
        return new DatasetAuditDomainKind();
    }

    @Override
    public TableInfo createTableInfo(UserSchema userSchema)
    {
        DefaultAuditTypeTable table = new DefaultAuditTypeTable(this, createStorageTableInfo(), userSchema, defaultVisibleColumns)
        {
            @Override
            protected void initColumn(ColumnInfo col)
            {
                if (COLUMN_NAME_DATASET_ID.equalsIgnoreCase(col.getName()))
                {
                    LookupForeignKey fk = new LookupForeignKey("DatasetId", "Label") {
                        public TableInfo getLookupTableInfo()
                        {
                            return StudySchema.getInstance().getTableInfoDataset();
                        }
                    };
                    fk.addJoin(FieldKey.fromParts("Container"), "container", false);
                    col.setLabel("Dataset");
                    col.setFk(fk);
                }
            }
        };
        appendValueMapColumns(table);

        DetailsURL url = DetailsURL.fromString("dataset/datasetAuditHistory.view?auditRowId=${rowId}");
        url.setStrictContainerContextEval(true);
        table.setDetailsURL(url);

        restrictDatasetAccess(table, userSchema.getUser());

        return table;
    }


    @Override
    public List<FieldKey> getDefaultVisibleColumns()
    {
        return defaultVisibleColumns;
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
                for (Dataset ds : study.getDatasets())
                {
                    if (ds.canRead(user))
                        readDatasets.add(ds.getDatasetId());
                }

                table.addInClause(table.getRealTable().getColumn(COLUMN_NAME_DATASET_ID), readDatasets);
            }
        }
    }

    @Override
    public Map<FieldKey, String> legacyNameMap()
    {
        Map<FieldKey, String> legacyNames = super.legacyNameMap();
        legacyNames.put(FieldKey.fromParts("intKey1"), COLUMN_NAME_DATASET_ID);
        legacyNames.put(FieldKey.fromParts("intKey2"), COLUMN_NAME_HAS_DETAILS);
        legacyNames.put(FieldKey.fromParts("key1"), COLUMN_NAME_LSID);
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
        private String _lsid;
        private String _oldRecordMap;
        private String _newRecordMap;

        public DatasetAuditEvent()
        {
            super();
        }

        public DatasetAuditEvent(String container, String comment)
        {
            super(DATASET_AUDIT_EVENT, container, comment);
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

        public String getLsid()
        {
            return _lsid;
        }

        public void setLsid(String lsid)
        {
            _lsid = lsid;
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

        @Override
        public Map<String, Object> getAuditLogMessageElements()
        {
            Map<String, Object> elements = new LinkedHashMap<>();
            elements.put("datasetId", getDatasetId());
            elements.put("hasDetails", isHasDetails());
            elements.put("lsid", getLsid());
            // N.B. oldRecordMap and newRecordMap can be very large; not included here
            elements.putAll(super.getAuditLogMessageElements());
            return elements;
        }
    }

    public static class DatasetAuditDomainKind extends AbstractAuditDomainKind
    {
        public static final String NAME = "DatasetAuditDomain";
        public static String NAMESPACE_PREFIX = "Audit-" + NAME;

        private static Set<PropertyDescriptor> _fields;

        public DatasetAuditDomainKind()
        {
            super(DATASET_AUDIT_EVENT);

            Set<PropertyDescriptor> fields = new LinkedHashSet<>();
            fields.add(createPropertyDescriptor(COLUMN_NAME_DATASET_ID, PropertyType.INTEGER));
            fields.add(createPropertyDescriptor(COLUMN_NAME_HAS_DETAILS, PropertyType.BOOLEAN));
            fields.add(createPropertyDescriptor(COLUMN_NAME_LSID, PropertyType.STRING));
            fields.add(createPropertyDescriptor(OLD_RECORD_PROP_NAME, PropertyType.STRING, -1));        // varchar max
            fields.add(createPropertyDescriptor(NEW_RECORD_PROP_NAME, PropertyType.STRING, -1));        // varchar max
            _fields = Collections.unmodifiableSet(fields);
        }

        @Override
        public Set<PropertyDescriptor> getProperties()
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
