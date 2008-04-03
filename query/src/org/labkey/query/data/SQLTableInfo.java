package org.labkey.query.data;

import org.labkey.api.data.*;
import org.labkey.api.query.QuerySchema;

import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.List;

public class SQLTableInfo extends AbstractTableInfo
{
    SQLFragment _fromSQL;

    public SQLTableInfo(DbSchema schema)
    {
        super(schema);
    }

    protected boolean isCaseSensitive()
    {
        return true;
    }

    public SQLFragment getFromSQL(String aliasName)
    {
        SQLFragment ret = new SQLFragment();
        ret.append(_fromSQL);
        ret.append(" AS " + aliasName);
        return ret;
    }

    public void setFromSQL(SQLFragment sql)
    {
        _fromSQL = sql;
    }
}

