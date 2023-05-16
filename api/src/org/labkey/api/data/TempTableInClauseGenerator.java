/*
 * Copyright (c) 2015-2019 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.util.GUID;
import org.labkey.api.util.HashHelpers;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Created by davebradlee on 6/5/15.
 *
 * Generator for very long in-clauses
 */
public class TempTableInClauseGenerator implements InClauseGenerator
{
    private static final Cache<String, TempTableInfo> _tempTableCache =
            CacheManager.getStringKeyCache(200, CacheManager.MINUTE * 5, "IN clause temp tables");

    /**
     * @param sql    fragment to append to
     * @param params list of values
     * @return null if can't use TempTableInClauseGenerator, therefore requires a fallback generator
     */
    @Override
    public SQLFragment appendInClauseSql(SQLFragment sql, final @NotNull Collection<?> params)
    {
        Object first = params.iterator().next();
        if (first instanceof Integer)
            return appendInClauseSql(sql, params, JdbcType.INTEGER);
        else if (first instanceof String)
            return appendInClauseSql(sql, params, JdbcType.VARCHAR);
        return null;
    }

    private SQLFragment appendInClauseSql(SQLFragment sql, final @NotNull Collection<?> paramsCollection, JdbcType jdbcType)
    {
        List<?> sortedParameters = null;
        if (jdbcType == JdbcType.INTEGER)
        {
            sortedParameters = collectIntegers(paramsCollection);
        }
        else if (jdbcType == JdbcType.VARCHAR)
        {
            // https://technet.microsoft.com/en-US/library/ms191241(v=SQL.105).aspx
            if (paramsCollection.stream().mapToInt(s -> null == s ? 0 : ((String) s).length()).max().orElse(0) >= 450)
                return null;
            sortedParameters = collectStrings(paramsCollection);
        }
        if (null == sortedParameters)
            return null;

        String cacheKey = getCacheKey(jdbcType, sortedParameters);
        TempTableInfo tempTableInfo = _tempTableCache.get(cacheKey);
        if (tempTableInfo == null)
        {
            tempTableInfo = new TempTableInfo("InClause", Collections.singletonList(new BaseColumnInfo("Id", jdbcType, 0, false)), null);
            SQLFragment sqlCreate = new SQLFragment("CREATE TABLE ");
            sqlCreate.append(tempTableInfo)
                    .append("\n(Id ")
                    .append(DbSchema.getTemp().getSqlDialect().getSqlTypeName(jdbcType))
                    .append(jdbcType == JdbcType.VARCHAR ? "(450)" : "")
                    .append(")");

            // When the in clause receives more parameters than it is set to handle, a temporary table is created to handle the overflow.
            // While the associated mutating operations are necessary, they are not a viable CSRF attack vector.
            try (var ignored = SpringActionController.ignoreSqlUpdates())
            {
                new SqlExecutor(DbSchema.getTemp()).execute(sqlCreate);
            }
            tempTableInfo.track();
            String tableName = tempTableInfo.getSelectName();
            String sql1 = "INSERT INTO " + tableName + " (Id) VALUES (?)";
            String sql100 = "INSERT INTO " + tableName + " (Id) VALUES (?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?)";
            try
            {
                try (var ignored = SpringActionController.ignoreSqlUpdates())
                {
                    if (jdbcType == JdbcType.VARCHAR)
                        Table.batchExecute1String(DbSchema.getTemp(), sql1, (ArrayList<String>) sortedParameters);
                    else
                        Table.batchExecute1Integer(DbSchema.getTemp(), sql1, sql100, (ArrayList<Integer>) sortedParameters);
                }
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }

            String indexSql = "CREATE INDEX IX_Id" + new GUID().toStringNoDashes() + " ON " + tableName + "(Id)";
            try (var ignored = SpringActionController.ignoreSqlUpdates())
            {
                new SqlExecutor(DbSchema.getTemp()).execute(indexSql);
            }
            TempTableInfo cacheEntry = tempTableInfo;

            // Don't bother caching if we're in a transaction
            // a) The table won't be visible to other connections until we commit
            // b) It is more likely that this temptable is only used once anyway (e.g. used by a data iterator)
            if (!DbSchema.getTemp().getScope().isTransactionActive())
                _tempTableCache.put(cacheKey, cacheEntry);
        }

        sql.append(" IN (SELECT Id FROM ").append(tempTableInfo).append(")");
        sql.addTempToken(tempTableInfo);
        return sql;
    }


    // unique and ordered list
    private List<String> collectStrings(@NotNull Collection<?> paramsCollection)
    {
        boolean hasNull = false;
        Set<String> ts = new TreeSet<>();
        for (Object s : paramsCollection)
        {
            if (null == s)
                hasNull = true;
            else if (!(s instanceof String))
                return null;
            else
                ts.add((String) s);
        }
        List<String> params = new ArrayList<>(ts);
        if (hasNull)
            params.add(null);
        return params;
    }

    // unique and ordered list
    private List<Integer> collectIntegers(@NotNull Collection<?> paramsCollection)
    {
        boolean hasNull = false;
        Set<Integer> ts = new TreeSet<>();
        for (Object i : paramsCollection)
        {
            if (null == i)
                hasNull = true;
            else if (!(i instanceof Integer))
                return null;
            else
                ts.add((Integer) i);
        }
        List<Integer> params = new ArrayList<>(ts);
        if (hasNull)
            params.add(null);
        return params;
    }

    private String getCacheKey(@NotNull JdbcType jdbcType, @NotNull Collection<?> params)
    {
        StringBuilder paramBuilder = new StringBuilder();
        for (Object param : params)
            paramBuilder.append(param);

        // Hash the param string to avoid very large cache keys filling up memory
        return jdbcType.name() + "_" + params.size() + "_" + HashHelpers.hash(paramBuilder.toString());
    }

    public static class TestCase
    {
        private static final List<Integer> INTEGERS = Arrays.asList(1, 2, 3, 4);
        private static final List<String> STRINGS = Arrays.asList("a", "b", "c", "d");

        private DbScope _scope = CoreSchema.getInstance().getSchema().getScope();

        @Before
        public void init()
        {
            _tempTableCache.clear();
        }

        @Test
        public void testString()
        {
            SQLFragment sql = new SQLFragment("SELECT a from (SELECT 'a' AS a UNION SELECT 'b' AS a UNION SELECT 'g' AS a) b WHERE a ");
            SQLFragment firstSQL = new TempTableInClauseGenerator().appendInClauseSql(new SQLFragment(sql), STRINGS);
            Assert.assertEquals("Validate first string IN clause", 2, new SqlSelector(_scope, firstSQL).getRowCount());
            SQLFragment secondSQL = new TempTableInClauseGenerator().appendInClauseSql(new SQLFragment(sql), STRINGS);
            Assert.assertEquals("Validate second string IN clause", 2, new SqlSelector(_scope, secondSQL).getRowCount());

            Assert.assertEquals("Validate SQL matches, indicated cached results", firstSQL, secondSQL);
        }
    }
}
