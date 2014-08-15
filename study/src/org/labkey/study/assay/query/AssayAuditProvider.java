/*
 * Copyright (c) 2013-2014 LabKey Corporation
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
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.AuditTypeProvider;
import org.labkey.api.audit.data.ProtocolColumn;
import org.labkey.api.audit.data.RunColumn;
import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.audit.query.DefaultAuditTypeTable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.PropertyStorageSpec.Index;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.util.ContainerContext;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.study.StudySchema;
import org.labkey.study.assay.AssayPublishManager;

import java.util.ArrayList;
import java.util.Collections;
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

    public static final String COLUMN_NAME_PROTOCOL = "Protocol";
    public static final String COLUMN_NAME_TARGET_STUDY = "TargetStudy";
    public static final String COLUMN_NAME_DATASET_ID = "DatasetId";
    public static final String COLUMN_NAME_SOURCE_LSID = "SourceLsid";
    public static final String COLUMN_NAME_RECORD_COUNT = "RecordCount";

    static final List<FieldKey> defaultVisibleColumns = new ArrayList<>();

    static {

        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED_BY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_IMPERSONATED_BY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_PROTOCOL));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_SOURCE_LSID));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_TARGET_STUDY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_COMMENT));
    }

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
    public TableInfo createTableInfo(UserSchema userSchema)
    {
        Domain domain = getDomain();

        DefaultAuditTypeTable table = new DefaultAuditTypeTable(this, domain, userSchema)
        {
            @Override
            protected void initColumn(ColumnInfo col)
            {
                if (COLUMN_NAME_PROTOCOL.equalsIgnoreCase(col.getName()))
                {
                    final ColumnInfo containerCol = getColumn(FieldKey.fromParts(COLUMN_NAME_CONTAINER));

                    col.setLabel("Assay/Protocol");
                    col.setDisplayColumnFactory(new DisplayColumnFactory()
                    {
                        public DisplayColumn createRenderer(ColumnInfo colInfo)
                        {
                            return new ProtocolColumn(colInfo, containerCol, null);
                        }
                    });
                }
                else if (COLUMN_NAME_SOURCE_LSID.equalsIgnoreCase(col.getName()))
                {
                    final ColumnInfo containerCol = getColumn(FieldKey.fromParts(COLUMN_NAME_CONTAINER));

                    col.setLabel("Run");
                    col.setDisplayColumnFactory(new DisplayColumnFactory()
                    {
                        public DisplayColumn createRenderer(ColumnInfo colInfo)
                        {
                            return new RunColumn(colInfo, containerCol, null);
                        }
                    });
                }
                else if (COLUMN_NAME_TARGET_STUDY.equalsIgnoreCase(col.getName()))
                {
                    LookupForeignKey fk = new LookupForeignKey("Container", "Label") {
                        public TableInfo getLookupTableInfo()
                        {
                            return StudySchema.getInstance().getTableInfoStudy();
                        }
                    };
                    col.setLabel("Target Study");
                    col.setFk(fk);
                }
            }

            @Override
            public List<FieldKey> getDefaultVisibleColumns()
            {
                return defaultVisibleColumns;
            }
        };
        FieldKey containerFieldKey = FieldKey.fromParts(COLUMN_NAME_TARGET_STUDY);
        DetailsURL url = DetailsURL.fromString("study/publishHistoryDetails.view?protocolId=${protocol}&datasetId=${datasetId}&sourceLsid=${sourceLsid}&recordCount=${recordCount}", new ContainerContext.FieldKeyContext(containerFieldKey));
        url.setStrictContainerContextEval(true);

        table.setDetailsURL(url);

        return table;
    }

    @Override
    protected AbstractAuditDomainKind getDomainKind()
    {
        return new AssayAuditDomainKind();
    }

    @Override
    public <K extends AuditTypeEvent> K convertEvent(AuditLogEvent event)
    {
        AssayAuditEvent bean = new AssayAuditEvent();
        copyStandardFields(bean, event);

        if (event.getIntKey1() != null)
            bean.setProtocol(event.getIntKey1());

        bean.setTargetStudy(event.getKey1());

        return (K)bean;
    }

    @Override
    public <K extends AuditTypeEvent> K convertEvent(AuditLogEvent event, @Nullable Map<String, Object> dataMap)
    {
        AssayAuditEvent bean = convertEvent(event);

        if (dataMap != null)
        {
            if (dataMap.containsKey("datasetId"))
                bean.setDatasetId((Integer)dataMap.get("datasetId"));

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

    @Override
    public <K extends AuditTypeEvent> Class<K> getEventClass()
    {
        return (Class<K>)AssayAuditEvent.class;
    }

    public static class AssayAuditEvent extends AuditTypeEvent
    {
        private int _protocol;
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

        public int getProtocol()
        {
            return _protocol;
        }

        public void setProtocol(int protocol)
        {
            _protocol = protocol;
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

        private final Set<PropertyDescriptor> _fields;

        public AssayAuditDomainKind()
        {
            super(AssayPublishManager.ASSAY_PUBLISH_AUDIT_EVENT);

            Set<PropertyDescriptor> fields = new LinkedHashSet<>();
            fields.add(createPropertyDescriptor(COLUMN_NAME_PROTOCOL, PropertyType.INTEGER));
            fields.add(createPropertyDescriptor(COLUMN_NAME_TARGET_STUDY, PropertyType.STRING));
            fields.add(createPropertyDescriptor(COLUMN_NAME_DATASET_ID, PropertyType.INTEGER));
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
        public Set<Index> getPropertyIndices()
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

