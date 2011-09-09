package org.labkey.api.etl;

import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.BatchValidationException;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: 2011-09-07
 * Time: 5:14 PM
 */
public class TriggerDataBuilder
{
    public static DataIteratorBuilder before(DataIteratorBuilder in, TableInfo target, Container c)
    {
        if (!target.hasTriggers(c))
            return in;
        return new Before(in, target, c);
    }

    public static DataIteratorBuilder after(DataIteratorBuilder in, TableInfo target, Container c)
    {
        if (!target.hasTriggers(c))
            return in;
        return new After(in, target, c);
    }


    static class Before implements DataIteratorBuilder
    {
        DataIteratorBuilder _input;

        Before(DataIteratorBuilder in, TableInfo target, Container c)
        {
            this._input = in;
        }

        @Override
        public DataIterator getDataIterator(BatchValidationException x)
        {
            return _input.getDataIterator(x);
        }
    }


    static class After implements DataIteratorBuilder
    {
        DataIteratorBuilder _input;

        After(DataIteratorBuilder in, TableInfo target, Container c)
        {
            _input = in;
        }

        @Override
        public DataIterator getDataIterator(BatchValidationException x)
        {
            return _input.getDataIterator(x);
        }
    }
}
