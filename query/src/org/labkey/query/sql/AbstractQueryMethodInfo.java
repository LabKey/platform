package org.labkey.query.sql;

import org.labkey.api.data.DbSchema;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.query.AbstractMethodInfo;

/**
 * Created with IntelliJ IDEA.
 * User: matthew
 * Date: 9/26/13
 * Time: 3:31 PM
 */
public abstract class AbstractQueryMethodInfo extends AbstractMethodInfo
{
    AbstractQueryMethodInfo(JdbcType jdbcType)
    {
        super(jdbcType);
    }

    @Override
    final public SQLFragment getSQL(DbSchema schema, SQLFragment[] arguments)
    {
        return getSQL((Query)null, schema, arguments);
    }

    @Override
    final public SQLFragment getSQL(String tableAlias, DbSchema schema, SQLFragment[] arguments)
    {
        return super.getSQL(tableAlias, schema, arguments);
    }

    abstract public SQLFragment getSQL(Query query, DbSchema schema, SQLFragment[] arguments);
}
