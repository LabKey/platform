package org.labkey.query.sql;

import org.apache.commons.lang.StringUtils;
import org.labkey.api.query.QueryParseException;
import org.labkey.query.sql.SqlTokenTypes;

import java.sql.Types;

public class QNumber extends QExpr implements IConstant
{
    public QNumber()
    {
    }

    public void setText(String str)
    {
        super.setText(str);

    }

    public QNumber(Number value)
    {
        if (value instanceof Double)
        {
            setTokenType(SqlTokenTypes.NUM_DOUBLE);
        }
        else if (value instanceof Float)
        {
            setTokenType(SqlTokenTypes.NUM_FLOAT);
        }
        else if (value instanceof Integer)
        {
            setTokenType(SqlTokenTypes.NUM_INT);
        }
        else if (value instanceof Long)
        {
            setTokenType(SqlTokenTypes.NUM_LONG);
        }
        else
        {
            throw new IllegalArgumentException();
        }
        setText(value.toString());
    }

    public Number getValue()
    {
        String text = getTokenText();
        if (StringUtils.isEmpty(text))
            return null;
        switch (getTokenType())
        {
            case SqlTokenTypes.NUM_DOUBLE:
                return Double.valueOf(text);
            case SqlTokenTypes.NUM_FLOAT:
                return Float.valueOf(text);
            case SqlTokenTypes.NUM_INT:
                return Integer.valueOf(text);
            case SqlTokenTypes.NUM_LONG:
                return Long.valueOf(text);
            default:
                throw new QueryParseException("Unexpected", null, getLine(), getColumn());
        }
    }

    public void appendSql(SqlBuilder builder)
    {
        builder.append(getValue().toString());
    }

    public void appendSource(SourceBuilder builder)
    {
        builder.append(getValue().toString());
    }

    public int getSqlType()
    {
        return Types.DOUBLE;
    }

    public String getValueString()
    {
        return getValue().toString();
    }
}
