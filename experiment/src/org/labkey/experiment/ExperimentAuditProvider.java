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
package org.labkey.experiment;

import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.AuditTypeProvider;
import org.labkey.api.audit.ExperimentAuditEvent;
import org.labkey.api.audit.data.ExperimentAuditColumn;
import org.labkey.api.audit.data.ProtocolColumn;
import org.labkey.api.audit.data.RunColumn;
import org.labkey.api.audit.data.RunGroupColumn;
import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.audit.query.DefaultAuditTypeTable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.UserSchema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: klum
 * Date: 7/21/13
 */
public class ExperimentAuditProvider extends AbstractAuditTypeProvider implements AuditTypeProvider
{
    public static final String COLUMN_NAME_PROTOCOL_LSID = "ProtocolLsid";
    public static final String COLUMN_NAME_RUN_LSID = "RunLsid";
    public static final String COLUMN_NAME_PROTOCOL_RUN = "ProtocolRun";
    public static final String COLUMN_NAME_RUN_GROUP = "RunGroup";
    public static final String COLUMN_NAME_MESSAGE = "Message";
    public static final String COLUMN_NAME_QCSTATE = "QCState";

    static final List<FieldKey> defaultVisibleColumns = new ArrayList<>();

    static {

        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED_BY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_IMPERSONATED_BY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_PROJECT_ID));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_PROTOCOL_LSID));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_RUN_LSID));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_RUN_GROUP));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_MESSAGE));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_QCSTATE));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_COMMENT));
    }

    public ExperimentAuditProvider()
    {
        super(new ExperimentAuditDomainKind());
    }

    @Override
    public String getEventName()
    {
        return ExperimentAuditEvent.EVENT_TYPE;
    }

    @Override
    public String getLabel()
    {
        return "Assay/Experiment events";
    }

    @Override
    public String getDescription()
    {
        return "Data about assay run creations, deletions, modifications.";
    }

    @Override
    public Map<FieldKey, String> legacyNameMap()
    {
        Map<FieldKey, String> legacyNames = super.legacyNameMap();
        legacyNames.put(FieldKey.fromParts("key1"), COLUMN_NAME_PROTOCOL_LSID);
        legacyNames.put(FieldKey.fromParts("key2"), COLUMN_NAME_RUN_LSID);
        legacyNames.put(FieldKey.fromParts("key3"), COLUMN_NAME_PROTOCOL_RUN);
        legacyNames.put(FieldKey.fromParts("intKey1"), COLUMN_NAME_RUN_GROUP);
        return legacyNames;
    }

    @Override
    public <K extends AuditTypeEvent> Class<K> getEventClass()
    {
        return (Class<K>)ExperimentAuditEvent.class;
    }

    public static String getKey3(ExpProtocol protocol, ExpRun run)
    {
        return protocol.getName() + ExperimentAuditColumn.KEY_SEPARATOR + (run != null ? run.getName() : "");
    }

    @Override
    public TableInfo createTableInfo(UserSchema userSchema, ContainerFilter cf)
    {
        DefaultAuditTypeTable table = new DefaultAuditTypeTable(this, createStorageTableInfo(), userSchema, cf, defaultVisibleColumns)
        {
            @Override
            protected void initColumn(MutableColumnInfo col)
            {
                if (COLUMN_NAME_PROTOCOL_LSID.equalsIgnoreCase(col.getName()))
                {
                    final ColumnInfo containerCol = getColumn(FieldKey.fromParts(COLUMN_NAME_CONTAINER));
                    final ColumnInfo protocolRunCol = getColumn(FieldKey.fromParts(COLUMN_NAME_PROTOCOL_RUN));

                    col.setLabel("Assay/Protocol");
                    col.setDisplayColumnFactory(new DisplayColumnFactory()
                    {
                        @Override
                        public DisplayColumn createRenderer(ColumnInfo colInfo)
                        {
                            return new ProtocolColumn(colInfo, containerCol, protocolRunCol);
                        }
                    });
                }
                else if (COLUMN_NAME_RUN_LSID.equalsIgnoreCase(col.getName()))
                {
                    final ColumnInfo containerCol = getColumn(FieldKey.fromParts(COLUMN_NAME_CONTAINER));
                    final ColumnInfo protocolRunCol = getColumn(FieldKey.fromParts(COLUMN_NAME_PROTOCOL_RUN));

                    col.setLabel("Run");
                    col.setDisplayColumnFactory(new DisplayColumnFactory()
                    {
                        @Override
                        public DisplayColumn createRenderer(ColumnInfo colInfo)
                        {
                            return new RunColumn(colInfo, containerCol, protocolRunCol);
                        }
                    });
                }
                else if (COLUMN_NAME_RUN_GROUP.equalsIgnoreCase(col.getName()))
                {
                    final ColumnInfo containerCol = getColumn(FieldKey.fromParts(COLUMN_NAME_CONTAINER));
                    final ColumnInfo protocolRunCol = getColumn(FieldKey.fromParts(COLUMN_NAME_PROTOCOL_RUN));

                    col.setLabel("Run Group");
                    col.setDisplayColumnFactory(new DisplayColumnFactory()
                    {
                        @Override
                        public DisplayColumn createRenderer(ColumnInfo colInfo)
                        {
                            return new RunGroupColumn(colInfo, containerCol, protocolRunCol);
                        }
                    });
                }
                else if (COLUMN_NAME_QCSTATE.equalsIgnoreCase(col.getName()))
                {
                    col.setFk(new QueryForeignKey(CoreSchema.getInstance().getTableInfoDataStates(), null, "RowId", "Label"));
                }
            }
        };

        return table;
    }

    @Override
    public List<FieldKey> getDefaultVisibleColumns()
    {
        return defaultVisibleColumns;
    }

    public static class ExperimentAuditDomainKind extends AbstractAuditDomainKind
    {
        public static final String NAME = "ExperimentAuditDomain";
        public static String NAMESPACE_PREFIX = "Audit-" + NAME;

        private static Set<PropertyDescriptor> _fields = new LinkedHashSet<>();

        public ExperimentAuditDomainKind()
        {
            super(ExperimentAuditEvent.EVENT_TYPE);

            Set<PropertyDescriptor> fields = new LinkedHashSet<>();
            fields.add(createPropertyDescriptor(COLUMN_NAME_PROTOCOL_LSID, PropertyType.STRING));
            fields.add(createPropertyDescriptor(COLUMN_NAME_RUN_LSID, PropertyType.STRING));
            fields.add(createPropertyDescriptor(COLUMN_NAME_PROTOCOL_RUN, PropertyType.STRING));
            fields.add(createPropertyDescriptor(COLUMN_NAME_RUN_GROUP, PropertyType.INTEGER));
            fields.add(createPropertyDescriptor(COLUMN_NAME_MESSAGE, PropertyType.STRING));
            fields.add(createPropertyDescriptor(COLUMN_NAME_QCSTATE, PropertyType.INTEGER));

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
