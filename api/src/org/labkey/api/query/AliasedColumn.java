package org.labkey.api.query;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;

import java.util.Map;

public class AliasedColumn extends ColumnInfo
{
    ColumnInfo _column;
    public AliasedColumn(TableInfo parent, String name, ColumnInfo column)
    {
        super(name, parent);
        setName(name);
        setAlias(name);
        copyAttributesFrom(column);
        if (!name.equalsIgnoreCase(column.getName()))
        {
            setCaption(null);
        }
        _column = column;
    }

    public AliasedColumn(String name, ColumnInfo column)
    {
        this(column.getParentTable(), name, column);
    }

    public SQLFragment getValueSql()
    {
        if (getParentTable() == _column.getParentTable())
        {
            return _column.getValueSql();
        }
        return super.getValueSql();
    }

    public SQLFragment getValueSql(String tableAlias)
    {
        if (getParentTable() == _column.getParentTable())
        {
            return _column.getValueSql(tableAlias);
        }
        SQLFragment ret = new SQLFragment();
        ret.append(tableAlias);
        ret.append(".");
        ret.append(getSqlDialect().getColumnSelectName(_column.getAlias()));
        return ret;
    }

    public void declareJoins(Map<String, SQLFragment> map)
    {
        if (getParentTable() == _column.getParentTable())
            _column.declareJoins(map);
    }

    public ColumnInfo getColumn()
    {
        return _column;
    }
}
