package org.labkey.api.audit;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.AuditConfigurable;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.gwt.client.AuditBehaviorType;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public abstract class ServiceWithDetailedAuditing
{
    protected abstract DetailedAuditTypeEvent createDetailedAuditRecord(User user, Container c, AuditConfigurable tIfo, String comment, @Nullable Map<String, Object> row);

    public void addAuditEvent(User user, Container c, TableInfo table, @Nullable AuditBehaviorType auditType, QueryService.AuditAction action, List<Map<String, Object>>... params)
    {
        if (table.supportsAuditTracking())
        {
            AuditConfigurable auditConfigurable = (AuditConfigurable)table;
            auditType = auditType == null ? auditConfigurable.getAuditBehavior() : auditType;

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
                        String comment = QueryService.AuditAction.TRUNCATE.getCommentSummary();
                        AuditTypeEvent event = createDetailedAuditRecord(user, c, auditConfigurable, comment, null);
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
                    String comment = String.format(action.getCommentSummary(), rows.size());
                    AuditTypeEvent event = createDetailedAuditRecord(user, c, auditConfigurable, comment, rows.get(0));

                    AuditLogService.get().addEvent(user, event);
                    break;
                }
                case DETAILED:
                {
                    assert (params.length > 0);

                    List<Map<String, Object>> rows = params[0];
                    for (int i=0; i < rows.size(); i++)
                    {
                        Map<String, Object> row = rows.get(i);
                        String comment = String.format(action.getCommentDetailed(), row.size());

                        DetailedAuditTypeEvent event = createDetailedAuditRecord(user, c, auditConfigurable, comment, row);

                        switch (action)
                        {
                            case INSERT:
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
                                assert (params.length >= 2);

                                List<Map<String, Object>> updatedRows = params[1];
                                Map<String, Object> updatedRow = updatedRows.get(i);

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
