/*
 * Copyright (c) 2010-2011 LabKey Corporation
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
package org.labkey.api.data;

import junit.framework.Assert;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.collections15.iterators.ArrayIterator;
import org.junit.Test;
import org.labkey.api.query.FieldKey;
import org.labkey.api.view.ViewContext;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * User: adam
 * Date: Sep 7, 2010
 * Time: 10:02:25 AM
 */
public class MultiValuedRenderContext extends RenderContextDecorator
{
    private final Map<FieldKey, Iterator<String>> _iterators = new HashMap<FieldKey, Iterator<String>>();
    private final Map<FieldKey, String> _currentValues = new HashMap<FieldKey, String>();

    public MultiValuedRenderContext(RenderContext ctx, Set<FieldKey> requiredFieldKeys)
    {
        super(ctx);

        // For each required column (e.g., display value, rowId), retrieve the concatenated values, split them, and
        // stash away an iterator of those values.
        int length = -1;
        Set<FieldKey> nullFieldKeys = new HashSet<FieldKey>();
        for (FieldKey fieldKey : requiredFieldKeys)
        {
            String valueString = (String)ctx.get(fieldKey);
            if (valueString == null)
            {
                nullFieldKeys.add(fieldKey);
            }
            else
            {
                String[] values = valueString.split(",");
                if (length != -1 && values.length != length)
                {
                    throw new IllegalStateException("Expected all columns to have the same number of values, but '" + fieldKey + "' has " + values.length + " and " + _iterators.keySet() + " had " + length);
                }
                length = values.length;
                _iterators.put(fieldKey, new ArrayIterator<String>(values));
            }

            for (FieldKey nullFieldKey : nullFieldKeys)
            {
                _iterators.put(nullFieldKey, new ArrayIterator<String>(new String[length == -1 ? 0 : length]));
            }
        }
    }

    // Advance all the iterators, if another value is present.  Check that all iterators are in lock step.
    public boolean next()
    {
        Boolean previousHasNext = null;

        for (Map.Entry<FieldKey, Iterator<String>> entry : _iterators.entrySet())
        {
            Iterator<String> iter = entry.getValue();
            boolean hasNext = iter.hasNext();

            if (hasNext)
                _currentValues.put(entry.getKey(), iter.next());

            if (null == previousHasNext)
                previousHasNext = hasNext;
            else
                assert previousHasNext == hasNext : "Mismatch in number of values for " + entry.getKey() + " compared with other fields: " + _iterators.keySet();
        }

        return null != previousHasNext && previousHasNext;
    }

    @Override
    public Object get(Object key)
    {
        Object value = _currentValues.get(key);

        if (null != value)
        {
            ColumnInfo columnInfo = getFieldMap().get(key);
            // The value was concatenated with others, so it's become a string.
            // Do conversion to switch it back to the expected type. 
            if (columnInfo != null && !columnInfo.getJavaClass().isInstance(value))
            {
                value = ConvertUtils.convert(value.toString(), columnInfo.getJavaClass());
            }
        }
        else
        {
            value = super.get(key);
        }

        return value;
    }

    public static class TestCase extends Assert
    {
        private FieldKey _fk1 = FieldKey.fromParts("Parent", "Child");
        private FieldKey _fk2 = FieldKey.fromParts("Standalone");
        private FieldKey _otherFK = FieldKey.fromParts("NotInRow");

        @Test
        public void testMatchingValues()
        {
            Set<FieldKey> fieldKeys = new HashSet<FieldKey>();
            fieldKeys.add(_fk1);
            fieldKeys.add(_fk2);
            Map<FieldKey, String> values = new HashMap<FieldKey, String>();
            values.put(_fk1, "1,2,3");
            values.put(_fk2, "a,b,c");
            MultiValuedRenderContext mvContext = new MultiValuedRenderContext(new TestRenderContext(values), fieldKeys);
            assertTrue(mvContext.next());
            assertEquals(1, mvContext.get(_fk1));
            assertEquals("a", mvContext.get(_fk2));
            assertTrue(mvContext.next());
            assertEquals(2, mvContext.get(_fk1));
            assertEquals("b", mvContext.get(_fk2));
            assertTrue(mvContext.next());
            assertEquals(3, mvContext.get(_fk1));
            assertEquals("c", mvContext.get(_fk2));
            assertFalse(mvContext.next());
        }

        @Test
        public void testMissingColumn()
        {
            // Be sure that if there's a column that couldn't be found, we don't blow up
            Set<FieldKey> fieldKeys = new HashSet<FieldKey>();
            fieldKeys.add(_fk1);
            fieldKeys.add(_fk2);
            fieldKeys.add(_otherFK);
            Map<FieldKey, String> values = new HashMap<FieldKey, String>();
            values.put(_fk1, "1,2,3");
            values.put(_fk2, "a,b,c");
            MultiValuedRenderContext mvContext = new MultiValuedRenderContext(new TestRenderContext(values), fieldKeys);
            assertTrue(mvContext.next());
            assertEquals(1, mvContext.get(_fk1));
            assertEquals("a", mvContext.get(_fk2));
            assertNull(mvContext.get(_otherFK));
            assertTrue(mvContext.next());
            assertEquals(2, mvContext.get(_fk1));
            assertEquals("b", mvContext.get(_fk2));
            assertNull(mvContext.get(_otherFK));
            assertTrue(mvContext.next());
            assertEquals(3, mvContext.get(_fk1));
            assertEquals("c", mvContext.get(_fk2));
            assertNull(mvContext.get(_otherFK));
            assertFalse(mvContext.next());
        }

        @Test
        public void testMismatchedValues()
        {
            Set<FieldKey> fieldKeys = new HashSet<FieldKey>();
            fieldKeys.add(_fk1);
            fieldKeys.add(_fk2);
            Map<FieldKey, String> values = new HashMap<FieldKey, String>();
            values.put(_fk1, "1,2,3");
            values.put(_fk2, "a,b,c,d");
            try
            {
                new MultiValuedRenderContext(new TestRenderContext(values), fieldKeys);
                fail("Should have gotten an exception");
            }
            catch (IllegalStateException ignored) {}
        }

        private class TestRenderContext extends RenderContext
        {
            private Map<FieldKey, String> _values;

            public TestRenderContext(Map<FieldKey, String> values)
            {
                _values = values;
            }

            @Override
            public Object get(Object key)
            {
                return _values.get(key);
            }

            @Override
            public Map<FieldKey, ColumnInfo> getFieldMap()
            {
                Map<FieldKey, ColumnInfo> result = new HashMap<FieldKey, ColumnInfo>();
                ColumnInfo col1 = new ColumnInfo(_fk1);
                col1.setJdbcType(JdbcType.INTEGER);
                result.put(_fk1, col1);
                ColumnInfo col2 = new ColumnInfo(_fk2);
                col2.setJdbcType(JdbcType.VARCHAR);
                result.put(_fk2, col2);
                return result;
            }
        }
    }
}
