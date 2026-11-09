/*
 * Copyright (c) 2025-2026 LabKey Corporation
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
package org.labkey.assay.plate.audit;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.TransactionAuditProvider;
import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.audit.query.DefaultAuditTypeTable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.UserSchema;
import org.labkey.assay.plate.PlateImpl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class PlateAuditProvider extends AbstractAuditTypeProvider
{
    public static final String EVENT_NAME = "PlateEvent";

    enum Column
    {
        PlateEventType,
        PlateName,
        PlateRowId,
        PlateSetRowId,
        PlateTypeRowId,
        Reimport,
        ImportRunId,
        SourcePlateRowId,
        Template;

        private final FieldKey _fieldKey = FieldKey.fromParts(name());

        public FieldKey fieldKey()
        {
            return _fieldKey;
        }
    }

    static final List<FieldKey> defaultVisibleColumns = new ArrayList<>();

    static
    {
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED));
        defaultVisibleColumns.add(Column.PlateRowId.fieldKey());
        defaultVisibleColumns.add(Column.PlateTypeRowId.fieldKey());
        defaultVisibleColumns.add(Column.PlateSetRowId.fieldKey());
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_COMMENT));
        defaultVisibleColumns.add(Column.SourcePlateRowId.fieldKey());
        defaultVisibleColumns.add(Column.Template.fieldKey());
        defaultVisibleColumns.add(Column.ImportRunId.fieldKey());
        defaultVisibleColumns.add(Column.Reimport.fieldKey());
    }

    public PlateAuditProvider()
    {
        super(new PlateAuditDomainKind());
    }

    @Override
    public List<FieldKey> getDefaultVisibleColumns()
    {
        return defaultVisibleColumns;
    }

    @Override
    public TableInfo createTableInfo(UserSchema userSchema, ContainerFilter cf)
    {
        DefaultAuditTypeTable table = new DefaultAuditTypeTable(this, createStorageTableInfo(), userSchema, cf, getDefaultVisibleColumns());
        appendValueMapColumns(table, getEventName());

        return table;
    }

    @Override
    public String getEventName()
    {
        return EVENT_NAME;
    }

    @Override
    public String getLabel()
    {
        return "Plate events";
    }

    @Override
    public String getDescription()
    {
        return "Events related to plates";
    }

    @Override
    public <K extends AuditTypeEvent> Class<K> getEventClass()
    {
        return (Class<K>) PlateAuditEvent.class;
    }

    public enum PlateEventType
    {
        CREATE_PLATE("%s was created.", "Created"),
        DELETE_PLATE("%s was deleted.", "Deleted"),
        PLATE_IMPORT("Plate was imported into an assay run.", "Imported");

        private final String _actionLabel;
        private final String _comment;

        PlateEventType(String comment, String actionLabel)
        {
            _comment = comment;
            _actionLabel = actionLabel;
        }

        public String getActionLabel()
        {
            return _actionLabel;
        }

        public String getComment(boolean isTemplate)
        {
            return String.format(_comment, isTemplate ? "Plate template" : "Plate");
        }
    }

    public static class PlateAuditDomainKind extends AbstractAuditDomainKind
    {
        private static final String NAME = "PlateAuditDomain";
        private static final String NAMESPACE_PREFIX = "Audit-" + NAME;
        private final Set<PropertyDescriptor> fields;

        public PlateAuditDomainKind()
        {
            super(EVENT_NAME);

            Set<PropertyDescriptor> _fields = new LinkedHashSet<>();

            // PlateAuditEvent fields
            _fields.add(createPropertyDescriptor(Column.PlateEventType.name(), PropertyType.STRING));
            _fields.add(createPropertyDescriptor(Column.PlateName.name(), PropertyType.STRING));
            _fields.add(createPropertyDescriptor(Column.PlateRowId.name(), PropertyType.BIGINT));
            _fields.add(createPropertyDescriptor(Column.PlateSetRowId.name(), PropertyType.BIGINT));
            _fields.add(createPropertyDescriptor(Column.PlateTypeRowId.name(), PropertyType.BIGINT));
            _fields.add(createPropertyDescriptor(Column.SourcePlateRowId.name(), PropertyType.BIGINT));
            _fields.add(createPropertyDescriptor(Column.Reimport.name(), PropertyType.BOOLEAN));
            _fields.add(createPropertyDescriptor(Column.ImportRunId.name(), PropertyType.BIGINT));
            _fields.add(createPropertyDescriptor(Column.Template.name(), PropertyType.BOOLEAN));

            // AbstractAuditTypeProvider fields
            _fields.add(createPropertyDescriptor(COLUMN_NAME_TRANSACTION_ID, PropertyType.BIGINT));
            _fields.add(createPropertyDescriptor(COLUMN_NAME_USER_COMMENT, PropertyType.STRING));
            _fields.add(createOldDataMapPropertyDescriptor());
            _fields.add(createNewDataMapPropertyDescriptor());

            fields = Collections.unmodifiableSet(_fields);
        }

        @Override
        protected String getNamespacePrefix()
        {
            return NAMESPACE_PREFIX;
        }

        @Override
        public Set<PropertyDescriptor> getProperties()
        {
            return fields;
        }

        @Override
        public String getKindName()
        {
            return NAME;
        }
    }

    public static class EventFactory
    {
        public static PlateAuditEvent plateCreated(
            Container container,
            TransactionAuditProvider.TransactionAuditEvent transactionAuditEvent,
            PlateImpl plate,
            @Nullable String additionalComment
        )
        {
            var event = new PlateAuditEvent(PlateEventType.CREATE_PLATE, container, plate, transactionAuditEvent);
            event.setNewRecordMap(container, plate);

            if (additionalComment != null)
                event.setComment(event.getComment() + " " + additionalComment);

            return event;
        }

        public static PlateAuditEvent plateDeleted(Container container, TransactionAuditProvider.TransactionAuditEvent transactionAuditEvent, PlateImpl plate)
        {
            var event = new PlateAuditEvent(PlateEventType.DELETE_PLATE, container, plate, transactionAuditEvent);
            event.setOldRecordMap(container, plate);

            return event;
        }

        public static PlateAuditEvent plateImported(Container container, TransactionAuditProvider.TransactionAuditEvent transactionAuditEvent, PlateImpl plate, ExpRun run, boolean isReimport)
        {
            var event = new PlateAuditEvent(PlateEventType.PLATE_IMPORT, container, plate, transactionAuditEvent);
            event.setImportRunId(run.getRowId());
            event.setReimport(isReimport);

            return event;
        }
    }
}
