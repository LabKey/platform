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
import org.labkey.api.gwt.client.ui.domain.CancellationException;
import org.labkey.api.query.BatchValidationException;

import java.sql.SQLException;
import java.util.ArrayList;

class ParameterMapPump implements Runnable
{
    protected Parameter.ParameterMap stmt;
    final BatchValidationException errors;
    DataIterator data;
    int _rowCount = 0;



    public ParameterMapPump(DataIterator data, Parameter.ParameterMap map, BatchValidationException errors)
    {
        this.data = data;
        this.stmt = map;
        this.errors = errors;
    }

    public int getRowCount()
    {
        return _rowCount;
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
    public void run()
    {
        try
        {
            // map from source to target
            ArrayList<Triple> bindings = new ArrayList<Triple>(stmt.size());
            // by name
            for (int i=1 ; i<=data.getColumnCount() ; i++)
            {
                ColumnInfo col = data.getColumnInfo(i);
                Parameter to = null;
                if (to == null && null != col.getPropertyURI())
                    to = stmt.getParameter(col.getPropertyURI());
                if (to == null)
                    to = stmt.getParameter(col.getName());
                if (null != to)
                {
                    String mvName = col.getMvColumnName();
                    Parameter mv = null==mvName ? null : stmt.getParameter(mvName);
                    bindings.add(new Triple(i, to, mv));
                }
            }

            while (data.next())
            {
                if (Thread.currentThread().isInterrupted())
                    throw new CancellationException();

                stmt.clearParameters();
                for (Triple binding : bindings)
                {
                    Object value = data.get(binding.fromIndex);
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
                if (errors.hasErrors())
                    continue;
                stmt.execute();
                _rowCount++;
            }
        }
        catch (BatchValidationException x)
        {
            assert x == errors;
        }
        catch (SQLException x)
        {
            //errors.addException(x);
            throw new RuntimeSQLException(x);
        }
    }
}
