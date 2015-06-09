package org.labkey.api.data;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.cache.StringKeyCache;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.util.GUID;

import java.sql.Types;
import java.util.Collection;
import java.util.Collections;
import java.util.Random;

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

                // Insert values; batch size must be <= 1000 values
                SQLFragment sqlInsert = new SQLFragment("INSERT INTO ");
                sqlInsert.append(tableName).append(" (Id) VALUES ");
                String sep = "";
                int valueCount = 0;
                for (Object param : params)
                {
                    if (valueCount >= 1000)
                    {
                        new SqlExecutor(DbSchema.getTemp()).execute(sqlInsert);
                        sqlInsert = new SQLFragment("INSERT INTO ");
                        sqlInsert.append(tableName).append(" (Id) VALUES ");
                        sep = "";
                        valueCount = 0;
                    }
                    sqlInsert.append(sep).append("(").append(param).append(")");
                    sep = ", ";
                    valueCount += 1;
                }
                new SqlExecutor(DbSchema.getTemp()).execute(sqlInsert);


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
