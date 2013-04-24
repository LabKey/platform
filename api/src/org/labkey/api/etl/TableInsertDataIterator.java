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
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SchemaTableInfo;
import org.labkey.api.data.StatementUtils;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.UpdateableTableInfo;
import org.labkey.api.query.FieldKey;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;

public class TableInsertDataIterator extends StatementDataIterator implements DataIteratorBuilder
{
    DbScope _scope = null;
    Connection _conn = null;
    final TableInfo _table;
    final Container _c;
    boolean _selectIds = false;
    final Set<String> _skipColumnNames = new CaseInsensitiveHashSet();

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

        ColumnInfo colAutoIncrement = null;
        Integer indexAutoIncrement = null;

        Map<String,Integer> map = DataIteratorUtil.createColumnNameMap(data);
        for (ColumnInfo col : table.getColumns())
        {
            Integer index = map.get(col.getName());

            if (null == index && null != col.getJdbcDefaultValue())
                _skipColumnNames.add(col.getName());

            if (col.isAutoIncrement())
            {
                indexAutoIncrement = index;
                colAutoIncrement = col;
            }
            FieldKey mvColumnName = col.getMvColumnName();
            if (null == index || null == mvColumnName)
                continue;
            data.getColumnInfo(index).setMvColumnName(mvColumnName);
        }

        // NOTE StatementUtils figures out reselect etc, but we need to get our metadata straight at construct time
        // Can't move StatementUtils.insertStatement here because the transaction might not be started yet
        boolean forImport = _context.isForImport();
        boolean hasTriggers = _table.hasTriggers(_c);
        _selectIds = !forImport || hasTriggers;
        if (_selectIds)
        {
            SchemaTableInfo t = (SchemaTableInfo)((UpdateableTableInfo)table).getSchemaTableInfo();
            // check that there is actually an autoincrement column in schema table (List has fake auto increment)
            for (ColumnInfo col : t.getColumns())
            {
                if (col.isAutoIncrement())
                {
                    setRowIdColumn(indexAutoIncrement==null?-1:indexAutoIncrement, colAutoIncrement);
                    break;
                }
            }
        }
    }


    @Override
    void init()
    {
        try
        {
            _scope = ((UpdateableTableInfo)_table).getSchemaTableInfo().getSchema().getScope();
            _conn = _scope.getConnection();
            _stmt = StatementUtils.insertStatement(_conn, _table, _skipColumnNames, _c, null, _selectIds, false);
            super.init();
            if (_selectIds)
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


    boolean _closed = false;

    @Override
    public void close() throws IOException
    {
        if (_closed)
            return;
        _closed = true;
        super.close();
        _scope.releaseConnection(_conn);
    }
}
