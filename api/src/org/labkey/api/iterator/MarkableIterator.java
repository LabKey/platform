package org.labkey.api.iterator;

import org.apache.commons.collections15.iterators.ArrayIterator;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * User: adam
 * Date: 8/1/2014
 * Time: 5:04 PM
 */

// An Iterator wrapper that supports mark-reset functionality, similar to BufferedInputStream and BufferedReader. Can be
// used with any Iterator to read elements and then back up, allowing them to be read again. Could be useful for inferring
// type information or column headers before actually reading data.
public class MarkableIterator<T> implements Iterator<T>
{
    // There are simpler ways to do this (e.g., building an IteratorChain<>(_buffer.iterator(), _iter) on every reset()
    // would be cleaner), but more bookkeepping minimizes memory usage: we reuse the same buffer for repeated mark/reset
    // combinations and (if not currently marked) we clear the buffer as we iterate over it.

    // A marked iterator stores elements into _buffer as they are read. On reset(), _bufferedIter is set to iterate over
    // _buffer, and subsequent hasNext() and next() calls exhaust _bufferedIter and then defer back to _iter. On reset,
    // _buffer is left alone; if the iterator is marked again while iterating _bufferedIter we'll just reuse the existing
    // buffer.

    private Iterator<T> _iter;

    // Indicates whether the MarkableIterator is marked or not
    private boolean _marked = false;

    private SimpleLinkedList _buffer = null;
    private SimpleLinkedList.SimpleLinkedListIterator _bufferIter = null;

    public MarkableIterator(Iterator<T> iter)
    {
        _iter = iter;
    }

    @Override
    public boolean hasNext()
    {
        return bufferIterHasNext() || _iter.hasNext();
    }

    @Override
    public T next()
    {
        if (bufferIterHasNext())
        {
            T value = _bufferIter.next();

            if (!_marked)
                _bufferIter.remove();

            return value;
        }

        T value = _iter.next();

        if (_marked)
            _buffer.add(value);

        return value;
    }

    private boolean bufferIterHasNext()
    {
        if (null != _bufferIter)
        {
            if (_bufferIter.hasNext())
                return true;

            _bufferIter = null;
        }

        return false;
    }

    @Override
    public void remove()
    {
        throw new UnsupportedOperationException("remove");
    }

    public void mark()
    {
        if (null == _bufferIter)
            _buffer = new SimpleLinkedList();
        else
            _bufferIter.removeAllVisited();

        _marked = true;
    }

    public void reset()
    {
        verifyMarked();
        _bufferIter = _buffer.iterator();
        _marked = false;
    }

    public void clearMark()
    {
        verifyMarked();
        _buffer = null;
        _marked = false;
    }

    private void verifyMarked()
    {
        if (!_marked || null == _buffer)
            throw new IllegalStateException("Not marked");
    }


    private class Node
    {
        private final T _value;
        private Node _next = null;

        private Node(T value)
        {
            _value = value;
        }

        public T getValue()
        {
            return _value;
        }

        public Node getNext()
        {
            return _next;
        }

        public void setNext(Node next)
        {
            _next = next;
        }
    }


    private class SimpleLinkedList implements Iterable<T>
    {
        private final Node _head = new Node(null);
        private Node _tail = _head;

        private SimpleLinkedList()
        {
        }

        private void add(T value)
        {
            Node newNode = new Node(value);
            _tail.setNext(newNode);
            _tail = newNode;
        }

        @Override
        public String toString()
        {
            StringBuilder sb = new StringBuilder();

            for (T value : this)
                sb.append(value);

            return sb.toString();
        }

        @Override
        public SimpleLinkedListIterator iterator()
        {
            return new SimpleLinkedListIterator();
        }

        class SimpleLinkedListIterator implements Iterator<T>
        {
            private Node _current = _head;

            @Override
            public boolean hasNext()
            {
                return null != _current.getNext();
            }

            @Override
            public T next()
            {
                _current = _current.getNext();

                if (null == _current)
                    throw new NoSuchElementException();

                return _current.getValue();
            }

            // Remove all nodes in the underlying list between _head and _current
            void removeAllVisited()
            {
                // Just link _head to the upcoming node, orphaning all intervening nodes. Garbage collector should reclaim.
                Node next = _current.getNext();
                _head.setNext(next);
                if (null == next)
                    _tail = _head;
            }

            @Override
            public void remove()
            {
                removeAllVisited();
            }
        }
    }


    public static class TestCase extends Assert
    {
        private final String[] _array = {"A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z"};
        private MarkableIterator<String> _iter;

        @Before
        public void init()
        {
            _iter = new MarkableIterator<>(new ArrayIterator<String>(_array));
        }

        @Test
        public void testBasicOperations()
        {
            _iter.mark();
            assertEquals("A", _iter.next());
            assertEquals("B", _iter.next());
            assertEquals("C", _iter.next());
            assertEquals("D", _iter.next());
            assertEquals("E", _iter.next());
            assertEquals("F", _iter.next());
            assertEquals("G", _iter.next());
            assertEquals("H", _iter.next());
            _iter.reset();
            assertEquals("A", _iter.next());
            assertEquals("B", _iter.next());
            assertEquals("C", _iter.next());
            assertEquals("D", _iter.next());
            _iter.mark();
            assertEquals("E", _iter.next());
            assertEquals("F", _iter.next());
            assertEquals("G", _iter.next());
            assertEquals("H", _iter.next());
            assertEquals("I", _iter.next());
            assertEquals("J", _iter.next());
            assertEquals("K", _iter.next());
            assertEquals("L", _iter.next());
            assertEquals("M", _iter.next());
            assertEquals("N", _iter.next());
            assertEquals("O", _iter.next());
            assertEquals("P", _iter.next());
            _iter.reset();
            assertEquals("E", _iter.next());
            assertEquals("F", _iter.next());
            assertEquals("G", _iter.next());
            assertEquals("H", _iter.next());
            assertEquals("I", _iter.next());
            assertEquals("J", _iter.next());
            assertEquals("K", _iter.next());
            _iter.mark();
            assertEquals("L", _iter.next());
            assertEquals("M", _iter.next());
            assertEquals("N", _iter.next());
            _iter.mark();
            assertEquals("O", _iter.next());
            assertEquals("P", _iter.next());
            assertEquals("Q", _iter.next());
            assertEquals("R", _iter.next());
            assertEquals("S", _iter.next());
            assertEquals("T", _iter.next());
            _iter.reset();
            assertEquals("O", _iter.next());
            assertEquals("P", _iter.next());
            assertEquals("Q", _iter.next());
            assertEquals("R", _iter.next());
            assertEquals("S", _iter.next());
            assertEquals("T", _iter.next());
            _iter.mark();
            assertEquals("U", _iter.next());
            assertEquals("V", _iter.next());
            assertEquals("W", _iter.next());
            _iter.clearMark();
            assertEquals("X", _iter.next());
            assertEquals("Y", _iter.next());
            _iter.mark();
            assertEquals("Z", _iter.next());
            _iter.reset();
            assertEquals("Z", _iter.next());
        }

        @Test
        public void stressTest1()
        {
            for (String value : _array)
            {
                _iter.mark();
                assertEquals(value, _iter.next());
                _iter.reset();
                assertEquals(value, _iter.next());
            }
        }

        @Test
        public void stressTest2()
        {
            for (String value : _array)
            {
                _iter.mark();

                while (_iter.hasNext())
                    _iter.next();

                _iter.reset();
                assertEquals(value, _iter.next());
            }
        }

        @Test(expected = IllegalStateException.class)
        public void testReset1()
        {
            _iter.reset();
        }

        @Test(expected = IllegalStateException.class)
        public void testReset2()
        {
            assertEquals("A", _iter.next());
            assertEquals("B", _iter.next());
            assertEquals("C", _iter.next());
            _iter.reset();
        }

        @Test(expected = IllegalStateException.class)
        public void testReset3()
        {
            _iter.mark();
            assertEquals("A", _iter.next());
            assertEquals("B", _iter.next());
            assertEquals("C", _iter.next());
            _iter.reset();
            assertEquals("A", _iter.next());
            assertEquals("B", _iter.next());
            _iter.reset();
        }

        @Test
        public void testMark1()
        {
            _iter.mark();
            _iter.mark();
        }

        @Test(expected = IllegalStateException.class)
        public void testClearMark1()
        {
            _iter.clearMark();
        }

        @Test(expected = IllegalStateException.class)
        public void testClearMark2()
        {
            assertEquals("A", _iter.next());
            assertEquals("B", _iter.next());
            assertEquals("C", _iter.next());
            _iter.clearMark();
        }

        @Test(expected = IllegalStateException.class)
        public void testClearMark3()
        {
            assertEquals("A", _iter.next());
            assertEquals("B", _iter.next());
            assertEquals("C", _iter.next());
            _iter.clearMark();
            _iter.clearMark();
        }
    }
}
