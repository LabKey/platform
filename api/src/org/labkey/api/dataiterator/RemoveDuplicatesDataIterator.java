/*
 * Copyright (c) 2012-2016 LabKey Corporation
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
import org.labkey.api.util.GUID;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * User: matthewb
 * Date: 2012-05-10
 * Time: 12:42 PM
 *
 * Currently removes adjacent duplicate rows. It can be extended to remove non-adjacent duplicates if required.
 */
public class RemoveDuplicatesDataIterator extends WrapperDataIterator
{
    boolean _beforeFirst = true;
    ArrayList<Object> _previous;
    ArrayList<Object> _current;

    RemoveDuplicatesDataIterator(DataIterator di)
    {
        super(di);
        _previous = new ArrayList<>(getColumnCount()+1);
        _current = new ArrayList<>(getColumnCount()+1);
    }

    @Override
    public boolean next() throws BatchValidationException
    {
        while (super.next())
        {
            if (!isDuplicateRow())
                return true;
        }
        return false;
    }

    @Override
    public boolean isScrollable()
    {
        return false;
    }

    protected boolean isDuplicateRow()
    {
        int count = getColumnCount();
        while (_current.size() <= count)
            _current.add(null);

        // get normalized current row values
        for (int i=1 ; i<=count ; i++)
            _current.set(i, _normalize(get(i)));
        // check if duplicate
        if (_beforeFirst)
            return swap();
        for (int i=1 ; i<=count ; i++)
            if (!_equal(_previous.get(i),_current.get(i)))
                return swap();
        // yup duplicate
        return true;
    }

    protected boolean _equal(Object a, Object b)
    {
        if (null == a || null == b)
            return a == b;
        return a.equals(b);
    }

    protected boolean swap()
    {
        _beforeFirst = false;
        ArrayList<Object> t = _previous;
        _previous = _current;
        _current = t;
        return false;
    }

    protected Object _normalize(Object o)
    {
        return o;
/*
        if (null == o)
            return o;
        if (o instanceof MvFieldWrapper)
        {
            MvFieldWrapper mv = (MvFieldWrapper)o;
            o = null==mv.getMvIndicator() ? mv.getValue() : mv.getMvIndicator();
        }
        if (o instanceof Number)
        {
            if (o instanceof Long)
                return ((Number)o).longValue();
            else
                return ((Number) o).doubleValue();
        }
        if (o instanceof Date)
        {
            if (o.getClass() == Date.class)
                return o;
            return new Date(((Date)o).getTime());
        }
        else
            return o;
*/
    }


    private static String[] as(String... arr)
    {
        return arr;
    }

    public static class DeDuplicateTestCase extends Assert
    {
        String a = GUID.makeGUID(), b = GUID.makeGUID(), c = GUID.makeGUID(), d = GUID.makeGUID();

        StringTestIterator simpleData = new StringTestIterator
        (
            Arrays.asList("IntNotNull", "Text", "EntityId", "Int"),
            Arrays.asList(
                as("1", "one", a, ""),
                as("2", "two", b, "/N"),
                as("2", "two", b, "/N"),
                as("3", "three", c, "3"),
                as("3", "three", c, "3"),
                as("3", "three", c, "3"),
                as("4", "four", "", "4"),
                as("4", "four", "", "4"),
                as("4", "four", "", "4"),
                as("4", "four", "", "4")
            )
        );

        public DeDuplicateTestCase()
        {
        }


        @Test
        public void test() throws Exception
        {
            simpleData.setScrollable(false);
            DataIterator dedup = new RemoveDuplicatesDataIterator(simpleData);
            for (int i=1 ; i<=4 ; i++)
            {
                assertTrue(dedup.next());
                assertEquals(String.valueOf(i), dedup.get(1));
            }
        }
    }


}
