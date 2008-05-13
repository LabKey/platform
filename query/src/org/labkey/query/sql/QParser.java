/*
 * Copyright (c) 2006-2007 LabKey Corporation
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

import antlr.ASTFactory;
import antlr.RecognitionException;
import antlr.TokenStreamRecognitionException;
import org.labkey.api.query.QueryParseException;
import org.labkey.query.sql.SqlParser;
import org.labkey.api.util.CaseInsensitiveHashSet;
import org.labkey.api.util.PageFlowUtil;

import java.util.Set;
import java.util.List;

public class QParser
{
    static private ASTFactory _factory = new ASTFactory()
    {
        public Class getASTNodeType(int tokenType) {
            return Node.class;
        }
    };

    static private Set<String> keywords = new CaseInsensitiveHashSet(PageFlowUtil.set(
            "all",
            "any",
            "and",
            "as",
            "asc",
            "avg",
            "between",
            "class",
            "count",
            "delete",
            "desc",
            "distinct",
            "elements",
            "escape",
            "exists",
            "false",
            "fetch",
            "from",
            "full",
            "group",
            "having",
            "in",
            "indices",
            "inner",
            "insert",
            "into",
            "is",
            "join",
            "left",
            "like",
            "limit",
            "max",
            "min",
            "new",
            "not",
            "null",
            "or",
            "order",
            "outer",
            "right",
            "select",
            "set",
            "some",
            "sum",
            "true",
            "union",
            "update",
            "user",
            "versioned",
            "where",
            "case",
            "end",
            "else",
            "then",
            "when",
            "on",
            "both",
            "empty",
            "leading",
            "member",
            "of",
            "trailing"));


    static public SqlParser getParser(String str)
    {
        SqlParser parser = new SqlParser(str);
        parser.setASTFactory(_factory);
        parser.setFilter(true);
        return parser;
    }

    static public QueryParseException wrapParseException(Throwable e)
    {
        if (e instanceof QueryParseException)
        {
            return (QueryParseException) e;
        }
        if (e instanceof TokenStreamRecognitionException)
        {
            e = ((TokenStreamRecognitionException) e).recog;
        }
        if (e instanceof RecognitionException)
        {
            RecognitionException re = (RecognitionException) e;
            return new QueryParseException(re.getMessage(), re, re.getLine(), re.getColumn());
        }
        return new QueryParseException("Unexpected exception", e, 0, 0);
    }

    static public QQuery parseStatement(String str, List<? super QueryParseException> errors)
    {
        try
        {
            SqlParser parser = getParser(str);
            parser.statement();
            Node node = (Node) parser.getAST();
            if (node == null)
                return null;
            QQuery ret = (QQuery) node.getQNode();
            ret.syntaxCheck(errors);
            for (Throwable e : parser.getErrors())
            {
                errors.add(wrapParseException(e));
            }
            return ret;
        }
        catch (Exception e)
        {
            errors.add(wrapParseException(e));
        }
        return null;
    }

    static public QExpr parseExpr(String str, List<? super QueryParseException> errors)
    {
        String full = "SELECT " + str + " AS __COLUMN__ FROM __TABLE__";
        QQuery query = parseStatement(full, errors);
        if (query == null)
        {
            return null;
        }
        if (errors.size() != 0)
            return null;
        QSelect select = query.getSelect();
        if (select == null)
        {
            return null;
        }
        if (select.childList().size() != 1)
        {
            return null;
        }
        QExpr ret = ((QAs) select.getFirstChild()).getExpression();
        ret.removeSiblings();
        return ret;
    }

    static public boolean isLegalIdentifierChar(char ch, boolean fFirst)
    {
        if (!fFirst && ch >= '0' && ch <= '9')
            return true;
        return ch == '_' || ch == '$' ||
                ch >= 'a' && ch <= 'z' ||
                ch >= 'A' && ch <= 'Z';
    }

    static public boolean isLegalIdentifier(String str)
    {
        if (str.length() == 0)
            return false;
        if (keywords.contains(str))
            return false;
        for (int i = 0; i < str.length(); i ++)
        {
            if (!isLegalIdentifierChar(str.charAt(i), i == 0))
            {
                return false;
            }
        }
        return true;
    }

    static public String makeLegalIdentifier(String str)
    {
        StringBuilder ret = new StringBuilder();
        for (int i = 0; i < str.length(); i ++)
        {
            char ch = str.charAt(i);
            if (isLegalIdentifierChar(ch, i == 0))
            {
                ret.append(ch);
            }
            else
            {
                ret.append('_');
            }
        }
        return ret.toString();
    }
}
