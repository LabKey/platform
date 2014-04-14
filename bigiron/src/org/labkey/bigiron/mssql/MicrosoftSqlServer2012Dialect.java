/*
 * Copyright (c) 2012-2013 LabKey Corporation
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

import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Table;

/**
 * User: adam
 * Date: 2/9/12
 * Time: 8:34 AM
 */
public class MicrosoftSqlServer2012Dialect extends MicrosoftSqlServer2008R2Dialect
{
//    @Override          // Consider: remove this; just use TOP in this maxRows-only case
//    public SQLFragment limitRows(SQLFragment frag, int maxRows)
//    {
//        if (Table.ALL_ROWS == maxRows)
//            return frag;
//
//        String sql = frag.getSQL();
//        if (!sql.substring(0, 6).equalsIgnoreCase("SELECT"))
//            throw new IllegalArgumentException("ERROR: Limit SQL doesn't start with SELECT: " + sql);
//
//        // FETCH NEXT doesn't seem to work with 0 rows, so fallback to TOP in this case
//        if (Table.NO_ROWS == maxRows)
//        {
//            int offset = 6;
//            if (sql.substring(0, 15).equalsIgnoreCase("SELECT DISTINCT"))
//                offset = 15;
//            frag.insert(offset, " TOP 0");
//        }
//        else
//        {
//            frag.append(" OFFSET 0 ROWS FETCH NEXT ").append(maxRows).append(" ROWS ONLY");
//        }
//
//        return frag;
//    }

    // Called only if rowCount and offset are both > 0
    @Override
    protected SQLFragment _limitRows(SQLFragment select, SQLFragment from, SQLFragment filter, String order, String groupBy, int maxRows, long offset)
    {
        SQLFragment sql = new SQLFragment(select);
        sql.append("\n").append(from);
        if (null != filter && !filter.isEmpty()) sql.append("\n").append(filter);
        if (groupBy != null) sql.append("\n").append(groupBy);
        if (order != null) sql.append("\n").append(order);
        sql.append("\nOFFSET ").append(offset).append(" ROWS FETCH NEXT ").append(maxRows).append(" ROWS ONLY");

        return sql;
    }
}
