/*
 * Copyright (c) 2006-2013 LabKey Corporation
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

import java.util.*;

/**
 * User: Matthew
 * Date: Mar 20, 2006
 * Time: 11:20:01 AM
 *
 * In the spirit of org/apache/commons/collections
 */
public class Join
{

    /**
     * Join two maps: left and right returning a new collection of maps on 'key'
     * The right map is assumed to be unique on 'key'
     */
    public static List<Map> join(List<Map> left, List<Map> right, String key)
    {
        HashMap<Object, Map> lookup = new HashMap<>(right.size() * 2);
        ArrayList<Map> join = new ArrayList<>(left.size());

        for (Map r : right)
        {
            Object prev = lookup.put(r.get(key), r);
            if (null != prev)
                throw new IllegalStateException("join: map key is not unique");
        }

        for (Map l : left)
        {
            Map r = lookup.get(l.get(key));
            if (null == r)
                join.add(l);
            else
                join.add(new JoinMap(l, r));
        }

        return join;
    }


    protected static class JoinMap extends AbstractMap
    {
        Map first;
        Map second;

        JoinMap(Map a, Map b)
        {
            first = a;
            second = b;
        }


        public Set<Map.Entry> entrySet()
        {
            Set<Map.Entry> e1 = first.entrySet();
            Set<Map.Entry> e2 = second.entrySet();
            HashSet<Entry> all = new HashSet<>((e1.size() + e2.size()) * 2);
            all.addAll(first.entrySet());
            for (Object o : second.entrySet())
            {
                Map.Entry e = (Map.Entry) o;
                if (!first.containsKey(e.getKey()))
                    all.add(e);
            }
            return all;
        }


        public Object get(Object key)
        {
            if (null != first && first.containsKey(key))
                return first.get(key);
            if (null != second)
                return second.get(key);
            return null;
        }


        public boolean isEmpty()
        {
            return (null == first || first.isEmpty()) && (null == second || second.isEmpty());
        }


        public boolean containsKey(Object key)
        {
            return null != first && first.containsKey(key) || null != second && second.containsKey(key);
        }


        public Object put(Object key, Object value)
        {
            throw new UnsupportedOperationException("Join map is unmodifiable");
        }
    }
}
