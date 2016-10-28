/*
 * Copyright (c) 2012-2016 LabKey Corporation
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
import org.labkey.api.data.dialect.TableResolver;

/**
 * User: adam
 * Date: 2/9/12
 * Time: 8:34 AM
 */
public class MicrosoftSqlServer2012Dialect extends MicrosoftSqlServer2008R2Dialect
{
    public MicrosoftSqlServer2012Dialect(TableResolver tableResolver)
    {
        super(tableResolver);
    }

    // Called only if rowCount and offset are both > 0... and order is non-blank
    @Override
    protected SQLFragment _limitRows(SQLFragment select, SQLFragment from, SQLFragment filter, @NotNull String order, String groupBy, int maxRows, long offset)
    {
        SQLFragment sql = new SQLFragment(select);
        sql.append("\n").append(from);
        if (null != filter && !filter.isEmpty()) sql.append("\n").append(filter);
        if (groupBy != null) sql.append("\n").append(groupBy);
        sql.append("\n").append(order).append("\nOFFSET ").append(offset).append(" ROWS FETCH NEXT ").append(maxRows).append(" ROWS ONLY");

        return sql;
    }
}
