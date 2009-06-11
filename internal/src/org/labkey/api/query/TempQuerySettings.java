package org.labkey.api.query;

import org.labkey.api.data.Container;

/**
 * Created by IntelliJ IDEA.
 * User: dave
 * Date: Jun 11, 2009
 * Time: 11:32:35 AM
 */

/**
 * This class may be used ot create a QuerySettings from a given SQL statement,
 * schema name, and container.
 */
public class TempQuerySettings extends QuerySettings
{
    private String _schemaName;
    private String _sql;
    private Container _container;

    public TempQuerySettings(String schemaName, String sql, Container container)
    {
        super("query");
        _schemaName = schemaName;
        _sql = sql;
        _container = container;
    }

    public QueryDefinition getQueryDef(UserSchema schema)
    {
        QueryDefinition qdef = QueryService.get().createQueryDef(_container, _schemaName, "temp");
        qdef.setSql(_sql);
        return qdef;
    }

}
