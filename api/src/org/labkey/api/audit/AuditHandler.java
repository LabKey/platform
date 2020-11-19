package org.labkey.api.audit;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.AuditConfigurable;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.gwt.client.AuditBehaviorType;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.util.Pair;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Date;

public abstract class AuditHandler
{
    protected abstract AuditTypeEvent createSummaryAuditRecord(User user, Container c, AuditConfigurable tInfo, QueryService.AuditAction action, @Nullable String userComment, int rowCount, @Nullable Map<String, Object> row);

    protected abstract DetailedAuditTypeEvent createDetailedAuditRecord(User user, Container c, AuditConfigurable tInfo, QueryService.AuditAction action, @Nullable String userComment, @Nullable Map<String, Object> row, Map<String, Object> updatedRow);

    /**
     * Allow for adding fields that may be present in the updated row but not represented in the original row
     * @param originalRow the original data
     * @param modifiedRow the data from the updated row that has changed
     * @param updatedRow the row that has been updated, which may include fields that have not changed
     */
    protected void addDetailedModifiedFields(Map<String, Object> originalRow, Map<String, Object> modifiedRow, Map<String, Object> updatedRow)
    {
        // do nothing extra by default
    }

    public void addAuditEvent(User user, Container c, TableInfo table, @Nullable AuditBehaviorType auditType, @Nullable String userComment, QueryService.AuditAction action, List<Map<String, Object>>... params)
    {
        if (table.supportsAuditTracking())
        {
            AuditConfigurable auditConfigurable = (AuditConfigurable)table;
            if (auditType == null || auditConfigurable.getXmlAuditBehaviorType() != null)
                auditType = auditConfigurable.getAuditBehavior();

            // Truncate audit event doesn't accept any params
            if (action == QueryService.AuditAction.TRUNCATE)
            {
                assert params.length == 0;
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
                    assert (params.length > 0);

                    List<Map<String, Object>> rows = params[0];
                    AuditTypeEvent event = createSummaryAuditRecord(user, c, auditConfigurable, action, userComment, rows.size(), rows.get(0));

                    AuditLogService.get().addEvent(user, event);
                }
                case DETAILED:
                {
                    assert (params.length > 0);

                    List<Map<String, Object>> rows = params[0];
                    List<Map<String, Object>> updatedRows = params.length > 1 ? params[1] : Collections.emptyList();

                    for (int i=0; i < rows.size(); i++)
                    {
                        Map<String, Object> row = rows.get(i);
                        Map<String, Object> updatedRow = updatedRows.isEmpty() ? Collections.emptyMap() : updatedRows.get(i);
                        DetailedAuditTypeEvent event = createDetailedAuditRecord(user, c, auditConfigurable, action, userComment, row, updatedRow);

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
                                if (updatedRow.isEmpty())
                                {
                                    String newRecord = AbstractAuditTypeProvider.encodeForDataMap(c, row);
                                    if (newRecord != null)
                                        event.setNewRecordMap(newRecord);
                                }
                                else
                                {
                                    setOldAndNewMapsForUpdate(event, c, row, updatedRow, table);
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
                                Pair<Map<String, Object>, Map<String, Object>> rowPair = AuditHandler.getOldAndNewRecordForMerge(row, updatedRow, table.getExtraDetailedUpdateAuditFields());
                                Map<String, Object> originalRow = rowPair.first;
                                Map<String, Object> modifiedRow = rowPair.second;

                                // allow for adding fields that may be present in the updated row but not represented in the original row
                                addDetailedModifiedFields(row, modifiedRow, updatedRow);

                                String oldRecord = AbstractAuditTypeProvider.encodeForDataMap(c, originalRow);
                                if (oldRecord != null)
                                    event.setOldRecordMap(oldRecord);

                                String newRecord = AbstractAuditTypeProvider.encodeForDataMap(c, modifiedRow);
                                if (newRecord != null)
                                    event.setNewRecordMap(newRecord);
                                break;
                            }
                        }
                        AuditLogService.get().addEvent(user, event);
                    }
                    break;
                }
            }
        }
    }

    private void setOldAndNewMapsForUpdate(DetailedAuditTypeEvent event, Container c, Map<String, Object> row, Map<String, Object> updatedRow, TableInfo table)
    {
        Pair<Map<String, Object>, Map<String, Object>> rowPair = getOldAndNewRecordForMerge(row, updatedRow, table.getExtraDetailedUpdateAuditFields());

        Map<String, Object> originalRow = rowPair.first;
        Map<String, Object> modifiedRow = rowPair.second;

        // allow for adding fields that may be present in the updated row but not represented in the original row
        addDetailedModifiedFields(row, modifiedRow, updatedRow);

        String oldRecord = AbstractAuditTypeProvider.encodeForDataMap(c, originalRow);
        if (oldRecord != null)
            event.setOldRecordMap(oldRecord);

        String newRecord = AbstractAuditTypeProvider.encodeForDataMap(c, modifiedRow);
        if (newRecord != null)
            event.setNewRecordMap(newRecord);
    }

    public static Pair<Map<String, Object>, Map<String, Object>> getOldAndNewRecordForMerge(@NotNull Map<String, Object> row, @NotNull Map<String, Object> updatedRow, Set<String> extraFieldsToInclude)
    {
        // record modified fields
        Map<String, Object> originalRow = new HashMap<>();
        Map<String, Object> modifiedRow = new HashMap<>();

        for (Map.Entry<String, Object> entry : row.entrySet())
        {
            boolean isExtraAuditField = extraFieldsToInclude != null && extraFieldsToInclude.contains(entry.getKey());
            if (updatedRow.containsKey(entry.getKey()))
            {
                Object newValue = updatedRow.get(entry.getKey());
                // compare dates using string values to allow for both Date and Timestamp types
                if (newValue instanceof Date && entry.getValue() != null)
                {
                    SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
                    String newString = formatter.format((java.util.Date) newValue);
                    Object oldValue = entry.getValue();
                    String oldString = oldValue instanceof Date ? formatter.format((Date) oldValue) : oldValue.toString();
                    if (!newString.equals(oldString) || isExtraAuditField)
                    {
                        originalRow.put(entry.getKey(), oldValue);
                        modifiedRow.put(entry.getKey(), newValue);
                    }
                }
                else if (!Objects.equals(entry.getValue(), newValue) || isExtraAuditField)
                {
                    originalRow.put(entry.getKey(), entry.getValue());
                    modifiedRow.put(entry.getKey(), newValue);
                }
            }
            else if (isExtraAuditField)
            {
                // persist extra fields desired for audit details even if no change is made, so that extra field values is available after record is deleted
                // for example, a display label/id is desired in audit log for the record updated.
                originalRow.put(entry.getKey(), entry.getValue());
                modifiedRow.put(entry.getKey(), entry.getValue());
            }
        }
        return new Pair<>(originalRow, modifiedRow);
    }

}
