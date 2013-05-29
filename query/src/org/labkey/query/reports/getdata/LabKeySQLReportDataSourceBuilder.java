package org.labkey.query.reports.getdata;

import com.fasterxml.jackson.annotation.JsonTypeName;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;

/**
 * JSON deserialization target for GetData APIs using a LabKey SQL query as the root data source.
 * User: jeckels
 * Date: 5/20/13
 */

@JsonTypeName("sql")
public class LabKeySQLReportDataSourceBuilder extends AbstractReportDataSourceBuilder
{
    private String _sql;

    public String getSql()
    {
        return _sql;
    }

    public void setSql(String sql)
    {
        _sql = sql;
    }

    @Override
    public QueryReportDataSource create(User user, Container container)
    {
        return new LabKeySQLReportDataSource(user, container, getSchemaKey(), getContainerFilter(user), getParameters(), getSql());
    }
}
