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

package org.labkey.api.data;

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
    Map<String,SQLFragment> commonTableExpressionsMap = null;


    public SQLFragment()
    {
        sql = "";
    }

    /**
     *
     * @param sql
     * @param params list may be modified so clone() before passing in if necessary
     */
    public SQLFragment(CharSequence sql, @Nullable List<Object> params)      // TODO: Should be List<?>
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
            for (Map.Entry<String,SQLFragment> e : other.commonTableExpressionsMap.entrySet())
            {
                SQLFragment cte = deep ? new SQLFragment(e.getValue()) : e.getValue();
                this.addCommonTableExpression(e.getKey(), cte, true);
            }
        }
    }


    public boolean isEmpty()
    {
        return (null == sb || sb.length() == 0) && (sql == null || sql.length() == 0);
    }


    public String getSQL()
    {
        if (null == commonTableExpressionsMap || commonTableExpressionsMap.isEmpty())
            return null != sb ? sb.toString() : null != sql ? sql : "";

        // TODO: support CTE that in turn have CTE
        StringBuilder ret = new StringBuilder();
        String comma = "WITH\n\t";
        for (Map.Entry<String,SQLFragment> e : commonTableExpressionsMap.entrySet())
        {
            String name = e.getKey();
            SQLFragment expr = e.getValue();
            if (null != expr.commonTableExpressionsMap && !expr.commonTableExpressionsMap.isEmpty())
                throw new IllegalStateException("nested common table expressions are not supported");
            ret.append(comma).append(name).append(" AS (").append(expr.getSQL()).append(")");
            comma = ",\n\t";
        }
        ret.append("\n");
        ret.append(null != sb ? sb.toString() : null != sql ? sql : "");
        return ret.toString();
    }


    // It is a little confusion ghat getString() does not return the same charsequence that this object purports to
    // represent.  However, this is a good "display value" for this object.
    // see getSqlCharSequence()
    public String toString()
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
            for (Map.Entry<String,SQLFragment> e : commonTableExpressionsMap.entrySet())
                if (null != e.getValue().params)
                    ret.addAll(e.getValue().params);
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


    public SQLFragment append(Object o)
    {
        guard(o);
        getStringBuilder().append(o);
        return this;
    }

    public void add(Object p)
    {
        getModfiableParams().add(p);
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
        addAll(f.getParams());
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
            sb.append("\n-- ").append(comment).append('\n');
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
        if (s instanceof HString || source.indexOf("'") >= 0 || source.indexOf("\\") >= 0)
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

    public void addCommonTableExpression(String name, SQLFragment sqlf, boolean replace)
    {
        if (null == commonTableExpressionsMap)
            commonTableExpressionsMap = new LinkedHashMap<>();
        SQLFragment prev = commonTableExpressionsMap.put(name, sqlf);
        if (!replace && null != prev)
        {
            if (!prev.getSQL().equals(sqlf.getSQL()))
                throw new IllegalStateException("Value of common table expression changed");
        }
    }

    private void mergeCommonTableExpressions(SQLFragment from)
    {
        if (null == from.commonTableExpressionsMap || from.commonTableExpressionsMap.isEmpty())
            return;
        for (Map.Entry<String,SQLFragment> e : from.commonTableExpressionsMap.entrySet())
            addCommonTableExpression(e.getKey(), e.getValue(), false);
    }


    public static class TestCase extends Assert
    {
        @Test
        public void tests()
        {
            SQLFragment a = new SQLFragment("SELECT a FROM b WHERE x=?", 5);
            assertEquals("SELECT a FROM b WHERE x=?", a.getSQL());
            assertEquals("SELECT a FROM b WHERE x=5", a.toString());

            SQLFragment b = new SQLFragment("SELECT * FROM CTE WHERE y=?","xxyzzy");
            b.addCommonTableExpression("CTE", a, false);
            assertEquals("WITH\n" +
                    "\tCTE AS (SELECT a FROM b WHERE x=?)\n" +
                    "SELECT * FROM CTE WHERE y=?", b.getSQL());
            assertEquals("WITH\n" +
                    "\tCTE AS (SELECT a FROM b WHERE x=5)\n" +
                    "SELECT * FROM CTE WHERE y='xxyzzy'", b.toString());
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
                    "SELECT * FROM CTE WHERE y='xxyzzy'", c.toString());
            params = c.getParams();
            assertEquals(2,params.size());
            assertEquals(5, params.get(0));
            assertEquals("xxyzzy", params.get(1));
        }
    }
}
