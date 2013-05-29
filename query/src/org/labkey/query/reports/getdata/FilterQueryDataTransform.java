package org.labkey.query.reports.getdata;

import org.labkey.api.data.SimpleFilter;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryDefinition;

import java.util.Collection;

/**
 * User: jeckels
 * Date: 5/17/13
 */
public class FilterQueryDataTransform extends AbstractQueryReportDataTransform
{
    private final SimpleFilter _filter;

    private static final String SUBQUERY_ALIAS = "F";

    public FilterQueryDataTransform(QueryReportDataSource source, SimpleFilter filter)
    {
        super(source);
        _filter = filter;
    }

    @Override
    public QueryDefinition getQueryDefinition()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getLabKeySQL()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT ");
        sb.append(SUBQUERY_ALIAS);
        sb.append(".*");
        sb.append(" FROM (\n");
        sb.append(getSource().getLabKeySQL());
        sb.append("\n) ");
        sb.append(SUBQUERY_ALIAS);
        String where = _filter.toLabKeySQL(getSource().getColumnMap(getRequiredInputs()));
        if (where.length() > 0)
        {
            sb.append("\nWHERE ");
            sb.append(where);
        }
        return sb.toString();
    }

    @Override
    protected Collection<FieldKey> getRequiredInputs()
    {
        return _filter.getAllFieldKeys();
    }
}
