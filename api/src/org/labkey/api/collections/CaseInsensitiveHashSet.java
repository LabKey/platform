/*
 * Copyright (c) 2005-2014 Fred Hutchinson Cancer Research Center
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
import org.labkey.api.util.PageFlowUtil;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Simple case-insensitive version of HashSet -- simply forces all Strings to lowercase before adding, removing,
 * or searching. Could easily extend this to preserve the case... just add a lowercase version to uppercase version map.
 *
 * User: arauch
 * Date: Dec 25, 2004
 */
// TODO: Merge CaseInsensitiveHashSet and CaseInsensitiveHashMap implementations
public class CaseInsensitiveHashSet extends HashSet<String>
{
    public CaseInsensitiveHashSet()
    {
        super();
    }

    public CaseInsensitiveHashSet(String... values)
    {
        super(values.length);
        addAll(values);
    }

    public CaseInsensitiveHashSet(Collection<String> col)
    {
        super(col.size());
        addAll(col);
    }

    public boolean remove(Object o)
    {
        return super.remove(o == null ? null : ((String) o).toLowerCase());
    }

    public boolean add(String s)
    {
        return super.add(s == null ? null : s.toLowerCase());
    }

    public void addAll(String... values)
    {
        for (String value : values)
            add(value);
    }

    public boolean contains(Object o)
    {
        return super.contains(o == null ? null : ((String) o).toLowerCase());
    }

    @Override   // Always iterate over passed in collection, see #17492
    public boolean removeAll(Collection<?> c)
    {
        boolean modified = false;

        for (Object aC : c)
            modified |= remove(aC);

        return modified;
    }


    public static class TestCase extends Assert
    {
        @Test
        public void test()
        {
            Set<String> set = new CaseInsensitiveHashSet("This", "that", "TOTHER");
            assertTrue(set.contains("This"));
            assertTrue(set.contains("this"));
            assertTrue(set.contains("THIS"));
            assertTrue(set.contains("ThIs"));
            assertTrue(set.contains("That"));
            assertTrue(set.contains("that"));
            assertTrue(set.contains("THAT"));
            assertTrue(set.contains("tHaT"));
            assertTrue(set.contains("Tother"));
            assertTrue(set.contains("tother"));
            assertTrue(set.contains("TOTHER"));
            assertTrue(set.contains("tOtHeR"));

            assertFalse(set.contains("flip"));
            assertFalse(set.contains("FLAP"));
            assertFalse(set.contains("flop"));

            set.addAll(PageFlowUtil.set("flip", "FLAP", "fLoP"));
            assertTrue(set.contains("flip"));
            assertTrue(set.contains("FLIP"));
            assertTrue(set.contains("flap"));
            assertTrue(set.contains("FLAP"));
            assertTrue(set.contains("flop"));
            assertTrue(set.contains("FLOP"));

            assertEquals(6, set.size());
            set.removeAll(PageFlowUtil.set("FLIP", "FLAp"));
            assertEquals(4, set.size());
            set.removeAll(PageFlowUtil.set("foo", "BAR"));
            assertEquals(4, set.size());
            set.removeAll(PageFlowUtil.set("foo", "BAR", "FLIP", "FLAP", "FLOP", "THIS", "ThAt"));
            assertEquals(1, set.size());
        }
    }
}
