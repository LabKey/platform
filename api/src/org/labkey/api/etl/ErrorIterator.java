package org.labkey.api.etl;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.ValidationException;

import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: 2011-06-03
 * Time: 3:51 PM
 *
 * This slightly silly class lets you postpose errors encountered during construction of a iterator until
 * next() is called.
 *
 * Two cases:
 *  - return error on first call to next()
 *  - return error on first call to next IF input iterator has at least one row
 */
public class ErrorIterator extends AbstractDataIterator
{
    DataIterator _it;
    boolean _errorIfEmpty;
    boolean _failFast = true;
    ValidationException _error;

    public static DataIterator wrap(DataIterator di, BatchValidationException errors, boolean errorEvenIfEmpty, ValidationException x)
    {
        if (null == x || !x.hasErrors())
            return di;
        return new ErrorIterator(di, errors, errorEvenIfEmpty, x);
    }

    ErrorIterator(DataIterator di, BatchValidationException errors, boolean errorEvenIfEmpty, ValidationException x)
    {
        super(errors);
        this._it = di;
        this._errorIfEmpty = errorEvenIfEmpty;
        this._error = x;
    }

    @Override
    public int getColumnCount()
    {
        return _it.getColumnCount();
    }

    @Override
    public ColumnInfo getColumnInfo(int i)
    {
        return _it.getColumnInfo(i);
    }

    @Override
    public boolean next() throws BatchValidationException
    {
        boolean hasNext = _it.next();
        if (null != _error && (hasNext || _errorIfEmpty))
        {
            getGlobalError().addErrors(_error);
            _error = null;
        }
        return hasNext && !_failFast;
    }

    @Override
    public Object get(int i)
    {
        return _it.get(i);
    }

    @Override
    public void close() throws IOException
    {
        _it.close();
    }

    @Override
    public boolean isScrollable()
    {
        return _it.isScrollable();
    }
}
