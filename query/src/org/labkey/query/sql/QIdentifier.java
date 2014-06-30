/*
 * Copyright (c) 2006-2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.query.sql;

import org.antlr.runtime.tree.CommonTree;
import org.apache.commons.lang3.StringUtils;
import org.labkey.api.query.FieldKey;
import org.labkey.query.sql.antlr.SqlBaseParser;

public class QIdentifier extends QFieldKey
{
    static QFieldKey create(CommonTree n)
    {
        if (n.getType() == SqlBaseParser.QUOTED_IDENTIFIER)
        {
            // check for "{$FIELDKEY$}" hack to enable mondrian to use lookup columns
            String text = unquote(n.getText());
            if (text.length() > 4 && text.startsWith("{$") && text.endsWith("$}"))
            {
                text = text.substring(2,text.length()-2);
                FieldKey fk = FieldKey.decode(text);
                QFieldKey prev = null;
                while (null != fk)
                {
                    QIdentifier id = new QIdentifier(fk.getName());
                    if (null == prev)
                        prev = id;
                    else
                        prev = new QDot(id,prev);
                    fk = fk.getParent();
                }
                prev.setLineAndColumn(n);
                return prev;
            }
        }
        // fall through
        QIdentifier qid = new QIdentifier();
        qid.from(n);
        return qid;
    }

    public QIdentifier()
    {
    }

    public QIdentifier(String str)
    {
        if (SqlParser.isLegalIdentifier(str))
        {
            setTokenType(SqlBaseParser.IDENT);
            setTokenText(str);
            return;
        }
        setTokenType(SqlBaseParser.QUOTED_IDENTIFIER);
        setTokenText(quote(str));
    }

    public FieldKey getFieldKey()
    {
        return new FieldKey(null, getIdentifier());
    }

    public String getIdentifier()
    {
        if (getTokenType() == SqlBaseParser.IDENT)
            return getTokenText();
        return unquote(getTokenText());
    }

    private static String unquote(String str)
    {
        if (str.length() < 2)
            throw new IllegalArgumentException();
        if (!str.startsWith("\"") || !str.endsWith("\""))
            throw new IllegalArgumentException("Expected " + str + " to be surrounded by double quotes");
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

    @Override
    public boolean equalsNode(QNode other)
    {
        return other instanceof QIdentifier && getIdentifier().equalsIgnoreCase(((QIdentifier) other).getIdentifier());
    }

    @Override
    public boolean isConstant()
    {
        return false;
    }
}
