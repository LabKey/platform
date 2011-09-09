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

import org.labkey.api.query.BatchValidationException;

import java.io.IOException;

/**
 * Run a DataIterator pipeline, and throw away the result!
 */
public class Pump implements Runnable
{
    DataIterator _it;
    DataIteratorBuilder _builder;
    BatchValidationException _errors;
    int _errorLimit = Integer.MAX_VALUE;
    int _rowCount = 0;

    public Pump(DataIterator it, BatchValidationException errors)
    {
        this._it = it;
        this._errors = errors;
    }

    public Pump(DataIteratorBuilder builder, BatchValidationException errors)
    {
        this._builder = builder;
        this._errors = errors;
    }

    @Override
    public void run() throws RuntimeException
    {
        if (null == _it && null != _builder)
            _it = _builder.getDataIterator(_errors);

        try
        {
            while (_it.next())
            {
                _rowCount++;
                if (_errors.getRowErrors().size() > _errorLimit)
                    return;
            }
        }
        catch (BatchValidationException x)
        {
            assert x == _errors;
        }
        finally
        {
            try
            {
                _it.close();
            }
            catch (IOException x)
            {
                /* ignore */
            }
        }
    }

    public int getRowCount()
    {
        return _rowCount;
    }
}
