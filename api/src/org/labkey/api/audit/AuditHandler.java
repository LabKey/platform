package org.labkey.api.audit;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.dataiterator.DataIterator;
import org.labkey.api.dataiterator.ExistingRecordDataIterator;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExperimentService;
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


public interface AuditHandler
{
    void addSummaryAuditEvent(User user, Container c, TableInfo table, QueryService.AuditAction action, Integer dataRowCount, @Nullable AuditBehaviorType auditBehaviorType, @Nullable String userComment);

    void addAuditEvent(User user, Container c, TableInfo table, @Nullable AuditBehaviorType auditType, @Nullable String userComment, QueryService.AuditAction action,
                       @Nullable List<Map<String, Object>> rows, @Nullable List<Map<String, Object>> existingRows, boolean useTransactionAuditCache);

    /* In the case of update the 'existingRows' is the 'before' version of the record. Caller is not expected to provide existingRows without rows. */
    default void addAuditEvent(User user, Container c, TableInfo table, @Nullable AuditBehaviorType auditType, @Nullable String userComment, QueryService.AuditAction action,
                       @Nullable List<Map<String, Object>> rows, @Nullable List<Map<String, Object>> existingRows)
    {
        addAuditEvent(user, c, table, auditType, userComment, action, rows, existingRows, false);
    }


    static Map<String, Object> getRecordForInsert(Map<String, Object> updatedRow)
    {
        Map<String, Object> modifiedRow = new HashMap<>(updatedRow);
        // remove DataIterator artifacts
        modifiedRow.remove(DataIterator.ROWNUMBER_COLUMNNAME);
        modifiedRow.remove(ExistingRecordDataIterator.EXISTING_RECORD_COLUMN_NAME);
        modifiedRow.remove(ExperimentService.ALIASCOLUMNALIAS);
        return modifiedRow;
    }

    static Pair<Map<String, Object>, Map<String, Object>> getOldAndNewRecordForMerge(@NotNull Map<String, Object> row, @NotNull Map<String, Object> existingRow, Set<String> extraFieldsToInclude, Set<String> excludedFromDetailDiff, TableInfo table)
    {
        // record modified fields
        Map<String, Object> originalRow = new HashMap<>();
        Map<String, Object> modifiedRow = new HashMap<>();

        List<ColumnInfo> columns = table == null ? Collections.emptyList() : table.getColumns();
        // Iterate through existingRow keys since these have the casing we want
        // and we won't convert sample type and data class names into lower case.
        for (Map.Entry<String, Object> entry : existingRow.entrySet())
        {
            String key = entry.getKey();
            String nameFromAlias = columns
                    .stream()
                    .filter(column -> column.getAlias().equalsIgnoreCase(key))
                    .map((ColumnInfo::getName))
                    .findFirst()
                    .orElse(key);
            String lcName = nameFromAlias.toLowerCase();
            // Preserve casing of inputs so we can show the names properly
            if (!lcName.startsWith(ExpData.DATA_INPUT_PARENT.toLowerCase()) && !lcName.startsWith(ExpMaterial.MATERIAL_INPUT_PARENT.toLowerCase()))
                nameFromAlias = lcName;

            boolean isExtraAuditField = extraFieldsToInclude != null && extraFieldsToInclude.contains(nameFromAlias);
            if (!excludedFromDetailDiff.contains(nameFromAlias) && row.containsKey(nameFromAlias))
            {
                Object oldValue = entry.getValue();
                Object newValue = row.get(nameFromAlias);
                // compare dates using string values to allow for both Date and Timestamp types
                if (newValue instanceof Date && oldValue != null)
                {
                    SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
                    String newString = formatter.format((java.util.Date) newValue);
                    String oldString = oldValue instanceof Date ? formatter.format((Date) oldValue) : oldValue.toString();
                    if (!newString.equals(oldString) || isExtraAuditField)
                    {
                        originalRow.put(nameFromAlias, oldValue);
                        modifiedRow.put(nameFromAlias, newValue);
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
                            originalRow.put(nameFromAlias, oldValue);
                            modifiedRow.put(nameFromAlias, newValue);
                        }
                    }
                    catch (ParseException e)
                    {
                        // If a parsing error occurred e.g. one value was NaN, then include values in difference maps
                        originalRow.put(nameFromAlias, oldValue);
                        modifiedRow.put(nameFromAlias, newValue);
                    }
                }
                else if (!Objects.equals(oldValue, newValue) || isExtraAuditField)
                {
                    originalRow.put(nameFromAlias, oldValue);
                    modifiedRow.put(nameFromAlias, newValue);
                }
            }
            else if (isExtraAuditField)
            {
                // persist extra fields desired for audit details even if no change is made, so that extra field values is available after record is deleted
                // for example, a display label/id is desired in audit log for the record updated.
                originalRow.put(nameFromAlias, entry.getValue());
                modifiedRow.put(nameFromAlias, entry.getValue());
            }
        }
        return new Pair<>(originalRow, modifiedRow);
    }
}
