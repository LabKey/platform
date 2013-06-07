package org.labkey.query.sql;

import org.antlr.runtime.tree.CommonTree;
import org.labkey.api.data.JdbcType;
import org.labkey.api.util.DateUtil;


/**
 * Created with IntelliJ IDEA.
 * User: matthew
 * Date: 6/7/13
 * Time: 12:37 PM
 */
public class QDate extends QExpr implements IConstant
{
    java.sql.Date _value = null;

    public QDate(CommonTree n, java.sql.Date value)
    {
        super(false);
        from(n);
        _value = value;
    }

    public java.sql.Date getValue()
    {
        return _value;
    }

    public void appendSql(SqlBuilder builder)
    {
        builder.append("{d '" + DateUtil.toISO(_value).substring(0,10) + "'}");
    }

    public void appendSource(SourceBuilder builder)
    {
        builder.append("{d " + getTokenText() + "}");
    }

    public JdbcType getSqlType()
    {
        return JdbcType.DATE;
    }

    public String getValueString()
    {
        return"{d " + QString.quote(getTokenText()) + "}";
    }

    @Override
    public boolean equalsNode(QNode other)
    {
        return other instanceof QDate && getValue().equals(((QNumber) other).getValue());
    }

    @Override
    public boolean isConstant()
    {
        return true;
    }
}
