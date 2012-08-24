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
import org.labkey.api.query.BatchValidationException;
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

    public static TableInsertDataIterator create(DataIterator data, TableInfo table, BatchValidationException errors)
    {
        TableInsertDataIterator it = new TableInsertDataIterator(data, table, null, errors);
        return it;
    }

    /** If container != null, it will be set as a constant in the insert statement */
    public static TableInsertDataIterator create(DataIteratorBuilder data, TableInfo table, @Nullable Container c, boolean forImport, BatchValidationException errors)
    {
        TableInsertDataIterator it = new TableInsertDataIterator(data.getDataIterator(errors), table, c, errors);
        it.setForImport(forImport);
        return it;
    }


    protected TableInsertDataIterator(DataIterator data, TableInfo table, Container c, BatchValidationException errors)
    {
        super(data, null, errors);
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
            _stmt = StatementUtils.insertStatement(_conn, _table, _c, null, true, false);
            super.init();
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }

    @Override
    public DataIterator getDataIterator(BatchValidationException errors)
    {
        assert null == errors || null == _errors || _errors == errors;
        if (null != errors)
            _errors = errors;
        return this;
    }

    @Override
    public void setForImport(boolean forImport)
    {
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
