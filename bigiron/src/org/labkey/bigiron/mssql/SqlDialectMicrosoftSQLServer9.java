/*
 * Copyright (c) 2008-2010 LabKey Corporation
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

import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.data.SQLFragment;

/**
 * User: kevink
 * Date: Jan 28, 2008 2:56:27 PM
 */
public class SqlDialectMicrosoftSQLServer9 extends SqlDialectMicrosoftSQLServer
{
    public SqlDialectMicrosoftSQLServer9()
    {
        super();
        reservedWordSet.addAll(PageFlowUtil.set(
           "EXTERNAL", "PIVOT", "REVERT", "SECURITYAUDIT", "TABLESAMPLE", "UNPIVOT"
        ));
    }


    // JTDS driver always gets mapped to base SQL Server dialect, not this one
    @Override
    protected boolean claimsDriverClassName(String driverClassName)
    {
        return false;
    }


    @Override
    protected boolean claimsProductNameAndVersion(String dataBaseProductName, int databaseMajorVersion, int databaseMinorVersion, String jdbcDriverVersion, boolean logWarnings)
    {
        return dataBaseProductName.equals("Microsoft SQL Server") && (databaseMajorVersion >= 9);
    }


    @Override
    public boolean supportsOffset()
    {
        return true;
    }

    @Override
    protected SQLFragment _limitRows(SQLFragment select, SQLFragment from, SQLFragment filter, String order, int rowCount, long offset)
    {
        if (order == null || order.trim().length() == 0)
            throw new IllegalArgumentException("ERROR: ORDER BY clause required to limit");

        SQLFragment sql = new SQLFragment();
        sql.append("SELECT * FROM (\n");
        sql.append(select);
        sql.append(",\nROW_NUMBER() OVER (\n");
        sql.append(order);
        sql.append(") AS _RowNum\n");
        sql.append(from);
        if (filter != null) sql.append("\n").append(filter);
        sql.append("\n) AS z\n");
        sql.append("WHERE _RowNum BETWEEN ");
        sql.append(offset + 1);
        sql.append(" AND ");
        sql.append(offset + rowCount);
        return sql;
    }

    @Override
    public String getGroupConcatAggregateFunction(String selectName)
    {
        // TODO: Replace this with SQL Server 2005+ group concat SQL: see XML PATH code in ViabilityAssaySchema
        return super.getGroupConcatAggregateFunction(selectName);
    }
}
