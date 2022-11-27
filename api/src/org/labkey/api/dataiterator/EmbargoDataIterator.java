package org.labkey.api.dataiterator;

import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.JdbcType;
import org.labkey.api.query.BatchValidationException;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Supplier;

/**
 * StatementDataIterator is already complicated enough, so the cache-ahead functionality is implemented by a separate class called
 * EmbargoDataIterator.  This class is similar to CachingDataIterator, however, where CachingDataIterator
 * caches rows that have have already been seen, EmbargoDataIterator caches rows _ahead_ and holds them back until
 * the StatementDataIterator indicates that they have been 'saved' and may be released.
 */

public class EmbargoDataIterator extends AbstractDataIterator
{
    final DataIterator _in;
    final int _inputColumnCount;
    final int _outputColumnCount;
    Object[] _currentRow ;
    final LinkedList<Object[]> _rows = new LinkedList<>();
    int _releasedRowNumber = Integer.MIN_VALUE;
    boolean _atEndOfInput = false;
    Supplier[] _suppliers;
    final BaseColumnInfo _extraColumn;

    public EmbargoDataIterator(DataIteratorContext context, DataIterator in, String extraColumn, JdbcType extraType)
    {
        super(context);
        _in = in;
        _currentRow = new Object[]{-1};
        _inputColumnCount = in.getColumnCount();
        _outputColumnCount = _inputColumnCount + (null!=extraColumn?1:0);
        _suppliers = new Supplier[_inputColumnCount+1];
        for (int i=0 ; i<=_inputColumnCount ; i++)
            _suppliers[i] = _in.getSupplier(i);
        _extraColumn = null==extraColumn? null : new BaseColumnInfo(extraColumn, extraType);
    }

    @Override
    public boolean supportsGetExistingRecord()
    {
        return _in.supportsGetExistingRecord();
    }

    @Override
    public int getColumnCount()
    {
        return _outputColumnCount;
    }

    @Override
    public ColumnInfo getColumnInfo(int i)
    {
        if (i <= _inputColumnCount)
            return _in.getColumnInfo(i);
        if (i == _outputColumnCount)
            return _extraColumn;
        return null;
    }

    /*
     * This is how the paired dataiterator usually (always?) a StatementDataIterator, that
     * this EmbargoDataIterator can release a batch of rows that have been committed (inserted/updated)
     */
    public void setReleasedRowNumber(int n)
    {
        _releasedRowNumber = n;
    }

    public void setReleasedRowNumber(int n, List<Object> extraColumn)
    {
        assert extraColumn.size() == n-(Math.max(0,_releasedRowNumber));
        // set the last column (the extra column) to the extraColumn value (presumably rowid or some such)
        for (var i=0 ; i<extraColumn.size() ; i++)
            _rows.get(i)[_outputColumnCount] = extraColumn.get(i);
        _releasedRowNumber = n;
    }

    @Override
    public boolean next() throws BatchValidationException
    {
        while (!_atEndOfInput && (_rows.isEmpty() || ((Integer)_rows.getFirst()[0]) > _releasedRowNumber))
        {
            // add row before calling _in.next(), because the next() call might invoke callback setReleasedRowNumber()
            Object[] row = new Object[_outputColumnCount+1];
            _rows.add(row);
            boolean ret = _in.next();
            if (!ret)
            {
                _atEndOfInput = true;
                _rows.removeLast();
            }
            else
            {
                for (var i=0 ; i<=_inputColumnCount ; i++)
                    row[i] = _suppliers[i].get();
            }
        }
        assert(!_rows.isEmpty() || _atEndOfInput);
        if (_rows.isEmpty())
        {
            _currentRow = null;
            return false;
        }
        assert(((Integer)_rows.getFirst()[0]) <= _releasedRowNumber);
        assert(!_atEndOfInput || ((Integer)_rows.getLast()[0]) <= _releasedRowNumber);
        _currentRow = _rows.removeFirst();
        return true;
    }

    @Override
    public Object get(int i)
    {
        if (_currentRow == null)
            return null;
        return _currentRow[i];
    }

    @Override
    public void close() throws IOException
    {
        _in.close();
    }
}
