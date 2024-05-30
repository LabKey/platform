package org.labkey.api.iterator;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public interface ValidatingDataRowIterator extends Closeable
{
    boolean hasNext() throws ValidationException;

    Map<String, Object> next() throws ValidationException;

    @Nullable
    Map<String, Object> peek() throws ValidationException;

    /** The zero-based number of the most recent next() invocation. -1 if no data has been returned yet */
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

    class TestCase
    {
        @Test
        public void testPeek() throws ValidationException
        {
            try (ValidatingDataRowIterator iter = ValidatingDataRowIterator.of(createMaps(1, 3)))
            {
                assertTrue(iter.hasNext());
                assertEquals(-1, iter.getCurrentRowNumber());
                assertEquals(createMap(1), iter.peek());
                assertEquals(createMap(1), iter.peek());
                assertTrue(iter.hasNext());
                assertEquals(-1, iter.getCurrentRowNumber());
                assertEquals(createMap(1), iter.next());
                assertEquals(0, iter.getCurrentRowNumber());
                assertTrue(iter.hasNext());
                assertEquals(createMap(2), iter.peek());
                assertEquals(createMap(2), iter.next());
                assertTrue(iter.hasNext());
                assertEquals(createMap(3), iter.peek());
                assertEquals(createMap(3), iter.next());
                assertFalse(iter.hasNext());
                assertNull(iter.peek());
                assertEquals(2, iter.getCurrentRowNumber());
            }
        }

        @Test
        public void testConcat() throws ValidationException
        {
            try (ValidatingDataRowIterator i1 = ValidatingDataRowIterator.of(createMaps(1, 2));
               ValidatingDataRowIterator i2 = ValidatingDataRowIterator.of(createMaps(3, 4));
               ValidatingDataRowIterator i3 = ValidatingDataRowIterator.concat(i1, i2))
            {
                assertTrue(i3.hasNext());
                assertEquals(-1, i1.getCurrentRowNumber());
                assertEquals(-1, i2.getCurrentRowNumber());
                assertEquals(-1, i3.getCurrentRowNumber());
                assertEquals(createMap(1), i3.next());

                assertEquals(0, i1.getCurrentRowNumber());
                assertEquals(-1, i2.getCurrentRowNumber());
                assertEquals(0, i3.getCurrentRowNumber());
                assertTrue(i3.hasNext());
                assertEquals(createMap(2), i3.next());
                assertTrue(i3.hasNext());
                assertEquals(createMap(3), i3.next());
                assertTrue(i3.hasNext());
                assertEquals(createMap(4), i3.next());

                assertEquals(1, i1.getCurrentRowNumber());
                assertEquals(1, i2.getCurrentRowNumber());
                assertEquals(3, i3.getCurrentRowNumber());
                assertFalse(i3.hasNext());
            }
        }

        private List<Map<String, Object>> createMaps(int start, int end)
        {
            List<Map<String, Object>> result = new ArrayList<>();
            for (int i = start; i <= end; i++)
            {
                result.add(createMap(i));
            }
            return result;
        }

        private static @NotNull Map<String, Object> createMap(int i)
        {
            return Map.of("Row", i);
        }
    }
}
