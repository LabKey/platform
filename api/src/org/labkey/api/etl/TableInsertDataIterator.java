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
import org.labkey.api.data.Parameter;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.UpdateableTableInfo;
import org.labkey.api.query.BatchValidationException;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

public class TableInsertDataIterator extends StatementDataIterator
{
    DbScope _scope = null;
    Connection _conn = null;
    final TableInfo _table;

    public static TableInsertDataIterator create(DataIterator data, TableInfo table, BatchValidationException errors)
    {
        TableInsertDataIterator it = new TableInsertDataIterator(data, table, errors);
        it.init();
        return it;
    }

    protected TableInsertDataIterator(DataIterator data, TableInfo table, BatchValidationException errors)
    {
        super(data, null, errors);
        this._table = table;

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
    void init()
    {
        try
        {
            _scope = ((UpdateableTableInfo)_table).getSchemaTableInfo().getSchema().getScope();
            _conn = _scope.getConnection();
            _stmt = Table.insertStatement(_conn, _table, null, null, false, false);
            super.init();
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }

    @Override
    public void close() throws IOException
    {
        super.close();
        _scope.releaseConnection(_conn);
    }
}