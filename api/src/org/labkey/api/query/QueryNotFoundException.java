package org.labkey.api.query;

/**
 * Created by matthew on 5/20/15.
 */
public class QueryNotFoundException extends QueryParseException
{
    final String queryName;

    public QueryNotFoundException(String queryName, int line, int column)
    {
        super("Query or table not found: " + queryName, null, line, column);
        this.queryName = queryName;
    }

    public String getQueryName()
    {
        return queryName;
    }
}
