package org.labkey.api.iterator;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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

/**
 * Similar to a standard java.util.Iterator but capable of throwing ValidationExceptions and offering
 * some other specialized methods
 */
public interface ValidatingDataRowIterator extends Closeable
{
    boolean hasNext() throws ValidationException;

    Map<String, Object> next() throws ValidationException;

    /**
     * Returns the row (if any) that will be returned by the next call to next(), but doesn't advance to the subsequent row
     */
    @Nullable
    Map<String, Object> peek() throws ValidationException;

    /** @return the zero-based number of the most recent next() invocation. -1 if no data has been returned yet */
    int getCurrentRowNumber();

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

    /** @return an iterator that combines all the supplied iterators in order */
    static ValidatingDataRowIterator concat(ValidatingDataRowIterator... allIterators)
    {
        return new ValidatingDataRowIterator()
        {
            final List<ValidatingDataRowIterator> _iterators = new ArrayList<>(Arrays.asList(allIterators));
            private int _currentRowNumber = -1;

            @Override
            public boolean hasNext() throws ValidationException
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

            @Override @Nullable
            public Map<String, Object> peek() throws ValidationException
            {
                if (hasNext())
                {
                    return _iterators.get(0).peek();
                }
                return null;
            }

            @Override
            public Map<String, Object> next() throws ValidationException
            {
                if (_iterators.isEmpty())
                {
                    throw new NoSuchElementException();
                }
                Map<String, Object> result = _iterators.get(0).next();
                _currentRowNumber++;
                return result;
            }

            @Override
            public int getCurrentRowNumber()
            {
                return _currentRowNumber;
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
        private Map<String, Object> _peeked;
        private int _currentRowNumber = -1;

        public Wrapper(@NotNull Iterator<Map<String, Object>> i)
        {
            _i1 = i;
            _i2 = null;
        }
        public Wrapper(@NotNull ValidatingDataRowIterator i)
        {
            _i1 = null;
            _i2 = i;
        }

        @Override
        public boolean hasNext() throws ValidationException
        {
            if (_peeked != null)
            {
                return true;
            }
            return _i1 == null ? _i2.hasNext() : _i1.hasNext();
        }

        @Override
        public Map<String, Object> next() throws ValidationException
        {
            Map<String, Object> result = fetch();
            _currentRowNumber++;
            return result;
        }

        private Map<String, Object> fetch() throws ValidationException
        {
            if (_peeked != null)
            {
                Map<String, Object> result = _peeked;
                _peeked = null;
                return result;
            }
            return _i1 == null ? _i2.next() : _i1.next();
        }

        @Override
        public @Nullable Map<String, Object> peek() throws ValidationException
        {
            if (_peeked == null && hasNext())
            {
                _peeked = fetch();
            }
            return _peeked;
        }

        @Override
        public int getCurrentRowNumber()
        {
            return _currentRowNumber;
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
