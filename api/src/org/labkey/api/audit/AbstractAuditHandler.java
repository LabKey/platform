/*
 * Copyright (c) 2021-2026 LabKey Corporation
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
package org.labkey.api.audit;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.AuditConfigurable;
import org.labkey.api.data.BatchSize;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.gwt.client.AuditBehaviorType;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.util.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.labkey.api.data.BatchSize.Kind.AUDIT;
import static org.labkey.api.gwt.client.AuditBehaviorType.SUMMARY;

public abstract class AbstractAuditHandler implements AuditHandler
{
    protected abstract AuditTypeEvent createSummaryAuditRecord(User user, Container c, AuditConfigurable tInfo, QueryService.AuditAction action, @Nullable String userComment, int rowCount, @Nullable Map<String, Object> row, List<AuditTypeEvent> sideEffectEvents);

    @Override
    public void addSummaryAuditEvent(User user, Container c, TableInfo table, QueryService.AuditAction action, Integer dataRowCount, @Nullable AuditBehaviorType auditBehaviorType, @Nullable String userComment)
    {
        addSummaryAuditEvent(user, c, table, action, dataRowCount, auditBehaviorType, userComment, false);
    }

    @Override
    public void addSummaryAuditEvent(User user, Container c, TableInfo table, QueryService.AuditAction action, Integer dataRowCount, @Nullable AuditBehaviorType auditBehaviorType, @Nullable String userComment, boolean skipAuditLevelCheck)
    {
        if (table.supportsAuditTracking())
        {
            AuditConfigurable auditConfigurable = (AuditConfigurable) table;
            AuditBehaviorType auditType = auditConfigurable.getEffectiveAuditBehavior(auditBehaviorType);

            if (auditType == SUMMARY || skipAuditLevelCheck)
            {
                List<AuditTypeEvent> sideEffectEvents = new ArrayList<>();
                AuditTypeEvent event = createSummaryAuditRecord(user, c, auditConfigurable, action, userComment, dataRowCount, null, sideEffectEvents);

                AuditLogService.get().addEvent(user, event);
                addSideEffectEvents(AuditLogService.get(), user, sideEffectEvents, false);
            }
        }
    }

    /**
     * Create a detailed audit record object so it can be recorded in the audit tables
     * @param user           making change
     * @param c              container containing auditable data
     * @param tInfo          Auditable tableInfo containing auditable record
     * @param action         being performed
     * @param userComment    Comment provided by the user explaining reason for change. NOTE: This value is generally not currently supported by many audit logging domains, and may be ignored.
     * @param row            map of new data values
     * @param existingRow    map of data values
     * @param providedValues map of values provided by the user before conversion (e.g., for quantity values)
     * @param sideEffectEvents collects audit events raised as a side effect of building this record, for the caller to flush through {@link #addSideEffectEvents}
     * @return DetailedAuditTypeEvent object describing audit record (NOTE: not committed to DB yet)
     */
    protected abstract DetailedAuditTypeEvent createDetailedAuditRecord(User user, Container c, AuditConfigurable tInfo, QueryService.AuditAction action, @Nullable String userComment, @Nullable Map<String, Object> row, Map<String, Object> existingRow, Map<String, Object> providedValues, List<AuditTypeEvent> sideEffectEvents);

    /** Creates the detailed record for one row of an {@link #addAuditEvent} call */
    @FunctionalInterface
    protected interface DetailedAuditRecordFactory
    {
        /** @param precedingEvents collects events the record must follow, such as an item's move out ahead of its move in, saved before the record's batch */
        DetailedAuditTypeEvent create(@Nullable Map<String, Object> row, Map<String, Object> existingRow, @Nullable Map<String, Object> providedValues, List<AuditTypeEvent> sideEffectEvents, List<AuditTypeEvent> precedingEvents);

        /** Called once after the call's last record is created, to save what the records collected */
        default void complete()
        {
        }
    }

    /** Called once per detailed {@link #addAuditEvent} call; override to load what every row's record needs in one pass */
    protected DetailedAuditRecordFactory getDetailedAuditRecordFactory(User user, Container c, AuditConfigurable tInfo, QueryService.AuditAction action, @Nullable String userComment, List<Map<String, Object>> rows, @Nullable List<Map<String, Object>> existingRows)
    {
        return (row, existingRow, providedValues, sideEffectEvents, _) -> createDetailedAuditRecord(user, c, tInfo, action, userComment, row, existingRow, providedValues, sideEffectEvents);
    }

    /**
     * Allow for adding fields that may be present in the updated row but not represented in the original row
     *
     * @param originalRow the original data
     * @param modifiedRow the data from the updated row that has changed (after/new)
     * @param updatedRow the row that has been updated, which may include fields that have not changed (before/existing)
     */
    protected void addDetailedModifiedFields(Map<String, Object> originalRow, Map<String, Object> modifiedRow, Map<String, Object> updatedRow)
    {
        // do nothing extra by default
    }

    @Override
    public void addAuditEvent(User user, Container c, TableInfo table, @Nullable AuditBehaviorType auditType, @Nullable String userComment, QueryService.AuditAction action, List<Map<String, Object>> rows, @Nullable List<Map<String, Object>> existingRows, @Nullable List<Map<String, Object>> providedValues, boolean useTransactionAuditCache)
    {
        if (table.supportsAuditTracking())
        {
            AuditConfigurable auditConfigurable = (AuditConfigurable)table;
            auditType = auditConfigurable.getEffectiveAuditBehavior(auditType);

            // Truncate audit event doesn't accept any params
            if (action == QueryService.AuditAction.TRUNCATE)
            {
                assert null == rows && null == existingRows;
                switch (auditType)
                {
                    case NONE:
                        return;

                    case SUMMARY:
                    case DETAILED:
                        List<AuditTypeEvent> truncateSideEffects = new ArrayList<>();
                        AuditTypeEvent event = createSummaryAuditRecord(user, c, auditConfigurable, action, userComment, 0, null, truncateSideEffects);
                        AuditLogService.get().addEvent(user, event);
                        addSideEffectEvents(AuditLogService.get(), user, truncateSideEffects, useTransactionAuditCache);
                        return;
                }
            }

            switch (auditType)
            {
                case NONE:
                    return;

                case SUMMARY:
                {
                    assert null != rows;

                    List<AuditTypeEvent> sideEffectEvents = new ArrayList<>();
                    AuditTypeEvent event = createSummaryAuditRecord(user, c, auditConfigurable, action, userComment, rows.size(), rows.getFirst(), sideEffectEvents);

                    AuditLogService.get().addEvent(user, event);
                    addSideEffectEvents(AuditLogService.get(), user, sideEffectEvents, useTransactionAuditCache);

                    return;
                }
                case DETAILED:
                {
                    assert null != rows;

                    AuditLogService auditLog = AuditLogService.get();
                    List<DetailedAuditTypeEvent> batch = new ArrayList<>();
                    List<AuditTypeEvent> sideEffectEvents = new ArrayList<>();
                    List<AuditTypeEvent> precedingEvents = new ArrayList<>();
                    DetailedAuditRecordFactory recordFactory = getDetailedAuditRecordFactory(user, c, auditConfigurable, action, userComment, rows, existingRows);

                    for (int i=0; i < rows.size(); i++)
                    {
                        Map<String, Object> row = rows.get(i);
                        Map<String, Object> existingRow = null == existingRows ? Collections.emptyMap() : existingRows.get(i);
                        Map<String, Object> providedValueRow = null == providedValues || providedValues.size() <= i  ? null : providedValues.get(i);
                        DetailedAuditTypeEvent event = recordFactory.create(row, existingRow, providedValueRow, sideEffectEvents, precedingEvents);

                        switch (action)
                        {
                            case INSERT:
                            {
                                String newRecord = AbstractAuditTypeProvider.encodeForDataMap(row);
                                if (newRecord != null)
                                    event.setNewRecordMap(newRecord, c);
                                break;
                            }
                            case MERGE:
                            {
                                if (existingRow.isEmpty())
                                {
                                    String newRecord = AbstractAuditTypeProvider.encodeForDataMap(row);
                                    if (newRecord != null)
                                        event.setNewRecordMap(newRecord, c);
                                }
                                else
                                {
                                    setOldAndNewMapsForUpdate(event, c, row, existingRow, table);
                                }
                                break;
                            }
                            case DELETE:
                            {
                                String oldRecord = AbstractAuditTypeProvider.encodeForDataMap(row);
                                if (oldRecord != null)
                                    event.setOldRecordMap(oldRecord, c);
                                break;
                            }
                            case UPDATE:
                            {
                                setOldAndNewMapsForUpdate(event, c, row, existingRow, table);
                                break;
                            }
                        }
                        batch.add(event);
                        if (batch.size() >= BatchSize.get(AUDIT))
                            addBatch(auditLog, user, precedingEvents, batch, useTransactionAuditCache);
                        // a row can contribute more than one side effect, so bound these separately from the row batch
                        if (sideEffectEvents.size() >= BatchSize.get(AUDIT))
                        {
                            addSideEffectEvents(auditLog, user, sideEffectEvents, useTransactionAuditCache);
                            sideEffectEvents.clear();
                        }
                    }
                    if (!batch.isEmpty())
                        addBatch(auditLog, user, precedingEvents, batch, useTransactionAuditCache);
                    addSideEffectEvents(auditLog, user, sideEffectEvents, useTransactionAuditCache);
                    recordFactory.complete();
                    break;
                }
            }
        }
    }

    private static void addBatch(AuditLogService auditLog, User user, List<AuditTypeEvent> precedingEvents, List<DetailedAuditTypeEvent> batch, boolean useTransactionAuditCache)
    {
        addSideEffectEvents(auditLog, user, precedingEvents, useTransactionAuditCache);
        precedingEvents.clear();
        auditLog.addEvents(user, batch, useTransactionAuditCache);
        batch.clear();
    }

    /**
     * The one place side-effect and preceding events are written, so they can't pick up different batching or transaction-cache
     * behavior depending on which call path produced them. insertEvents() batches only a fully homogeneous list, so
     * group by event type and container -- each type is stored in its own provisioned table.
     */
    public static void addSideEffectEvents(AuditLogService auditLog, User user, List<AuditTypeEvent> events, boolean useTransactionAuditCache)
    {
        events.stream()
                .collect(Collectors.groupingBy(event -> Pair.of(event.getEventType(), event.getContainer()), LinkedHashMap::new, Collectors.toList()))
                .values()
                .forEach(group -> auditLog.addEvents(user, group, useTransactionAuditCache));
    }

    private void setOldAndNewMapsForUpdate(DetailedAuditTypeEvent event, Container c, Map<String, Object> row, Map<String, Object> existingRow, TableInfo table)
    {
        Pair<Map<String, Object>, Map<String, Object>> rowPair = AuditHandler.getOldAndNewRecordForMerge(row, existingRow, table.getExtraDetailedUpdateAuditFields(), table.getExcludedDetailedUpdateAuditFields(), table);

        Map<String, Object> originalRow = rowPair.first;
        Map<String, Object> modifiedRow = rowPair.second;

        // allow for adding fields that may be present in the updated row but not represented in the original row
        addDetailedModifiedFields(existingRow, modifiedRow, row);

        String oldRecord = AbstractAuditTypeProvider.encodeForDataMap(originalRow);
        if (oldRecord != null)
            event.setOldRecordMap(oldRecord, c);

        String newRecord = AbstractAuditTypeProvider.encodeForDataMap(modifiedRow);
        if (newRecord != null)
            event.setNewRecordMap(newRecord, c);
    }
}
