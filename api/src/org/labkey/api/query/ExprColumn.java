package org.labkey.api.query;

import org.apache.commons.lang.StringUtils;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;

import java.util.Map;

public class ExprColumn extends ColumnInfo
{
    static public final String STR_TABLE_ALIAS = "'''~~TABLE~~'''";
    SQLFragment _sql;
    private ColumnInfo[] _dependentColumns;
    public ExprColumn(TableInfo parent, String name, SQLFragment sql, int sqltype, ColumnInfo ... dependentColumns)
    {
        super(name, parent);
        setAlias(name);
        setSqlTypeName(ColumnInfo.sqlTypeNameFromSqlType(sqltype, getSqlDialect()));
        _sql = sql;
        _dependentColumns = dependentColumns;
    }

    public SQLFragment getValueSql(String tableAlias)
    {
        String sql = StringUtils.replace(_sql.getSQL(), STR_TABLE_ALIAS, tableAlias);
        SQLFragment ret = new SQLFragment(sql);
        ret.addAll(_sql.getParams());
        return ret;
    }

    public void setValueSQL(SQLFragment sql)
    {
        _sql = sql;
    }

    public void declareJoins(Map<String, SQLFragment> map)
    {
        for (ColumnInfo col : _dependentColumns)
        {
            col.declareJoins(map);
        }
    }
}
