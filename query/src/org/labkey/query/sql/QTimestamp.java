package org.labkey.query.sql;

import org.antlr.runtime.tree.CommonTree;
import org.labkey.api.data.JdbcType;
import org.labkey.api.util.DateUtil;
import java.sql.Timestamp;

/**
 * Created with IntelliJ IDEA.
 * User: matthew
 * Date: 6/7/13
 * Time: 12:37 PM
 */
public class QTimestamp extends QExpr implements IConstant
{
    Timestamp _value = null;

    public QTimestamp(CommonTree n, Timestamp value)
    {
        super(false);
        from(n);
        _value = value;
    }

    public Timestamp getValue()
    {
        return _value;
    }

    public void appendSql(SqlBuilder builder)
    {
        builder.append("{ts '" + DateUtil.toISO(_value) + "'}");
    }

    public void appendSource(SourceBuilder builder)
    {
        builder.append("{ts " + getTokenText() + "}");
    }

    public JdbcType getSqlType()
    {
        return JdbcType.TIMESTAMP;
    }

    public String getValueString()
    {
        return"{ts " + QString.quote(getTokenText()) + "}";
    }

    @Override
    public boolean equalsNode(QNode other)
    {
        return other instanceof QTimestamp && getValue().equals(((QNumber) other).getValue());
    }

    @Override
    public boolean isConstant()
    {
        return true;
    }
}
