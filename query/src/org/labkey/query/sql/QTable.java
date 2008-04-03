package org.labkey.query.sql;

import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.FieldKey;
import org.labkey.query.data.JoinType;
import org.labkey.api.data.TableInfo;

public class QTable
{
    QExpr _table;
    JoinType _joinType;
    QIdentifier _alias;
    QExpr _on;
    TableInfo _tableObject;
    FilteredTable _wrappedTableObject;

    public QTable(QExpr table, JoinType joinType)
    {
        _table = table;
        _joinType = joinType;
    }

    public void setAlias(QIdentifier alias)
    {
        _alias = alias;
    }

    public void setOn(QExpr with)
    {
        _on = with;
    }

    public FieldKey getTableKey()
    {
        return _table.getFieldKey();
    }

    public FieldKey getAlias()
    {
        if (_alias == null)
            return getTableKey();
        return new FieldKey(null, _alias.getIdentifier());
    }

    public void appendSource(SourceBuilder builder, boolean first)
    {
        if (!first)
        {
            switch (_joinType)
            {
                case inner:
                    builder.append("INNER JOIN ");
                    break;
                case left:
                    builder.append("LEFT JOIN ");
                    break;
                case right:
                    builder.append("RIGHT JOIN ");
                    break;
                case outer:
                    builder.append("OUTER JOIN ");
                    break;
            }
        }
        _table.appendSource(builder);
        if (_alias != null)
        {
            builder.append(" AS ");
            _alias.appendSource(builder);
        }
        if (_on != null)
        {
            builder.append(" ON ");
            _on.appendSource(builder);
        }
    }

    public QExpr getOn()
    {
        return _on;
    }

    public JoinType getJoinType()
    {
        return _joinType;
    }

    public QExpr getTable()
    {
        return _table;
    }

    public TableInfo getTableObject()
    {
        return _tableObject;
    }

    public void setTableObject(TableInfo tableObject)
    {
        _tableObject = tableObject;
    }
}
