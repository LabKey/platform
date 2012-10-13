package org.labkey.api.query;

import org.labkey.api.data.TableInfo;

/**
 * User: kevink
 * Date: 10/9/12
 *
 * WARNING: Walks the entire schema and query tree (which may be very expensive.)
 */
public class SchemaTreeWalker<R, P> extends SimpleSchemaTreeVisitor<R, P>
{
    @Override
    public R visitDefaultSchema(DefaultSchema schema, Path path, P param)
    {
        R r = null;
        r = visitAndReduce(schema.getSchemas(), path, param, r);
        return r;
    }

    @Override
    public R visitUserSchema(UserSchema schema, Path path, P param)
    {
        R r = null;
        r = visitAndReduce(schema.getSchemas(), path, param, r);
        r = visitAndReduce(schema.getTables(), path, param, r);
        return r;
    }

    @Override
    public R visitTable(TableInfo table, Path path, P param)
    {
        return null;
    }
}
