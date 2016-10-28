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
package org.labkey.api.collections;

import org.junit.Assert;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.ArrayList;
import java.util.List;

/**
 * A one-based list (for the initial index), useful for JDBC operations, etc.
 * User: adam
 * Date: 10/21/12
 */
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
        return new OneBasedList<>(list);
    }


    public static class TestCase extends Assert
    {
        @Test
        public void testList()
        {
            OneBasedList<String> list = new OneBasedList<>();
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

        @org.junit.Rule
        public ExpectedException exception = ExpectedException.none();

        @Test
        public void testGetOutOfBounds()
        {
            List<String> list = getTestList();
            exception.expect(IndexOutOfBoundsException.class);
            list.get(0);
        }

        @Test
        public void testAddOutOfBounds()
        {
            List<String> list = getTestList();
            exception.expect(IndexOutOfBoundsException.class);
            list.add(0, "Foo");
        }

        private List<String> getTestList()
        {
            List<String> list = new OneBasedList<>();
            list.add("Fee");
            list.add("Fi");
            list.add("Fo");
            list.add("Fum");

            return list;
        }
    }
}
