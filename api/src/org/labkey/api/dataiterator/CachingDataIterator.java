/*
 * Copyright (c) 2016-2019 LabKey Corporation
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
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.util.GUID;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.Supplier;

/**
 * User: matthewb
 * Date: 2011-05-31
 * Time: 5:11 PM
 */
public class CachingDataIterator extends AbstractDataIterator implements ScrollableDataIterator
{
    DataIterator _in;
    int _columnCount;

    int _currentPosition = -1;      // the zero-based position of the current row
    int _markPosition = 0;          // the zero-based position of the first row in arraylist _data
    int _inputPosition = -1;        // the zero-based position of input
    Object[] _currentRowArray = null;

    protected static class _ArrayList<T> extends ArrayList<T>
    {
        @Override
        public void removeRange(int fromIndex, int toIndex)
        {
            super.removeRange(fromIndex, toIndex);
        }
    }
    protected _ArrayList<Object[]> _data = new _ArrayList<>();
    boolean atEndOfInput = false;


    public static ScrollableDataIterator wrap(DataIterator in)
    {
        if (in instanceof ScrollableDataIterator sdi && sdi.isScrollable())
            return sdi;
        return new CachingDataIterator(in);
    }

    public static ScrollableDataIterator wrap(DataIterator in, boolean overflowToDisk)
    {
        if (in instanceof ScrollableDataIterator sdi && sdi.isScrollable())
            return sdi;
        return new DiskCachingDataIterator(in);
    }


    public CachingDataIterator(DataIterator in)
    {
        super(null);
        _in = in;
        _columnCount = in.getColumnCount();
    }

    @Override
    public boolean isScrollable()
    {
        return true;
    }

    @Override
    public boolean supportsGetExistingRecord()
    {
        return _in.supportsGetExistingRecord();
    }

    @Override
    public void beforeFirst()
    {
        _currentPosition = -1;
    }

    protected void mark()
    {
        int markPos = _currentPosition +1;   // index of first row that must be cached
        if (markPos == _markPosition)
            return;
        int index = markPos-_markPosition;
        _data.removeRange(0, index);
        _markPosition = markPos;
    }

    /* scroll back to row before mark (caller must immediately call next()), same as beforeFirst() if mark() has not been called */
    protected void reset()
    {
        _currentPosition = _markPosition-1;
        _currentRowArray = null;
    }

    @Override
    public int getColumnCount()
    {
        return _in.getColumnCount();
    }

    @Override
    public ColumnInfo getColumnInfo(int i)
    {
        return _in.getColumnInfo(i);
    }

    @Override
    public boolean next() throws BatchValidationException
    {
        ++_currentPosition;
        int index = _currentPosition - _markPosition;
        if (index >= _data.size())
        {
            populateRows();
            index = _currentPosition - _markPosition;
            if (index >= _data.size())
                return false;
        }
        _currentRowArray = _data.get(index);
        return true;
    }

    /* add one or more rows to _data array to satisfy next() request */
    protected void populateRows() throws BatchValidationException
    {
        if (atEndOfInput || !_in.next())
        {
            _currentRowArray = null;
            atEndOfInput = true;
            return;
        }
        _inputPosition++;
        Object[] row = new Object[_columnCount +1];
        for (int i=0 ; i<= _columnCount; ++i)
            row[i] = _in.get(i);
        _data.add(row);
    }

    @Override
    public Object get(int i)
    {
        if (_currentRowArray == null)
        {
            // No row available
            if (i == 0)
            {
                // Use null to signal we're not on a real data row
                return null;
            }
            // Otherwise, no value available to share
            throw new IllegalStateException("No current row");
        }
        return _currentRowArray[i];
    }

    @Override
    public Supplier<Object> getSupplier(int i)
    {
        return ()->_currentRowArray[i];
    }

    @Override
    public void close() throws IOException
    {
        _in.close();
    }

    @Override
    public void debugLogInfo(StringBuilder sb)
    {
        super.debugLogInfo(sb);
        if (null != _in)
            _in.debugLogInfo(sb);
    }


    private static String[] as(String... arr)
    {
      return arr;
    }

    public static class ScrollTestCase extends Assert
    {
        StringTestIterator simpleData = new StringTestIterator
        (
            Arrays.asList("IntNotNull", "Text", "EntityId", "Int"),
            Arrays.asList(
                as("1", "one", GUID.makeGUID(), ""),
                as("2", "two", GUID.makeGUID(), "/N"),
                as("3", "three", GUID.makeGUID(), "3")
            )
        );


        @Test
        public void scrollTest() throws Exception
        {
            simpleData.setScrollable(false);

            ScrollableDataIterator scrollable = CachingDataIterator.wrap(simpleData);
            assertTrue(scrollable.next());
            assertEquals("1", scrollable.get(1));
            assertEquals("", scrollable.get(4));
            scrollable.beforeFirst();
            assertTrue(scrollable.next());
            assertTrue(scrollable.next());
            assertTrue(scrollable.next());
            assertFalse(scrollable.next());
            scrollable.beforeFirst();
            assertTrue(scrollable.next());
            assertTrue(scrollable.next());
            assertTrue(scrollable.next());
            assertEquals("3", scrollable.get(1));
            assertEquals("3", scrollable.get(4));
            assertFalse(scrollable.next());
        }

        @Test
        public void markTest() throws Exception
        {
            simpleData.setScrollable(false);

            CachingDataIterator scrollable = (CachingDataIterator)CachingDataIterator.wrap(simpleData);
            assertTrue(scrollable.next());
            assertEquals("1", scrollable.get(1));

            scrollable.mark();

            assertTrue(scrollable.next());
            assertEquals("2", scrollable.get(1));

            scrollable.reset();

            assertTrue(scrollable.next());
            assertEquals("2", scrollable.get(1));
            assertTrue(scrollable.next());
            assertEquals("3", scrollable.get(1));
        }
    }
}
