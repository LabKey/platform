/*
 * Copyright (c) 2011-2012 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.StatementUtils;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.UpdateableTableInfo;
import org.labkey.api.query.FieldKey;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

public class TableInsertDataIterator extends StatementDataIterator implements DataIteratorBuilder
{
    DbScope _scope = null;
    Connection _conn = null;
    final TableInfo _table;
    final Container _c;

    public static TableInsertDataIterator create(DataIterator data, TableInfo table, DataIteratorContext context)
    {
        TableInsertDataIterator it = new TableInsertDataIterator(data, table, null, context);
        return it;
    }

    /** If container != null, it will be set as a constant in the insert statement */
    public static TableInsertDataIterator create(DataIteratorBuilder data, TableInfo table, @Nullable Container c, DataIteratorContext context)
    {
        TableInsertDataIterator it = new TableInsertDataIterator(data.getDataIterator(context), table, c, context);
        return it;
    }


    protected TableInsertDataIterator(DataIterator data, TableInfo table, Container c, DataIteratorContext context)
    {
        super(data, null, context);
        this._table = table;
        this._c = c;

        Map<String,Integer> map = DataIteratorUtil.createColumnNameMap(data);
        for (ColumnInfo col : table.getColumns())
        {
            Integer index = map.get(col.getName());
            FieldKey mvColumnName = col.getMvColumnName();
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
            boolean forImport = _context.isForImport();
            boolean hasTriggers = _table.hasTriggers(_c);
            boolean selectIds = !forImport || hasTriggers;
            _stmt = StatementUtils.insertStatement(_conn, _table, _c, null, selectIds, false);
            super.init();
            if (selectIds)
                _batchSize = 1;
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }

    @Override
    public DataIterator getDataIterator(DataIteratorContext context)
    {
        assert _context == context;
        return this;
    }

    @Override
    protected void onFirst()
    {
        init();
    }

    @Override
    public void close() throws IOException
    {
        super.close();
        _scope.releaseConnection(_conn);
    }
}
