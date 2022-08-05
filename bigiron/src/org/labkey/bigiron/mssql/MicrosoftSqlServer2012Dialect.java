/*
 * Copyright (c) 2012-2019 LabKey Corporation
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
import org.labkey.api.data.dialect.LimitRowsSqlGenerator;

/**
 * User: adam
 * Date: 2/9/12
 * Time: 8:34 AM
 */
public class MicrosoftSqlServer2012Dialect extends MicrosoftSqlServer2008R2Dialect
{
    // Called only if offset is > 0, maxRows is not NO_ROWS, and order is non-blank
    @Override
    protected SQLFragment _limitRows(SQLFragment select, SQLFragment from, SQLFragment filter, @NotNull String order, String groupBy, int maxRows, long offset)
    {
        SQLFragment sql = LimitRowsSqlGenerator.appendFromFilterOrderAndGroupByNoValidation(select, from, filter, order, groupBy);
        sql.append("\nOFFSET ").append(offset).append(" ROWS");
        if (maxRows != Table.ALL_ROWS)
            sql.append("\nFETCH NEXT ").append(maxRows).append(" ROWS ONLY");

        return sql;
    }

    @Override
    public String getMedianFunction()
    {
        return "percentile_cont";
    }

    @Override
    public boolean allowSortOnSubqueryWithoutLimit()
    {
        return true; // But only if the form is "ORDER BY xxx OFFSET 0 ROWS". See below and #38495.
    }

    @Override
    public void appendSortOnSubqueryWithoutLimitQualifier(SQLFragment builder)
    {
        builder.append(" OFFSET 0 ROWS"); // Trick SQL Server 2012+ into allowing an ORDER BY inside a subquery. See #38495.
    }
}
