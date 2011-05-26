package org.labkey.api.etl;

import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.ValidationException;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: 2011-05-26
 * Time: 2:48 PM
 */
public abstract class AbstractDataIterator implements DataIterator
{
    String _debugName = "";
    BatchValidationException _errors;
    ValidationException _rowError = null;

    protected AbstractDataIterator(BatchValidationException errors)
    {
        _errors = errors;
    }

    public void setDebugName(String name)
    {
        _debugName = name;
    }


    protected ValidationException getRowError()
    {
        int row = (Integer)this.get(0);

        if (null == _rowError || row != _rowError.getRowNumber())
        {
            _rowError = new ValidationException();
            _rowError.setRowNumber(row);
            _errors.addRowError(_rowError);
        }
        return _rowError;
    }
}
