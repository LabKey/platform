package org.labkey.api.data.collections;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
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
        HashMap<Object, Map> lookup = new HashMap<Object, Map>(right.size() * 2);
        ArrayList<Map> join = new ArrayList<Map>(left.size());

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
            HashSet<Entry> all = new HashSet<Map.Entry>((e1.size() + e2.size()) * 2);
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
