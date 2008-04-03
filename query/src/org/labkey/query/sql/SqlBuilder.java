package org.labkey.query.sql;

import org.labkey.api.data.SqlDialect;
import org.labkey.api.data.DbSchema;

import java.util.*;

public class SqlBuilder extends Builder
{
    private DbSchema _schema;

    /**
     */
    public SqlBuilder(DbSchema schema)
    {
        _schema = schema;
    }

    /**
     * Append a '?' to the generated SQL, and add the object to the list of the params.
     * @param value
     */
    public void appendParam(Object value)
    {
        append(" ? ");
        add(value);
    }

    public void addAll(Collection<? extends Object> params)
    {
        super.addAll(Arrays.asList(params.toArray()));
    }

    public void appendLiteral(String value)
    {
        if (value.indexOf("\\") >= 0 || value.indexOf("\'") >= 0)
            throw new IllegalArgumentException("Illegal characters in '" + value + "'");
        append("'" + value + "'");
    }

    public void appendConcatOperator()
    {
        append(" ");
        append(getDialect().getConcatenationOperator());
        append(" ");
    }

    public String getConcatOperator()
    {
        return getDialect().getConcatenationOperator();
    }

    public SqlDialect getDialect()
    {
        return _schema.getSqlDialect();
    }
    public DbSchema getDbSchema()
    {
        return _schema;
    }

    public void appendIdentifier(String str)
    {
        append("\"" + str + "\"");
    }

    public boolean allowUnsafeCode()
    {
        return false;
    }
}
