/*
 * Copyright (c) 2011 LabKey Corporation
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

package org.labkey.api.etl;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.UpdateableTableInfo;
import org.labkey.api.query.BatchValidationException;
import sun.java2d.pipe.SpanShapeRenderer;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: 2011-05-26
 * Time: 4:08 PM
 *
 *  Handles MvFieldWrapper
 */
public class TableLoaderPump extends ParameterMapPump
{
    final TableInfo table;

    public TableLoaderPump(DataIterator data, TableInfo table, BatchValidationException errors)
    {
        super(data, null, errors);
        this.table = table;

        Map<String,Integer> map = DataIteratorUtil.createColumnNameMap(data);
        for (ColumnInfo col : table.getColumns())
        {
            Integer index = map.get(col.getName());
            String mvColumnName = col.getMvColumnName();
            if (null == index || null == mvColumnName)
                continue;
            data.getColumnInfo(index).setMvColumnName(mvColumnName);
        }
    }

    @Override
    public void run()
    {
        DbScope scope = null;
        Connection conn = null;

        try
        {
            try
            {
                scope = ((UpdateableTableInfo)table).getSchemaTableInfo().getSchema().getScope();
                conn = scope.getConnection();
                stmt = Table.insertStatement(conn, (TableInfo) table, null, null, false);
                super.run();
            }
            catch (SQLException x)
            {
                throw new RuntimeSQLException(x);
            }
        }
        finally
        {
            if (null != stmt)
                try {stmt.close();} catch (SQLException x){}
            if (null != conn)
                scope.releaseConnection(conn);
        }
    }
}
