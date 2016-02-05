/*
 * Copyright (c) 2015-2016 LabKey Corporation
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
import org.labkey.api.cache.CacheManager;
import org.labkey.api.cache.StringKeyCache;
import org.labkey.api.util.GUID;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.TreeSet;

/**
 * Created by davebradlee on 6/5/15.
 *
 * Generator for very long in-clauses
 */
public class TempTableInClauseGenerator implements InClauseGenerator
{
    private static final StringKeyCache<TempTableInfo> _tempTableCache =
        CacheManager.getBlockingStringKeyCache(100, CacheManager.MINUTE * 5, "InClauseTempTableCache", null);

    /**
     * @param sql fragment to append to
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
        ArrayList<Object> sortedParameters = null;
        if (jdbcType == JdbcType.INTEGER)
        {
            sortedParameters = collectIntegers(paramsCollection);
        }
        else if (jdbcType == JdbcType.VARCHAR)
        {
            // https://technet.microsoft.com/en-US/library/ms191241(v=SQL.105).aspx
            if (paramsCollection.stream().mapToInt(s->null==s?0:((String)s).length()).max().orElse(0) >= 450)
                return null;
            sortedParameters = collectStrings(paramsCollection);
        }
        if (null == sortedParameters)
            return null;

        String cacheKey = getCacheKey(jdbcType, sortedParameters);
        TempTableInfo tempTableInfo = _tempTableCache.get(cacheKey, sortedParameters, (key, argument) ->
        {
            TempTableInfo tempTableInfo1 = new TempTableInfo("InClause", Collections.singletonList(new ColumnInfo("Id", jdbcType, 0, false)), null);
            String tableName = tempTableInfo1.getSelectName();
            SQLFragment sqlCreate = new SQLFragment("CREATE TABLE ");
            sqlCreate.append(tableName)
                    .append("\n(Id ")
                    .append(DbSchema.getTemp().getSqlDialect().sqlTypeNameFromSqlType(jdbcType.sqlType))
                    .append(jdbcType==JdbcType.VARCHAR?"(450)":"")
                    .append(");");

            new SqlExecutor(DbSchema.getTemp()).execute(sqlCreate);
            tempTableInfo1.track();

            String sql1 = "INSERT INTO " + tableName + " (Id) VALUES (?)";
            String sql100 = "INSERT INTO " + tableName + " (Id) VALUES (?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?),(?)";
            try
            {
                if (jdbcType == JdbcType.VARCHAR)
                    Table.batchExecute1String(DbSchema.getTemp(), sql1, (ArrayList<String>)argument);
                else
                    Table.batchExecute1Integer(DbSchema.getTemp(), sql1, sql100, (ArrayList<Integer>)argument);
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }

            String indexSql = "CREATE INDEX IX_Id" + new GUID().toStringNoDashes() + " ON " + tableName + "(Id)";
            new SqlExecutor(DbSchema.getTemp()).execute(indexSql);
            return tempTableInfo1;
        });

        sql.append(" IN (SELECT Id FROM ").append(tempTableInfo.getSelectName()).append(")");
        sql.addTempToken(tempTableInfo);
        return sql;
    }


    // unique and ordered list
    private  ArrayList<Object> collectStrings(@NotNull Collection<?> paramsCollection)
    {
        boolean hasNull = false;
        TreeSet<String> ts = new TreeSet<>();
        for (Object S : paramsCollection)
        {
            if (null == S)
                hasNull = true;
            else if (!(S instanceof String))
                return null;
            else
                ts.add((String)S);
        }
        ArrayList<Object> params = new ArrayList<>(ts);
        if (hasNull)
            params.add(null);
        return params;
    }

    // unique and ordered list
    private ArrayList<Object> collectIntegers(@NotNull Collection<?> paramsCollection)
    {
        boolean hasNull = false;
        TreeSet<Integer> ts = new TreeSet<>();
        for (Object I : paramsCollection)
        {
            if (null == I)
                hasNull = true;
            else if (!(I instanceof Integer))
                return null;
            else
                ts.add((Integer)I);
        }
        ArrayList<Object> params = new ArrayList<>(ts);
        if (hasNull)
            params.add(null);
        return params;
    }

    private String getCacheKey(@NotNull JdbcType jdbcType, @NotNull Collection<?> params)
    {
        StringBuilder key = new StringBuilder(jdbcType.name());
        for (Object param : params)
            key.append("_").append(param);

        return key.toString();
    }
}
