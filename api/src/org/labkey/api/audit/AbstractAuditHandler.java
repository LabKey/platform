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
    public void addSummaryAuditEvent(User user, Container c, TableInfo table, QueryService.AuditAction action, Integer dataRowCount, @Nullable AuditBehaviorType auditBehaviorType)
    {
        if (table.supportsAuditTracking())
        {
            AuditConfigurable auditConfigurable = (AuditConfigurable) table;
            AuditBehaviorType auditType = auditBehaviorType == null ? auditConfigurable.getAuditBehavior() : auditBehaviorType;

            if (auditType == SUMMARY)
            {
                AuditTypeEvent event = createSummaryAuditRecord(user, c, auditConfigurable, action, null, dataRowCount, null);

                AuditLogService.get().addEvent(user, event);
            }
        }
    }

    /**
     * Create a detailed audit record object so it can be recorded in the audit tables
     * @param user making change
     * @param c container containing auditable data
     * @param tInfo Auditable tableInfo containing auditable record
     * @param action being performed
     * @param userComment Comment provided by the user explaining reason for change. NOTE: This value is generally not currently supported by many audit logging domains, and may be ignored.
     * @param row map of new data values
     * @param existingRow map of data values
     * @return DetailedAuditTypeEvent object describing audit record (NOTE: not committed to DB yet)
     */
    protected abstract DetailedAuditTypeEvent createDetailedAuditRecord(User user, Container c, AuditConfigurable tInfo, QueryService.AuditAction action, @Nullable String userComment, @Nullable Map<String, Object> row, Map<String, Object> existingRow);

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
    public void addAuditEvent(User user, Container c, TableInfo table, @Nullable AuditBehaviorType auditType, @Nullable String userComment, QueryService.AuditAction action, List<Map<String, Object>> rows, @Nullable List<Map<String, Object>> existingRows)
    {
        if (table.supportsAuditTracking())
        {
            AuditConfigurable auditConfigurable = (AuditConfigurable)table;
            auditType = auditConfigurable.getAuditBehavior(auditType);

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

                    for (int i=0; i < rows.size(); i++)
                    {
                        Map<String, Object> row = rows.get(i);
                        Map<String, Object> existingRow = null == existingRows ? Collections.emptyMap() : existingRows.get(i);
                        DetailedAuditTypeEvent event = createDetailedAuditRecord(user, c, auditConfigurable, action, userComment, row, existingRow);

                        switch (action)
                        {
                            case INSERT:
                            {
                                String newRecord = AbstractAuditTypeProvider.encodeForDataMap(c, row);
                                if (newRecord != null)
                                    event.setNewRecordMap(newRecord);
                                break;
                            }
                            case MERGE:
                            {
                                if (existingRow.isEmpty())
                                {
                                    String newRecord = AbstractAuditTypeProvider.encodeForDataMap(c, row);
                                    if (newRecord != null)
                                        event.setNewRecordMap(newRecord);
                                }
                                else
                                {
                                    setOldAndNewMapsForUpdate(event, c, row, existingRow, table);
                                }
                                break;
                            }
                            case DELETE:
                            {
                                String oldRecord = AbstractAuditTypeProvider.encodeForDataMap(c, row);
                                if (oldRecord != null)
                                    event.setOldRecordMap(oldRecord);
                                break;
                            }
                            case UPDATE:
                            {
                                setOldAndNewMapsForUpdate(event, c, row, existingRow, table);
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

    private void setOldAndNewMapsForUpdate(DetailedAuditTypeEvent event, Container c, Map<String, Object> row, Map<String, Object> existingRow, TableInfo table)
    {
        Pair<Map<String, Object>, Map<String, Object>> rowPair = AuditHandler.getOldAndNewRecordForMerge(row, existingRow, table.getExtraDetailedUpdateAuditFields(), table.getExcludedDetailedUpdateAuditFields(), table);

        Map<String, Object> originalRow = rowPair.first;
        Map<String, Object> modifiedRow = rowPair.second;

        // allow for adding fields that may be present in the updated row but not represented in the original row
        addDetailedModifiedFields(existingRow, modifiedRow, row);

        String oldRecord = AbstractAuditTypeProvider.encodeForDataMap(c, originalRow);
        if (oldRecord != null)
            event.setOldRecordMap(oldRecord, c);

        String newRecord = AbstractAuditTypeProvider.encodeForDataMap(c, modifiedRow);
        if (newRecord != null)
            event.setNewRecordMap(newRecord, c);
    }
}
