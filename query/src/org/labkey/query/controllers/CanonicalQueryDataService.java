/*
 * Copyright (c) 2025 LabKey Corporation
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
package org.labkey.query.controllers;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Shared canonical query-shape and row-normalization helpers used by both
 * query-data diff and schema checksum comparison.
 */
class CanonicalQueryDataService
{
    static final String NULL_SENTINEL = "\\N";

    record CanonicalQueryPlan(List<ColumnInfo> columns, List<String> columnNames, Map<String, JdbcType> columnTypes,
                              List<String> realPrimaryKeyColumnNames)
    {
    }

    CanonicalQueryPlan createPlan(TableInfo tableInfo)
    {
        List<ColumnInfo> columns = getCanonicalColumns(tableInfo);

        Map<String, JdbcType> columnTypes = new LinkedHashMap<>();
        for (ColumnInfo col : columns)
            columnTypes.put(col.getName().toLowerCase(), col.getJdbcType());

        List<String> columnNames = columns.stream()
            .map(col -> col.getName().toLowerCase())
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .collect(Collectors.toList());

        List<String> realPkColumnNames = tableInfo.getPkColumns().stream()
            .map(col -> col.getName().toLowerCase())
            .collect(Collectors.toList());

        return new CanonicalQueryPlan(columns, columnNames, columnTypes, realPkColumnNames);
    }

    List<ColumnInfo> getCanonicalColumns(TableInfo tableInfo)
    {
        return tableInfo.getColumns().stream()
            .filter(col -> !col.isUnselectable())
            .filter(col -> !col.isCalculated() || !SchemaCompareService.containsNonDeterministicFunction(col))
            .filter(col -> !col.getName().equalsIgnoreCase("_ts"))
            .filter(col -> !col.getName().equalsIgnoreCase("enddatetimecoalesced"))
            .collect(Collectors.toList());
    }

    TableSelector createSelector(TableInfo tableInfo, List<ColumnInfo> selectedColumns, int rowLimit,
                                 boolean useDeterministicSort, @Nullable Map<String, Object> namedParameters,
                                 int queryTimeout)
    {
        SchemaCompareNormalizer.SortResult sortResult = SchemaCompareNormalizer.getSortForChecksumWithColumns(tableInfo);

        List<ColumnInfo> selectorColumns = new ArrayList<>(selectedColumns);
        selectorColumns.addAll(sortResult.extraSortColumns());

        TableSelector selector = new TableSelector(tableInfo, selectorColumns, null,
            useDeterministicSort ? sortResult.sort() : null);
        selector.setMaxRows(rowLimit > 0 ? rowLimit : Table.ALL_ROWS);
        if (queryTimeout > 0)
            selector.setQueryTimeout(queryTimeout);
        if (namedParameters != null && !namedParameters.isEmpty())
            selector.setNamedParameters(namedParameters);
        return selector;
    }

    Map<String, Object> extractRow(Map<String, Object> row, List<String> columnNames)
    {
        Map<String, Object> extracted = new LinkedHashMap<>();
        for (String colName : columnNames)
            extracted.put(colName, findValueCaseInsensitive(row, colName));
        return extracted;
    }

    Map<String, Object> normalizeRowForDiff(Map<String, Object> rowData, Map<String, JdbcType> columnTypes)
    {
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : normalizeEntries(rowData, columnTypes))
            normalized.put(entry.getKey(), entry.getValue() == null ? NULL_SENTINEL : entry.getValue().toString());
        return normalized;
    }

    List<Map.Entry<String, Object>> normalizeEntries(Map<String, Object> rowData, @Nullable Map<String, JdbcType> columnTypes)
    {
        return SchemaCompareNormalizer.normalizeRow(rowData, columnTypes).stream()
            .map(entry ->
            {
                Object value = entry.getValue();
                if (value instanceof String s)
                    value = SchemaCompareNormalizer.normalizeWhitespace(s);
                return (Map.Entry<String, Object>) new AbstractMap.SimpleImmutableEntry<>(entry.getKey(), value);
            })
            .collect(Collectors.toList());
    }

    private @Nullable Object findValueCaseInsensitive(Map<String, Object> row, String key)
    {
        if (row.containsKey(key))
            return row.get(key);

        for (Map.Entry<String, Object> entry : row.entrySet())
        {
            if (entry.getKey().equalsIgnoreCase(key))
                return entry.getValue();
        }
        return null;
    }
}
