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
package org.labkey.api.collections;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * User: adam
 * Date: Nov 12, 2010
 * Time: 4:25:37 PM
 */
public class Sets
{
    public static Set<String> newCaseInsensitiveHashSet()
    {
        return Collections.newSetFromMap(new CaseInsensitiveHashMap<>());
    }

    public static Set<String> newCaseInsensitiveHashSet(int count)
    {
        return Collections.newSetFromMap(new CaseInsensitiveHashMap<>(count));
    }

    public static Set<String> newCaseInsensitiveHashSet(String... values)
    {
        Set<String> set = newCaseInsensitiveHashSet(values.length);
        set.addAll(Arrays.asList(values));
        return set;
    }

    public static Set<String> newCaseInsensitiveHashSet(Collection<String> col)
    {
        Set<String> set = newCaseInsensitiveHashSet(col.size());
        set.addAll(col);
        return set;
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testSet()
        {
            Set<String> set = Sets.newCaseInsensitiveHashSet("test1", "test2");
            assertTrue(set.contains("test1"));
            assertTrue(set.contains("test2"));
            assertTrue(set.contains("TEST1"));
            assertTrue(set.contains("TEST2"));
            assertFalse(set.contains("TEST2a"));
            assertFalse(set.contains("test3"));

            set.addAll(Sets.newCaseInsensitiveHashSet("test3"));
            assertTrue(set.contains("test3"));
            assertTrue(set.contains("TEST3"));
        }
    }
}
