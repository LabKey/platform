package org.labkey.api.iterator;

import org.labkey.api.query.ValidationException;
import org.labkey.api.util.UnexpectedException;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

public interface ValidatingDataRowIterator extends Closeable
{
    boolean hasNext();

    Map<String, Object> next() throws ValidationException;

    /** For convenience. Use sparingly as it completely defeats the streaming benefits of this approach */
    default List<Map<String, Object>> collect() throws ValidationException
    {
        List<Map<String, Object>> result = new ArrayList<>();

        while (hasNext())
        {
            result.add(next());
        }

        return result;
    }

    /** Override to avoid throwing IOException */
    @Override
    void close();

    static ValidatingDataRowIterator concat(ValidatingDataRowIterator... allIterators)
    {
        return new ValidatingDataRowIterator()
        {
            final List<ValidatingDataRowIterator> _iterators = new ArrayList<>(Arrays.asList(allIterators));

            @Override
            public boolean hasNext()
            {
                while (!_iterators.isEmpty())
                {
                    ValidatingDataRowIterator current = _iterators.get(0);
                    if (current.hasNext())
                    {
                        return true;
                    }
                    current.close();
                    _iterators.remove(current);
                }
                return false;
            }

            @Override
            public Map<String, Object> next() throws ValidationException
            {
                if (_iterators.isEmpty())
                {
                    throw new NoSuchElementException();
                }
                return _iterators.get(0).next();
            }

            @Override
            public void close()
            {
                for (ValidatingDataRowIterator iterator : _iterators)
                {
                    iterator.close();
                }
                _iterators.clear();
            }
        };
    }

    static ValidatingDataRowIterator of(Iterable<Map<String, Object>> i)
    {
        return new Wrapper(i.iterator());
    }

    static ValidatingDataRowIterator of(Iterator<Map<String, Object>> i)
    {
        return new Wrapper(i);
    }

    static ValidatingDataRowIterator of(ValidatingDataRowIterator i)
    {
        return new Wrapper(i);
    }

    class Wrapper implements ValidatingDataRowIterator
    {
        private final Iterator<Map<String, Object>> _i1;
        private final ValidatingDataRowIterator _i2;

        public Wrapper(Iterator<Map<String, Object>> i)
        {
            _i1 = i;
            _i2 = null;
        }
        public Wrapper(ValidatingDataRowIterator i)
        {
            _i1 = null;
            _i2 = i;
        }

        @Override
        public boolean hasNext()
        {
            return _i1 == null ? _i2.hasNext() : _i1.hasNext();
        }

        @Override
        public Map<String, Object> next() throws ValidationException
        {
            return _i1 == null ? _i2.next() : _i1.next();
        }

        @Override
        public void close()
        {
            if (_i1 instanceof Closeable c)
            {
                try
                {
                    c.close();
                }
                catch (IOException e)
                {
                    throw UnexpectedException.wrap(e);
                }
            }
            if (_i2 != null)
            {
                _i2.close();
            }

        }
    }
}
