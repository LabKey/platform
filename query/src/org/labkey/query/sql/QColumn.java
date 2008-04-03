package org.labkey.query.sql;

import org.labkey.query.sql.SqlTokenTypes;
import org.labkey.api.query.FieldKey;
import org.apache.commons.lang.StringUtils;

public class QColumn
{
    QNode _node;
    QExpr _field;
    QIdentifier _alias;

    public QColumn(QNode<QExpr> node)
    {
        _node = node;
        if (node instanceof QAs)
        {
            _field = ((QAs) node).getExpression();
            _alias = ((QAs) node).getAlias();
        }
        else
        {
            _field = (QExpr) node;
        }
    }

    public QColumn(QExpr expr)
    {
        _field = expr;
    }

    public String getAlias()
    {
        if (_alias == null)
            return null;
        return _alias.getIdentifier();
    }

    public boolean isAliasQuoted()
    {
        if (_alias == null)
            return false;
        return _alias.getTokenType() == SqlTokenTypes.QUOTED_IDENTIFIER;
    }

    public void appendSource(SourceBuilder builder)
    {
        _field.appendSource(builder);
        if (_alias != null)
        {
            builder.append(" AS ");
            _alias.appendSource(builder);
        }
    }

    public FieldKey getFieldKey(FieldKey root, QExpr expr)
    {
        if (getAlias() != null)
        {
            return new FieldKey(root, getAlias());
        }
        if (expr instanceof QField)
        {
            return FieldKey.fromString(((QField) expr).getColumnInfo().getName());
        }
        return null;
    }

    public String getFieldName()
    {
        if (_field instanceof QField)
            return ((QField) _field).getName();
        return null;
    }

    public void setAlias(String alias)
    {
        alias = StringUtils.trimToNull(alias);
        if (alias == null)
        {
            _alias = null;
        }
        else
        {
            _alias = new QIdentifier(alias);
        }
    }

    public QExpr getField()
    {
        return _field;
    }

    public void setField(QExpr field)
    {
        _field = field;
    }
}
