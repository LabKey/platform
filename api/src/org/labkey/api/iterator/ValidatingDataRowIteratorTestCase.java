package org.labkey.api.iterator;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.labkey.api.query.ValidationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ValidatingDataRowIteratorTestCase
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
