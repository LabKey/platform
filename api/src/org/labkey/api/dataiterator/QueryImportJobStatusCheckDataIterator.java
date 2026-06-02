/*
 * Copyright (c) 2024-2026 LabKey Corporation
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

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.pipeline.CancelledException;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.QueryImportJobCancelledException;
import org.labkey.api.query.QueryImportPipelineJob;

import java.io.IOException;
import java.util.function.Supplier;

// This is a pass-through iterator, it does not change any of the data, it only throws exceptions
public class QueryImportJobStatusCheckDataIterator extends AbstractDataIterator
{
    final DataIterator _data;
    final int _batchSize;
    final QueryImportPipelineJob _job;
    int _currentRow = -1;

    public QueryImportJobStatusCheckDataIterator(DataIterator data, DataIteratorContext context, int batchSize)
    {
        super(context);
        _data = data;
        _job = context.getBackgroundJob();
        _batchSize = batchSize > 0 ? batchSize : 1;
    }

    @Override
    public int getColumnCount()
    {
        return _data.getColumnCount();
    }

    @Override
    public ColumnInfo getColumnInfo(int i)
    {
        return _data.getColumnInfo(i);
    }


    @Override
    public boolean next() throws BatchValidationException
    {
        if (!_data.next())
            return false;

        if ((_currentRow % _batchSize) == 0)
        {
            try
            {
                _job.setStatus(PipelineJob.TaskStatus.running);
            }
            catch (CancelledException e)
            {
                throw new QueryImportJobCancelledException();
            }

        }
        _currentRow++;
        return true;
    }

    @Override
    public Object get(int i)
    {
        return _data.get(i);
    }


    @Override
    public Supplier<Object> getSupplier(int i)
    {
        return _data.getSupplier(i);
    }


    @Override
    public void close() throws IOException
    {
        _data.close();
    }

}
