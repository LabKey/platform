package org.labkey.api.iterator;

import org.labkey.api.util.Filter;

import java.io.IOException;

/**
* User: adam
* Date: Aug 20, 2010
* Time: 12:40:48 PM
*/
public class CloseableFilteredIterator<T> extends FilteredIterator<T> implements CloseableIterator<T>
{
    protected CloseableIterator<T> _iter;

    public CloseableFilteredIterator(CloseableIterator<T> iter, Filter<T> filter)
    {
        super(iter, filter);
        _iter = iter;
    }

    public void close() throws IOException
    {
        _iter.close();
    }
}
