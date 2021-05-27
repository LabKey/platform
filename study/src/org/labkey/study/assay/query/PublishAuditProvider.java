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
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.column.BuiltInColumnTypes;
import org.labkey.api.study.Dataset;
import org.labkey.api.util.ContainerContext;
import org.labkey.api.util.PageFlowUtil;

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
public class PublishAuditProvider extends AbstractAuditTypeProvider implements AuditTypeProvider
{
    // NOTE: The event type is kept for backwards compatibility even though this audit log supports publish/recall events for both Assay and SampleType.
    public static final String PUBLISH_AUDIT_EVENT = "AssayPublishAuditEvent";

    public static final String COLUMN_NAME_PROTOCOL = "Protocol"; // assay id
    public static final String COLUMN_NAME_SAMPLE_TYPE_ID = "SampleTypeID";
    public static final String COLUMN_NAME_TARGET_STUDY = "TargetStudy";
    public static final String COLUMN_NAME_DATASET_ID = "DatasetId";
    // Dataset.PublishSource.Assay or SampleType
    public static final String COLUMN_NAME_SOURCE_TYPE = "SourceType";
    // For samples, the sourceLsid is the SampleType's LSID.
    // For assay, sourceLsid is typically the assay run's LSID. See AssayProvider.getSourceLSID().
    public static final String COLUMN_NAME_SOURCE_LSID = "SourceLsid";
    // assay or sample type name
    public static final String COLUMN_NAME_SOURCE_NAME = "SourceName";
    public static final String COLUMN_NAME_RECORD_COUNT = "RecordCount";

    static final List<FieldKey> defaultVisibleColumns = new ArrayList<>();

    static {

        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED_BY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_IMPERSONATED_BY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_PROTOCOL));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_SAMPLE_TYPE_ID));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_SOURCE_LSID));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_TARGET_STUDY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_COMMENT));
    }

    @Override
    public String getEventName()
    {
        return PUBLISH_AUDIT_EVENT;
    }

    @Override
    public String getLabel()
    {
        return "Link to Study events";
    }

    @Override
    public String getDescription()
    {
        return "Data about assay and sample data linked and recalled to studies.";
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
                    col.setDisplayColumnFactory(colInfo -> new ProtocolColumn(colInfo, containerCol, null));
                }
                else if (COLUMN_NAME_SOURCE_NAME.equalsIgnoreCase(col.getName()))
                {
                    col.setLabel("Source Name");
                }
                else if (COLUMN_NAME_SAMPLE_TYPE_ID.equalsIgnoreCase(col.getName()))
                {
                    // lookup to SampleType by ID
                    col.setLabel("Sample Type ID");
                    col.setFk(QueryForeignKey.from(getUserSchema(), ContainerFilter.EVERYTHING).schema(ExpSchema.SCHEMA_NAME).table(ExpSchema.TableType.SampleSets));

                    // ExpSampleTypeTableImpl uses a details URL with the current Container as the URL's fixed
                    // container context, but we would like to use the audit event row's container column instead.
                    var fieldKeyContext = new ContainerContext.FieldKeyContext(FieldKey.fromParts(COLUMN_NAME_CONTAINER));
                    var detailsURL = DetailsURL.fromString("experiment-showSampleType.view?rowId=${" +COLUMN_NAME_SAMPLE_TYPE_ID + "}", fieldKeyContext);
                    col.setURL(detailsURL);
                }
                else if (COLUMN_NAME_SOURCE_TYPE.equalsIgnoreCase(col.getName()))
                {
                    col.setLabel("Source Type");
                }
                else if (COLUMN_NAME_SOURCE_LSID.equalsIgnoreCase(col.getName()))
                {
                    final ColumnInfo containerCol = getColumn(FieldKey.fromParts(COLUMN_NAME_CONTAINER));

                    col.setLabel("Run");
                    col.setDisplayColumnFactory(colInfo -> new RunColumn(colInfo, containerCol, null));
                }
                else if (COLUMN_NAME_TARGET_STUDY.equalsIgnoreCase(col.getName()))
                {
                    // ContainerIdColumnInfoTransformer will create the FK
                    col.setConceptURI(BuiltInColumnTypes.CONTAINERID_CONCEPT_URI);
                    col.setLabel("Target Study");
                }
            }
        };

        FieldKey targetStudyContainerFieldKey = FieldKey.fromParts(COLUMN_NAME_TARGET_STUDY);
        DetailsURL url = DetailsURL.fromString("study/publishHistoryDetails.view?protocolId=${protocol}&sampleTypeId=${sampleTypeId}&datasetId=${datasetId}&sourceLsid=${sourceLsid}&recordCount=${recordCount}",
                new ContainerContext.FieldKeyContext(targetStudyContainerFieldKey));
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
        return new PublishAuditDomainKind();
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
        return (Class<K>) AuditEvent.class;
    }

    /**
     * Represents both publish and recall events.
     */
    public static class AuditEvent extends AuditTypeEvent
    {
        private String _sourceType; // the type of published data source (assay, sample type, ...)
        private @Nullable Integer _protocol; // assay protocol id
        private @Nullable String _sourceName; // assay name or sample type name
        private @Nullable Integer _sampleTypeId; // sample type id
        private String _targetStudy;
        private int _datasetId;
        private String _sourceLsid;
        private int _recordCount;

        public AuditEvent()
        {
            super();
        }

        public AuditEvent(String container, String comment, Dataset.PublishSource sourceType, @Nullable ExpObject source, @Nullable String sourceLsid)
        {
            super(PUBLISH_AUDIT_EVENT, container, comment);
            _sourceType = sourceType.name();
            _sourceLsid = sourceLsid;
            if (source != null)
            {
                setSourceName(source.getName());
                switch (sourceType)
                {
                    case Assay -> {
                        setProtocol(source.getRowId());
                    }
                    case SampleType -> {
                        setSampleTypeId(source.getRowId());
                    }
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

        public @Nullable String getSourceName()
        {
            return _sourceName;
        }

        public void setSourceName(@Nullable String sourceName)
        {
            _sourceName = sourceName;
        }

        public @Nullable Integer getSampleTypeId()
        {
            return _sampleTypeId;
        }

        public void setSampleTypeId(@Nullable Integer sampleTypeId)
        {
            _sampleTypeId = sampleTypeId;
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
            elements.put("sampleTypeId", getSampleTypeId());
            elements.put("targetStudy", getTargetStudy());
            elements.put("datasetId", getDatasetId());
            elements.put("sourceType", getSourceType());
            elements.put("sourceName", getSourceName());
            elements.put("sourceLsid", getSourceLsid());
            elements.put("recordCount", getRecordCount());
            elements.putAll(super.getAuditLogMessageElements());
            return elements;
        }
    }

    public static class PublishAuditDomainKind extends AbstractAuditDomainKind
    {
        public static final String NAME = "AssayAuditDomain";
        public static String NAMESPACE_PREFIX = "Audit-" + NAME;

        private final Set<PropertyDescriptor> _fields;

        public PublishAuditDomainKind()
        {
            super(PUBLISH_AUDIT_EVENT);

            Set<PropertyDescriptor> fields = new LinkedHashSet<>();
            fields.add(createPropertyDescriptor(COLUMN_NAME_PROTOCOL, PropertyType.INTEGER));
            fields.add(createPropertyDescriptor(COLUMN_NAME_SAMPLE_TYPE_ID, PropertyType.INTEGER));
            fields.add(createPropertyDescriptor(COLUMN_NAME_TARGET_STUDY, PropertyType.STRING));
            fields.add(createPropertyDescriptor(COLUMN_NAME_DATASET_ID, PropertyType.INTEGER));
            fields.add(createPropertyDescriptor(COLUMN_NAME_SOURCE_TYPE, PropertyType.STRING, 20));
            fields.add(createPropertyDescriptor(COLUMN_NAME_SOURCE_NAME, PropertyType.STRING));
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
            return PageFlowUtil.set(
                    new Index(false, COLUMN_NAME_PROTOCOL),
                    new Index(false, COLUMN_NAME_SAMPLE_TYPE_ID)
            );
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

