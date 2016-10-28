/*
 * Copyright (c) 2011-2016 LabKey Corporation
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

/**
 * User: matthewb
 * Date: 2011-05-31
 * Time: 5:11 PM
 */
public class CachingDataIterator extends AbstractDataIterator implements ScrollableDataIterator
{
    DataIterator _in;
    int _columnCount;
    int _currentRow = -1;
    ArrayList<Object[]> _data = new ArrayList<>(100);
    boolean atEndOfInput = false;


    public static ScrollableDataIterator wrap(DataIterator in)
    {
        if (in instanceof ScrollableDataIterator && ((ScrollableDataIterator)in).isScrollable())
            return (ScrollableDataIterator)in;
        return new CachingDataIterator(in);
    }


    protected CachingDataIterator(DataIterator in)
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
    public void beforeFirst()
    {
        _currentRow = -1;
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
        ++_currentRow;
        if (_currentRow < _data.size())
            return true;
        if (atEndOfInput || !_in.next())
        {
            atEndOfInput = true;
            return false;
        }

        Object[] row = new Object[_columnCount +1];
        for (int i=0 ; i<= _columnCount; ++i)
            row[i] = _in.get(i);
        _data.add(row);
        return true;
    }

    @Override
    public Object get(int i)
    {
        return _data.get(_currentRow)[i];
    }

    @Override
    public void close() throws IOException
    {
        _in.close();
    }

    @Override
    public void debugLogInfo(StringBuilder sb)
    {
        sb.append(this.getClass().getName() + "\n");
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
    }
}
