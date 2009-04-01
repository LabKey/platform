package org.labkey.api.query;

import org.labkey.api.data.DbSchema;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.security.User;

import java.util.Set;
import java.util.Collections;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Mar 31, 2009
 * Time: 10:14:40 AM
 *
 * Wrap an DbSchema, QUERY INTERNAL USE ONLY
 */
public class QuerySchemaWrapper implements QuerySchema
{
    final DbSchema _schema;

    public QuerySchemaWrapper(DbSchema schema)
    {
        _schema = schema;
    }

    public User getUser()
    {
        return null;
    }

    public Container getContainer()
    {
        return null;
    }

    public DbSchema getDbSchema()
    {
        return _schema;
    }

    public TableInfo getTable(String name)
    {
        return _schema.getTable(name);
    }

    public QuerySchema getSchema(String name)
    {
        return null;
    }

    public Set<String> getSchemaNames()
    {
        return Collections.EMPTY_SET;
    }
}
