package org.labkey.query.reports.getdata;

import org.labkey.api.data.Container;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.security.User;
import org.labkey.api.util.GUID;

/**
 * User: jeckels
 * Date: 5/15/13
 */
public class LabKeySQLReportDataSource extends AbstractQueryReportDataSource
{
    private final String _sql;

    public LabKeySQLReportDataSource(User user, Container container, SchemaKey schemaKey, String sql)
    {
        super(user, container, schemaKey);
        _sql = sql;
    }

    @Override
    public QueryDefinition getQueryDefinition()
    {
        QueryDefinition queryDef = QueryService.get().createQueryDef(getSchema().getUser(), getSchema().getContainer(), getSchema(), GUID.makeGUID().replace("-", ""));
        queryDef.setSql(_sql);
        return queryDef;
    }

    @Override
    public String getLabKeySQL()
    {
        return _sql;
    }
}
