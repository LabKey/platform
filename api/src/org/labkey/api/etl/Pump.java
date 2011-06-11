package org.labkey.api.etl;

import org.labkey.api.query.BatchValidationException;

import java.io.IOException;

/**
 * Run a DataIterator pipeline, and throw away the result!
 */
public class Pump implements Runnable
{
    final DataIterator _it;
    BatchValidationException _errors;
    int _errorLimit = Integer.MAX_VALUE;

    public Pump(DataIterator it, BatchValidationException errors)
    {
        this._it = it;
        this._errors = errors;
    }

    @Override
    public void run() throws RuntimeException
    {
        try
        {
            while (_it.next())
            {
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
}
