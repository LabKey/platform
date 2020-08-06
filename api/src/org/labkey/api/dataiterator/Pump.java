/*
 * Copyright (c) 2016-2019 LabKey Corporation
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
package org.labkey.api.dataiterator;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.labkey.api.exp.list.ListImportProgress;
import org.labkey.api.query.BatchValidationException;

import java.io.IOException;

/**
 * Run a DataIterator pipeline, and throw away the result!
 */
public class Pump implements Runnable
{
    static Logger log = LogManager.getLogger(Pump.class);
    DataIterator _it;
    DataIteratorBuilder _builder;
    final DataIteratorContext _context;
    final BatchValidationException _errors;
    int _errorLimit = Integer.MAX_VALUE;
    int _rowCount = 0;
    ListImportProgress _progress = null;

    public Pump(DataIterator it, DataIteratorContext context)
    {
        _it = it;
        _context = context;
        _errors = context.getErrors();
    }

    public Pump(DataIteratorBuilder builder,  DataIteratorContext context)
    {
        _builder = builder;
        _context = context;
        _errors = context.getErrors();
    }

    public void setProgress(ListImportProgress progress)
    {
        _progress = progress;
    }

    @Override
    public void run() throws RuntimeException
    {
        if (null == _it && null != _builder)
            _it = _builder.getDataIterator(_context);

        if (log.isTraceEnabled())
        {
            StringBuilder sb = new StringBuilder("PUMP\n");
            _it.debugLogInfo(sb);
            log.trace(sb.toString());
        }

        try (DataIterator it = _it)
        {
            while (it.next())
            {
                _rowCount++;
                if (_errors.getRowErrors().size() > _errorLimit)
                    return;
                if (null != _progress)
                    _progress.setCurrentRow(_rowCount);
            }
        }
        catch (BatchValidationException x)
        {
            assert x == _errors;
        }
        catch (IOException x)
        {
            /* ignore */
        }
    }

    public int getRowCount()
    {
        return _rowCount;
    }
}
