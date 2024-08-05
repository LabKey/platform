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
