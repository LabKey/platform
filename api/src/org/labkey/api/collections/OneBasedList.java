package org.labkey.api.collections;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * User: adam
 * Date: 10/21/12
 * Time: 6:28 AM
 */

// A one-based list, useful for JDBC operations, etc.
public class OneBasedList<E> extends IndexMappingList<OneBasedList<E>, E>
{
    public OneBasedList()
    {
        super(new ArrayList<E>());
    }

    public OneBasedList(int initialCapacity)
    {
        super(new ArrayList<E>(initialCapacity));
    }

    public OneBasedList(List<E> list)
    {
        super(list);
    }

    @Override
    protected int mapIndexIn(int index)
    {
        return index - 1;
    }

    @Override
    protected int mapIndexOut(int index)
    {
        return index + 1;
    }

    @Override
    protected OneBasedList<E> wrapList(List<E> list)
    {
        return new OneBasedList<E>(list);
    }


    public static class TestCase extends Assert
    {
        @Test
        public void testList()
        {
            OneBasedList<String> list = new OneBasedList<String>();
            list.add(1, "First");
            list.add("Second");
            list.add(3, "Third");
            list.add("Fourth");
            list.add("Fifth");
            list.add("Sixth");
            assertEquals(6, list.size());
            validateAgainstUnderlyingList(list);

            assertEquals("Fourth", list.remove(4));
            assertTrue(list.remove("Sixth"));
            assertEquals(4, list.size());
            validateAgainstUnderlyingList(list);

            OneBasedList<String> sublist = list.subList(2, 4);
            assertEquals(2, sublist.size());
            assertEquals("Second", sublist.get(1));
            assertEquals("Third", sublist.get(2));
            validateAgainstUnderlyingList(sublist);
        }

        private void validateAgainstUnderlyingList(OneBasedList<String> list)
        {
            List<String> zeroBasedList = list.getUnderlyingList();
            assertEquals(zeroBasedList.size(), list.size());

            for (int i = 1; i <= list.size(); i++)
                assertEquals(zeroBasedList.get(i - 1), list.get(i));
        }

        @Test(expected=IndexOutOfBoundsException.class)
        public void testGetOutOfBounds()
        {
            List<String> list = getTestList();
            list.get(0);
        }

        @Test(expected=IndexOutOfBoundsException.class)
        public void testAddOutOfBounds()
        {
            List<String> list = getTestList();
            list.add(0, "Foo");
        }

        private List<String> getTestList()
        {
            List<String> list = new OneBasedList<String>();
            list.add("Fee");
            list.add("Fi");
            list.add("Fo");
            list.add("Fum");

            return list;
        }
    }
}
