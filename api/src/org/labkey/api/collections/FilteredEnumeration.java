package org.labkey.api.collections;

import java.util.Enumeration;
import java.util.function.Predicate;

/**
 * Wraps an Enumeration, filtering it to include only elements that pass the Predicate's test() method
 *
 * Created by adam on 6/5/2017.
 */
public class FilteredEnumeration<E> implements Enumeration<E>
{
    private final Enumeration<E> _enumeration;
    private final Predicate<E> _predicate;

    private E _next = null;

    public FilteredEnumeration(Enumeration<E> enumeration, Predicate<E> predicate)
    {
        _enumeration = enumeration;
        _predicate = predicate;
    }

    @Override
    public boolean hasMoreElements()
    {
        while (_enumeration.hasMoreElements())
        {
            _next = _enumeration.nextElement();

            if (_predicate.test(_next))
                return true;
        }

        return false;
    }

    @Override
    public E nextElement()
    {
        return _next;
    }
}
