package org.labkey.api.audit;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.AuditConfigurable;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.gwt.client.AuditBehaviorType;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.util.Pair;

import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.labkey.api.gwt.client.AuditBehaviorType.SUMMARY;


public interface AuditHandler
{
    void addSummaryAuditEvent(User user, Container c, TableInfo table, QueryService.AuditAction action, Integer dataRowCount);

    /* In the case of update the 'existingRows' is the 'before' version of the record. Caller is not expected to provide existingRows without rows. */
    void addAuditEvent(User user, Container c, TableInfo table, @Nullable AuditBehaviorType auditType, @Nullable String userComment, QueryService.AuditAction action,
                       @Nullable List<Map<String, Object>> rows, @Nullable List<Map<String, Object>> existingRows);

    abstract class AbstractAuditHandler implements AuditHandler
    {
        protected abstract AuditTypeEvent createSummaryAuditRecord(User user, Container c, AuditConfigurable tInfo, QueryService.AuditAction action, @Nullable String userComment, int rowCount, @Nullable Map<String, Object> row);

        @Override
        public void addSummaryAuditEvent(User user, Container c, TableInfo table, QueryService.AuditAction action, Integer dataRowCount)
        {
            if (table.supportsAuditTracking())
            {
                AuditConfigurable auditConfigurable = (AuditConfigurable)table;
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

                        for (int i=0; i < rows.size(); i++)
                        {
                            Map<String, Object> updatedRow = rows.get(i);
                            Map<String, Object> existingRow = null == existingRows ? Collections.emptyMap() : existingRows.get(i);
                            DetailedAuditTypeEvent event = createDetailedAuditRecord(user, c, auditConfigurable, action, userComment, updatedRow, existingRow);

                            switch (action)
                            {
                                case INSERT:
                                {
                                    String newRecord = AbstractAuditTypeProvider.encodeForDataMap(c, updatedRow);
                                    if (newRecord != null)
                                        event.setNewRecordMap(newRecord);
                                    break;
                                }
                                case MERGE:
                                {
                                    if (existingRow.isEmpty())
                                    {
                                        String newRecord = AbstractAuditTypeProvider.encodeForDataMap(c, updatedRow);
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
                                    Map<String,Object> deletedRow = null!=updatedRow ? updatedRow : existingRow;
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
                            AuditLogService.get().addEvent(user, event);
                        }
                        break;
                    }
                }
            }
        }

        private void setOldAndNewMapsForUpdate(DetailedAuditTypeEvent event, Container c, Map<String, Object> oldRow, Map<String, Object> updatedRow, TableInfo table)
        {
            Pair<Map<String, Object>, Map<String, Object>> rowPair = getOldAndNewRecordForMerge(oldRow, updatedRow, table.getExtraDetailedUpdateAuditFields());

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



    /* NOTE there is probably a better place for this helper.  AuditService? */

    // we exclude these from the detailed record because they are already on the audit record itself and
    // depending on the data iterator behavior (e.g., for ExpDataIteraotrs.getDataIterator), these values
    // time of creating the audit log may actually already have been updated so the difference shown will be incorrect.
    Set<String> excludedFromDetailDiff = CaseInsensitiveHashSet.of("Modified", "ModifiedBy", "Created", "CreatedBy");

    public static Pair<Map<String, Object>, Map<String, Object>> getOldAndNewRecordForMerge(@NotNull Map<String, Object> existingRow, @NotNull Map<String, Object> updatedRow, Set<String> extraFieldsToInclude)
    {
        // record modified fields
        Map<String, Object> originalRow = new HashMap<>();
        Map<String, Object> modifiedRow = new HashMap<>();

        for (Map.Entry<String, Object> entry : existingRow.entrySet())
        {
            boolean isExtraAuditField = extraFieldsToInclude != null && extraFieldsToInclude.contains(entry.getKey());
            if (!excludedFromDetailDiff.contains(entry.getKey()) && updatedRow.containsKey(entry.getKey()))
            {
                Object newValue = updatedRow.get(entry.getKey());
                Object oldValue = entry.getValue();
                // compare dates using string values to allow for both Date and Timestamp types
                if (newValue instanceof Date && oldValue != null)
                {
                    SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
                    String newString = formatter.format((java.util.Date) newValue);
                    String oldString = oldValue instanceof Date ? formatter.format((Date) oldValue) : oldValue.toString();
                    if (!newString.equals(oldString) || isExtraAuditField)
                    {
                        originalRow.put(entry.getKey(), oldValue);
                        modifiedRow.put(entry.getKey(), newValue);
                    }
                }
                else if (newValue instanceof Number && oldValue != null)
                {
                    try
                    {
                        //Trying to catch 1.000 != 1.0
                        Number num = NumberFormat.getInstance().parse(String.valueOf(oldValue));
                        Double newVal = ((Number) newValue).doubleValue();
                        Double oldVal = num.doubleValue();

                        // If values differ than include in difference maps
                        if (!newVal.equals(oldVal) || isExtraAuditField)
                        {
                            originalRow.put(entry.getKey(), oldValue);
                            modifiedRow.put(entry.getKey(), newValue);
                        }
                    }
                    catch (ParseException e)
                    {
                        // If a parsing error occurred e.g. one value was NaN, then include values in difference maps
                        originalRow.put(entry.getKey(), oldValue);
                        modifiedRow.put(entry.getKey(), newValue);
                    }
                }
                else if (!Objects.equals(oldValue, newValue) || isExtraAuditField)
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
