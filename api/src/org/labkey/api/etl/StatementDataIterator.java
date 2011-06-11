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
import org.labkey.api.data.Parameter;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.exp.MvFieldWrapper;
import org.labkey.api.query.BatchValidationException;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;

class StatementDataIterator extends AbstractDataIterator
{
    protected Parameter.ParameterMap _stmt;
    final BatchValidationException _errors;
    DataIterator _data;
    int _executeCount = 0;
    ColumnInfo[] _columns = null;
    boolean _failFast = false;

    Triple[] _bindings = null;

    protected StatementDataIterator(DataIterator data, Parameter.ParameterMap map, BatchValidationException errors)
    {
        super(errors);
        this._data = data;
        this._stmt = map;
        this._errors = errors;
    }

    void init()
    {
        // map from source to target
        ArrayList<Triple> bindings = new ArrayList<Triple>(_stmt.size());
        // by name
        for (int i=1 ; i<=_data.getColumnCount() ; i++)
        {
            ColumnInfo col = _data.getColumnInfo(i);
            Parameter to = null;
            if (to == null && null != col.getPropertyURI())
                to = _stmt.getParameter(col.getPropertyURI());
            if (to == null)
                to = _stmt.getParameter(col.getName());
            if (null != to)
            {
                String mvName = col.getMvColumnName();
                Parameter mv = null==mvName ? null : _stmt.getParameter(mvName);
                bindings.add(new Triple(i, to, mv));
            }
        }
        _bindings = bindings.toArray(new Triple[bindings.size()]);
    }

    public int getExecuteCount()
    {
        return _executeCount;
    }

    private static class Triple
    {
        Triple(int from, Parameter to, Parameter mv)
        {
            this.fromIndex = from;
            this.to = to;
            this.mv = mv;
        }
        int fromIndex;
        Parameter to;
        Parameter mv;
    }

    @Override
    public int getColumnCount()
    {
        return null==_columns ? _data.getColumnCount() : _columns.length-1;
    }


    @Override
    public ColumnInfo getColumnInfo(int i)
    {
        return null==_columns ? _data.getColumnInfo(i) : _columns[i];
    }


    @Override
    public boolean next() throws BatchValidationException
    {
        if (!_data.next())
            return false;

        try
        {
            _stmt.clearParameters();
            for (Triple binding : _bindings)
            {
                Object value = _data.get(binding.fromIndex);
                if (null == value)
                    continue;
                if (value instanceof MvFieldWrapper)
                {
                    if (null != binding.mv)
                        binding.mv.setValue(((MvFieldWrapper) value).getMvIndicator());
                    binding.to.setValue(((MvFieldWrapper) value).getValue());
                }
                else
                {
                    binding.to.setValue(value);
                }
            }
            if (_errors.hasErrors())
                return !_failFast;
            _stmt.execute();
            _executeCount++;
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
        return true;
    }


    @Override
    public Object get(int i)
    {
        return _data.get(i);
    }


    @Override
    public void close() throws IOException
    {
        _data.close();
        if (_stmt != null)
        {
            try {
                _stmt.close();
            }
            catch (SQLException x)
            {
                throw new RuntimeSQLException(x);
            }
        }
        _stmt = null;
    }

}
