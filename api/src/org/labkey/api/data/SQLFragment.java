/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;
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

    // use ordered map to make sql generation more deterministic (see collectCommonTableExpressions())
    private LinkedHashMap<Object,CTE> commonTableExpressionsMap = null;

    private class CTE implements Cloneable
    {
        CTE(@NotNull String name)
        {
            this.preferredName = name;
            tokens.add("/*$*/" + GUID.makeGUID() + ":" + name + "/*$*/");
        }

        CTE(@NotNull String name, SQLFragment sqlf, boolean recursive)
        {
            this(name);
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
        boolean recursive = false;        // NOTE this is dialect dependant (getSql() does not take a dialect)
        final Set<String> tokens = new TreeSet<>();
        SQLFragment sqlf = null;
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


    @Override
    public boolean isEmpty()
    {
        return (null == sb || sb.length() == 0) && (sql == null || sql.length() == 0);
    }


    /* same as getSQL() but without CTE handling */
    public String getRawSQL()
    {
        return null != sb ? sb.toString() : null != sql ? sql : "";
    }

    /* useful for wrapping existing SQL, for instance adding a cast
     * Obviously parameter number and order must remain unchanged
     */
    public void setRawSQL(String sql)
    {
        sb = new StringBuilder(sql);
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


    private List<SQLFragment.CTE> collectCommonTableExpressions()
    {
        List<SQLFragment.CTE> list = new ArrayList<>();
        _collectCommonTableExpressions(list);
        return list;
    }

    private void _collectCommonTableExpressions(List<SQLFragment.CTE> list)
    {
        if (null != commonTableExpressionsMap)
        {
            commonTableExpressionsMap.values().forEach(cte -> cte.sqlf._collectCommonTableExpressions(list));
            list.addAll(commonTableExpressionsMap.values());
        }
    }


    public String getSQL()
    {
        if (null == commonTableExpressionsMap || commonTableExpressionsMap.isEmpty())
            return null != sb ? sb.toString() : null != sql ? sql : "";

        List<SQLFragment.CTE> commonTableExpressions = collectCommonTableExpressions();

        boolean recursive = commonTableExpressions.stream()
                .anyMatch(cte -> cte.recursive);
        StringBuilder ret = new StringBuilder("WITH" + (recursive ? " RECURSIVE" : ""));

        // generate final aliases for each CTE */
        AliasManager am = new AliasManager((SqlDialect)null);
        List<Pair<String,CTE>> ctes = commonTableExpressions.stream()
                .map(cte -> new Pair<>(am.decideAlias(cte.preferredName),cte))
                .collect(Collectors.toList());

        String comma = "\n/*CTE*/\n\t";
        for (Pair<String,CTE> p : ctes)
        {
            String alias = p.first;
            CTE cte = p.second;
            SQLFragment expr = cte.sqlf;
            String sql = expr._getOwnSql(alias, ctes);
            ret.append(comma).append(alias).append(" AS (").append(sql).append(")");
            comma = "\n,/*CTE*/\n\t";
        }
        ret.append("\n");

        String select = _getOwnSql( null, ctes );
        ret.append(replaceCteTokens(null, select, ctes));
        return ret.toString();
    }


    private String _getOwnSql(String alias, List<Pair<String,CTE>> ctes)
    {
        String ownSql = null != sb ? sb.toString() : null != this.sql ? this.sql : "";
        return replaceCteTokens(alias, ownSql, ctes);
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
        var ctes = collectCommonTableExpressions();
        List<Object> ret = new ArrayList<>();

        for (var cte : ctes)
            ret.addAll(cte.sqlf.getParamsNoCTEs());
        ret.addAll(getParamsNoCTEs());
        return Collections.unmodifiableList(ret);
    }


    public List<Pair<SQLFragment, Integer>> getParamsWithFragments()
    {
        var ctes = collectCommonTableExpressions();
        List<Pair<SQLFragment, Integer>> ret = new ArrayList<>();

        for (CTE cte : ctes)
        {
            if (null != cte.sqlf && null != cte.sqlf.params)
            {
                for (int i = 0; i < cte.sqlf.params.size(); i++)
                {
                    ret.add(new Pair<>(cte.sqlf, i));
                }
            }
        }

        if (null != params)
        {
            for (int i = 0; i < params.size(); i++)
            {
                ret.add(new Pair<>(this, i));
            }
        }
        return ret;
    }

    private final static Object[] EMPTY_ARRAY = new Object[0];

    public Object[] getParamsArray()
    {
        return null == params ? EMPTY_ARRAY : params.toArray();
    }

    public List<Object> getParamsNoCTEs()
    {
        return params == null ? Collections.emptyList() : Collections.unmodifiableList(params);
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
    public SQLFragment append(CharSequence csq, int start, int end)
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
    @Override
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

    public SQLFragment appendInClause(@NotNull Collection<?> params, SqlDialect dialect)
    {
        dialect.appendInClauseSql(this, params);
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
        if (null != sql.params)
            getMutableParams().addAll(0, sql.params);
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
        List<Object> params = getParams();
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

    public SQLFragment appendIf(boolean test, CharSequence s)
    {
        if (test)
            this.append(s);
        return this;
    }

    public SQLFragment applyIf(boolean test, Consumer<SQLFragment> block)
    {
        if (test)
            block.accept(this);
        return this;
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
        commonTableExpressionsMap.put(key, cte);
        return cte.token();
    }

    public String createCommonTableExpressionToken(Object key, String proposedName)
    {
        if (null == commonTableExpressionsMap)
            commonTableExpressionsMap = new LinkedHashMap<>();
        CTE prev = commonTableExpressionsMap.get(key);
        if (null != prev)
            throw new IllegalStateException("Cannot create CTE token from already used key.");
        CTE cte = new CTE(proposedName);
        commonTableExpressionsMap.put(key, cte);
        return cte.token();
    }

    public void setCommonTableExpressionSql(Object key, SQLFragment sqlf, boolean recursive)
    {
        if (null == commonTableExpressionsMap)
            commonTableExpressionsMap = new LinkedHashMap<>();

        if (null != sqlf.commonTableExpressionsMap && !sqlf.commonTableExpressionsMap.isEmpty())
        {
            // Need to merge CTEs up; this.cte depends on newSql.ctes, so they need to come first
            SQLFragment newSql = new SQLFragment(sqlf);
            LinkedHashMap<Object, CTE> toMap = new LinkedHashMap<>(newSql.commonTableExpressionsMap);
            for (Map.Entry<Object, CTE> e : commonTableExpressionsMap.entrySet())
            {
                CTE from = e.getValue();
                CTE to = toMap.get(e.getKey());
                if (null != to)
                    to.tokens.addAll(from.tokens);
                else
                    toMap.put(e.getKey(), from.copy(false));
            }

            commonTableExpressionsMap = toMap;
            newSql.commonTableExpressionsMap = null;
            sqlf = newSql;
        }

        CTE cte = commonTableExpressionsMap.get(key);
        if (null == cte)
            throw new IllegalStateException("CTE not found.");
        cte.sqlf = sqlf;
        cte.recursive = recursive;
    }

    @Nullable
    public String getCommonTableExpressionToken(Object key)
    {
        if (null == commonTableExpressionsMap)
            commonTableExpressionsMap = new LinkedHashMap<>();
        CTE cte = commonTableExpressionsMap.get(key);
        return null != cte ? cte.token() : null;
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

            sb.append("\t".repeat(Math.max(0, indent)));

            sb.append(line);
            sb.append('\n');

            if (t.startsWith("-- <") && !t.startsWith("-- </"))
                indent++;
        }

        sqlf.sb = sb;
        sqlf.sql = null;
        return sqlf;
    }


    public static class UnitTestCase extends Assert
    {
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

        @Test
        public void nested_cte()
        {
            // one-level cte using cteToken (CTE fragment 'a' does not contain a CTE)
            {
                SQLFragment a = new SQLFragment("SELECT 1 as i, 'one' as s, CAST(? AS VARCHAR) as p", "parameterONE");
                assertEquals("SELECT 1 as i, 'one' as s, CAST('parameterONE' AS VARCHAR) as p", a.toDebugString());
                SQLFragment b = new SQLFragment();
                String cteToken = b.addCommonTableExpression(new Object(), "CTE", a);
                b.append("SELECT * FROM ").append(cteToken).append(" WHERE p=?").add("parameterTWO");
                assertEquals("WITH\n/*CTE*/\n" +
                                "\tCTE AS (SELECT 1 as i, 'one' as s, CAST('parameterONE' AS VARCHAR) as p)\n" +
                                "SELECT * FROM CTE WHERE p='parameterTWO'",
                        b.toDebugString());
                assertEquals("parameterONE", b.getParams().get(0));
            }

            // two-level cte using cteTokens (CTE fragment 'b' contains a CTE of fragment a)
            {
                SQLFragment a = new SQLFragment("SELECT 1 as i, 'one' as s, CAST(? AS VARCHAR) as p", "parameterONE");
                assertEquals("SELECT 1 as i, 'one' as s, CAST('parameterONE' AS VARCHAR) as p", a.toDebugString());
                SQLFragment b = new SQLFragment();
                String cteTokenA = b.addCommonTableExpression(new Object(), "_A_", a);
                b.append("SELECT * FROM ").append(cteTokenA).append(" WHERE p=?").add("parameterTWO");
                SQLFragment c = new SQLFragment();
                String cteTokenB = c.addCommonTableExpression(new Object(), "_B_", b);
                c.append("SELECT * FROM ").append(cteTokenB).append(" WHERE i=?").add(3);
                assertEquals("WITH\n" +
                                "/*CTE*/\n" +
                                "\t_A_ AS (SELECT 1 as i, 'one' as s, CAST('parameterONE' AS VARCHAR) as p)\n" +
                                ",/*CTE*/\n" +
                                "\t_B_ AS (SELECT * FROM _A_ WHERE p='parameterTWO')\n" +
                                "SELECT * FROM _B_ WHERE i=3",
                        c.toDebugString());
                List<Object> params = c.getParams();
                assertEquals(3, params.size());
                assertEquals("parameterONE", params.get(0));
                assertEquals("parameterTWO", params.get(1));
                assertEquals(3, params.get(2));
            }

            // Same as previous but top-level query has both a nested and non-nested CTE
            {
                SQLFragment a = new SQLFragment("SELECT 1 as i, 'Aone' as s, CAST(? AS VARCHAR) as p", "parameterAone");
                SQLFragment a2 = new SQLFragment("SELECT 2 as i, 'Atwo' as s, CAST(? AS VARCHAR) as p", "parameterAtwo");
                SQLFragment b = new SQLFragment();
                String cteTokenA = b.addCommonTableExpression(new Object(), "_A_", a);
                b.append("SELECT * FROM ").append(cteTokenA).append(" WHERE p=?").add("parameterB");
                SQLFragment c = new SQLFragment();
                String cteTokenB  = c.addCommonTableExpression(new Object(), "_B_", b);
                String cteTokenA2 = c.addCommonTableExpression(new Object(), "_A2_", a2);
                c.append("SELECT *, ? as xyz FROM ").add(4).append(cteTokenB).append("B,").append(cteTokenA2).append("A WHERE B.i=A.i");
                assertEquals("WITH\n" +
                                "/*CTE*/\n" +
                                "\t_A_ AS (SELECT 1 as i, 'Aone' as s, CAST('parameterAone' AS VARCHAR) as p)\n" +
                                ",/*CTE*/\n" +
                                "\t_B_ AS (SELECT * FROM _A_ WHERE p='parameterB')\n" +
                                ",/*CTE*/\n" +
                                "\t_A2_ AS (SELECT 2 as i, 'Atwo' as s, CAST('parameterAtwo' AS VARCHAR) as p)\n" +
                                "SELECT *, 4 as xyz FROM _B_B,_A2_A WHERE B.i=A.i",
                        c.toDebugString());
                List<Object> params = c.getParams();
                assertEquals(4, params.size());
                assertEquals("parameterAone", params.get(0));
                assertEquals("parameterB", params.get(1));
                assertEquals("parameterAtwo", params.get(2));
                assertEquals(4, params.get(3));
            }

            // Same as previous but two of the CTEs are the same and should be collapsed (e.g. imagine a container filter implemented with a CTE)
            // TODO, we only collapse CTEs that are siblings
            {
                SQLFragment cf = new SQLFragment("SELECT 1 as i, 'Aone' as s, CAST(? AS VARCHAR) as p", "parameterAone");
                SQLFragment b = new SQLFragment();
                String cteTokenA = b.addCommonTableExpression("CTE_KEY_CF", "_A_", cf);
                b.append("SELECT * FROM ").append(cteTokenA).append(" WHERE p=?").add("parameterB");
                SQLFragment c = new SQLFragment();
                String cteTokenB  = c.addCommonTableExpression(new Object(), "_B_", b);
                String cteTokenA2 = c.addCommonTableExpression("CTE_KEY_CF", "_A2_", cf);
                c.append("SELECT *, ? as xyz FROM ").add(4).append(cteTokenB).append("B,").append(cteTokenA2).append("A WHERE B.i=A.i");
                assertEquals("WITH\n" +
                                "/*CTE*/\n" +
                                "\t_A_ AS (SELECT 1 as i, 'Aone' as s, CAST('parameterAone' AS VARCHAR) as p)\n" +
                                ",/*CTE*/\n" +
                                "\t_B_ AS (SELECT * FROM _A_ WHERE p='parameterB')\n" +
                                ",/*CTE*/\n" +
                                "\t_A2_ AS (SELECT 1 as i, 'Aone' as s, CAST('parameterAone' AS VARCHAR) as p)\n" +
                                "SELECT *, 4 as xyz FROM _B_B,_A2_A WHERE B.i=A.i",
                        c.toDebugString());
                List<Object> params = c.getParams();
                assertEquals(4, params.size());
                assertEquals("parameterAone", params.get(0));
                assertEquals("parameterB", params.get(1));
                assertEquals("parameterAone", params.get(2));
                assertEquals(4, params.get(3));
            }
        }
    }


    public static class IntegrationTestCase extends Assert
    {
        @Test
        public void test()
        {
            // try some Dialect stuff and CTE executed against core schema
        }
    }


    @Override
    public boolean equals(Object obj)
    {
        if (!(obj instanceof SQLFragment other))
        {
            return false;
        }
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
