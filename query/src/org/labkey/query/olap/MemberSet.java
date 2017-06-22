/*
 * Copyright (c) 2013-2017 LabKey Corporation
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
package org.labkey.query.olap;

import org.apache.commons.collections4.iterators.IteratorChain;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.collections.SparseBitSet;
import org.olap4j.OlapException;
import org.olap4j.metadata.Hierarchy;
import org.olap4j.metadata.Level;
import org.olap4j.metadata.Member;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: matthew
 * Date: 11/5/13
 * Time: 10:36 AM
 */


public class MemberSet extends AbstractSet<Member>
{
    Map<String,LevelMemberSet> levelMap = new HashMap<>();


    public MemberSet()
    {
    }


    public MemberSet(Collection<Member> from)
    {
        addAll(from);
    }


    public MemberSet(Level l, Collection<Member> from)
    {
        LevelMemberSet s = new LevelMemberSet(l, from);
        levelMap.put(l.getUniqueName(), s);
        s.addAll(from);
    }

    long getMemorySizeInBytes()
    {
        // approximating the smaller fixed overhead
        long size = 32;
        for (LevelMemberSet set : levelMap.values())
            size += 32 + set._set.getMemorySizeInBytes();
        return size;
    }



    private MemberSet(LevelMemberSet s)
    {
        levelMap.put(s._level.getUniqueName(), s);
    }

    public void seal()
    {
        for (Map.Entry<String,LevelMemberSet> entry : levelMap.entrySet())
        {
            LevelMemberSet s = entry.getValue();
            s._set.seal();
        }
    }

    // NOTE : Don't need to do this if the cube is a CachedCube.
    // For olap4j cube we can't hang onto metadata when sitting in a cache (e.g. Level objects)
    // we need a way to detach and reattch a MemberSet to a cube

    MemberSet detach()
    {
        seal();
        return this;
        /*
        MemberSet detach = new MemberSet();
        detach.levelMap.putAll(this.levelMap);
        for (Map.Entry<String,LevelMemberSet> entry : levelMap.entrySet())
        {
            LevelMemberSet s = entry.getValue();
            s._set.seal();
            LevelMemberSet copy = new LevelMemberSet(null, s._set);
            detach.levelMap.put(entry.getKey(), copy);
        }
        return detach;
        */
    }


    MemberSet attach(Map<String,Level> levelNameMap)
    {
        return this;
        /*
        MemberSet attach = new MemberSet();
        for (Map.Entry<String,LevelMemberSet> entry : levelMap.entrySet())
        {
            LevelMemberSet s = entry.getValue();
            Level l = levelNameMap.get(entry.getKey());
            if (null == l)
                throw new IllegalStateException("could not find level: " + entry.getKey());
            LevelMemberSet copy = new LevelMemberSet(l, s._set);
            attach.levelMap.put(entry.getKey(), copy);
        }
        return attach;
        */
    }


    public MemberSet onlyFor(Level l)
    {
        LevelMemberSet s = levelMap.get(l.getUniqueName());
        if (null == s)
            return new MemberSet(l, new HashSet<Member>());
        return new MemberSet(s);
    }


    public static MemberSet union(Collection<Collection<Member>> collections)
    {
        MemberSet ret = new MemberSet();
        for (Collection<Member> c : collections)
        {
            ret.addAll(c);
        }
        return ret;
    }


    public static MemberSet intersect(Collection<Member>... collections)
    {
        return intersect(Arrays.asList(collections));
    }


    public static MemberSet intersect(Collection<Collection<Member>> collections)
    {
        MemberSet ret = null;
        for (Collection<Member> c : collections)
        {
            if (null == ret)
            {
                ret = new MemberSet(c);
            }
            else if (c instanceof MemberSet)
            {
                ret.retainAll((MemberSet)c);
            }
            else
            {
                MemberSet i = new MemberSet();
                for (Member m : c)
                {
                    if (ret.contains(m))
                        i.add(m);
                }
                ret = i;
            }
        }
        return ret;
    }


    public static int countIntersect(MemberSet... memberSets)
    {
        // we expect to only count members within one level
        for (MemberSet m : memberSets)
        {
            if (!m.isEmpty() && null == m.getLevel())
                throw new UnsupportedOperationException();
        }
        if (memberSets.length == 1)
            return memberSets[0].size();
        String levelName = null;

        ArrayList<SparseBitSet> sets = new ArrayList<>();
        for (MemberSet m : memberSets)
        {
            if (m.isEmpty())
                return 0;
            if (levelName == null)
                levelName = m.getLevel().getUniqueName();
            LevelMemberSet s = m.levelMap.get(levelName);
            if (null == s || 0 == s._set.cardinality())
                return 0;
            sets.add(s._set);
        }

        assert !sets.isEmpty();

        if (sets.size() == 1)
            return sets.get(0).cardinality();

        if (sets.size() == 2)
            return countIntersect(sets.get(0), sets.get(1));

        SparseBitSet intersection = sets.get(0).clone();
        for (int s=1 ; s<sets.size() ; s++)
            intersection.and(sets.get(s));
        return intersection.cardinality();
    }


    public static int countIntersect(SparseBitSet A, SparseBitSet B)
    {
        int count = 0;
        int a = A.nextSetBit(0);
        int b = B.nextSetBit(0);

        while (a != -1 && b != -1)
        {
            if (a == b)
            {
                count++;
                a = A.nextSetBit(a+1);
                b = B.nextSetBit(b+1);
            }
            else if (a < b)
            {
                a = A.nextSetBit(b);
            }
            else
            {
                b = B.nextSetBit(a);
            }
        }
        return count;
    }


    public static MemberSet createRegularMembers(Level level) throws OlapException
    {
        MemberSet ret = new MemberSet();
        for (Member m : level.getMembers())
            if (!m.isCalculated())
                ret.add(m);
        return ret;
    }


    public static MemberSet createRegularMembers(Member member) throws OlapException
    {
        MemberSet ret = new MemberSet();
        for (Member m : member.getChildMembers())
            if (!m.isCalculated())
                ret.add(m);
        return ret;
    }


    /** return level if all members are from one level, else null */
    @Nullable
    Level getLevel()
    {
        if (levelMap.size() == 1)
        {
            Iterator<LevelMemberSet> it = levelMap.values().iterator();
            it.hasNext();
            return it.next()._level;
        }
        else
            return null;
    }


    /** return hierarchy if all members are from one hierarchy, else null */
    @Nullable
    Hierarchy getHierarchy()
    {
        if (levelMap.size() == 0)
            return null;
        Hierarchy h = null;
        for (LevelMemberSet l : levelMap.values())
        {
            if (h == null)
                h = l._level.getHierarchy();
            else if (!l._level.getHierarchy().getUniqueName().equals(h.getUniqueName()))
                    return null;
        }
        return h;
    }


    @Override @NotNull
    public Iterator<Member> iterator()
    {
        if (levelMap.isEmpty())
            return Collections.emptyIterator();
        if (levelMap.size() == 1)
        {
            Iterator<LevelMemberSet> it = levelMap.values().iterator();
            it.hasNext();
            LevelMemberSet only = it.next();
            return only.iterator();
        }

        // sort levels by depth
        List<LevelMemberSet> list = new ArrayList<>();
        list.addAll(levelMap.values());
        list.sort((s1, s2) -> s2._level.getDepth() - s1._level.getDepth());
        List<Iterator<? extends Member>> iterators = new ArrayList<>();
        for (LevelMemberSet s : list)
            iterators.add(s.iterator());
        return new IteratorChain<>(iterators);
    }


    public void addAll(Level l) throws OlapException
    {
        if (!levelMap.containsKey(l.getUniqueName()))
            levelMap.put(l.getUniqueName(), new LevelMemberSet(l));
        levelMap.get(l.getUniqueName()).addAll(l.getMembers());
    }

    public boolean addAll(MemberSet from)
    {
        for (LevelMemberSet s : from.levelMap.values())
        {
            if (!levelMap.containsKey(s._level.getUniqueName()))
            {
                levelMap.put(s._level.getUniqueName(), new LevelMemberSet(s));
            }
            else
            {
                levelMap.get(s._level.getUniqueName()).addAll(s);
            }
        }
        return true;
    }


    @Override
    public boolean addAll(Collection<? extends Member> c)
    {
        if (c instanceof MemberSet)
            return addAll((MemberSet)c);
        for (Member m : c)
            add(m);
        return true;
    }


    @Override
    public boolean retainAll(Collection<?> c)
    {
        if (c instanceof MemberSet)
        {
            return retainAll((MemberSet)c);
        }
        throw new UnsupportedOperationException();
    }


    public boolean retainAll(MemberSet from)
    {
        for (Map.Entry<String,LevelMemberSet> e : levelMap.entrySet())
        {
            LevelMemberSet setRetain = e.getValue();
            LevelMemberSet setFrom = from.levelMap.get(e.getKey());
            if (null == setFrom)
                setRetain.clear();
            else
                setRetain.retainAll(setFrom);
        }
        return true;
    }


    @Override
    public int size()
    {
        int size = 0;
        for (LevelMemberSet s : levelMap.values())
            size += s.size();
        return size;
    }


    @Override
    public boolean isEmpty()
    {
        for (LevelMemberSet s : levelMap.values())
        {
            if (!s.isEmpty())
                return false;
        }
        return true;
    }


    @Override
    public boolean contains(Object o)
    {
        if (!(o instanceof Member))
            return false;
        Member m = (Member)o;
        LevelMemberSet s = levelMap.get(m.getLevel().getUniqueName());
        return s != null && s.contains(m);
    }


    @Override
    public boolean add(Member member)
    {
        Level l = member.getLevel();
        LevelMemberSet s = levelMap.get(l.getUniqueName());
        if (null == s)
        {
            levelMap.put(l.getUniqueName(), s = new LevelMemberSet(l));
        }
        return s.add(member);
    }


    /* this is only used for debugging */
    @Override
    public boolean remove(Object o)
    {
        if (!(o instanceof Member))
            return false;
        Member m = (Member)o;
        LevelMemberSet s = levelMap.get(m.getLevel().getUniqueName());
        return s != null && s.remove(m);
    }


    @Override
    public void clear()
    {
        for (LevelMemberSet s : levelMap.values())
            s.clear();
    }


    public boolean containsAny(MemberSet other)
    {
        for (Map.Entry<String,LevelMemberSet> entry : levelMap.entrySet())
        {
            String levelName = entry.getKey();
            LevelMemberSet setMe = entry.getValue();
            LevelMemberSet setOther = other.levelMap.get(levelName);
            if (null != setOther)
            {
                if (setMe._set.intersects(setOther._set))
                    return true;
            }
        }
        return false;
    }



    /** inner implmentation for a members of a single level, with natural ordering/ordinality */

    private class LevelMemberSet implements Set<Member>
    {
        Level _level;
        final SparseBitSet _set;

        private LevelMemberSet(Level level)
        {
            _level = level;
            _set = new SparseBitSet();
        }


        private LevelMemberSet(Level level, SparseBitSet s)
        {
            _level = level;
            _set = s;
        }


        LevelMemberSet(Level level, Collection<Member> members)
        {
            _level = level;
            _set = new SparseBitSet(members.size());
            for (Member m : members)
            {
                if (!m.getLevel().getUniqueName().equals(_level.getUniqueName()))
                    throw new IllegalStateException();
                _set.set(m.getOrdinal());
            }
        }

        LevelMemberSet(LevelMemberSet from)
        {
            _level = from._level;
            _set = from._set.clone();
        }

        void seal()
        {
            _set.seal();
        }

        void addMembers(List<Member> c)
        {
            for (Member m : c)
            {
                if (m.getLevel() != _level)
                    throw new IllegalArgumentException();
                _set.set(m.getOrdinal());
            }
        }

        List<Member> getMembers() throws OlapException
        {
            SparseBitSet s = _set;
            ArrayList<Member> list = new ArrayList<>(s.cardinality());
            List<Member> members = _level.getMembers();
            for( int i = s.nextSetBit(0); i >= 0; i = s.nextSetBit(i+1) )
            {
                Member m = members.get(i);
                if (m.getOrdinal() != i)
                    throw new IllegalArgumentException();
                list.add(m);
            }
            return list;
        }

        @Override
        public int size()
        {
            return _set.cardinality();
        }

        @Override
        public boolean isEmpty()
        {
            return _set.isEmpty();
        }

        @Override
        public boolean contains(Object o)
        {
            if (!(o instanceof Member))
                return false;
            Member m = (Member)o;
            if (m.getLevel() != _level)
                return false;
            return _set.get(m.getOrdinal());
        }

        @NotNull
        @Override
        public Iterator<Member> iterator()
        {
            return new _Iterator();
        }

        @NotNull
        @Override
        public Object[] toArray()
        {
            throw new UnsupportedOperationException();
        }

        @NotNull
        @Override
        public <T> T[] toArray(T[] a)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean add(Member member)
        {
            if (member.getLevel() != _level)
            {
                if (!member.getLevel().getUniqueName().equals(_level.getUniqueName()))
                    throw new IllegalArgumentException();
            }
            assert member.getOrdinal() >= 0;
            boolean ret = !_set.get(member.getOrdinal());
            _set.set(member.getOrdinal());
            return ret;
        }

        @Override
        public boolean remove(Object o)
        {
            if (!(o instanceof Member))
                return false;
            Member m = (Member)o;
            if (m.getLevel() != _level)
                return false;
            boolean ret = _set.get(m.getOrdinal());
            _set.clear(m.getOrdinal());
            return ret;
        }

        @Override
        public boolean containsAll(Collection<?> c)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean addAll(Collection<? extends Member> c)
        {
            for (Member m : c)
                add(m);
            return true;
        }


        @Override
        public boolean retainAll(Collection<?> c)
        {
            if (c instanceof LevelMemberSet)
                return retainAll((LevelMemberSet)c);
            throw new UnsupportedOperationException();
        }


        public boolean retainAll(LevelMemberSet from)
        {
            _set.and(from._set);
            return true;
        }


        @Override
        public boolean removeAll(Collection<?> c)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void clear()
        {
            _set.clear();
        }


        class _Iterator implements Iterator<Member>
        {
            int _bit = 0;
            Member _current;
            Member _nextMember;

            @Override
            public boolean hasNext()
            {
                try
                {
                    if (null == _nextMember && _bit >= 0)
                    {
                        _bit = _set.nextSetBit(_bit);
                        if (_bit >= 0)
                        {
                            _nextMember = _level.getMembers().get(_bit);
                            assert _nextMember.getOrdinal() == _bit;
                            _bit++;
                        }

                    }
                    return null != _nextMember;
                }
                catch (OlapException x)
                {
                    throw new RuntimeException(x);
                }
            }

            @Override
            public Member next()
            {
                _current = _nextMember;
                _nextMember = null;
                return _current;
            }

            @Override
            public void remove()
            {
                _set.clear(_current.getOrdinal());
            }
        }
    }







    /** TESTING **/


    static class MockOlapProxy implements InvocationHandler
    {
        final String uniqueName;
        final int ordinal;

        MockOlapProxy(String uniqueName, int ordinal)
        {
            this.uniqueName = uniqueName;
            this.ordinal = ordinal;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable
        {
            switch (method.getName())
            {
                case "getUniqueName":
                    return getUniqueName();
                case "getOrdinal":
                    return getOrdinal();
                case "equals":
                    throw new IllegalStateException("equals doesn't work use getUniqueName().equals(...)");
                default:
                    return null;
            }
        }

        String getUniqueName()
        {
            return uniqueName;
        }

        int getOrdinal()
        {
            return ordinal;
        }
    }


    static class MockLevelProxy extends MockOlapProxy
    {
        final Hierarchy h;
        final String[] memberNames;
        MockLevelProxy(Hierarchy h, String name, int ordinal, String[] members)
        {
            super(name,ordinal);
            this.h = h;
            this.memberNames = members;
        }
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable
        {
            switch (method.getName())
            {
                case "getHierarchy":
                    return getHierarchy();
                case "getMembers":
                    return getMembers(proxy);
                default:
                    return super.invoke(proxy, method, args);
            }
        }
        Hierarchy getHierarchy()
        {
            return h;
        }
        List<Member> getMembers(Object proxy)
        {
            ArrayList<Member> ret = new ArrayList<Member>(memberNames.length);
            for (int i=0 ; i<memberNames.length ; i++)
            {
                String name = memberNames[i];
                ret.add((Member)Proxy.newProxyInstance(MemberSet.class.getClassLoader(),new Class[]{Member.class}, new MockMemberProxy((Level)proxy,name,i)));
            }
            return ret;
        }
    }


    static class MockMemberProxy extends MockOlapProxy
    {
        final Level l;
        MockMemberProxy(Level l, String name, int ordinal)
        {
            super(name,ordinal);
            this.l = l;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable
        {
            switch (method.getName())
            {
                case "getLevel":
                    return getLevel();
                case "getHierarchy":
                    return getHierarchy();
                default:
                    return super.invoke(proxy, method, args);
            }
        }

        Hierarchy getHierarchy()
        {
            return l.getHierarchy();
        }
        Level getLevel()
        {
            return l;
        }
    }


    static String[] l1_members = {"Doe", "Jones"};
    static String[] l2_members = {"Doe, John", "Doe, Jane", "Jones, Davie", "Jones, John Paul", "Jones, January"};

    static Hierarchy h = (Hierarchy)Proxy.newProxyInstance(MemberSet.class.getClassLoader(), new Class<?>[]{Hierarchy.class}, new MockOlapProxy("Names",1));
    static Level l0 = (Level)Proxy.newProxyInstance(MemberSet.class.getClassLoader(), new Class<?>[]{Level.class}, new MockLevelProxy(h,"[(ALL)]",0,new String[]{"ALL"}));
    static Level l1 = (Level)Proxy.newProxyInstance(MemberSet.class.getClassLoader(), new Class<?>[]{Level.class}, new MockLevelProxy(h,"LastName",1,l1_members));
    static Level l2 = (Level)Proxy.newProxyInstance(MemberSet.class.getClassLoader(), new Class<?>[]{Level.class}, new MockLevelProxy(h,"FullName",2,l2_members));

    public static class TestCase extends Assert
    {
        @Test
        public void testSparseBitSet()
        {
            SparseBitSet empty = new SparseBitSet();
            empty.seal();
            SparseBitSet fib = new SparseBitSet();
            for (int f : Arrays.asList(
                /* 1 */ 1, 1, 2, 3, 5, 8, 13, 21, 34, 55,
                /* 11 */ 89, 144, 233, 377, 610, 987, 1597, 2584, 4181, 6765,
                /* 21 */ 10946, 17711, 28657, 46368, 75025, 121393, 196418, 317811, 514229,  832040,
                /* 31 */ 1346269, 2178309, 3524578, 5702887, 9227465, 14930352, 24157817, 39088169, 63245986, 102_334_155))
                fib.set(f);
            fib.seal();
            SparseBitSet twosAndThrees = new SparseBitSet();
            for (int i=0 ; i<=6 ; i++)
            {
                twosAndThrees.set((int)Math.pow(2,i) * 1);
                twosAndThrees.set((int)Math.pow(2,i) * 3);
                twosAndThrees.set((int)Math.pow(2,i) * 9);
            }
            twosAndThrees.seal();
            SparseBitSet even = new SparseBitSet();
            SparseBitSet odd = new SparseBitSet();
            for (int i=0 ; i<50 ; i++)
            {
                even.set(i * 2);
                odd.set(i * 2 + 1);
            }
            even.seal(); odd.seal();
            assertEquals(0, empty.cardinality());
            assertEquals(39, fib.cardinality());
            assertEquals(21, twosAndThrees.cardinality());
            assertEquals(50, even.cardinality());
            assertEquals(50, odd.cardinality());
            SparseBitSet t = new SparseBitSet();
            t.or(fib);
            t.and(odd);
            assertEquals(7,t.cardinality());
            assertEquals(7,countIntersect(fib,odd));

            t = new SparseBitSet();
            t.or(fib);
            t.and(twosAndThrees);
            // 1,2,3,8,144
            assertEquals(5,t.cardinality());
            assertEquals(5,countIntersect(fib,twosAndThrees));
        }

        @Test
        public void testLevelMemberSet()
        {

        }

        @Test
        public void testMemberSet() throws OlapException
        {
            MemberSet allLevel0 = new MemberSet(l0, l0.getMembers());
            MemberSet allLevel1= new MemberSet(l1, l1.getMembers());
            MemberSet allLevel2= new MemberSet(l2, l2.getMembers());

            assertEquals(1, allLevel0.size());
            assertEquals(2, allLevel1.size());
            assertEquals(5, allLevel2.size());

            MemberSet all = new MemberSet();
            all.addAll(allLevel0);
            all.addAll(allLevel1);
            all.addAll(allLevel2);
            assertEquals(8, all.size());

            List<Member> list = l2.getMembers();
            MemberSet even = new MemberSet();
            even.add(list.get(0));
            even.add(list.get(2));
            even.add(list.get(4));
            assertEquals(3,even.size());
            MemberSet odd = new MemberSet();
            odd.add(list.get(1));
            odd.add(list.get(3));
            assertEquals(2, odd.size());

            assertEquals(0, countIntersect(even,odd));
            MemberSet union = new MemberSet();
            union.addAll(even);
            union.addAll(odd);
            assertEquals(5, union.size());
            assertEquals(3, countIntersect(even, union));
            assertEquals(3, countIntersect(union, even));
            assertEquals(2, countIntersect(odd, union));
            assertEquals(2, countIntersect(union,odd));
        }
    }
}
