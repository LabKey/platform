/*
 * Copyright (c) 2015-2026 LabKey Corporation
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
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.dialect.SqlDialect;
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
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Generator for very long in-clauses
 */
public class TempTableInClauseGenerator implements InClauseGenerator
{
    private static final Cache<String, TempTableInfo> _tempTableCache =
            CacheManager.getCache(String.class, 200, CacheManager.UNLIMITED, CacheManager.MINUTE * 5, "IN clause temp tables");

    // Need to set a supplier instead of setting the default temp schema directly because this class is constructed at
    // dialect init time, before schemas can be referenced.
    private final Supplier<DbSchema> _tempSchemaSupplier;

    // By default, use the primary database temp schema
    public TempTableInClauseGenerator()
    {
        this(DbSchema::getTemp);
    }

    // Use in cases where the default temp schema won't do, e.g., you need to apply a large IN clause in an external data source
    public TempTableInClauseGenerator(Supplier<DbSchema> tempSchemaSupplier)
    {
        _tempSchemaSupplier = tempSchemaSupplier;
    }

    /**
     * @param sql    fragment to append to
     * @param params list of values
     * @return null if can't use TempTableInClauseGenerator, therefore requires a fallback generator
     */
    @Override
    public SQLFragment appendInClauseSql(SQLFragment sql, @NotNull Collection<?> params)
    {
        params = unwrap(params);
        JdbcType jdbcType = jdbcTypeFor(params);
        if (null == jdbcType)
            return null;
        TempTableInfo tempTableInfo = getOrCreateTempTable(params, jdbcType);
        if (null == tempTableInfo)
            return null;
        sql.unsafeAppend(" IN (SELECT Id FROM ").append(tempTableInfo).unsafeAppend(")");
        sql.addTempToken(tempTableInfo);
        return sql;
    }

    @Override
    public @Nullable TempTableInfo getTempTableInfo(@NotNull Collection<?> params)
    {
        params = unwrap(params);
        JdbcType jdbcType = jdbcTypeFor(params);
        return null == jdbcType ? null : getOrCreateTempTable(params, jdbcType);
    }

    // Container, User, GUID, etc. bind as their ids, so the temp table must hold those ids
    private static @NotNull Collection<?> unwrap(@NotNull Collection<?> params)
    {
        if (params.stream().noneMatch(p -> p instanceof Parameter.JdbcParameterValue))
            return params;
        return params.stream().map(p -> p instanceof Parameter.JdbcParameterValue v ? v.getJdbcParameterValue() : p).toList();
    }

    private static @Nullable JdbcType jdbcTypeFor(@NotNull Collection<?> params)
    {
        if (params.isEmpty())
            return null;
        Object first = params.iterator().next();
        if (first instanceof Long)
            return JdbcType.BIGINT;
        else if (first instanceof Integer)
            return JdbcType.INTEGER;
        else if (first instanceof String)
            return JdbcType.VARCHAR;
        return null;
    }

    private @Nullable TempTableInfo getOrCreateTempTable(final @NotNull Collection<?> paramsCollection, JdbcType jdbcType)
    {
        List<?> sortedParameters = null;
        if (jdbcType == JdbcType.INTEGER)
        {
            sortedParameters = collectIntegers(paramsCollection);
        }
        else if (jdbcType == JdbcType.BIGINT)
        {
            sortedParameters = collectLongs(paramsCollection);
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

        DbSchema tempSchema = _tempSchemaSupplier.get();
        // UnloggedTableGeneration only detects resets in the LabKey database
        boolean unlogged = tempSchema.getScope().isLabKeyScope();
        // Scope by database and schema, so an external temp schema never reuses a LabKey table or vice versa
        String cacheKey = tempSchema.getScope().getDataSourceName() + "_" + tempSchema.getName() + "_" + getCacheKey(jdbcType, sortedParameters);
        // A reset empties the table, so a new generation must not hit the old entry
        if (unlogged)
            cacheKey += "_" + UnloggedTableGeneration.current();
        TempTableInfo tempTableInfo = _tempTableCache.get(cacheKey);
        if (tempTableInfo == null)
        {
            SqlDialect dialect = tempSchema.getSqlDialect();
            tempTableInfo = new TempTableInfo(tempSchema, "InClause", Collections.singletonList(new BaseColumnInfo("Id", jdbcType, 0, false)), null);
            SQLFragment sqlCreate = SQLFragment.unsafe(unlogged ? dialect.getCreateUnloggedTableCommand() + " " : "CREATE TABLE ");
            sqlCreate.append(tempTableInfo)
                .unsafeAppend("\n(Id ")
                .append(dialect.getSqlTypeNameAsSql(jdbcType))
                .unsafeAppend(jdbcType == JdbcType.VARCHAR ? "(450)" : "")
                .unsafeAppend(")");

            // When the in clause receives more parameters than it is set to handle, a temporary table is created to handle the overflow.
            // While the associated mutating operations are necessary, they are not a viable CSRF attack vector.
            try (var ignored = SpringActionController.ignoreSqlUpdates())
            {
                new SqlExecutor(tempSchema).execute(sqlCreate);
            }
            tempTableInfo.track();
            String tableName = tempTableInfo.getSelectName();
            String sql1 = "INSERT INTO " + tableName + " (Id) VALUES (?)";
            String sql100 = "INSERT INTO " + tableName + " (Id) VALUES (?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?)";
            if (dialect.supportsArrays())
            {
                // One array-bound statement is several times faster than batched single-row INSERTs
                SQLFragment sqlInsert = SQLFragment.unsafe("INSERT INTO " + tableName + " (Id) ")
                    .append(dialect.array_unnest(jdbcType, sortedParameters));
                try (var ignored = SpringActionController.ignoreSqlUpdates())
                {
                    new SqlExecutor(tempSchema).execute(sqlInsert);
                }
            }
            else try
            {
                try (var ignored = SpringActionController.ignoreSqlUpdates())
                {
                    if (jdbcType == JdbcType.VARCHAR)
                        Table.batchExecute1String(tempSchema, sql1, (ArrayList<String>) sortedParameters);
                    else if (jdbcType == JdbcType.INTEGER)
                        Table.batchExecute1Integer(tempSchema, sql1, sql100, (ArrayList<Integer>) sortedParameters);
                    else
                        Table.batchExecute1Long(tempSchema, sql1, sql100, (ArrayList<Long>) sortedParameters);
                }
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }

            // UNIQUE tells the planner the ids are distinct, so it skips de-duplicating them in IN (SELECT ...)
            String indexSql = "CREATE UNIQUE INDEX IX_Id" + new GUID().toStringNoDashes() + " ON " + tableName + "(Id)";
            try (var ignored = SpringActionController.ignoreSqlUpdates())
            {
                new SqlExecutor(tempSchema).execute(indexSql);
                dialect.updateStatistics(tempTableInfo); // Immediately analyze the newly populated & indexed table
            }

            TempTableInfo cacheEntry = tempTableInfo;

            // Don't bother caching if we're in a transaction
            // a) The table won't be visible to other connections until we commit
            // b) It is more likely that this temptable is only used once anyway (e.g. used by a data iterator)
            if (!tempSchema.getScope().isTransactionActive())
                _tempTableCache.put(cacheKey, cacheEntry);
        }

        return tempTableInfo;
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


    // unique and ordered list
    private List<Long> collectLongs(@NotNull Collection<?> paramsCollection)
    {
        boolean hasNull = false;
        Set<Long> ts = new TreeSet<>();
        for (Object i : paramsCollection)
        {
            if (null == i)
                hasNull = true;
            else if (i instanceof Long)
                ts.add((Long)i);
            else if (i instanceof Integer)
                ts.add(Long.valueOf(((Integer) i).longValue()));
            else
                return null;
        }
        List<Long> params = new ArrayList<>(ts);
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

        private final DbScope _scope = CoreSchema.getInstance().getSchema().getScope();

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

        @Test
        public void testTypesWithNull()
        {
            record Case(String values, List<?> params) {}
            for (Case c : List.of(
                new Case("SELECT 'a' UNION SELECT 'b' UNION SELECT 'g'", Arrays.asList("d", "a", null, "b", "a")),
                new Case("SELECT 1 UNION SELECT 2 UNION SELECT 7", Arrays.asList(4, 1, null, 2, 1)),
                new Case("SELECT CAST(1 AS BIGINT) UNION SELECT 2 UNION SELECT 7", Arrays.asList(4L, 1L, null, 2L, 1L))))
            {
                SQLFragment sql = SQLFragment.unsafe("SELECT a FROM (" + c.values() + ") b(a) WHERE a ");
                SQLFragment inClause = new TempTableInClauseGenerator().appendInClauseSql(sql, c.params());
                Assert.assertNotNull(c.values(), inClause);
                Assert.assertEquals(c.values(), 2, new SqlSelector(_scope, inClause).getRowCount());
            }
        }

        @Test
        public void testUnlogged()
        {
            Assume.assumeTrue(_scope.getSqlDialect().isPostgreSQL());
            TempTableInfo tti = new TempTableInClauseGenerator().getTempTableInfo(STRINGS);
            Assert.assertNotNull(tti);
            SQLFragment sql = SQLFragment.unsafe("SELECT relpersistence FROM pg_class WHERE oid = CAST(? AS regclass)").add(tti.getSelectName());
            Assert.assertEquals("u", new SqlSelector(_scope, sql).getObject(String.class));
        }

        @Test
        public void testUnloggedReset()
        {
            Assume.assumeTrue(_scope.getSqlDialect().isPostgreSQL());
            SQLFragment sql = SQLFragment.unsafe("SELECT a from (SELECT 'a' AS a UNION SELECT 'b' AS a UNION SELECT 'g' AS a) b WHERE a ");
            TempTableInfo before = new TempTableInClauseGenerator().getTempTableInfo(STRINGS);
            Assert.assertNotNull(before);
            new SqlExecutor(_scope).execute(SQLFragment.unsafe("TRUNCATE ").append(before));
            UnloggedTableGeneration.simulateResetForTest();
            SQLFragment after = new TempTableInClauseGenerator().appendInClauseSql(new SQLFragment(sql), STRINGS);
            Assert.assertEquals("A reset must rebuild the emptied temp table", 2, new SqlSelector(_scope, after).getRowCount());
        }

        @Test
        public void testJdbcParameterValues()
        {
            List<GUID> guids = Stream.generate(GUID::new).limit(4).toList();
            SQLFragment sql = new SQLFragment("SELECT a FROM (SELECT ").appendValue(guids.get(0)).append(" AS a UNION SELECT ").appendValue(guids.get(1)).append(" AS a UNION SELECT 'g' AS a) b WHERE a ");
            SQLFragment inClause = new TempTableInClauseGenerator().appendInClauseSql(new SQLFragment(sql), guids);
            Assert.assertNotNull("GUIDs should use a temp table", inClause);
            Assert.assertEquals("Validate GUID IN clause", 2, new SqlSelector(_scope, inClause).getRowCount());
        }
    }
}
