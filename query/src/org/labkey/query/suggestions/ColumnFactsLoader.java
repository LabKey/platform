/*
 * Copyright (c) 2026 LabKey Corporation
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
package org.labkey.query.suggestions;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Aggregate;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.QueryLogging;
import org.labkey.api.data.Results;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.suggestions.SuggestionColumn;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reads column facts through the table, so its container filter and query rules apply to every scan. */
public class ColumnFactsLoader implements ColumnFactsCache.Source
{
    static final int QUERY_TIMEOUT_SECONDS = 30;
    static final int SHAPE_SAMPLE_SIZE = 200;
    // Longer values are rarely typed as a search term, and would dominate the cache's memory
    static final int MAX_VALUE_LENGTH = 200;

    @Override
    public @NotNull Map<FieldKey, ColumnFact.Stats> loadStats(@NotNull TableInfo table, @NotNull Map<String, Object> parameters, @NotNull List<ColumnFactsCache.StatsRequest> requests)
    {
        record Aggregates(@Nullable Aggregate min, @Nullable Aggregate max, @Nullable Aggregate distinct) {}

        List<ColumnInfo> columns = new ArrayList<>();
        List<Aggregate> aggregates = new ArrayList<>();
        Map<FieldKey, Aggregates> byFieldKey = new LinkedHashMap<>();
        for (ColumnFactsCache.StatsRequest request : requests)
        {
            FieldKey fieldKey = request.column().getFieldKey();
            Aggregate min = request.range() ? new Aggregate(fieldKey, Aggregate.BaseType.MIN) : null;
            Aggregate max = request.range() ? new Aggregate(fieldKey, Aggregate.BaseType.MAX) : null;
            Aggregate distinct = request.distinctCount() ? new Aggregate(fieldKey, Aggregate.BaseType.COUNT, null, true) : null;
            for (Aggregate aggregate : new Aggregate[]{min, max, distinct})
                if (aggregate != null)
                    aggregates.add(aggregate);
            columns.add(request.column());
            byFieldKey.put(fieldKey, new Aggregates(min, max, distinct));
        }

        Map<String, List<Aggregate.Result>> results = new TableSelector(table, columns, null, null)
            .setNamedParameters(parameters)
            .setQueryTimeout(QUERY_TIMEOUT_SECONDS)
            .getAggregates(aggregates);

        Map<FieldKey, ColumnFact.Stats> stats = new HashMap<>();
        byFieldKey.forEach((fieldKey, aggs) -> {
            // Absent when the aggregate doesn't apply to the column's type; the caller records that as a failure
            List<Aggregate.Result> columnResults = results.get(fieldKey.toString());
            if (columnResults == null)
                return;
            Object rawMin = getValue(columnResults, aggs.min());
            Object rawMax = getValue(columnResults, aggs.max());
            Long distinct = getValue(columnResults, aggs.distinct()) instanceof Number n ? n.longValue() : null;
            boolean rangeUnknown = isNonFinite(rawMin) || isNonFinite(rawMax);
            stats.put(fieldKey, new ColumnFact.Stats(toRangeValue(rawMin), toRangeValue(rawMax), distinct, rangeUnknown));
        });
        return stats;
    }

    @Override
    public @NotNull ColumnFact.Values loadValues(@NotNull TableInfo table, @NotNull ColumnInfo column, @NotNull SuggestionColumn.Type type, @NotNull Map<String, Object> parameters)
    {
        QueryService service = QueryService.get();
        QueryLogging queryLogging = new QueryLogging();
        SQLFragment selectSql = service.getSelectBuilder(table)
            .columns(List.of(column))
            .queryLogging(queryLogging)
            .distinct(true)
            .buildSqlFragment();

        SQLFragment sql = new SQLFragment("SELECT S.").appendIdentifier(column.getAlias()).append(" AS value FROM (")
            .append(selectSql)
            .append(") S WHERE S.").appendIdentifier(column.getAlias()).append(" IS NOT NULL");
        // One extra row for a blank value, which is dropped, and one to detect overflow
        sql = table.getSqlDialect().limitRows(sql, ColumnFactsCache.DISTINCT_VALUE_LIMIT + 2);
        service.bindNamedParameters(sql, parameters);
        service.validateNamedParameters(sql);

        List<String> values = new ArrayList<>();
        int count = 0;
        for (String value : new SqlSelector(table.getSchema().getScope(), sql, queryLogging).setQueryTimeout(QUERY_TIMEOUT_SECONDS).getArrayList(String.class))
        {
            if (value == null || value.isBlank())
                continue;
            count++;
            if (value.length() <= MAX_VALUE_LENGTH)
                values.add(type == SuggestionColumn.Type.INT ? toIntegerString(value) : value);
        }
        return new ColumnFact.Values(count > ColumnFactsCache.DISTINCT_VALUE_LIMIT ? null : values);
    }

    @Override
    public @NotNull Map<FieldKey, ColumnFact.Shapes> loadShapes(@NotNull TableInfo table, @NotNull Collection<ColumnInfo> columns, @NotNull Map<String, Object> parameters)
    {
        Map<FieldKey, Set<String>> shapes = new LinkedHashMap<>();
        columns.forEach(column -> shapes.put(column.getFieldKey(), new LinkedHashSet<>()));

        TableSelector selector = new TableSelector(table, columns, null, null)
            .setMaxRows(SHAPE_SAMPLE_SIZE)
            .setNamedParameters(parameters)
            .setQueryTimeout(QUERY_TIMEOUT_SECONDS);
        try (Results results = selector.getResults(false))
        {
            while (results.next())
            {
                for (Map.Entry<FieldKey, Set<String>> entry : shapes.entrySet())
                {
                    Object value = results.getObject(entry.getKey());
                    String text = value == null ? null : value.toString();
                    if (text != null && !text.isBlank() && text.length() <= MAX_VALUE_LENGTH)
                        entry.getValue().add(FilterSuggestionRanker.getValueShape(text));
                }
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeException(e);
        }

        Map<FieldKey, ColumnFact.Shapes> result = new HashMap<>();
        shapes.forEach((fieldKey, columnShapes) -> result.put(fieldKey, new ColumnFact.Shapes(List.copyOf(columnShapes))));
        return result;
    }

    private static @Nullable Object getValue(List<Aggregate.Result> results, @Nullable Aggregate aggregate)
    {
        if (aggregate == null)
            return null;
        return results.stream().filter(result -> result.getAggregate() == aggregate).findFirst().map(Aggregate.Result::getValue).orElse(null);
    }

    private static boolean isNonFinite(@Nullable Object value)
    {
        return (value instanceof Double d && !Double.isFinite(d)) || (value instanceof Float f && !Float.isFinite(f));
    }

    /** Numbers become BigDecimal and date/time values LocalDate, the forms the ranker compares against. */
    static @Nullable Object toRangeValue(@Nullable Object value)
    {
        return switch (value)
        {
            case null -> null;
            case BigDecimal bd -> bd;
            case Number n when isNonFinite(n) -> null;
            case Number n -> new BigDecimal(n.toString());
            case LocalDate date -> date;
            case LocalDateTime dateTime -> dateTime.toLocalDate();
            // java.sql.Date doesn't support toInstant()
            case java.sql.Date date -> date.toLocalDate();
            case java.util.Date date -> LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault()).toLocalDate();
            default -> null;
        };
    }

    /** Matches the term's own form, e.g. "3" rather than "3.0". */
    static @NotNull String toIntegerString(@NotNull String value)
    {
        try
        {
            return new BigDecimal(value.trim()).stripTrailingZeros().toPlainString();
        }
        catch (NumberFormatException e)
        {
            return value;
        }
    }
}
