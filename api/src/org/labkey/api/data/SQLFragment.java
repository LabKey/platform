/*
 * Copyright (c) 2006-2017 LabKey Corporation
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.query.AliasManager;
import org.labkey.api.util.GUID;
import org.labkey.api.util.JdbcUtil;
import org.labkey.api.util.Pair;

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
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Holds both the SQL text and JDBC parameter values to use during invocation.
 * User: Matthew
 * Date: Apr 19, 2006
 */
public class SQLFragment implements Appendable, CharSequence
{
    private String sql;
    private StringBuilder sb = null;
    private List<Object> params;          // TODO: Should be List<?>

    private final List<Object> tempTokens = new ArrayList<>();      // Hold refs to ensure they're not GC'd

    private Map<Object,CTE> commonTableExpressionsMap = null;

    private class CTE implements Cloneable
    {
        CTE(@NotNull String name, SQLFragment sqlf, boolean recursive)
        {
            this.preferredName = name;
            tokens.add("/*$*/" + GUID.makeGUID() + ":" + name + "/*$*/");
            this.sqlf = sqlf;
            this.recursive = recursive;
        }

        CTE(CTE from)
        {
            this.preferredName = from.preferredName;
            this.tokens.addAll(from.tokens);
            this.sqlf = from.sqlf;
            this.recursive = from.recursive;
        }

        public CTE copy(boolean deep)
        {
            CTE copy = new CTE(this);
            if (deep)
                copy.sqlf = new SQLFragment().append(copy.sqlf);
            return copy;
        }

        private String token()
        {
            return  tokens.iterator().next();
        }

        final String preferredName;
        final boolean recursive;        // NOTE this is dialect dependant (getSql() does not take a dialect)
        final Set<String> tokens = new TreeSet<>();
        SQLFragment sqlf;
    }

    public SQLFragment()
    {
        sql = "";
    }

    public SQLFragment(CharSequence sql, @Nullable List<?> params)
    {
        this.sql = sql.toString();
        if (null != params)
            this.params = new ArrayList<>(params);
    }


    public SQLFragment(CharSequence sql, Object... params)
    {
        this(sql, Arrays.asList(params));
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
                CTE cte = e.getValue().copy(deep);
                this.commonTableExpressionsMap.put(e.getKey(),cte);
            }
        }
        this.tempTokens.addAll(other.tempTokens);
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


    private String replaceCteTokens(String self, String select, List<Pair<String,CTE>> ctes)
    {
        for (Pair<String,CTE> pair : ctes)
        {
            String alias = pair.first;
            CTE cte = pair.second;
            for (String token : cte.tokens)
            {
                select = StringUtils.replace(select, token, alias);
            }
        }
        if (null != self)
            select = StringUtils.replace(select, "$SELF$", self);
        return select;
    }


    public String getSQL()
    {
        if (null == commonTableExpressionsMap || commonTableExpressionsMap.isEmpty())
            return null != sb ? sb.toString() : null != sql ? sql : "";

        boolean recursive = commonTableExpressionsMap.values().stream()
                .anyMatch(cte -> cte.recursive);
        StringBuilder ret = new StringBuilder("WITH" + (recursive ? " RECURSIVE" : ""));

        // generate final aliases for each CTE */
        AliasManager am = new AliasManager(null);
        List<Pair<String,CTE>> ctes = commonTableExpressionsMap.values().stream()
                .map(cte -> new Pair<>(am.decideAlias(cte.preferredName),cte))
                .collect(Collectors.toList());

        String comma = "\n/*CTE*/\n\t";
        for (Pair<String,CTE> p : ctes)
        {
            String alias = p.first;
            CTE cte = p.second;
            SQLFragment expr = cte.sqlf;
            if (null != expr.commonTableExpressionsMap && !expr.commonTableExpressionsMap.isEmpty())
                throw new IllegalStateException("nested common table expressions are not supported");
            String sql = replaceCteTokens(alias, expr.getSQL(), ctes);
            ret.append(comma).append(alias).append(" AS (").append(sql).append(")");
            comma = "\n,/*CTE*/\n\t";
        }
        ret.append("\n");

        String select = null != sb ? sb.toString() : null != this.sql ? this.sql : "";
        ret.append(replaceCteTokens(null, select, ctes));
        return ret.toString();
    }


    static Pattern markerPattern = Pattern.compile("/\\*\\$\\*/.*/\\*\\$\\*/");

    /* This is not an exhaustive .equals() test, but it give pretty good confidence that these statements are the same */
    static boolean debugCompareSQL(SQLFragment sql1, SQLFragment sql2)
    {
        String select1 = sql1.getRawSQL();
        String select2 = sql2.getRawSQL();

        if ((null == sql1.commonTableExpressionsMap || sql1.commonTableExpressionsMap.isEmpty()) &&
            (null == sql2.commonTableExpressionsMap || sql2.commonTableExpressionsMap.isEmpty()))
            return select1.equals(select2);

        select1 = markerPattern.matcher(select1).replaceAll("CTE");
        select2 = markerPattern.matcher(select2).replaceAll("CTE");
        if (!select1.equals(select2))
            return false;

        Set<String> ctes1 = sql1.commonTableExpressionsMap.values().stream()
                .map(cte -> markerPattern.matcher(cte.sqlf.getRawSQL()).replaceAll("CTE"))
                .collect(Collectors.toSet());
        Set<String> ctes2 = sql2.commonTableExpressionsMap.values().stream()
                .map(cte -> markerPattern.matcher(cte.sqlf.getRawSQL()).replaceAll("CTE"))
                .collect(Collectors.toSet());
        return ctes1.equals(ctes2);
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
        return Collections.unmodifiableList(ret);
     }


    private final static Object[] EMPTY_ARRAY = new Object[0];

    public Object[] getParamsArray()
    {
        return null == params ? EMPTY_ARRAY : params.toArray();
    }


    private List<Object> getMutableParams()
    {
        if (!(params instanceof ArrayList))
        {
            List<Object> t = new ArrayList<>();
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
        getStringBuilder().append(s);
        return this;
    }


    @Override
    public SQLFragment append(CharSequence csq, int start, int end) throws IOException
    {
        append(csq.subSequence(start, end));
        return this;
    }

    /** Adds the container's ID as an in-line string constant to the SQL */
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

    /** Adds the object's toString() to the SQL */
    public SQLFragment append(Object o)
    {
        getStringBuilder().append(o);
        return this;
    }

    /** Adds the object as a JDBC parameter value */
    public SQLFragment add(Object p)
    {
        getMutableParams().add(p);
        return this;
    }


    /** Adds the objects as JDBC parameter values */
    public void addAll(Collection<?> l)
    {
        getMutableParams().addAll(l);
    }


    /** Adds the objects as JDBC parameter values */
    public void addAll(Object... values)
    {
        if (values == null)
            return;
        addAll(Arrays.asList(values));
    }


    /** Sets the parameter at the index to the object's value */
    public void set(int i, Object p)
    {
        getMutableParams().set(i,p);
    }

    /** Append both the SQL and the parameters from the other SQLFragment to this SQLFragment */
    public SQLFragment append(SQLFragment f)
    {
        if (null != f.sb)
            append(f.sb);
        else
            append(f.sql);
        if (null != f.params)
            addAll(f.params);
        mergeCommonTableExpressions(f);
        tempTokens.addAll(f.tempTokens);
        return this;
    }


    /** Append a full statement (using the correct dialect syntax) and its parameters to this SQLFragment */
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

    /** Add a table/query to the SQL with an alias, as used in a FROM clause */
    public SQLFragment append(TableInfo table, String alias)
    {
        return append(table.getFromSQL(alias));
    }

    /** Add to the SQL */
    public SQLFragment append(char ch)
    {
        getStringBuilder().append(ch);
        return this;
    }

    /** Add to the SQL as either an in-line string literal or as a JDBC parameter depending on whether it would need escaping */
    public SQLFragment appendStringLiteral(@NotNull CharSequence s)
    {
        if (StringUtils.contains(s,"'") || StringUtils.contains(s,"\\"))
        {
            append("?");
            add(s.toString());
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

    /** Insert into the SQL */
    public void insert(int index, String str)
    {
        getStringBuilder().insert(index, str);
    }

    /** Insert this SQLFragment's SQL and parameters at the start of the existing SQL and parameters */
    public void prepend(SQLFragment sql)
    {
        insert(0, sql.getSqlCharSequence().toString());
        getMutableParams().addAll(0, sql.getParams());
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
        return addCommonTableExpression(key, proposedName, sqlf, false);
    }

    public String addCommonTableExpression(Object key, String proposedName, SQLFragment sqlf, boolean recursive)
    {
        if (null == commonTableExpressionsMap)
            commonTableExpressionsMap = new LinkedHashMap<>();
        CTE prev = commonTableExpressionsMap.get(key);
        if (null != prev)
            return prev.token();
        CTE cte = new CTE(proposedName, sqlf, recursive);
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
                commonTableExpressionsMap.put(e.getKey(), from.copy(false));
        }
    }


    public void addTempToken(Object tempToken)
    {
        tempTokens.add(tempToken);
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
            assertEquals( "WITH\n/*CTE*/\n" +
                    "\tCTE AS (SELECT a FROM b WHERE x=?)\n" +
                    "SELECT * FROM CTE WHERE y=?", b.getSQL());
            assertEquals( "WITH\n/*CTE*/\n" +
                    "\tCTE AS (SELECT a FROM b WHERE x=5)\n" +
                    "SELECT * FROM CTE WHERE y='xxyzzy'", b.toDebugString());
            List<Object> params = b.getParams();
            assertEquals(2,params.size());
            assertEquals(5, params.get(0));
            assertEquals("xxyzzy", params.get(1));


            SQLFragment c = new SQLFragment(b);
            assertEquals( "WITH\n/*CTE*/\n" +
                    "\tCTE AS (SELECT a FROM b WHERE x=?)\n" +
                    "SELECT * FROM CTE WHERE y=?", c.getSQL());
            assertEquals( "WITH\n/*CTE*/\n" +
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
                    "WITH\n/*CTE*/\n" +
                    "\tcte1 AS (SELECT * FROM a)\n" +
                    "SELECT * FROM cte1 _1",
                sqlf.getSQL());

            SQLFragment sqlf2 = new SQLFragment();
            String token2 = sqlf2.addCommonTableExpression("KEY_A", "cte2", new SQLFragment("SELECT * FROM a"));
            sqlf2.append("SELECT * FROM ").append(token2).append(" _2");
            assertEquals(
                    "WITH\n/*CTE*/\n" +
                            "\tcte2 AS (SELECT * FROM a)\n" +
                            "SELECT * FROM cte2 _2",
                    sqlf2.getSQL());

            SQLFragment sqlf3 = new SQLFragment();
            String token3 = sqlf3.addCommonTableExpression("KEY_B", "cte3", new SQLFragment("SELECT * FROM b"));
            sqlf3.append("SELECT * FROM ").append(token3).append(" _3");
            assertEquals(
                    "WITH\n/*CTE*/\n" +
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
                "WITH\n/*CTE*/\n" +
                    "\tcte1 AS (SELECT * FROM a)\n" +
                    ",/*CTE*/\n" +
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

    /**
     * Joins the SQLFragments in the provided {@code Iterable} into a single SQLFragment. The SQL is joined by string
     * concatenation using the provided separator. The parameters are combined to form the new parameter list.
     *
     * @param fragments SQLFragments to join together
     * @param separator Separator to use on the SQL portion
     * @return A new SQLFragment that joins all the SQLFragments
     */
    public static SQLFragment join(Iterable<SQLFragment> fragments, String separator)
    {
        if (separator.contains("?"))
            throw new IllegalStateException("separator must not include a parameter marker");

        // Join all the SQL statements
        String sql = StreamSupport.stream(fragments.spliterator(), false)
            .map(SQLFragment::getSQL)
            .collect(Collectors.joining(separator));

        // Collect all the parameters to a single list
        List<?> params = StreamSupport.stream(fragments.spliterator(), false)
            .map(SQLFragment::getParams)
            .flatMap(Collection::stream)
            .collect(Collectors.toList());

        return new SQLFragment(sql, params);
    }
}
