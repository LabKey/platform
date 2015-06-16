/*
 * Copyright (c) 2015 LabKey Corporation
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
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.cache.StringKeyCache;
import org.labkey.api.util.GUID;

import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Created by davebradlee on 6/5/15.
 */
public class TempTableInClauseGenerator implements InClauseGenerator
{
    private static final StringKeyCache<TempTableInfo> _tempTableCache =
        CacheManager.getBlockingStringKeyCache(100, CacheManager.MINUTE * 5, "InClauseTempTableCache", null);

    @Override
    public SQLFragment appendInClauseSql(SQLFragment sql, final @NotNull Collection<?> params)
    {
        // TODO: should we sort the params
        assert params.iterator().next() instanceof Integer;
        String cacheKey = getCacheKey(params);
        TempTableInfo tempTableInfo = _tempTableCache.get(cacheKey, null, new CacheLoader<String, TempTableInfo>()
        {
            @Override
            public TempTableInfo load(String key, @Nullable Object argument)
            {
                TempTableInfo tempTableInfo = new TempTableInfo("InClause", Collections.singletonList(new ColumnInfo("Id", JdbcType.INTEGER, 0, false)), null);
                String tableName = tempTableInfo.getSelectName();
                SQLFragment sqlCreate = new SQLFragment("CREATE TABLE ");
                sqlCreate.append(tableName)
                         .append("\n(Id ")
                         .append(DbSchema.getTemp().getSqlDialect().sqlTypeNameFromSqlType(Types.INTEGER))
                         .append(");");

                new SqlExecutor(DbSchema.getTemp()).execute(sqlCreate);
                tempTableInfo.track();

                List<List<?>> paramList = new ArrayList<>();
                for (Object param : params)
                    paramList.add(Collections.singletonList(param));

                String sql = "INSERT INTO " + tableName + " (Id) VALUES (?)";
                try
                {
                    Table.batchExecute(DbSchema.getTemp(), sql, paramList);
                }
                catch (SQLException e)
                {
                    throw new RuntimeSQLException(e);
                }

                String indexSql = "CREATE INDEX IX_Id" + new GUID().toStringNoDashes() + " ON " + tableName + "(Id)";
                new SqlExecutor(DbSchema.getTemp()).execute(indexSql);
                return tempTableInfo;
            }
        });

        sql.append("IN (SELECT Id FROM ").append(tempTableInfo.getSelectName()).append(")");
        sql.addTempToken(tempTableInfo);
        return sql;
    }

    private String getCacheKey(@NotNull Collection<?> params)
    {
        StringBuilder key = new StringBuilder("ttkey");
        for (Object param : params)
            key.append("_").append(param);

        return key.toString();
    }
}
