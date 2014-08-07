package org.labkey.api.iterator;

import org.apache.commons.collections15.iterators.ArrayIterator;
import org.apache.commons.collections15.iterators.IteratorChain;
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
public class MarkableIterator<T> implements Iterator<T>
{
    private Iterator<T> _iter;
    private boolean _marked = false;
    private SimpleLinkedList _buffer = null;

    public MarkableIterator(Iterator<T> iter)
    {
        _iter = iter;
    }

    @Override
    public boolean hasNext()
    {
        return _iter.hasNext();
    }

    @Override
    public T next()
    {
        T value = _iter.next();

        if (_marked)
        {
            _buffer.add(value);
        }

        return value;
    }

    public void mark()
    {
        _marked = true;
        _buffer = new SimpleLinkedList();
    }

    public void reset()
    {
        verifyMarked();
        _marked = false;
        _iter = new IteratorChain<>(_buffer.iterator(), _iter);
    }

    public void clearMark()
    {
        verifyMarked();
        _marked = false;
        _buffer = null;
    }

    private void verifyMarked()
    {
        if (!_marked)
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
        private Node _head = new Node(null);
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

        private T remove()
        {
            Node node = _head.getNext();

            if (null != node)
            {
                _head.setNext(node.getNext());
                return node.getValue();
            }

            return null;
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

        private class SimpleLinkedListIterator implements Iterator<T>
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

            // Remove all nodes between _head and _current in the underlying list
            void removeAllVisited()
            {
                // Just link _head to the upcoming node, orphaning all intervening nodes. Garbage collector should reclaim.
                _head.setNext(_current.getNext());
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
