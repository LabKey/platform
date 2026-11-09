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
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.assay.plate.PlateManager;
import org.labkey.assay.plate.PlateSetImpl;
import org.labkey.assay.plate.query.PlateTable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class PlateSetAuditProvider extends AbstractAuditTypeProvider
{
    public static final String EVENT_NAME = "PlateSetEvent";

    enum Column
    {
        Archived,
        PlateSetEventType,
        PlateSetName,
        PlateSetRowId,
        PlateSetType,
        ParentPlateSetRowId,
        PrimaryPlateSetRowId,
        RootPlateSetRowId;

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
        defaultVisibleColumns.add(Column.PlateSetRowId.fieldKey());
        defaultVisibleColumns.add(Column.PlateSetType.fieldKey());
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_COMMENT));
        defaultVisibleColumns.add(Column.ParentPlateSetRowId.fieldKey());
        defaultVisibleColumns.add(Column.PrimaryPlateSetRowId.fieldKey());
        defaultVisibleColumns.add(Column.RootPlateSetRowId.fieldKey());
        defaultVisibleColumns.add(Column.Archived.fieldKey());
    }

    public PlateSetAuditProvider()
    {
        super(new PlateSetAuditDomainKind());
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
        return "Plate set events";
    }

    @Override
    public String getDescription()
    {
        return "Events related to plate sets";
    }

    @Override
    public <K extends AuditTypeEvent> Class<K> getEventClass()
    {
        return (Class<K>) PlateSetAuditEvent.class;
    }

    public enum PlateSetEventType
    {
        ARCHIVE_PLATE_SET("Plate set was archived.", "Archived"),
        CREATE_PLATE_SET("Plate set was created.", "Created"),
        RESTORE_PLATE_SET("Plate set was restored from the archive.", "Restored");

        private final String _actionLabel;
        private final String _comment;

        PlateSetEventType(String comment, String actionLabel)
        {
            _comment = comment;
            _actionLabel = actionLabel;
        }

        public String getActionLabel()
        {
            return _actionLabel;
        }

        public String getComment()
        {
            return _comment;
        }
    }

    public static class PlateSetAuditDomainKind extends AbstractAuditDomainKind
    {
        private static final String NAME = "PlateSetAuditDomain";
        private static final String NAMESPACE_PREFIX = "Audit-" + NAME;
        private final Set<PropertyDescriptor> fields;

        public PlateSetAuditDomainKind()
        {
            super(EVENT_NAME);

            Set<PropertyDescriptor> _fields = new LinkedHashSet<>();

            // PlateSetAuditEvent fields
            _fields.add(createPropertyDescriptor(Column.PlateSetEventType.name(), PropertyType.STRING));
            _fields.add(createPropertyDescriptor(Column.PlateSetName.name(), PropertyType.STRING));
            _fields.add(createPropertyDescriptor(Column.PlateSetRowId.name(), PropertyType.BIGINT));
            _fields.add(createPropertyDescriptor(Column.PlateSetType.name(), PropertyType.STRING));
            _fields.add(createPropertyDescriptor(Column.ParentPlateSetRowId.name(), PropertyType.BIGINT));
            _fields.add(createPropertyDescriptor(Column.PrimaryPlateSetRowId.name(), PropertyType.BIGINT));
            _fields.add(createPropertyDescriptor(Column.RootPlateSetRowId.name(), PropertyType.BIGINT));
            _fields.add(createPropertyDescriptor(Column.Archived.name(), PropertyType.BOOLEAN));
            
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
        public static PlateSetAuditEvent plateSetCreated(
            Container container,
            TransactionAuditProvider.TransactionAuditEvent transactionAuditEvent,
            PlateSetImpl plateSet,
            @Nullable String additionalComment
        )
        {
            var event = new PlateSetAuditEvent(PlateSetEventType.CREATE_PLATE_SET, container, plateSet, transactionAuditEvent);
            event.setNewRecordMap(container, plateSet);

            if (additionalComment != null)
                event.setComment(event.getComment() + " " + additionalComment);

            return event;
        }

        public static List<PlateSetAuditEvent> plateSetsArchived(
            Container container,
            TransactionAuditProvider.TransactionAuditEvent transactionAuditEvent,
            List<Long> plateSetIds,
            boolean archive
        ) throws ValidationException
        {
            if (plateSetIds.isEmpty())
                return Collections.emptyList();

            var events = new ArrayList<PlateSetAuditEvent>(plateSetIds.size());
            var eventType = archive ? PlateSetEventType.ARCHIVE_PLATE_SET : PlateSetEventType.RESTORE_PLATE_SET;

            for (var plateSetId : plateSetIds)
            {
                var plateSet = (PlateSetImpl) PlateManager.get().getPlateSet(ContainerFilter.getUnsafeEverythingFilter(), plateSetId);
                if (plateSet == null)
                    throw new ValidationException(String.format("Failed to audit archive change for plate set %d. Plate set not found.", plateSetId));

                plateSet.setArchived(archive);

                var event = new PlateSetAuditEvent(eventType, container, plateSet, transactionAuditEvent);
                event.setOldRecordMap(AbstractAuditTypeProvider.encodeForDataMap(CaseInsensitiveHashMap.of(PlateTable.Column.Archived.name(), String.valueOf(!archive))));
                event.setNewRecordMap(AbstractAuditTypeProvider.encodeForDataMap(CaseInsensitiveHashMap.of(PlateTable.Column.Archived.name(), String.valueOf(archive))));
                events.add(event);
            }

            return events;
        }
    }
}
