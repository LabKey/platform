package org.labkey.api.audit;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.AuditConfigurable;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.gwt.client.AuditBehaviorType;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public abstract class AuditHandler
{
    protected abstract AuditTypeEvent createSummaryAuditRecord(User user, Container c, AuditConfigurable tInfo, QueryService.AuditAction action, int rowCount, @Nullable Map<String, Object> row);

    protected abstract DetailedAuditTypeEvent createDetailedAuditRecord(User user, Container c, AuditConfigurable tInfo, QueryService.AuditAction action, @Nullable Map<String, Object> row, Map<String, Object> updatedRow);

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

    public void addAuditEvent(User user, Container c, TableInfo table, @Nullable AuditBehaviorType auditType, QueryService.AuditAction action, List<Map<String, Object>>... params)
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
                        AuditTypeEvent event = createSummaryAuditRecord(user, c, auditConfigurable, action, 0, null);
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
                    AuditTypeEvent event = createSummaryAuditRecord(user, c, auditConfigurable, action, rows.size(), rows.get(0));

                    AuditLogService.get().addEvent(user, event);
                    break;
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
                        DetailedAuditTypeEvent event = createDetailedAuditRecord(user, c, auditConfigurable, action, row, updatedRow);

                        switch (action)
                        {
                            case INSERT:
                            case MERGE:
                            {
                                String newRecord = AbstractAuditTypeProvider.encodeForDataMap(c, row);
                                if (newRecord != null)
                                    event.setNewRecordMap(newRecord);
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
                                // record modified fields
                                Map<String, Object> originalRow = new HashMap<>();
                                Map<String, Object> modifiedRow = new HashMap<>();

                                Set<String> extraFieldsToInclude = table.getExtraDetailedUpdateAuditFields();

                                for (Map.Entry<String, Object> entry : row.entrySet())
                                {
                                    boolean isExtraAuditField = extraFieldsToInclude != null && extraFieldsToInclude.contains(entry.getKey());
                                    if (updatedRow.containsKey(entry.getKey()))
                                    {
                                        Object newValue = updatedRow.get(entry.getKey());
                                        if (!Objects.equals(entry.getValue(), newValue) || isExtraAuditField)
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

}
