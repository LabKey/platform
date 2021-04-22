/*
 * Copyright (c) 2013-2019 LabKey Corporation
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
package org.labkey.study.assay.query;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.AuditTypeProvider;
import org.labkey.api.audit.data.ProtocolColumn;
import org.labkey.api.audit.data.RunColumn;
import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.audit.query.DefaultAuditTypeTable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.PropertyStorageSpec.Index;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.study.Dataset;
import org.labkey.api.util.ContainerContext;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.study.StudySchema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: klum
 * Date: 7/17/13
 */
public class AssayAuditProvider extends AbstractAuditTypeProvider implements AuditTypeProvider
{
    public static final String ASSAY_PUBLISH_AUDIT_EVENT = "AssayPublishAuditEvent";

    public static final String COLUMN_NAME_PROTOCOL = "Protocol";
    public static final String COLUMN_NAME_SAMPLE_TYPE = "SampleType";
    public static final String COLUMN_NAME_SAMPLE_TYPE_ID = "SampleTypeID";
    public static final String COLUMN_NAME_TARGET_STUDY = "TargetStudy";
    public static final String COLUMN_NAME_DATASET_ID = "DatasetId";
    public static final String COLUMN_NAME_SOURCE_TYPE = "SourceType";
    public static final String COLUMN_NAME_SOURCE_LSID = "SourceLsid";
    public static final String COLUMN_NAME_RECORD_COUNT = "RecordCount";

    static final List<FieldKey> defaultVisibleColumns = new ArrayList<>();

    static {

        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED_BY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_IMPERSONATED_BY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_PROTOCOL));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_SAMPLE_TYPE));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_SOURCE_LSID));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_TARGET_STUDY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_COMMENT));
    }

    @Override
    public String getEventName()
    {
        return ASSAY_PUBLISH_AUDIT_EVENT;
    }

    @Override
    public String getLabel()
    {
        return "Link-to-Study events";
    }

    @Override
    public String getDescription()
    {
        return "Data about assay or sample data linked and recalled to studies.";
    }

    @Override
    public TableInfo createTableInfo(UserSchema userSchema, ContainerFilter cf)
    {
        DefaultAuditTypeTable table = new DefaultAuditTypeTable(this, createStorageTableInfo(), userSchema, cf, defaultVisibleColumns)
        {
            @Override
            protected void initColumn(MutableColumnInfo col)
            {
                if (COLUMN_NAME_PROTOCOL.equalsIgnoreCase(col.getName()))
                {
                    final ColumnInfo containerCol = getColumn(FieldKey.fromParts(COLUMN_NAME_CONTAINER));

                    col.setLabel("Assay/Protocol");
                    col.setDisplayColumnFactory(new DisplayColumnFactory()
                    {
                        @Override
                        public DisplayColumn createRenderer(ColumnInfo colInfo)
                        {
                            return new ProtocolColumn(colInfo, containerCol, null);
                        }
                    });
                }
                else if (COLUMN_NAME_SAMPLE_TYPE.equalsIgnoreCase(col.getName()))
                {
                    col.setLabel("Sample Type");
                    col.setFk(QueryForeignKey.from(getUserSchema(), cf).schema(ExpSchema.SCHEMA_NAME).table(ExpSchema.TableType.SampleSets));
                }
                else if (COLUMN_NAME_SAMPLE_TYPE_ID.equalsIgnoreCase(col.getName()))
                {
                    col.setLabel("Sample Type ID");
                }
                else if (COLUMN_NAME_SOURCE_TYPE.equalsIgnoreCase(col.getName()))
                {
                    col.setLabel("Source Type");
                }
                else if (COLUMN_NAME_SOURCE_LSID.equalsIgnoreCase(col.getName()))
                {
                    final ColumnInfo containerCol = getColumn(FieldKey.fromParts(COLUMN_NAME_CONTAINER));

                    col.setLabel("Run");
                    col.setDisplayColumnFactory(new DisplayColumnFactory()
                    {
                        @Override
                        public DisplayColumn createRenderer(ColumnInfo colInfo)
                        {
                            return new RunColumn(colInfo, containerCol, null);
                        }
                    });
                }
                else if (COLUMN_NAME_TARGET_STUDY.equalsIgnoreCase(col.getName()))
                {
                    LookupForeignKey fk = new LookupForeignKey("Container", "Label") {
                        @Override
                        public TableInfo getLookupTableInfo()
                        {
                            return StudySchema.getInstance().getTableInfoStudy();
                        }
                    };
                    col.setLabel("Target Study");
                    col.setFk(fk);
                }
            }
        };
        FieldKey containerFieldKey = FieldKey.fromParts(COLUMN_NAME_TARGET_STUDY);
        DetailsURL url = DetailsURL.fromString("study/publishHistoryDetails.view?publishSourceId=${protocol}&datasetId=${datasetId}&sourceLsid=${sourceLsid}&recordCount=${recordCount}", new ContainerContext.FieldKeyContext(containerFieldKey));
        url.setStrictContainerContextEval(true);

        table.setDetailsURL(url);

        return table;
    }


    @Override
    public List<FieldKey> getDefaultVisibleColumns()
    {
        return defaultVisibleColumns;
    }



    @Override
    protected AbstractAuditDomainKind getDomainKind()
    {
        return new AssayAuditDomainKind();
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

    @Override
    public <K extends AuditTypeEvent> Class<K> getEventClass()
    {
        return (Class<K>)AssayAuditEvent.class;
    }

    public static class AssayAuditEvent extends AuditTypeEvent
    {
        private String _sourceType; // the type of published data source (assay, sample type, ...)
        private @Nullable Integer _protocol;
        private @Nullable Integer _sampleType;
        private String _targetStudy;
        private int _datasetId;
        private String _sourceLsid;
        private int _recordCount;

        public AssayAuditEvent()
        {
            super();
        }

        public AssayAuditEvent(String container, String comment, Dataset.PublishSource sourceType, @Nullable ExpObject source)
        {
            super(ASSAY_PUBLISH_AUDIT_EVENT, container, comment);
            _sourceType = sourceType.name();
            if (source != null)
            {
                switch (sourceType)
                {
                    case Assay -> setProtocol(source.getRowId());
                    case SampleType -> setSampleType(source.getRowId());
                }
            }
        }

        public String getSourceType()
        {
            return _sourceType;
        }

        public void setSourceType(String sourceType)
        {
            _sourceType = sourceType;
        }

        public @Nullable Integer getProtocol()
        {
            return _protocol;
        }

        public void setProtocol(@Nullable Integer protocol)
        {
            _protocol = protocol;
        }

        public @Nullable Integer getSampleType()
        {
            return _sampleType;
        }

        public void setSampleType(@Nullable Integer sampleType)
        {
            _sampleType = sampleType;
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

        @Override
        public Map<String, Object> getAuditLogMessageElements()
        {
            Map<String, Object> elements = new LinkedHashMap<>();
            elements.put("protocol", getProtocol());
            elements.put("sampleType", getSampleType());
            elements.put("sampleTypeId", getSampleType());
            elements.put("targetStudy", getTargetStudy());
            elements.put("datasetId", getDatasetId());
            elements.put("sourceType", getSourceType());
            elements.put("sourceLsid", getSourceLsid());
            elements.put("recordCount", getRecordCount());
            elements.putAll(super.getAuditLogMessageElements());
            return elements;
        }
    }

    public static class AssayAuditDomainKind extends AbstractAuditDomainKind
    {
        public static final String NAME = "AssayAuditDomain";
        public static String NAMESPACE_PREFIX = "Audit-" + NAME;

        private final Set<PropertyDescriptor> _fields;

        public AssayAuditDomainKind()
        {
            super(ASSAY_PUBLISH_AUDIT_EVENT);

            Set<PropertyDescriptor> fields = new LinkedHashSet<>();
            fields.add(createPropertyDescriptor(COLUMN_NAME_PROTOCOL, PropertyType.INTEGER));
            fields.add(createPropertyDescriptor(COLUMN_NAME_SAMPLE_TYPE, PropertyType.STRING));
            fields.add(createPropertyDescriptor(COLUMN_NAME_SAMPLE_TYPE_ID, PropertyType.INTEGER));
            fields.add(createPropertyDescriptor(COLUMN_NAME_TARGET_STUDY, PropertyType.STRING));
            fields.add(createPropertyDescriptor(COLUMN_NAME_DATASET_ID, PropertyType.INTEGER));
            fields.add(createPropertyDescriptor(COLUMN_NAME_SOURCE_TYPE, PropertyType.STRING, 20));
            fields.add(createPropertyDescriptor(COLUMN_NAME_SOURCE_LSID, PropertyType.STRING));
            fields.add(createPropertyDescriptor(COLUMN_NAME_RECORD_COUNT, PropertyType.INTEGER));
            _fields = Collections.unmodifiableSet(fields);
        }

        @Override
        public Set<PropertyDescriptor> getProperties()
        {
            return _fields;
        }

        @Override
        public Set<Index> getPropertyIndices(Domain domain)
        {
            return PageFlowUtil.set(new Index(false, COLUMN_NAME_PROTOCOL));
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

