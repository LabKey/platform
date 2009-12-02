/*
 * Copyright (c) 2006-2009 LabKey Corporation
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

import org.labkey.api.util.HString;
import org.labkey.api.util.DateUtil;

import java.util.*;

/**
 * User: Matthew
 * Date: Apr 19, 2006
 * Time: 4:56:01 PM
 */
public class SQLFragment
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
        this.params = params;
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
        return null == sb || sb.length() == 0;
    }


    public String getSQL()
    {
        return null != sb ? sb.toString() : null != sql ? sql : "";
    }

    /** Drop the JDBC parameters into the SQL string so that it can be executed directly in a database tool */
    public String toString()
    {
        String sql = getSQL();
        if (null != params && !params.isEmpty())
        {
            // Question marks in comments and string literals aren't supported right now - so we only do substitution
            // if the number of parameter markers equals the number of parameters

            // Pad with spaces to prevent question marks from being the first or last entries, which would confuse
            // our split() count
            String[] pieces = (" " + sql + " ").split("\\?");
            if (pieces.length == params.size() + 1)
            {
                StringBuilder result = new StringBuilder(pieces[0]);
                for (int i = 0; i < params.size(); i++)
                {
                    Object o = params.get(i);
                    Parameter param;
                    // Create a Parameter if we don't already have one
                    if (o instanceof Parameter)
                    {
                        param = (Parameter)o;
                    }
                    else
                    {
                        param = new Parameter(o);
                    }
                    Object value = param.getValueToBind();
                    if (value == null)
                    {
                        result.append("NULL");
                    }
                    else if (value instanceof String)
                    {
                        result.append("'");
                        result.append(((String)value).replace("'", "''"));
                        result.append("'");
                    }
                    else if (value instanceof Date)
                    {
                        result.append("'");
                        result.append(DateUtil.formatDateTime((Date)value));
                        result.append("'");
                    }
                    else if (value instanceof Boolean)
                    {
                        result.append(CoreSchema.getInstance().getSqlDialect().getBooleanLiteral(((Boolean)value).booleanValue()));
                    }
                    else
                    {
                        result.append(value.toString());
                    }
                    result.append(pieces[i + 1]);
                }
                return result.toString();
            }
        }
        return sql;
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

    public SQLFragment append(CharSequence s)
    {
        guard(s);
        getStringBuilder().append(s);
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


    public SQLFragment append(TableInfo table)
    {
        String s = table.getSelectName();
        if (s != null)
            return append(s);

        append("(");
        append(table.getFromSQL());
        append(") ");
        append(table.getName());
        return this;
    }


    public SQLFragment append(TableInfo table, String alias)
    {
        String s = table.getSelectName();
        if (s != null)
            return append(s).append(" ").append(alias);

        append("(");
        append(table.getFromSQL());
        append(") ");
        append(alias);
        return this;
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

    protected CharSequence getSqlCharSequence()
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
}
