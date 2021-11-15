package org.labkey.core.dialect;

import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.dialect.PostgreSql91Dialect;
import org.labkey.api.data.dialect.SqlDialect;

import java.util.ArrayList;
import java.util.Arrays;

/*
 TestCase migrated from PostgreSql91Dialect when that class promoted to api.
 */
public class PostgreSqlInClauseTest extends Assert
{
    private PostgreSql_10_Dialect getDialect()
    {
        DbSchema core = CoreSchema.getInstance().getSchema();
        SqlDialect d = core.getSqlDialect();
        if (d instanceof PostgreSql_10_Dialect)
            return (PostgreSql_10_Dialect) d;
        return null;
    }

    @Test
    public void testInClause()
    {
        PostgreSql_10_Dialect d = getDialect();
        if (null == d)
            return;
        DbSchema core = CoreSchema.getInstance().getSchema();

        SQLFragment shortSql = new SQLFragment("SELECT COUNT(*) FROM core.usersdata WHERE userid ");
        d.appendInClauseSql(shortSql, Arrays.asList(1, 2, 3));
        assertEquals(1, new SqlSelector(core, shortSql).getRowCount());

        ArrayList<Object> l = new ArrayList<>();
        for (int i = 1; i <= PostgreSql91Dialect.TEMPTABLE_GENERATOR_MINSIZE + 1; i++)
            l.add(i);
        SQLFragment longSql = new SQLFragment("SELECT COUNT(*) FROM core.usersdata WHERE userid ");
        d.appendInClauseSql(longSql, l);
        assertEquals(1, new SqlSelector(core, longSql).getRowCount());

        SQLFragment shortSqlStr = new SQLFragment("SELECT COUNT(*) FROM core.usersdata WHERE displayname ");
        d.appendInClauseSql(shortSqlStr, Arrays.asList("1", "2", "3"));
        assertEquals(1, new SqlSelector(core, shortSqlStr).getRowCount());

        l = new ArrayList<>();
        for (int i = 1; i <= PostgreSql91Dialect.TEMPTABLE_GENERATOR_MINSIZE + 1; i++)
            l.add(String.valueOf(i));
        SQLFragment longSqlStr = new SQLFragment("SELECT COUNT(*) FROM core.usersdata WHERE displayname ");
        d.appendInClauseSql(longSqlStr, l);
        assertEquals(1, new SqlSelector(core, longSqlStr).getRowCount());
    }
}
