/*
 * Copyright (c) 2006-2013 LabKey Corporation
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
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.util.HString;
import org.labkey.api.util.JdbcUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

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

    public SQLFragment()
    {
        sql = "";
    }


    /**
     *
     * @param sql
     * @param params list may be modified so clone() before passing in if necessary
     */
    public SQLFragment(CharSequence sql, List<Object> params)      // TODO: Should be List<?>
    {
        guard(sql);
        this.sql = sql.toString();
        this.params = new ArrayList<Object>(params);
    }


    public SQLFragment(CharSequence sql, Object... params)
    {
        guard(sql);
        this.sql = sql.toString();
        this.params = Arrays.asList(params);
    }


    public SQLFragment(SQLFragment other)
    {
        this(other.getSQL(), other.getParams());
    }


    public boolean isEmpty()
    {
        return (null == sb || sb.length() == 0) && (sql == null || sql.length() == 0);
    }


    public String getSQL()
    {
        return null != sb ? sb.toString() : null != sql ? sql : "";
    }

    public String toString()
    {
        return JdbcUtil.format(this);
    }


    public List<Object> getParams()
    {
        return params == null ? Collections.emptyList() : params;
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
            ArrayList<Object> t = new ArrayList<Object>();
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

    public SQLFragment append(SQLFragment f)
    {
        if (null != f.sb)
            append(f.sb);
        else
            append(f.sql);
        addAll(f.getParams());
        return this;
    }


    // Append a full statement (using the correct dialect syntax) and its parameters to this SQLFragment
    public SQLFragment appendStatement(@Nullable SQLFragment statement, SqlDialect dialect)
    {
        if (null == statement || statement.isEmpty())
            return this;
        if (null != statement.sb)
            dialect.appendStatement(this, statement.sb.toString());
        else
            dialect.appendStatement(this, statement.sql.toString());
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
}
