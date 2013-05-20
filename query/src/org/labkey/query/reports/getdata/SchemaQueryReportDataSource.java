package org.labkey.query.reports.getdata;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.security.User;
import org.labkey.api.view.NotFoundException;
import org.labkey.query.LinkedSchema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * User: jeckels
 * Date: 5/15/13
 */
public class SchemaQueryReportDataSource extends AbstractQueryReportDataSource
{
    private final String _queryName;
    private final SimpleFilter _simpleFilter;

    public SchemaQueryReportDataSource(User user, Container container, SchemaKey schemaKey, String queryName)
    {
        super(user, container, schemaKey);
        _queryName = queryName;
        _simpleFilter = new SimpleFilter();
    }

    @Override
    public QueryDefinition getQueryDefinition()
    {
        QueryDefinition result = getSchema().getQueryDefForTable(_queryName);
        if (result == null)
        {
            throw new NotFoundException("No such query '" + _queryName + "' in schema '" + getSchema().getName());
        }
        return result;
    }

    @Override
    public String getLabKeySQL()
    {
        return LinkedSchema.generateLabKeySQL(getQueryDefinition().getTable(getSchema(), new ArrayList<QueryException>(), true), new LinkedSchema.SQLWhereClauseSource()
        {
            @Override
            public List<String> getWhereClauses()
            {
                List<String> result = new ArrayList<>();
                for (SimpleFilter.FilterClause filterClause : _simpleFilter.getClauses())
                {
                    result.add(filterClause.getLabKeySQLWhereClause(Collections.<FieldKey, ColumnInfo>emptyMap()));
                }
                return result;
            }
        }, Collections.<QueryService.ParameterDecl>emptySet());
    }
}
