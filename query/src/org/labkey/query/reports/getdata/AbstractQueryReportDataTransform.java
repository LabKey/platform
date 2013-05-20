package org.labkey.query.reports.getdata;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Aggregate;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * User: jeckels
 * Date: 5/17/13
 */
public abstract class AbstractQueryReportDataTransform implements ReportDataTransform, QueryReportDataSource
{
    private final QueryReportDataSource _source;

    public AbstractQueryReportDataTransform(QueryReportDataSource source)
    {
        _source = source;
    }

    @Override
    public QueryReportDataSource getSource()
    {
        return _source;
    }

    @NotNull
    @Override
    public UserSchema getSchema()
    {
        return getSource().getSchema();
    }

    @Override
    public QueryDefinition getQueryDefinition()
    {
        QueryDefinition queryDef = QueryService.get().createQueryDef(getSource().getSchema().getUser(), getSource().getSchema().getContainer(), getSchema(), "InternalTransform");
        queryDef.setSql(getLabKeySQL());
        return queryDef;
    }

    protected abstract Collection<FieldKey> getRequiredInputs();

    protected Map<FieldKey, ColumnInfo> getSourceColumnMap()
    {
        QueryDefinition sourceQueryDef = getSource().getQueryDefinition();
        ArrayList<QueryException> errors = new ArrayList<>();
        TableInfo table = sourceQueryDef.getTable(_source.getSchema(), errors, true);
        if (!errors.isEmpty())
        {
            throw errors.get(0);
        }
        return QueryService.get().getColumns(table, getRequiredInputs());
    }

}
