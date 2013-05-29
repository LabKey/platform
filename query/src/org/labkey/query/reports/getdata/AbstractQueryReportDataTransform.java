package org.labkey.query.reports.getdata;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;

import java.util.Collection;

/**
 * User: jeckels
 * Date: 5/17/13
 */
public abstract class AbstractQueryReportDataTransform extends AbstractBaseQueryReportDataSource implements ReportDataTransform
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
}
