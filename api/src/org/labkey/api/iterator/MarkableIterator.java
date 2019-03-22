/*
 * Copyright (c) 2014-2016 LabKey Corporation
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

import org.apache.commons.collections4.iterators.ArrayIterator;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * An Iterator wrapper that supports mark-reset functionality, similar to BufferedInputStream and BufferedReader. Can be
 * used with any Iterator to provide "look ahead", the ability to read elements and then back up, allowing those elements
 * to be re-read. Useful for inferring type information or column headers before actually processing data.
 * User: adam
 * Date: 8/1/2014
 */
public class MarkableIterator<T> implements Iterator<T>
{
    // There are simpler ways to do this (e.g., building an IteratorChain<>(_buffer.iterator(), _iter) on every reset()
    // would be cleaner), but more bookkeeping minimizes memory usage: we reuse the same buffer for repeated mark/reset
    // combinations and (if not currently marked) we remove elements from the buffer as we iterate through it.

    // A MarkableIterator that has been marked stores elements into _buffer as they are read. On reset(), _bufferIter is
    // set to iterate over _buffer, and subsequent hasNext() and next() calls exhaust _bufferIter and then defer back to
    // _iter. On reset, _buffer is drained as elements are read; if the iterator is marked again while iterating
    // _bufferIter we'll stop draining and maintain the previously buffered elements, appending to _buffer if we start
    // iterating unbuffered elements.

    private final Iterator<T> _iter;
    private final SimpleLinkedList _buffer = new SimpleLinkedList();

    // Indicates whether the MarkableIterator is marked or not
    private boolean _marked = false;

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
            _buffer.clear();
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
        _buffer.clear();
        _marked = false;
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

        private void clear()
        {
            _head.setNext(null);
            _tail = _head;
        }

        // For testing purposes only, not meant to be efficient
        private int size()
        {
            int size = 0;
            Node current = _head;

            while (null != current.getNext())
            {
                current = current.getNext();
                size++;
            }

            return size;
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
            _iter = new MarkableIterator<>(new ArrayIterator<>(_array));
        }

        private void assertBufferSize(int expected)
        {
            assertEquals(expected, _iter._buffer.size());
        }

        private void advanceAndTest(String expectedElement, int expectedBufferSize)
        {
            assertEquals(expectedElement, _iter.next());
            assertBufferSize(expectedBufferSize);
        }

        @Test
        public void testBasicOperations()
        {
            assertBufferSize(0);
            _iter.mark();
            assertBufferSize(0);
            advanceAndTest("A", 1);
            advanceAndTest("B", 2);
            advanceAndTest("C", 3);
            advanceAndTest("D", 4);
            advanceAndTest("E", 5);
            advanceAndTest("F", 6);
            advanceAndTest("G", 7);
            advanceAndTest("H", 8);
            _iter.reset();
            assertBufferSize(8);
            advanceAndTest("A", 7);
            advanceAndTest("B", 6);
            advanceAndTest("C", 5);
            advanceAndTest("D", 4);
            _iter.mark();
            assertBufferSize(4);
            advanceAndTest("E", 4);
            advanceAndTest("F", 4);
            advanceAndTest("G", 4);
            advanceAndTest("H", 4);
            advanceAndTest("I", 5);
            advanceAndTest("J", 6);
            advanceAndTest("K", 7);
            advanceAndTest("L", 8);
            advanceAndTest("M", 9);
            advanceAndTest("N", 10);
            advanceAndTest("O", 11);
            advanceAndTest("P", 12);
            _iter.reset();
            assertBufferSize(12);
            advanceAndTest("E", 11);
            advanceAndTest("F", 10);
            advanceAndTest("G", 9);
            advanceAndTest("H", 8);
            advanceAndTest("I", 7);
            advanceAndTest("J", 6);
            advanceAndTest("K", 5);
            _iter.mark();
            assertBufferSize(5);
            advanceAndTest("L", 5);
            advanceAndTest("M", 5);
            advanceAndTest("N", 5);
            _iter.mark();
            assertBufferSize(2);
            advanceAndTest("O", 2);
            advanceAndTest("P", 2);
            advanceAndTest("Q", 3);
            advanceAndTest("R", 4);
            advanceAndTest("S", 5);
            advanceAndTest("T", 6);
            _iter.reset();
            assertBufferSize(6);
            advanceAndTest("O", 5);
            advanceAndTest("P", 4);
            advanceAndTest("Q", 3);
            advanceAndTest("R", 2);
            advanceAndTest("S", 1);
            advanceAndTest("T", 0);
            advanceAndTest("U", 0);
            _iter.mark();
            assertBufferSize(0);
            advanceAndTest("V", 1);
            advanceAndTest("W", 2);
            _iter.clearMark();
            assertBufferSize(0);
            advanceAndTest("X", 0);
            advanceAndTest("Y", 0);
            _iter.mark();
            assertBufferSize(0);
            advanceAndTest("Z", 1);
            _iter.reset();
            assertBufferSize(1);
            advanceAndTest("Z", 0);
        }

        @Test
        public void stressTest1()
        {
            assertBufferSize(0);

            for (String value : _array)
            {
                _iter.mark();
                assertBufferSize(0);
                advanceAndTest(value, 1);
                _iter.reset();
                assertBufferSize(1);
                advanceAndTest(value, 0);
                assertBufferSize(0);
            }
        }

        @Test
        public void stressTest2()
        {
            int bufferSize = 0;
            assertBufferSize(bufferSize);
            boolean firstIteration = true;

            for (String value : _array)
            {
                _iter.mark();

                // Buffer should be empty first time through, then one element smaller each iteration
                int expected = firstIteration ? 0 : 91 - value.charAt(0);
                assertEquals(expected, bufferSize);
                assertBufferSize(bufferSize);

                while (_iter.hasNext())
                {
                    _iter.next();
                    assertBufferSize(firstIteration ? ++bufferSize : bufferSize);
                }

                _iter.reset();
                assertBufferSize(bufferSize);
                advanceAndTest(value, --bufferSize);

                firstIteration = false;
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
            advanceAndTest("A", 0);
            advanceAndTest("B", 0);
            advanceAndTest("C", 0);
            _iter.reset();
        }

        @Test(expected = IllegalStateException.class)
        public void testReset3()
        {
            assertBufferSize(0);
            _iter.mark();
            assertBufferSize(0);
            advanceAndTest("A", 1);
            advanceAndTest("B", 2);
            advanceAndTest("C", 3);
            _iter.reset();
            assertBufferSize(3);
            advanceAndTest("A", 2);
            advanceAndTest("B", 1);
            _iter.reset();
        }

        @Test(expected = IllegalStateException.class)
        public void testReset4()
        {
            assertBufferSize(0);
            _iter.mark();
            assertBufferSize(0);
            advanceAndTest("A", 1);
            advanceAndTest("B", 2);
            advanceAndTest("C", 3);
            _iter.reset();
            assertBufferSize(3);
            advanceAndTest("A", 2);
            advanceAndTest("B", 1);
            advanceAndTest("C", 0);
            advanceAndTest("D", 0);
            advanceAndTest("E", 0);
            _iter.reset();
        }

        @Test
        public void testMark1()
        {
            assertBufferSize(0);
            _iter.mark();
            assertBufferSize(0);
            _iter.mark();
            assertBufferSize(0);
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
