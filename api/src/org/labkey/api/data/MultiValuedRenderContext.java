/*
 * Copyright (c) 2010-2017 LabKey Corporation
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

import org.apache.commons.collections4.iterators.ArrayIterator;
import org.junit.Assert;
import org.apache.commons.beanutils.ConvertUtils;
import org.junit.Test;
import org.labkey.api.query.FieldKey;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Wrapper for another {@link RenderContext} that allows multiple values to be rendered for a single row's column.
 * Used in conjunction with {@link MultiValuedDisplayColumn}.
 * User: adam
 * Date: Sep 7, 2010
 * Time: 10:02:25 AM
 */
public class MultiValuedRenderContext extends RenderContextDecorator
{
    private final Map<FieldKey, Iterator<String>> _iterators = new HashMap<>();
    private final Map<FieldKey, String> _currentValues = new HashMap<>();

    public static final String VALUE_DELIMITER = "{@~^";
    public static final String VALUE_DELIMITER_REGEX = "\\Q" + VALUE_DELIMITER + "\\E";

    public MultiValuedRenderContext(RenderContext ctx, Set<FieldKey> requiredFieldKeys)
    {
        super(ctx);

        // For each required column (e.g., display value, rowId), retrieve the concatenated values, split them, and
        // stash away an iterator of those values.
        int length = -1;
        Set<FieldKey> nullFieldKeys = new HashSet<>();
        for (FieldKey fieldKey : requiredFieldKeys)
        {
            Object value = ctx.get(fieldKey);
            if (value == null || "".equals(value))
            {
                nullFieldKeys.add(fieldKey);
            }
            else
            {
                String[] values = value.toString().split(VALUE_DELIMITER_REGEX);
                if (length != -1 && values.length != length)
                {
                    throw new IllegalStateException("Expected all columns to have the same number of values, but '" + fieldKey + "' has " + values.length + " and " + _iterators.keySet() + " had " + length);
                }
                length = values.length;
                _iterators.put(fieldKey, new ArrayIterator<>(values));
            }

            for (FieldKey nullFieldKey : nullFieldKeys)
            {
                _iterators.put(nullFieldKey, new ArrayIterator<>(new String[length == -1 ? 0 : length]));
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
                // empty string values map to null
                if ("".equals(value))
                    value = null;
                else
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
            Set<FieldKey> fieldKeys = new HashSet<>();
            fieldKeys.add(_fk1);
            fieldKeys.add(_fk2);
            Map<FieldKey, String> values = new HashMap<>();
            values.put(_fk1, "1" + VALUE_DELIMITER + "2" + VALUE_DELIMITER + "3");
            values.put(_fk2, "a" + VALUE_DELIMITER + "b" + VALUE_DELIMITER + "c");
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
            Set<FieldKey> fieldKeys = new HashSet<>();
            fieldKeys.add(_fk1);
            fieldKeys.add(_fk2);
            fieldKeys.add(_otherFK);
            Map<FieldKey, String> values = new HashMap<>();
            values.put(_fk1, "1" + VALUE_DELIMITER + "2" + VALUE_DELIMITER + "3");
            values.put(_fk2, "a" + VALUE_DELIMITER + "b" + VALUE_DELIMITER + "c");
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
            Set<FieldKey> fieldKeys = new HashSet<>();
            fieldKeys.add(_fk1);
            fieldKeys.add(_fk2);
            Map<FieldKey, String> values = new HashMap<>();
            values.put(_fk1, "1" + VALUE_DELIMITER + "2" + VALUE_DELIMITER + "3");
            values.put(_fk2, "a" + VALUE_DELIMITER + "b" + VALUE_DELIMITER + "c" + VALUE_DELIMITER + "d");
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
                Map<FieldKey, ColumnInfo> result = new HashMap<>();
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
