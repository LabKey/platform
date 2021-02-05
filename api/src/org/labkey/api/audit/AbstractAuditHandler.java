package org.labkey.api.audit;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.AuditConfigurable;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.gwt.client.AuditBehaviorType;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.util.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.labkey.api.gwt.client.AuditBehaviorType.SUMMARY;

public abstract class AbstractAuditHandler implements AuditHandler
{
    protected abstract AuditTypeEvent createSummaryAuditRecord(User user, Container c, AuditConfigurable tInfo, QueryService.AuditAction action, @Nullable String userComment, int rowCount, @Nullable Map<String, Object> row);

    @Override
    public void addSummaryAuditEvent(User user, Container c, TableInfo table, QueryService.AuditAction action, Integer dataRowCount)
    {
        if (table.supportsAuditTracking())
        {
            AuditConfigurable auditConfigurable = (AuditConfigurable) table;
            AuditBehaviorType auditType = auditConfigurable.getAuditBehavior();

            if (auditType == SUMMARY)
            {
                AuditTypeEvent event = createSummaryAuditRecord(user, c, auditConfigurable, action, null, dataRowCount, null);

                AuditLogService.get().addEvent(user, event);
            }
        }
    }

    protected abstract DetailedAuditTypeEvent createDetailedAuditRecord(User user, Container c, AuditConfigurable tInfo, QueryService.AuditAction action, @Nullable String userComment, @Nullable Map<String, Object> row, Map<String, Object> updatedRow);

    /**
     * Allow for adding fields that may be present in the updated row but not represented in the original row
     *
     * @param originalRow the original data
     * @param modifiedRow the data from the updated row that has changed (after/new)
     * @param updatedRow  the row that has been updated, which may include fields that have not changed (before/existing)
     */
    protected void addDetailedModifiedFields(Map<String, Object> originalRow, Map<String, Object> modifiedRow, Map<String, Object> updatedRow)
    {
        // do nothing extra by default
    }

    @Override
    public void addAuditEvent(User user, Container c, TableInfo table, @Nullable AuditBehaviorType auditType, @Nullable String userComment, QueryService.AuditAction action, List<Map<String, Object>> rows, @Nullable List<Map<String, Object>> existingRows)
    {
        if (table.supportsAuditTracking())
        {
            AuditConfigurable auditConfigurable = (AuditConfigurable) table;
            if (auditType == null || auditConfigurable.getXmlAuditBehaviorType() != null)
                auditType = auditConfigurable.getAuditBehavior();

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
                        AuditTypeEvent event = createSummaryAuditRecord(user, c, auditConfigurable, action, userComment, 0, null);
                        AuditLogService.get().addEvent(user, event);
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

                    AuditTypeEvent event = createSummaryAuditRecord(user, c, auditConfigurable, action, userComment, rows.size(), rows.get(0));

                    AuditLogService.get().addEvent(user, event);
                }
                case DETAILED:
                {
                    assert null != rows;

                    AuditLogService auditLog = AuditLogService.get();
                    List<DetailedAuditTypeEvent> batch = new ArrayList<>();

                    for (int i = 0; i < rows.size(); i++)
                    {
                        Map<String, Object> updatedRow = rows.get(i);
                        Map<String, Object> existingRow = null == existingRows ? Collections.emptyMap() : existingRows.get(i);
                        DetailedAuditTypeEvent event = createDetailedAuditRecord(user, c, auditConfigurable, action, userComment, updatedRow, existingRow);

                        switch (action)
                        {
                            case INSERT:
                            {
                                String newRecord = AbstractAuditTypeProvider.encodeForDataMap(c, AuditHandler.getRecordForInsert(updatedRow));
                                if (newRecord != null)
                                    event.setNewRecordMap(newRecord);
                                break;
                            }
                            case MERGE:
                            {
                                if (existingRow.isEmpty())
                                {
                                    String newRecord = AbstractAuditTypeProvider.encodeForDataMap(c, AuditHandler.getRecordForInsert(updatedRow));
                                    if (newRecord != null)
                                        event.setNewRecordMap(newRecord);
                                }
                                else
                                {
                                    setOldAndNewMapsForUpdate(event, c, existingRow, updatedRow, table);
                                }
                                break;
                            }
                            case DELETE:
                            {
                                Map<String, Object> deletedRow = null != updatedRow ? updatedRow : existingRow;
                                String oldRecord = AbstractAuditTypeProvider.encodeForDataMap(c, deletedRow);
                                if (oldRecord != null)
                                    event.setOldRecordMap(oldRecord);
                                break;
                            }
                            case UPDATE:
                            {
                                setOldAndNewMapsForUpdate(event, c, existingRow, updatedRow, table);
                                break;
                            }
                        }
                        batch.add(event);
                        if (batch.size() > 1000)
                        {
                            auditLog.addEvents(user, batch);
                            batch.clear();
                        }
                    }
                    if (batch.size() > 0)
                    {
                        auditLog.addEvents(user, batch);
                        batch.clear();
                    }
                    break;
                }
            }
        }
    }

    private void setOldAndNewMapsForUpdate(DetailedAuditTypeEvent event, Container c, Map<String, Object> oldRow, Map<String, Object> updatedRow, TableInfo table)
    {
        Pair<Map<String, Object>, Map<String, Object>> rowPair = AuditHandler.getOldAndNewRecordForMerge(oldRow, updatedRow, table.getExtraDetailedUpdateAuditFields(), table.getExcludedDetailedUpdateAuditFields());

        Map<String, Object> originalRow = rowPair.first;
        Map<String, Object> modifiedRow = rowPair.second;

        // allow for adding fields that may be present in the updated row but not represented in the original row
        addDetailedModifiedFields(oldRow, modifiedRow, updatedRow);

        String oldRecord = AbstractAuditTypeProvider.encodeForDataMap(c, originalRow);
        if (oldRecord != null)
            event.setOldRecordMap(oldRecord);

        String newRecord = AbstractAuditTypeProvider.encodeForDataMap(c, modifiedRow);
        if (newRecord != null)
            event.setNewRecordMap(newRecord);
    }
}
