/*
 * Copyright (c) 2006-2015 LabKey Corporation
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

package org.labkey.api.data;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.util.HString;
import org.labkey.api.util.JdbcUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * User: Matthew
 * Date: Apr 19, 2006
 * Time: 4:56:01 PM
 */
public class SQLFragment implements Appendable, CharSequence
{
    String sql;
    StringBuilder sb = null;
    List<Object> params;          // TODO: Should be List<?>

    Map<Object,CTE> commonTableExpressionsMap = null;

    class CTE
    {
        CTE(@NotNull String name, SQLFragment sqlf)
        {
            this.name = name;
            tokens.add(token(name));
            this.sqlf = sqlf;
        }

        CTE(String name, Collection<String> tokens, SQLFragment sqlf)
        {
            this.name = name;
            this.tokens.addAll(tokens);
            this.sqlf = sqlf;
            assert(!this.tokens.isEmpty());
        }

        private String token()
        {
            return token(name);
        }

        private String token(String name)
        {
            if (token == null)
                token = "/*${cte " + name + "}*/";
            return token;
        }

        final String name;
        final Set<String> tokens = new TreeSet<>();
        SQLFragment sqlf;
        String token;
    }

    public SQLFragment()
    {
        sql = "";
    }

    public SQLFragment(CharSequence sql, @Nullable List<?> params)
    {
        guard(sql);
        this.sql = sql.toString();
        if (null != params)
            this.params = new ArrayList<>(params);
    }


    public SQLFragment(CharSequence sql, Object... params)
    {
        guard(sql);
        this.sql = sql.toString();
        this.params = Arrays.asList(params);
    }


    public SQLFragment(SQLFragment other)
    {
        this(other,false);
    }


    public SQLFragment(SQLFragment other, boolean deep)
    {
        this(other.getSqlCharSequence(), other.params);
        if (null != other.commonTableExpressionsMap && !other.commonTableExpressionsMap.isEmpty())
        {
            if (null == this.commonTableExpressionsMap)
                this.commonTableExpressionsMap = new LinkedHashMap<>();
            for (Map.Entry<Object,CTE> e : other.commonTableExpressionsMap.entrySet())
            {
                SQLFragment sqlf = deep ? new SQLFragment(e.getValue().sqlf) : e.getValue().sqlf;
                this.commonTableExpressionsMap.put(e.getKey(), new CTE(e.getValue().name, e.getValue().tokens, sqlf));
            }
        }
    }


    public boolean isEmpty()
    {
        return (null == sb || sb.length() == 0) && (sql == null || sql.length() == 0);
    }


    /* same as getSQL() but without CTE handling */
    public String getRawSQL()
    {
        return null != sb ? sb.toString() : null != sql ? sql : "";
    }


    public String getSQL()
    {
        if (null == commonTableExpressionsMap || commonTableExpressionsMap.isEmpty())
            return null != sb ? sb.toString() : null != sql ? sql : "";

        // TODO: support CTE that in turn have CTE???
        String select = null != sb ? sb.toString() : null != this.sql ? this.sql : "";
        StringBuilder ret = new StringBuilder();
        String comma = "WITH\n\t";
        for (Map.Entry<Object,CTE> e : commonTableExpressionsMap.entrySet())
        {
            CTE cte = e.getValue();
            SQLFragment expr = cte.sqlf;
            if (null != expr.commonTableExpressionsMap && !expr.commonTableExpressionsMap.isEmpty())
                throw new IllegalStateException("nested common table expressions are not supported");
            ret.append(comma).append(cte.name).append(" AS (").append(expr.getSQL()).append(")");
            comma = ",\n\t";
            for (String token : cte.tokens)
            {
                select = StringUtils.replace(select, token, cte.name);
            }
        }
        ret.append("\n");
        ret.append(select);
        return ret.toString();
    }


    /** this is a to allow us to compare fragments with CTE that haven't been collapsed yet (only used in assertions, debugging etc) **/
    public String getCompareSQL()
    {
        if (null == commonTableExpressionsMap || commonTableExpressionsMap.isEmpty())
            return null != sb ? sb.toString() : null != sql ? sql : "";

        String select = null != sb ? sb.toString() : null != this.sql ? this.sql : "";
        for (Map.Entry<Object, CTE> e : commonTableExpressionsMap.entrySet())
        {
            CTE cte = e.getValue();
            String repl = cte.sqlf.getCompareSQL();
            for (String token : cte.tokens)
                select = StringUtils.replace(select, token, " FROM (" + repl + ") ");
        }
        return select;
    }


    // It is a little confusing that getString() does not return the same charsequence that this object purports to
    // represent.  However, this is a good "display value" for this object.
    // see getSqlCharSequence()
    @NotNull
    public String toString()
    {
        return "SQLFragment@" + System.identityHashCode(this) + "\n" + JdbcUtil.format(this);
    }


    public String toDebugString()
    {
        return JdbcUtil.format(this);
    }

    public List<Object> getParams()
    {
        List<Object> ret;

        if (null == commonTableExpressionsMap || commonTableExpressionsMap.isEmpty())
            ret = params == null ? Collections.emptyList() : params;
        else
        {
            ret = new ArrayList<>();
            for (Map.Entry<Object,CTE> e : commonTableExpressionsMap.entrySet())
            {
                if (null != e.getValue().sqlf.params)
                    ret.addAll(e.getValue().sqlf.params);
            }
            if (null != params)
                ret.addAll(params);
        }
        assert null != (ret = Collections.unmodifiableList(ret));
        return ret;
    }


    final static Object[] emptyArray = new Object[0];

    public Object[] getParamsArray()
    {
        return null == params ? emptyArray : params.toArray();
    }


    private List<Object> getModfiableParams()
    {
        if (!(params instanceof ArrayList))
        {
            ArrayList<Object> t = new ArrayList<>();
            if (params != null)
                t.addAll(params);
            params = t;
        }
        return params;
    }


    private StringBuilder getStringBuilder()
    {
        if (null == sb)
            sb = new StringBuilder(sql);
        return sb;
    }


    @Override
    public SQLFragment append(CharSequence s)
    {
        guard(s);
        getStringBuilder().append(s);
        return this;
    }


    @Override
    public SQLFragment append(CharSequence csq, int start, int end) throws IOException
    {
        append(csq.subSequence(start, end));
        return this;
    }

    public SQLFragment append(@NotNull Container c)
    {
        String id = c.getId();
        String name = c.getName();

        if (StringUtils.containsAny(id,"*/'\"?"))
            throw new IllegalStateException();

        append("'").append(id).append("'");
        if (!StringUtils.containsAny(name,"*/'\"?"))
            append("/* ").append(c.getName()).append(" */");
        return this;
    }

    public SQLFragment append(Object o)
    {
        guard(o);
        getStringBuilder().append(o);
        return this;
    }

    public SQLFragment add(Object p)
    {
        getModfiableParams().add(p);
        return this;
    }


    public void addAll(Collection<?> l)
    {
        getModfiableParams().addAll(l);
    }


    public void addAll(Object[] values)
    {
        if (values == null)
            return;
        addAll(Arrays.asList(values));
    }


    public void set(int i, Object p)
    {
        getModfiableParams().set(i,p);
    }


    public SQLFragment append(SQLFragment f)
    {
        if (null != f.sb)
            append(f.sb);
        else
            append(f.sql);
        if (null != f.params)
            addAll(f.params);
        mergeCommonTableExpressions(f);
        return this;
    }


    // Append a full statement (using the correct dialect syntax) and its parameters to this SQLFragment
    public SQLFragment appendStatement(@Nullable SQLFragment statement, SqlDialect dialect)
    {
        if (null == statement || statement.isEmpty())
            return this;
        // getSQL() flattens out common table expressions
        dialect.appendStatement(this, statement.getSQL());
        addAll(statement.getParams());
        return this;
    }


    // return boolean so this can be used in an assert.  passing in a dialect is not ideal, but parsing comments out
    // before submitting the fragment is not reliable and holding statements & comments separately (to eliminate the
    // need to parse them) isn't particularly easy... so punt for now.
    public boolean appendComment(String comment, SqlDialect dialect)
    {
        if (dialect.supportsComments())
        {
            StringBuilder sb = getStringBuilder();
            int len = sb.length();
            if (len > 0 && sb.charAt(len-1) != '\n')
                sb.append('\n');
            sb.append("\n-- ");
            boolean truncated = comment.length() > 1000;
            if (truncated)
                comment = comment.substring(0,1000);
            sb.append(comment);
            if (StringUtils.countMatches(comment, "'")%2==1)
                sb.append("'");
            if (truncated)
                sb.append("...");
            sb.append('\n');
        }
        return true;
    }


    /** use append(TableInfo, String alias) */
    @Deprecated
    public SQLFragment append(TableInfo table)
    {
        String s = table.getSelectName();
        if (s != null)
            return append(s);

        return append(table.getFromSQL(table.getName()));
    }


    public SQLFragment append(TableInfo table, String alias)
    {
        return append(table.getFromSQL(alias));
    }

    
    public SQLFragment append(char ch)
    {
        getStringBuilder().append(ch);
        return this;
    }

    public SQLFragment appendStringLiteral(CharSequence s)
    {
        String source = HString.source(s);        
        if (s instanceof HString || source.contains("'") || source.contains("\\"))
        {
            append("?");
            add(s);
            return this;
        }
        append("'");
        append(s);
        append("'");
        return this;
    }

    public CharSequence getSqlCharSequence()
    {
        if (null != sb)
        {
            return sb;
        }
        return sql;
    }

    public void insert(int index, String str)
    {
        getStringBuilder().insert(index, str);
    }

    // Insert this SQLFragment's SQL and parameters at the start of the existing SQL and parameters
    public void prepend(SQLFragment sql)
    {
        insert(0, sql.getSqlCharSequence().toString());
        getModfiableParams().addAll(0, sql.getParams());
        mergeCommonTableExpressions(sql);
    }


    public int indexOf(String str)
    {
        return getStringBuilder().indexOf(str);
    }


    // Display query in "English" (display SQL with params substituted)
    // with a little more work could probably be made to be SQL legal
    public String getFilterText()
    {
        String sql = getSQL().replaceFirst("WHERE ", "");
        List params = getParams();
        for (Object param1 : params)
        {
            String param = param1.toString();
            param = param.replaceAll("\\\\", "\\\\\\\\");
            param = param.replaceAll("\\$", "\\\\\\$");
            sql = sql.replaceFirst("\\?", param);
        }
        return sql.replaceAll("\"", "");
    }


    void guard(Object s)
    {
        if (s instanceof HString && ((HString)s).isTainted())
            throw new IllegalArgumentException(((HString)s).getSource());
    }

    @Override
    public char charAt(int index)
    {
        return getSqlCharSequence().charAt(index);
    }

    @Override
    public int length()
    {
        return getSqlCharSequence().length();
    }

    @Override
    public CharSequence subSequence(int start, int end)
    {
        return getSqlCharSequence().subSequence(start, end);
    }


    /**
     * KEY is used as a faster way to look for equivalent CTE expressions.
     * returning a name here allows us to potentially merge CTE at add time
     *
     * if you don't have a key you can just use sqlf.toString()
     */
    public String addCommonTableExpression(Object key, String proposedName, SQLFragment sqlf)
    {
        if (null == commonTableExpressionsMap)
            commonTableExpressionsMap = new LinkedHashMap<>();
        CTE prev = commonTableExpressionsMap.get(key);
        if (null != prev)
            return prev.token();
        CTE cte = new CTE(proposedName,sqlf);
        commonTableExpressionsMap.put(key,cte);
        return cte.token();
    }


    private void mergeCommonTableExpressions(SQLFragment sqlFrom)
    {
        if (null == sqlFrom.commonTableExpressionsMap || sqlFrom.commonTableExpressionsMap.isEmpty())
            return;
        if (null == commonTableExpressionsMap)
            commonTableExpressionsMap = new LinkedHashMap<>();
        for (Map.Entry<Object, CTE> e : sqlFrom.commonTableExpressionsMap.entrySet())
        {
            CTE from = e.getValue();
            CTE to = commonTableExpressionsMap.get(e.getKey());
            if (null != to)
                to.tokens.addAll(from.tokens);
            else
                commonTableExpressionsMap.put(e.getKey(),new CTE(from.name, from.tokens,from.sqlf));
        }
    }



    public static SQLFragment prettyPrint(SQLFragment from)
    {
        SQLFragment sqlf = new SQLFragment(from);

        String s = from.getSqlCharSequence().toString();
        StringBuilder sb = new StringBuilder(s.length() + 200);
        String[] lines = StringUtils.split(s, '\n');
        int indent = 0;

        for (String line : lines)
        {
            String t = line.trim();

            if (t.length() == 0)
                continue;

            if (t.startsWith("-- </"))
                indent = Math.max(0,indent-1);

            for (int i=0 ; i<indent ; i++)
                sb.append('\t');

            sb.append(line);
            sb.append('\n');

            if (t.startsWith("-- <") && !t.startsWith("-- </"))
                indent++;
        }

        sqlf.sb = sb;
        sqlf.sql = null;
        return sqlf;
    }



    public static class TestCase extends Assert
    {
        @Test
        public void params()
        {
            // append(SQLFragment)
            // toString() w/ params
            // prettyprint()
        }


        @Test
        public void cte()
        {
            SQLFragment a = new SQLFragment("SELECT a FROM b WHERE x=?", 5);
            assertEquals("SELECT a FROM b WHERE x=?", a.getSQL());
            assertEquals("SELECT a FROM b WHERE x=5", a.toDebugString());

            SQLFragment b = new SQLFragment("SELECT * FROM CTE WHERE y=?","xxyzzy");
            b.addCommonTableExpression(new Object(), "CTE", a);
            assertEquals("WITH\n" +
                    "\tCTE AS (SELECT a FROM b WHERE x=?)\n" +
                    "SELECT * FROM CTE WHERE y=?", b.getSQL());
            assertEquals("WITH\n" +
                    "\tCTE AS (SELECT a FROM b WHERE x=5)\n" +
                    "SELECT * FROM CTE WHERE y='xxyzzy'", b.toDebugString());
            List<Object> params = b.getParams();
            assertEquals(2,params.size());
            assertEquals(5, params.get(0));
            assertEquals("xxyzzy", params.get(1));


            SQLFragment c = new SQLFragment(b);
            assertEquals("WITH\n" +
                    "\tCTE AS (SELECT a FROM b WHERE x=?)\n" +
                    "SELECT * FROM CTE WHERE y=?", c.getSQL());
            assertEquals("WITH\n" +
                    "\tCTE AS (SELECT a FROM b WHERE x=5)\n" +
                    "SELECT * FROM CTE WHERE y='xxyzzy'", c.toDebugString());
            params = c.getParams();
            assertEquals(2,params.size());
            assertEquals(5, params.get(0));
            assertEquals("xxyzzy", params.get(1));


            // combining

            SQLFragment sqlf = new SQLFragment();
            String token = sqlf.addCommonTableExpression("KEY_A", "cte1", new SQLFragment("SELECT * FROM a"));
            sqlf.append("SELECT * FROM ").append(token).append(" _1");

            assertEquals(
                    "WITH\n" +
                    "\tcte1 AS (SELECT * FROM a)\n" +
                    "SELECT * FROM cte1 _1",
                sqlf.getSQL());

            SQLFragment sqlf2 = new SQLFragment();
            String token2 = sqlf2.addCommonTableExpression("KEY_A", "cte2", new SQLFragment("SELECT * FROM a"));
            sqlf2.append("SELECT * FROM ").append(token2).append(" _2");
            assertEquals(
                    "WITH\n" +
                            "\tcte2 AS (SELECT * FROM a)\n" +
                            "SELECT * FROM cte2 _2",
                    sqlf2.getSQL());

            SQLFragment sqlf3 = new SQLFragment();
            String token3 = sqlf3.addCommonTableExpression("KEY_B", "cte3", new SQLFragment("SELECT * FROM b"));
            sqlf3.append("SELECT * FROM ").append(token3).append(" _3");
            assertEquals(
                    "WITH\n" +
                            "\tcte3 AS (SELECT * FROM b)\n" +
                            "SELECT * FROM cte3 _3",
                    sqlf3.getSQL());

            SQLFragment union = new SQLFragment();
            union.append(sqlf);
            union.append("\nUNION\n");
            union.append(sqlf2);
            union.append("\nUNION\n");
            union.append(sqlf3);
            assertEquals(
                "WITH\n" +
                    "\tcte1 AS (SELECT * FROM a),\n" +
                    "\tcte3 AS (SELECT * FROM b)\n" +
                    "SELECT * FROM cte1 _1\n" +
                    "UNION\n" +
                    "SELECT * FROM cte1 _2\n" +
                    "UNION\n" +
                    "SELECT * FROM cte3 _3",
                union.getSQL());
        }
    }

    @Override
    public boolean equals(Object obj)
    {
        if (!(obj instanceof SQLFragment))
        {
            return false;
        }
        SQLFragment other = (SQLFragment)obj;
        return getSQL().equals(other.getSQL()) && getParams().equals(other.getParams());
    }
}
