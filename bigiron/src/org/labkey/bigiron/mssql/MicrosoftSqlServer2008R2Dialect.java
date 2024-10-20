/*
 * Copyright (c) 2011-2019 LabKey Corporation
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
package org.labkey.bigiron.mssql;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Table;

public class MicrosoftSqlServer2008R2Dialect extends BaseMicrosoftSqlServerDialect
{
    // Called only if offset is > 0, maxRows is not NO_ROWS, and order is non-blank
    @Override
    protected SQLFragment _limitRows(SQLFragment select, SQLFragment from, SQLFragment filter, @NotNull String order, String groupBy, int maxRows, long offset)
    {
        SQLFragment sql = new SQLFragment();
        sql.append("SELECT * FROM (\n");
        sql.append(select);
        sql.append(",\nROW_NUMBER() OVER (\n");
        sql.append(order);
        sql.append(") AS _RowNum\n");
        sql.append(from);
        if (filter != null) sql.append("\n").append(filter);
        if (groupBy != null) sql.append("\n").append(groupBy);
        sql.append("\n) AS z\n");
        sql.append("WHERE _RowNum ");

        if (maxRows == Table.ALL_ROWS)
        {
            sql.append("> ").appendValue(offset);
        }
        else
        {
            sql.append("BETWEEN ");
            sql.appendValue(offset + 1);
            sql.append(" AND ");
            sql.appendValue(offset + maxRows);
        }

        return sql;
    }

    @Override
    public String getStdDevPopFunction()
    {
        return "stdevp";
    }

    @Override
    public SQLFragment getDatabaseSizeSql(String databaseName)
    {
        return new SQLFragment("SELECT SUM(size) FROM sys.master_files WHERE database_id = DB_ID(?)", databaseName);
    }

    @Override
    public String getVarianceFunction()
    {
        return "var";
    }

    @Override
    public String getVarPopFunction()
    {
        return "varp";
    }
}
