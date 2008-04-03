package org.labkey.query.sql;

import org.labkey.api.query.FieldKey;
import org.labkey.query.sql.SqlTokenTypes;
import org.apache.commons.lang.StringUtils;

public class QIdentifier extends QFieldKey
{
    public QIdentifier()
    {
    }

    public QIdentifier(String str)
    {
        if (QParser.isLegalIdentifier(str))
        {
            setTokenType(SqlTokenTypes.IDENT);
            setText(str);
            return;
        }
        setTokenType(SqlTokenTypes.QUOTED_IDENTIFIER);
        setText(quote(str));
    }

    public FieldKey getFieldKey()
    {
        return new FieldKey(null, getIdentifier());
    }

    public String getIdentifier()
    {
        if (getTokenType() == SqlTokenTypes.IDENT)
            return getTokenText();
        return unquote(getTokenText());
    }

    private String unquote(String str)
    {
        if (str.length() < 2)
            throw new IllegalArgumentException();
        if (!str.startsWith("\"") || !str.endsWith("\""))
            throw new IllegalArgumentException();
        str = str.substring(1, str.length() - 1);
        str = StringUtils.replace(str, "\"\"", "\"");
        return str;
    }

    private String quote(String str)
    {
        return "\"" + StringUtils.replace(str, "\"", "\"\"") + "\"";
    }


    public void appendSource(SourceBuilder builder)
    {
        builder.append(getTokenText());
    }

    public String getValueString()
    {
        return getTokenText();
    }
}
