package org.labkey.experiment.api;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.Results;
import org.labkey.api.data.ResultsImpl;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.util.Pair;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ReexecutableRenderContext extends RenderContext
{
    Map<Set<FieldKey>, Pair<List<ColumnInfo>, SQLFragment>> fragments;

    ReexecutableRenderContext(RenderContext ctx)
    {
        super(ctx.getViewContext(), ctx.getErrors());

        fragments = new HashMap<>();
    }

    @Override
    protected Results selectForDisplay(TableInfo table, Collection<ColumnInfo> columns, Map<String, Object> parameters, SimpleFilter filter, Sort sort, int maxRows, long offset, boolean async)
    {
        Set<FieldKey> fieldKeys = columns.stream().map(ColumnInfo::getFieldKey).collect(Collectors.toSet());

        // Create SQLFragment for each unique set of fieldKeys columns we're handed
        var pair = fragments.computeIfAbsent(fieldKeys, (keys) -> {
            TableSelector selector = new TableSelector(table, columns, filter, sort)
                    .setNamedParameters(null)       // leave named parameters in SQLFragment
                    .setMaxRows(maxRows)
                    .setOffset(offset)
                    .setForDisplay(true);
            var sqlfWithCTE = selector.getSql();
            // flatten out CTEs
            SQLFragment sqlf = new SQLFragment(sqlfWithCTE.getSQL(), sqlfWithCTE.getParams());
            List<ColumnInfo> selectedColumns = new ArrayList<>(selector.getSelectedColumns());

            return Pair.of(selectedColumns, sqlf);
        });

        SQLFragment sqlf = pair.second;
        List<ColumnInfo> selectedColumns = pair.first;

        SQLFragment copy = new SQLFragment(sqlf, true);
        QueryService.get().bindNamedParameters(copy, parameters);
        QueryService.get().validateNamedParameters(copy);
        return new ResultsImpl(new SqlSelector(table.getSchema(), copy).getResultSet(), selectedColumns);
    }
}
