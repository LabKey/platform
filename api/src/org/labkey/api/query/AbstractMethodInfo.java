package org.labkey.api.query;

import org.labkey.api.data.*;

import java.util.List;
import java.util.ArrayList;

abstract public class AbstractMethodInfo implements MethodInfo
{
    int _sqlType;
    public AbstractMethodInfo(int sqlType)
    {
        _sqlType = sqlType;
    }
    protected int getSqlType(ColumnInfo[] arguments)
    {
        return _sqlType;
    }

    public ColumnInfo createColumnInfo(TableInfo parentTable, ColumnInfo[] arguments, String alias)
    {
        return new ExprColumn(parentTable, alias, getSQL(parentTable.getSchema(), getSQLFragments(arguments)), getSqlType(arguments));
    }

    protected SQLFragment[] getSQLFragments(ColumnInfo[] arguments)
    {
        List<SQLFragment> ret = new ArrayList();
        for (ColumnInfo col : arguments)
        {
            ret.add(col.getValueSql());
        }
        return ret.toArray(new SQLFragment[0]);
    }
}
