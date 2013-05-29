package org.labkey.query.reports.getdata;

import com.fasterxml.jackson.annotation.JsonTypeName;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;

/**
 * JSON deserialization target for GetData APIs using a schema/query name combination as the root data source.
 *
 * User: jeckels
 * Date: 5/20/13
 */
@JsonTypeName("query")
public class SchemaQueryReportDataSourceBuilder extends AbstractReportDataSourceBuilder
{
    private String _queryName;

    public String getQueryName()
    {
        return _queryName;
    }

    public void setQueryName(String queryName)
    {
        _queryName = queryName;
    }

    @Override
    public QueryReportDataSource create(User user, Container container)
    {
        return new SchemaQueryReportDataSource(user, container, getSchemaKey(), getQueryName(), getContainerFilter(user), getParameters());
    }
}
