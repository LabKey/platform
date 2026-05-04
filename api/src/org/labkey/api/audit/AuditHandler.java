package org.labkey.api.audit;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.MultiValuedForeignKey;
import org.labkey.api.data.MultiValuedRenderContext;
import org.labkey.api.data.TableInfo;
import org.labkey.api.dataiterator.DataIterator;
import org.labkey.api.dataiterator.ExistingRecordDataIterator;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.gwt.client.AuditBehaviorType;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.util.Pair;

import java.io.IOException;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;


public interface AuditHandler
{
    String PROVIDED_DATA_PREFIX = ":::provided:::";
    String DELTA_PROVIDED_DATA_PREFIX = ":::delta_provided:::";

    void addSummaryAuditEvent(User user, Container c, TableInfo table, QueryService.AuditAction action, Integer dataRowCount, @Nullable AuditBehaviorType auditBehaviorType, @Nullable String userComment);

    default void addSummaryAuditEvent(User user, Container c, TableInfo table, QueryService.AuditAction action, Integer dataRowCount, @Nullable AuditBehaviorType auditBehaviorType, @Nullable String userComment, boolean skipAuditLevelCheck)
    {
        addSummaryAuditEvent(user, c, table, action, dataRowCount, auditBehaviorType, userComment);
    }

    void addAuditEvent(User user, Container c, TableInfo table, @Nullable AuditBehaviorType auditType, @Nullable String userComment, QueryService.AuditAction action,
                       @Nullable List<Map<String, Object>> rows, @Nullable List<Map<String, Object>> existingRows, @Nullable List<Map<String, Object>> providedValues, boolean useTransactionAuditCache);

    /* In the case of update the 'existingRows' is the 'before' version of the record. Caller is not expected to provide existingRows without rows. */
    default void addAuditEvent(User user, Container c, TableInfo table, @Nullable AuditBehaviorType auditType, @Nullable String userComment, QueryService.AuditAction action,
                               @Nullable List<Map<String, Object>> rows, @Nullable List<Map<String, Object>> existingRows)
    {
        addAuditEvent(user, c, table, auditType, userComment, action, rows, existingRows, null, false);
    }

    /* In the case of update the 'existingRows' is the 'before' version of the record. Caller is not expected to provide existingRows without rows. */
    default void addAuditEvent(User user, Container c, TableInfo table, @Nullable AuditBehaviorType auditType, @Nullable String userComment, QueryService.AuditAction action,
                               @Nullable List<Map<String, Object>> rows, @Nullable List<Map<String, Object>> existingRows, @Nullable List<Map<String, Object>> providedValues)
    {
        addAuditEvent(user, c, table, auditType, userComment, action, rows, existingRows, providedValues, false);
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
            boolean isMultiValued = false;
            String key = entry.getKey();
            // getDatasetRows() (at least) should return key==column.getName(), expect getColumn(name) to work
            ColumnInfo col = null==table ? null : table.getColumn(key);
            if (col != null && (col.isMultiValued() || col.getFk() instanceof MultiValuedForeignKey))
                isMultiValued = true;

            String nameFromAlias = key;
            if (null != col)
                nameFromAlias = col.getName();
            else
            {
                ColumnInfo aliasColumn = columns.stream().filter(c -> c.getAlias().getId().equalsIgnoreCase(key)).findFirst().orElse(null);

                if (aliasColumn != null)
                {
                    if (aliasColumn.getFk() != null && (aliasColumn.isMultiValued() || aliasColumn.getFk() instanceof MultiValuedForeignKey))
                        isMultiValued = true;
                    col = aliasColumn; // GitHub Issue 913: Updating a sample details page shows an update to the MVTC field
                    nameFromAlias = aliasColumn.getName();
                }
            }

            boolean isMultiChoice = col != null && col.getPropertyType() == PropertyType.MULTI_CHOICE;

            String lcName = nameFromAlias.toLowerCase();
            // Preserve casing of inputs so we can show the names properly
            boolean isExpInput = false; // TODO: extract lineage handling out of this generic method
            String encodedInputColumn = ExperimentService.getEncodedLineageKey(lcName);
            if (encodedInputColumn != null)
            {
                if (row.containsKey(encodedInputColumn))
                {
                    isExpInput = true;
                    nameFromAlias = encodedInputColumn;
                }
            }

            boolean isAliasInput = row.containsKey(ExperimentService.ALIASCOLUMNALIAS) && "Alias".equalsIgnoreCase(lcName);

            boolean isExtraAuditField = extraFieldsToInclude != null && extraFieldsToInclude.contains(nameFromAlias);
            if (!excludedFromDetailDiff.contains(nameFromAlias) && (row.containsKey(nameFromAlias) || isExpInput || isAliasInput))
            {
                Object oldValue = entry.getValue();
                Object newValue = row.get(nameFromAlias);

                // See ExpDataIterator: step1.addColumn(ExperimentService.ALIASCOLUMNALIAS, colNameMap.get(Alias.name()))
                if (isAliasInput && newValue == null)
                {
                    newValue = row.get(ExperimentService.ALIASCOLUMNALIAS);
                    if (oldValue instanceof String aliasStr)
                        oldValue = Arrays.asList(aliasStr.split(MultiValuedRenderContext.VALUE_DELIMITER_REGEX));
                }

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
                    // If multivalued columns change, the value in this table will remain the key to the junction table
                    // but at this point newValue will look like the newly chosen values not that key. So we skip
                    // this in the diff unless the value changes from non-null to null or vice versa.
                    if (isMultiValued && !isAliasInput)
                    {
                        if ((oldValue == null && newValue != null) || (newValue == null && oldValue != null))
                        {
                            originalRow.put(nameFromAlias, oldValue);
                            modifiedRow.put(nameFromAlias, newValue);
                        }
                    }
                    else if (isMultiChoice)
                    {
                        Object convertedOldVal = col.convert(oldValue);
                        Object convertedNewVal = col.convert(newValue);
                        if (!Objects.equals(convertedOldVal, convertedNewVal))
                        {
                            originalRow.put(nameFromAlias, convertedOldVal); // use converted array instead of raw pgarray
                            modifiedRow.put(nameFromAlias, newValue);
                        }
                    }
                    else
                    {
                        if (isExpInput && oldValue != null && newValue != null)
                        {
                            // For parent inputs, the order of the values does not matter, so compare as sets
                            try
                            {
                                Set<String> oldSet = Arrays.stream(ExperimentService.getParentValues(oldValue.toString())).collect(Collectors.toSet());
                                Set<String> newSet = Arrays.stream(ExperimentService.getParentValues(newValue.toString())).collect(Collectors.toSet());
                                if (oldSet.equals(newSet) && !isExtraAuditField)
                                    continue;
                            }
                            catch (IOException ignore)
                            {
                            }
                        }

                        originalRow.put(nameFromAlias, oldValue);
                        modifiedRow.put(nameFromAlias, newValue);
                    }
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

        // we want to include the fields that indicate parent lineage has changed.
        // Note that we don't need to check for output fields because lineage can be modified only by changing inputs not outputs
        Set<String> existingEncodedInputColumns = new CaseInsensitiveHashSet();
        for (String fieldName : existingRow.keySet())
        {
            String existingEncodedInputColumn = ExperimentService.getEncodedLineageKey(fieldName);
            if (existingEncodedInputColumn != null)
                existingEncodedInputColumns.add(existingEncodedInputColumn);
        }
        row.forEach((fieldName, value) -> {
            if (fieldName.toLowerCase().startsWith(ExpData.DATA_INPUTS_PREFIX_LC) || fieldName.toLowerCase().startsWith(ExpMaterial.MATERIAL_INPUTS_PREFIX_LC))
                if (!originalRow.containsKey(fieldName) && !existingEncodedInputColumns.contains(fieldName))
                {
                    modifiedRow.put(fieldName, value);
                }
        });

        return new Pair<>(originalRow, modifiedRow);
    }
}
