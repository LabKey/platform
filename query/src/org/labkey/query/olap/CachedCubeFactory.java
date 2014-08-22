/*
 * Copyright (c) 2014 LabKey Corporation
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

import mondrian.olap.Annotated;
import mondrian.olap.Annotation;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.ArrayListMap;
import org.labkey.api.util.Pair;
import org.olap4j.OlapException;
import org.olap4j.OlapWrapper;
import org.olap4j.impl.Named;
import org.olap4j.impl.NamedListImpl;
import org.olap4j.mdx.IdentifierSegment;
import org.olap4j.mdx.ParseTreeNode;
import org.olap4j.metadata.Cube;
import org.olap4j.metadata.Datatype;
import org.olap4j.metadata.Dimension;
import org.olap4j.metadata.Hierarchy;
import org.olap4j.metadata.Level;
import org.olap4j.metadata.Measure;
import org.olap4j.metadata.Member;
import org.olap4j.metadata.MetadataElement;
import org.olap4j.metadata.NamedList;
import org.olap4j.metadata.NamedSet;
import org.olap4j.metadata.Property;
import org.olap4j.metadata.Schema;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Created by matthew on 4/25/14.
 *
 * This class implements the Cube interface, but is 'detached' from the underlying OlapConnection
 *
 * It also adds two features
 *
 * 1) uniqueNameMap includes all cube members and allows for fast lookup
 * 2) getOrdinal() works for all members
 *
 * It does not preserve all the possible properties on meta data elements.  You need to modify the
 * code for any properties you want to preserve.
 *
 */
public class CachedCubeFactory
{
    public static CachedCube createCachedCube(Cube c) throws SQLException
    {
        return new CachedCube(c);
    }

    public static class _MetadataElement implements MetadataElement, Named
    {
        final String name;
        final String uniqueName;
//        final String caption;
//        final String desciption;
//        final boolean visible;

        _MetadataElement(MetadataElement mde)
        {
            this.name = mde.getName();
            this.uniqueName = mde.getUniqueName();
            // TODO: errors with getCaption() and getDesciption()
            // at mondrian.olap4j.MondrianOlap4jConnection.getLocale(MondrianOlap4jConnection.java:652)
            // at mondrian.olap4j.MondrianOlap4jSchema.getLocale(MondrianOlap4jSchema.java:119)
            // at mondrian.olap4j.MondrianOlap4jCube.getDescription(MondrianOlap4jCube.java:169)
//            this.caption = mde.getCaption();
//            this.desciption = mde.getDescription();
//          isVisible() seems to be missing a null check
//            this.visible = mde.isVisible();
        }

        @Override
        public String getName()
        {
            return name;
        }

        @Override
        public String getUniqueName()
        {
            return uniqueName;
        }

        @Override
        public String getCaption()
        {
            return name;
        }

        @Override
        public String getDescription()
        {
            return name;
        }

        @Override
        public boolean isVisible()
        {
            return true;
        }
    }


    private static class _Hash
    {
        long l=-1;
        long add(int i)
        {
            l *= 37;
            l += i;
            return l;
        }
        long add(String s)
        {
            return add(s.hashCode());
        }
    }


    public static class CachedCube extends _MetadataElement implements Cube, Annotated
    {
        final long hash;
        final _NamedList<_Dimension,Dimension> dimensions = new _NamedList<>();
        final Map<String, Annotation> annotations;

        CachedCube(Cube c) throws SQLException
        {
            super(c);
            _Hash hash = new _Hash();
            hash.add(getUniqueName());

            for (Dimension d : c.getDimensions())
            {
                dimensions.add(new _Dimension(d, hash));
            }
            dimensions.seal();

            Map<String,Annotation> map = new HashMap<>();
            if (c instanceof OlapWrapper)
            {
                Annotated annotated = ((OlapWrapper)c).unwrap(Annotated.class);
                Map<String, Annotation> annotations = annotated.getAnnotationMap();
                map.putAll(annotations);
            }
            this.annotations = Collections.unmodifiableMap(map);
            this.hash = hash.l;
        }

        public long getLongHashCode()
        {
            return hash;
        }

        @Override
        public int hashCode()
        {
            return (int)hash;
        }

        @Override
        public Schema getSchema()
        {
            return null;
        }

        @Override
        public NamedList<Dimension> getDimensions()
        {
            return dimensions.recast();
        }

        @Override
        public NamedList<Hierarchy> getHierarchies()
        {
            _NamedList<_Hierarchy,Hierarchy> ret = new _NamedList<>();

            for (_Dimension d : dimensions)
            {
                for (Hierarchy h : d.getHierarchies())
                {
                    ret.add((_Hierarchy)h);
                }
            }
            return ret.recast();
        }

        @Override
        public List<Measure> getMeasures()
        {
            Dimension measures = getDimensions().get("Measures");
            Level l = measures.getHierarchies().get(0).getLevels().get(0);
            try
            {
                return (List<Measure>)(List)l.getMembers();
            }
            catch (OlapException x)
            {
                throw new RuntimeException(x);
            }
        }

        @Override
        public NamedList<NamedSet> getSets()
        {
            return emptyNamedSetList;
        }

        @Override
        public Collection<Locale> getSupportedLocales()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Member lookupMember(List<IdentifierSegment> identifierSegments) throws OlapException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Member> lookupMembers(Set<Member.TreeOp> treeOps, List<IdentifierSegment> identifierSegments) throws OlapException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isDrillThroughEnabled()
        {
            return false;
        }

        @Override
        public Map<String, Annotation> getAnnotationMap()
        {
            return annotations;
        }
    }


    static class _Dimension extends _MetadataElement implements Dimension
    {
        final _NamedList<_Hierarchy,Hierarchy> hierarchies = new _NamedList<>();
        final _Hierarchy defaultHierarchy;
        final Dimension.Type dimensionType;

        _Dimension(Dimension d, _Hash hash) throws OlapException
        {
            super(d);
            hash.add(getUniqueName());
            for (Hierarchy h : d.getHierarchies())
                hierarchies.add(new _Hierarchy(this, h, hash));
            hierarchies.seal();
            dimensionType = d.getDimensionType();
            Hierarchy def = d.getDefaultHierarchy();
            defaultHierarchy = null == def ? null : hierarchies.get(def.getName());
        }

        @Override
        public NamedList<Hierarchy> getHierarchies()
        {
            return hierarchies.recast();
        }

        @Override
        public Type getDimensionType() throws OlapException
        {
            return dimensionType;
        }

        @Override
        public Hierarchy getDefaultHierarchy()
        {
            return defaultHierarchy;
        }
    }


    static class _Hierarchy extends _MetadataElement implements Hierarchy
    {
        final _Dimension dimension;
        final _NamedList<_Level,Level> levels = new _NamedList<>();
        final Member defaultMember;


        _Hierarchy(_Dimension dimension, Hierarchy h, _Hash hash) throws OlapException
        {
            super(h);
            hash.add(getUniqueName());
            this.dimension = dimension;

            _Level lowerLevel = null;
            ArrayList<_Level> list = new ArrayList<>();
            for (int i = h.getLevels().size()-1 ; i>= 0 ; i--)
            {
                Level l = h.getLevels().get(i);
                _Level thisLevel = new _Level(this, l, lowerLevel, hash);
                list.add(thisLevel);
                lowerLevel = thisLevel;
            }
            Collections.reverse(list);
            for (_Level l : list)
                this.levels.add(l);
            this.levels.seal();

            _Level defaultMemberLevel = levels.get(h.getDefaultMember().getLevel().getName());
            defaultMember = defaultMemberLevel.getMembers().get(h.getDefaultMember().getUniqueName());

            // NOTE: the cube configuration related to ordering members (ordinalColumn etc)
            // only apply to the results of MDX queries.  It seems that ordering of members
            // in the cube metadata can not be relied on.  So we sort them ourselves.
            orderMembers(this);

            for (_Level l : list)
            {
                l.orig = null;
                l.members.seal();
                for (_Member m : l.members)
                    m.orig = null;
            }
        }


        @Override
        public Dimension getDimension()
        {
            return dimension;
        }

        @Override
        public NamedList<Level> getLevels()
        {
            return levels.recast();
        }

        @Override
        public boolean hasAll()
        {
            return false;
        }

        @Override
        public Member getDefaultMember() throws OlapException
        {
            return defaultMember;
        }

        @Override
        public NamedList<Member> getRootMembers() throws OlapException
        {
            return (NamedList<Member>)getLevels().get(0).getMembers();
        }
    }


    static void orderMembers(_Hierarchy h) throws OlapException
    {
        for (Level L : h.getLevels())
        {
            _Level l = (_Level) L;
            orderLevelMembers(l.orig, l.members);
        }
        for (Level L : h.getLevels())
        {
            _Level l = (_Level) L;
            if (l.getDepth() != h.getLevels().size() - 1)
                orderChildMembers(l.members);
        }
    }


    static void orderLevelMembers(Level l, _NamedList<_Member, Member> list)
    {
        Property keyProperty = l.getProperties().get("KEY");
        Comparator<_Member> comp = new MemberComparator(keyProperty);
        list._sort(comp);
        for (int i = 0; i < list.size(); i++)
            list.get(i).ordinal = i;
    }


    static void orderChildMembers(_NamedList<_Member, Member> list) throws OlapException
    {
        for (_Member m : list)
            if (null != m.childMembers)
                Arrays.sort(m.childMembers,ORDINAL_COMPARATOR);
    }


    static class _Level extends _MetadataElement implements Level
    {
        final int depth;
        final _Hierarchy hierarchy;
        final Level.Type levelType;
        final _NamedList<_Member,Member> members = new _UniqueNamedList<>();
        final ArrayListMap.FindMap<String> memberPropertiesFindMap= new ArrayListMap.FindMap<>(new HashMap<String,Integer>());
        final _NamedList<_Property,Property> memberProperties = new _NamedList<>();

        // temporary pointer
        Level orig;

        _Level(_Hierarchy h, Level l, @Nullable _Level lowerLevel, _Hash hash) throws OlapException
        {
            super(l);
            hash.add(getUniqueName());

            this.depth = l.getDepth();
            this.hierarchy = h;
            this.levelType = l.getLevelType();
            this.orig = l;

            for (Property p : l.getProperties())
            {
                String n = p.getName();
                if (n.endsWith("_NAME") || n.startsWith("LEVEL_") || n.startsWith("PARENT_"))
                    continue;
                if (n.equals("MEMBER_ORDINAL") || n.equals("MEMBER_TYPE") || n.equals("CHILDREN_CARDINALITY"))
                    continue;
                memberProperties.add(new _Property(p));
            }

            ArrayList<_Member> list = new ArrayList<>(l.getMembers().size());
            if ("[Measures]".equals(getDimension().getUniqueName()))
            {
                for (Member m : l.getMembers())
                {
                    list.add(new _Measure(this, lowerLevel, (Measure) m, hash));
                }
            }
            else
            {
                for (Member m : l.getMembers())
                {
                    list.add(new _Member(this, lowerLevel, m, hash));
                }
            }
            members.addAll(list);
        }

        @Override
        public int getDepth()
        {
            return depth;
        }

        @Override
        public Hierarchy getHierarchy()
        {
            return hierarchy;
        }

        @Override
        public Dimension getDimension()
        {
            return hierarchy.getDimension();
        }

        @Override
        public Type getLevelType()
        {
            return levelType;
        }

        @Override
        public boolean isCalculated()
        {
            return false;
        }

        @Override
        public NamedList<Property> getProperties()
        {
            return memberProperties.recast();
        }


        /**
         * NOTE: Level.getMembers() is defined as returning a List, not a NamedList()
         * because member names may not be unique.  CachedCube returns a NamedList() that
         * indexes by getUniqueName()
         *
         * @return Member
         * @throws OlapException
         */
        @Override
        public NamedList<Member> getMembers() throws OlapException
        {
            return members.recast();
        }

        @Override
        public int getCardinality()
        {
            try
            {
                return getMembers().size();
            }
            catch (OlapException x)
            {
                throw new RuntimeException(x);
            }
        }
    }

    static private String getProperty(Member m, String s)
    {
        try
        {
            Property p = m.getProperties().get(s);
            if (null == p)
                return "''";
            Object o = m.getPropertyValue(p);
            if (null == o)
                return "''";
            return String.valueOf(o);
        }
        catch(OlapException x)
        {
            return "<exception>";
        }
    }


    public static class _Member extends _MetadataElement implements Member
    {
        final boolean all;
        final _Level level;
        final Member.Type memberType;
        final Member[] childMembers;
        Member _parent;
        final Map<String,Pair<Object,String>> _properties;

        // orig is to facilitate sorting, should be cleard after cached cube is constructed
        Member orig = null;
        int ordinal = -1;

        _Member(_Level level, _Level lowerLevel, Member m, _Hash hash) throws OlapException
        {
            super(m);
            hash.add(getUniqueName());
            this.level = level;
            this.all = m.isAll();
            this.memberType = m.getMemberType();
            this.orig = m;

            _Member[] arr = null;

            List<? extends Member> list = m.getChildMembers();
            if (null != lowerLevel && 0 < list.size())
            {
                arr = new _Member[list.size()];
                for (int i=0 ; i<list.size() ; i++)
                {
                    arr[i] = (_Member)lowerLevel.getMembers().get(list.get(i).getUniqueName());
                    assert null != arr[i];
                    arr[i]._parent = this;
                }
            }
            childMembers = arr;

            ArrayListMap<String,Pair<Object,String>> map = new ArrayListMap<>(level.memberPropertiesFindMap);
            for (Property p : level.memberProperties)
            {
                Object value = m.getPropertyValue(p);
                if (null == value)
                    continue;
                String formatted = m.getPropertyFormattedValue(p);
                map.put(p.getUniqueName(), new Pair<>(value,formatted));
            }
            _properties = map.isEmpty() ? null : map;
        }


        public List<? extends Member> getChildMembersArray() // well not array actually, but fast list
        {
            List<? extends Member> list = Arrays.asList(childMembers);
            assert null != (list = Collections.unmodifiableList(list));
            return list;
        }

        @Override
        public NamedList<? extends Member> getChildMembers()
                throws OlapException
        {
            if (null == childMembers)
                return emptyMemberList;
            else
            {
                _NamedList<_Member,Member> ret = new _NamedList<>();
                for (Member m : childMembers)
                    ret.add((_Member)m);
                return ret.recast();
            }
        }

        @Override
        public int getChildMemberCount() throws OlapException
        {
            return null == childMembers ? 0 : childMembers.length;
        }

        @Override
        public Member getParentMember()
        {
            return null;
        }

        @Override
        public Level getLevel()
        {
            return level;
        }

        @Override
        public Hierarchy getHierarchy()
        {
            return level.getHierarchy();
        }

        @Override
        public Dimension getDimension()
        {
            return level.getDimension();
        }

        @Override
        public Type getMemberType()
        {
            return memberType;
        }

        @Override
        public boolean isAll()
        {
            return all;
        }

        @Override
        public boolean isChildOrEqualTo(Member member)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isCalculated()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getSolveOrder()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public ParseTreeNode getExpression()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Member> getAncestorMembers()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isCalculatedInQuery()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object getPropertyValue(Property property) throws OlapException
        {
            if (null == _properties)
                return null;
            Pair<Object,String> p = _properties.get(property.getUniqueName());
            if (null == p)
                return null;
            return p.first;
        }

        @Override
        public String getPropertyFormattedValue(Property property) throws OlapException
        {
            if (null == _properties)
                return null;
            Pair<Object,String> p = _properties.get(property.getUniqueName());
            if (null == p)
                return null;
            return p.second;
        }

        @Override
        public void setProperty(Property property, Object o) throws OlapException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public NamedList<Property> getProperties()
        {
            return level.getProperties();
        }

        @Override
        public int getOrdinal()
        {
            return ordinal;
        }

        @Override
        public boolean isHidden()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getDepth()
        {
            return level.getDepth();
        }

        @Override
        public Member getDataMember()
        {
            throw new UnsupportedOperationException();
        }
    }


    static class _Measure extends _Member implements Measure
    {
        _Measure(_Level l, _Level lowerLevel, Measure m, _Hash hash) throws OlapException
        {
            super(l,lowerLevel,m,hash);
        }

        @Override
        public Aggregator getAggregator()
        {
            return null;
        }

        @Override
        public Datatype getDatatype()
        {
            return null;
        }
    }


    public static class _Property extends _MetadataElement implements Property
    {
        final Datatype datatype;
        final Set<TypeFlag> type;
        final ContentType contentType;
        final boolean visible;

        _Property(Property p)
        {
            super(p);
            datatype = p.getDatatype();
            type = Collections.unmodifiableSet(new HashSet<TypeFlag>(p.getType()));
            contentType = p.getContentType();
            visible = p.isVisible();
        }

        @Override
        public Datatype getDatatype()
        {
            return datatype;
        }

        @Override
        public Set<TypeFlag> getType()
        {
            return type;
        }

        @Override
        public ContentType getContentType()
        {
            return contentType;
        }

        @Override
        public boolean isVisible()
        {
            return visible;
        }
    }



    static class _NamedList<T extends org.olap4j.impl.Named,MDE> extends NamedListImpl<T>
    {
        Map<String,Integer> indexMap = null;
        boolean readonly = false;

        NamedList<MDE> recast()
        {
            return (NamedList<MDE>)(NamedList)this;
        }

        public boolean seal()
        {
            if (null==indexMap)
                recompute();
            readonly = true;
            return true;
        }

        private void recompute()
        {
            if (readonly)
                throw new IllegalStateException();
            indexMap = new HashMap<>();
            for (int i=0 ; i<size() ; i++)
            {
                T t = get(i);
                Integer prev = indexMap.put(getName(t), i);
                assert null == prev : getName(t) + " found twice" ;
            }
            assert indexMap.size() == this.size();
        }

        // cant call this sort() and be safe in both java 7 and 8
        private void _sort(Comparator<? super T> c)
        {
            if (readonly)
                throw new IllegalStateException();
            indexMap = null;
            Collections.sort(this, c);
        }

        @Override
        public int indexOfName(String name)
        {
            if (null == indexMap)
                recompute();
            assert indexMap.size() == this.size();
            Integer I = indexMap.get(name);
            return null==I ? -1 : I.intValue();
        }

        @Override
        public T get(String name)
        {
            int i = indexOfName(name);
            return i==-1 ? null : get(i);
        }

        protected String getName(T t)
        {
            return t.getName();
        }

        @Override
        public boolean add(T t)
        {
            if (readonly)
                throw new IllegalStateException();
            if (null != indexMap)
                indexMap.put(getName(t),size());
            return super.add(t);
        }

        @Override
        public boolean addAll(Collection<? extends T> c)
        {
            if (readonly)
                throw new IllegalStateException();
            for (T t : c)
            {
                if (null != indexMap)
                    indexMap.put(getName(t),size());
                super.add(t);
            }
            assert null == indexMap || indexMap.size() == this.size();
            return !c.isEmpty();
        }

        @Override
        public T set(int index, T element)
        {
            if (readonly)
                throw new IllegalStateException();
            indexMap = null;
            return super.set(index,element);
        }

        @Override
        protected void removeRange(int fromIndex, int toIndex)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean removeAll(Collection<?> c)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean retainAll(Collection<?> c)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void add(int index, T element)
        {
            throw new UnsupportedOperationException();
        }
    }

    /* same as named list, but uses getUniqueName() instead of getName(), needed for MemberList
     * PS: that's why getMember() return List instead of NamedList
     */
    static class _UniqueNamedList<T extends org.olap4j.impl.Named,MDE> extends _NamedList<T,MDE>
    {
        @Override
        protected String getName(T t)
        {
            return ((MetadataElement)t).getUniqueName();
        }
    }


    private static class _EmptyNamedList<MDE> extends _NamedList<Named,MDE>
    {
        public boolean add(Named n)
        {
            throw new UnsupportedOperationException();
        }
    }

    static final NamedList<Property> emptyPropertyList = (new _EmptyNamedList<Property>()).recast();
    static final NamedList<NamedSet> emptyNamedSetList = (new _EmptyNamedList<NamedSet>()).recast();
    static final NamedList<Member> emptyMemberList = (new _EmptyNamedList<Member>() {
        public void sort(Comparator<? super Named> c)
        {
        }
    }).recast();

    static final Comparator<Member> ORDINAL_COMPARATOR = new Comparator<Member>(){
        @Override
        public int compare(Member o1, Member o2)
        {
            return o1.getOrdinal() - o2.getOrdinal();
        }
    };


    static class MemberComparator implements Comparator<_Member>
    {
        final Property key;

        MemberComparator(Property key)
        {
            this.key = key;
        }

        @Override
        public int compare(_Member m1, _Member m2)
        {
            try
            {
                // we sort parent levels first, so parent.ordinal should already be set
                _Member p1 = (_Member)m1.getParentMember();
                _Member p2 = (_Member)m2.getParentMember();
                if (null != p1 && null != p2 && p1.ordinal != p2.ordinal)
                    return p1.ordinal - p2.ordinal;

                int o = m1.orig.getOrdinal() - m2.orig.getOrdinal();
                if (0 != o)
                    return o;

                if (null != key)
                {
                    Object k1 = m1.orig.getPropertyValue(key);
                    Object k2 = m2.orig.getPropertyValue(key);
                    if (null != k1 && null != k2)
                    {
                        int k = compareKey((Comparable) k1, (Comparable) k2);
                        if (0 != k)
                            return k;
                    }
                }

                return m1.getName().compareToIgnoreCase(m2.getName());
            }
            catch (OlapException x)
            {
                throw new RuntimeException(x);
            }
        }
    }

    static int compareKey(Comparable k1, Comparable k2)
    {
        if (k1 instanceof String && k2 instanceof String)
        {
            return ((String) k1).compareToIgnoreCase((String) k2);
        }
        if (k1.getClass() == k2.getClass())
        {
            return k1.compareTo(k2);
        }
        // handle special classes (e.g. #null)

        // check for special classes (e.g. #null)
        if (!(k1 instanceof Number) && !(k1 instanceof String) && !(k1 instanceof Date))
            return k1.compareTo(k2);
        else
            return -1 * k2.compareTo(k1);
    }
}
