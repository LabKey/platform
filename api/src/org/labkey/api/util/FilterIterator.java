package org.labkey.api.util;

import java.util.NoSuchElementException;
import java.util.Iterator;

/**
 * User: adam
 * Date: Apr 10, 2009
 * Time: 9:54:36 AM
 */
public class FilterIterator<T> implements Iterator<T>
{
    private Iterator<T> _iterator;
    private Filter<T> _filter;
    private T _next;

    public FilterIterator(Iterator<T> iterator, Filter<T> filter)
    {
        _iterator = iterator;
        _filter = filter;
        toNext();
    }

    public boolean hasNext()
    {
        return _next != null;
    }

    public T next()
    {
        if (_next == null)
            throw new NoSuchElementException();

        T returnValue = _next;
        toNext();

        return returnValue;
    }

    public void remove()
    {
        throw new UnsupportedOperationException();
    }

    private void toNext()
    {
        _next = null;

        while (_iterator.hasNext())
        {
            T item = _iterator.next();
            if (item != null && _filter.accept(item))
            {
                _next = item;
                break;
            }
        }
    }
}
