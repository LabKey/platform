/*
 * Copyright (c) 2026 LabKey Corporation
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
package org.labkey.api.dataiterator;

import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.query.BatchValidationException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Reads a window of rows ahead and hands each one to {@link #collectRow()}, then calls {@link #prefetch()} before the
 * window's rows are returned, so a subclass can resolve the window's values in bulk.
 * Construct directly: {@link CachingDataIterator#wrap} returns a scrollable input as is, without the look-ahead.
 */
public abstract class LookAheadDataIterator extends CachingDataIterator
{
    private final int _windowSize;
    private int _windowEnd = -1;    // position of the last row read ahead
    private Exception _failure;     // thrown by the input while reading ahead; rethrown when its row is reached

    protected LookAheadDataIterator(DataIterator in, int windowSize)
    {
        super(in);
        _windowSize = windowSize;
    }

    /** Lets a class other than a subclass gather and resolve each window */
    public interface LookAheadCallback
    {
        /** Reads the current row of {@code lookAhead} while reading ahead */
        void collectRow(DataIterator lookAhead);

        /** Resolves what {@link #collectRow} gathered for the window */
        void prefetch();
    }

    public static LookAheadDataIterator of(DataIterator in, int windowSize, LookAheadCallback callback)
    {
        return new LookAheadDataIterator(in, windowSize)
        {
            @Override
            protected void collectRow()
            {
                callback.collectRow(this);
            }

            @Override
            protected void prefetch()
            {
                callback.prefetch();
            }
        };
    }

    /** Reads the current row while reading ahead */
    protected abstract void collectRow();

    /** Resolves what {@link #collectRow()} gathered for the window */
    protected abstract void prefetch();

    @Override
    public boolean next() throws BatchValidationException
    {
        if (_currentPosition >= _windowEnd)
        {
            if (_failure == null && !atEndOfInput)
                readAhead();

            if (_failure != null && _currentPosition >= _windowEnd)
                rethrowFailure();
        }
        return super.next();
    }

    @Override
    public boolean isScrollable()
    {
        return false;
    }

    @Override
    public void beforeFirst()
    {
        throw new UnsupportedOperationException("Rows before the current window are no longer cached");
    }

    private void readAhead()
    {
        mark();
        int rowsRead = 0;
        try
        {
            while (rowsRead < _windowSize && super.next())
            {
                collectRow();
                rowsRead++;
            }
        }
        catch (BatchValidationException | RuntimeException e)
        {
            // Earlier rows must still reach the consumer first, so their errors are reported in order
            _failure = e;
        }
        _windowEnd = _markPosition + rowsRead - 1;
        reset();

        if (rowsRead > 0)
            prefetch();
    }

    private void rethrowFailure() throws BatchValidationException
    {
        if (_failure instanceof BatchValidationException bve)
            throw bve;
        throw (RuntimeException) _failure;
    }

    public static class TestCase extends Assert
    {
        private static String[] row(String... values)
        {
            return values;
        }

        /** Records the order of reads and prefetches, so tests can check that each window is prefetched before its rows are returned */
        private static class RecordingIterator extends LookAheadDataIterator
        {
            final List<String> events = new ArrayList<>();
            final List<String> window = new ArrayList<>();

            RecordingIterator(DataIterator in, int windowSize)
            {
                super(in, windowSize);
            }

            @Override
            protected void collectRow()
            {
                window.add((String) get(1));
            }

            @Override
            protected void prefetch()
            {
                events.add("prefetch " + window);
                window.clear();
            }

            List<String> readAll() throws BatchValidationException
            {
                while (next())
                    events.add("row " + get(1));
                return events;
            }
        }

        private static DataIterator data(int rowCount)
        {
            List<String[]> rows = new ArrayList<>();
            for (int i = 1; i <= rowCount; i++)
                rows.add(row(String.valueOf(i)));
            return new StringTestIterator(List.of("Value"), rows);
        }

        @Test
        public void prefetchesEachWindowBeforeItsRows() throws Exception
        {
            assertEquals(
                List.of("prefetch [1, 2]", "row 1", "row 2", "prefetch [3, 4]", "row 3", "row 4", "prefetch [5]", "row 5"),
                new RecordingIterator(data(5), 2).readAll()
            );
        }

        @Test
        public void exactWindowsAndEmptyInput() throws Exception
        {
            assertEquals(List.of("prefetch [1, 2]", "row 1", "row 2"), new RecordingIterator(data(2), 2).readAll());
            assertEquals(List.of(), new RecordingIterator(data(0), 2).readAll());
        }

        @Test
        public void rowNumbersAndValuesPassThrough() throws Exception
        {
            var it = new RecordingIterator(new StringTestIterator(List.of("A", "B"), Arrays.asList(row("a1", "b1"), row("a2", "b2"), row("a3", "b3"))), 2);
            for (int i = 1; i <= 3; i++)
            {
                assertTrue(it.next());
                assertEquals(i, it.get(0));
                assertEquals("a" + i, it.get(1));
                assertEquals("b" + i, it.get(2));
            }
            assertFalse(it.next());
            assertFalse(it.next());
        }

        @Test
        public void notScrollable() throws Exception
        {
            RecordingIterator it = new RecordingIterator(data(3), 2);
            it.readAll();
            assertFalse(it.isScrollable());
            assertThrows(UnsupportedOperationException.class, it::beforeFirst);
            assertFalse(DataIteratorUtil.wrapScrollable(new RecordingIterator(data(3), 2)) instanceof LookAheadDataIterator);
        }

        private static DataIterator failsOnRow3()
        {
            return new WrapperDataIterator(data(5))
            {
                @Override
                public boolean next() throws BatchValidationException
                {
                    boolean ret = super.next();
                    if (ret && Integer.valueOf(3).equals(get(0)))
                        throw new IllegalStateException("bad row 3");
                    return ret;
                }
            };
        }

        @Test
        public void inputFailureSurfacesAtItsRow() throws Exception
        {
            // Window size 10 fails mid-window; window size 2 fails reading ahead the second window
            for (int windowSize : List.of(10, 2))
            {
                RecordingIterator it = new RecordingIterator(failsOnRow3(), windowSize);
                try
                {
                    it.readAll();
                    fail("Expected the input's failure with window size " + windowSize);
                }
                catch (IllegalStateException e)
                {
                    assertEquals("bad row 3", e.getMessage());
                }
                assertEquals("Window size " + windowSize, List.of("prefetch [1, 2]", "row 1", "row 2"), it.events);
            }
        }
    }
}
