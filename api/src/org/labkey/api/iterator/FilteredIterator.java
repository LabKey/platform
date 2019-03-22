/*
 * Copyright (c) 2009-2017 LabKey Corporation
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
package org.labkey.api.iterator;

import org.labkey.api.util.Filter;

import java.util.NoSuchElementException;
import java.util.Iterator;

/**
 * Wrapper over some other Iterator that filters out certain objects.
 * User: adam
 * Date: Apr 10, 2009
 */
public class FilteredIterator<T> implements Iterator<T>
{
    private final Iterator<T> _iterator;
    private final Filter<T> _filter;
    private T _next;

    public FilteredIterator(Iterator<T> iterator, Filter<T> filter)
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
