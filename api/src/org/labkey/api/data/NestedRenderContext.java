/*
 * Copyright (c) 2007-2016 LabKey Corporation
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

package org.labkey.api.data;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryNestingOption;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ActionURL;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Constructs the right context for showing a NestedQueryView, including translating sorts and filters to operate
 * on the right columns (and in the right sequence for sorts).
 *
 * User: jeckels
 * Date: Apr 11, 2007
 */
public class NestedRenderContext extends RenderContext
{
    private QueryNestingOption _nestingOption;

    public NestedRenderContext(QueryNestingOption nestingOption, ViewContext context)
    {
        super(context);
        _nestingOption = nestingOption;
    }

    public Sort buildSort(TableInfo tinfo, ActionURL url, String name)
    {
        Sort standardSort = super.buildSort(tinfo, url, name);
        if (_nestingOption != null)
        {
            boolean foundGroupId = false;
            standardSort.getSortList();
            Sort sort = new Sort();

            int totalIndex = 0;
            int outerIndex = 0;
            for (Sort.SortField field : standardSort.getSortList())
            {
                boolean innerColumn = _nestingOption.isOuter(field.getFieldKey());
                foundGroupId = foundGroupId || field.getFieldKey().equals(_nestingOption.getRowIdFieldKey());
                sort.insertSortColumn(field.getFieldKey(), field.getSortDirection(), field.isUrlClause(), innerColumn ? outerIndex++ : totalIndex);
                totalIndex++;
            }

            if (!foundGroupId)
            {
                sort.insertSortColumn(_nestingOption.getRowIdFieldKey(), Sort.SortDirection.ASC, false, outerIndex++);
            }

            return sort;
        }
        else
        {
            return standardSort;
        }
    }

    public SimpleFilter buildFilter(TableInfo tinfo, List<ColumnInfo> displayColumns, ActionURL url, String name, int maxRows, long offset, Sort sort)
    {
        SimpleFilter result = super.buildFilter(tinfo, displayColumns, url, name, maxRows, offset, sort);
        if (_nestingOption != null && (maxRows > 0 || offset > 0))
        {
            // We have to apply pagination as a subquery, since we want to paginate based
            // on groups, not on the actual rows in the query we end up executing
            result.addCondition(_nestingOption.getRowIdFieldKey(), null, CompareType.NONBLANK);

            // We only need to sort on the columns that are part of the grouping, not the nested
            // columns. These will also form the basis of the GROUP BY clause
            Sort groupingSort = new Sort();
            for (Sort.SortField sortField : sort.getSortList())
            {
                if (_nestingOption.isOuter(sortField.getFieldKey()))
                {
                    // Only include ones that are part of the grouping data, and preserve their order
                    groupingSort.insertSortColumn(sortField.getFieldKey(), sortField.getSortDirection(), sortField.isUrlClause(), groupingSort.getSortList().size());
                }
            }

            SQLFragment fromSQL = new SQLFragment(" FROM (");
            ColumnInfo groupColumn = appendFromSQL(tinfo, name, groupingSort, fromSQL, _nestingOption.getRowIdFieldKey());
            fromSQL.append(" ) FilterOnly ");

            Collection<ColumnInfo> cols = Collections.singletonList(groupColumn);
            SQLFragment withoutSort = QueryService.get().getSelectSQL(tinfo, cols, new SimpleFilter(), new Sort(), Table.ALL_ROWS, Table.NO_OFFSET, false);
            SQLFragment withSort = QueryService.get().getSelectSQL(tinfo, cols, new SimpleFilter(), groupingSort, Table.ALL_ROWS, Table.NO_OFFSET, false);

            // Figure out what the ORDER BY is
            String sortSQL = withSort.getSQL().substring(withSort.getSQL().toUpperCase().lastIndexOf("ORDER BY"));

            // The ORDER BY shouldn't include any parameters of its own
            assert withoutSort.getParams().size() == withSort.getParams().size() : "Parameter objects don't match: " + withoutSort.getParams() + " vs " + withSort.getParams();

            // Our GROUP BY is exactly the same set of columns as the ORDER BY, but without the
            // ASC or DESC. In buildSort we explicitly include the grouping PK in the Sort
            String groupBySQL = sortSQL.replaceAll(" DESC", "").replaceAll(" ASC", "").replaceAll("ORDER BY ", "GROUP BY ");

            // Add one to the limit so we can tell if there are more groups or not, and should therefore show pagination
            SQLFragment fullSQL = new SQLFragment(" " + groupColumn.getAlias() + " IN (SELECT " + groupColumn.getAlias() + " FROM (");
            fullSQL.append(tinfo.getSchema().getSqlDialect().limitRows(new SQLFragment("SELECT "  + groupColumn.getAlias() + " "),
                    fromSQL, null, sortSQL, groupBySQL, maxRows == 0 ? 0 : maxRows + 1, offset));
            fullSQL.append(" ) Limited )");

            // Apply a filter that restricts the group ids to the right "page" of data
            result.addClause(new SimpleFilter.SQLClause(fullSQL.getSQL(), fullSQL.getParamsArray()));
        }
        return result;
    }

    @Override
    public Map<String, List<Aggregate.Result>> getAggregates(List<DisplayColumn> displayColumns, TableInfo tinfo, QuerySettings settings, String dataRegionName, List<Aggregate> aggregatesIn,
            Map<String, Object> parameters, boolean async) throws IOException
    {
        if (aggregatesIn == null || aggregatesIn.isEmpty())
            return Collections.emptyMap();

        // We only support a single aggregate - COUNT(*)
        assert aggregatesIn.size() <= 1;
        if (_nestingOption == null)
        {
            // If we're not a nested query, don't do anything special
            return super.getAggregates(displayColumns, tinfo, settings, dataRegionName, aggregatesIn, parameters, async);
        }

        // We want to do the aggregate query on the grouped data, since ultimately we want
        // a count of the number of groups
        Sort sort = new Sort(_nestingOption.getRowIdFieldKey());
        final SQLFragment fromSQL = new SQLFragment();
        ColumnInfo groupColumn = appendFromSQL(tinfo, dataRegionName, sort, fromSQL, _nestingOption.getAggregateRowIdFieldKey());
        fromSQL.insert(0, "SELECT " + groupColumn.getAlias() + " FROM (");
        fromSQL.append(") FilterOnly GROUP BY ");
        fromSQL.append(groupColumn.getAlias());

        // Create a TableInfo that wraps the GROUP BY query
        VirtualTable aggTableInfo = new VirtualTable(tinfo.getSchema(), "AggTable")
        {
            @NotNull
            @Override
            public SQLFragment getFromSQL()
            {
                return fromSQL;
            }
        };

        if (!aggregatesIn.isEmpty())
        {
            TableSelector selector = new TableSelector(aggTableInfo, Collections.emptyList(), null, null);

            if (async)
                return selector.getAggregatesAsync(aggregatesIn, getViewContext().getResponse());
            else
                return selector.getAggregates(aggregatesIn);
        }

        return Collections.emptyMap();
    }

    private ColumnInfo appendFromSQL(TableInfo tinfo, String dataRegionName, Sort sort, SQLFragment sql, FieldKey groupFieldKey)
    {
        SimpleFilter filter = super.buildFilter(tinfo, getViewContext().getActionURL(), dataRegionName, Table.ALL_ROWS, Table.NO_OFFSET, sort);

        // We need to do an aggregate query to find out the group ids that we want to show based
        // on pagination.
        // To do this, we take the full query, do a GROUP BY on the grouping columns, and apply
        // the limit and offset

        // Use Query to build up the SQL that we need
        Collection<ColumnInfo> cols = new ArrayList<>();
        Map<FieldKey, ColumnInfo> groupColumns = QueryService.get().getColumns(tinfo, Collections.singleton(groupFieldKey));
        assert groupColumns.size() == 1;
        ColumnInfo groupColumn = groupColumns.get(groupFieldKey);
        assert groupColumn != null;
        cols.add(groupColumn);
        cols = QueryService.get().ensureRequiredColumns(tinfo, cols, filter, sort, _ignoredColumnFilters);

        // We need to stick the GROUP BY before the ORDER BY. QueryService won't help us generate
        // the GROUP BY, so get the query with and without the ORDER BY
        SQLFragment withoutSort = QueryService.get().getSelectSQL(tinfo, cols, filter, new Sort(), Table.ALL_ROWS, Table.NO_OFFSET, false);

        sql.append(withoutSort);

        return groupColumn;
    }

    @Override
    protected Results selectForDisplay(TableInfo table, Collection<ColumnInfo> columns, Map<String, Object> parameters, SimpleFilter filter, Sort sort, int maxRows, long offset, boolean async) throws SQLException, IOException
    {
        if (_nestingOption != null)
        {
            // We're handling paging on our own, so don't rely on the default implementation. Force it to not limit the rows
            maxRows = Table.ALL_ROWS;
            offset = 0;
        }

        TableSelector selector = new TableSelector(table, columns, filter, sort).setForDisplay(true);
        selector.setMaxRows(maxRows).setOffset(offset).setNamedParameters(parameters);

        // Force the result set to be cached so that we can do our nesting
        if (async)
        {
            return selector.getResultsAsync(getCache(), true, getViewContext().getResponse());
        }
        else
        {
            return selector.getResults(getCache(), true);
        }
    }
}
