/*
 * Copyright (c) 2014-2017 LabKey Corporation
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
package org.labkey.query.olap.metadata;

import org.labkey.api.collections.ArrayListMap;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.JdbcType;
import org.labkey.api.util.Pair;
import org.labkey.query.olap.rolap.RolapCubeDef;
import org.olap4j.OlapException;
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
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
* Created by matthew on 9/4/14.
 *
 * TODO consider hardening this class so that it can be made read-only after construction.
*/
public class CachedCube extends MetadataElementBase implements Cube
{
    Long hash = null;
    final _NamedList<_Dimension,Dimension> dimensions = new _NamedList<>();

    /* TODO redo compute hash code */
    CachedCube(Cube c) throws SQLException
    {
        super(null, c, null);
    }

    CachedCube(String name) throws SQLException
    {
        super(null, name, null);
    }

    public long getLongHashCode()
    {
        if (null == hash)
            hash = computeHash(false);
        return hash.longValue();
    }


    @Override
    public int hashCode()
    {
        return (int)getLongHashCode();
    }


    private Long computeHash(boolean caseSensitive)
    {
        Hash hash = new Hash(caseSensitive);
        hash.add(getName());
        for (_Dimension d : dimensions)
        {
            hash.add(d.getName());
            for (_Hierarchy h : d.hierarchies)
            {
                hash.add(h.getName());
                for (_Level l : h.levels)
                {
                    hash.add(l.getName());
                    for (_Member m : l.members)
                        hash.add(m.getName());
                }
            }
        }
        return hash.get();
    }


    static boolean debugCompareCubes(CachedCube a, CachedCube b)
    {
        return a.getLongHashCode() == b.getLongHashCode();
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
        return Olap4JCachedCubeFactory.emptyNamedSetList;
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


    public static class _Dimension extends MetadataElementBase implements Dimension
    {
        final _NamedList<_Hierarchy,Hierarchy> hierarchies = new _NamedList<>();
        _Hierarchy defaultHierarchy;
        Type dimensionType;

        _Dimension(CachedCube cc, Dimension d) throws OlapException
        {
            super(cc, d, null);
        }

        _Dimension(CachedCube cc, String name) throws OlapException
        {
            super(cc, name, null);
        }

        _Dimension(CachedCube cc, RolapCubeDef.DimensionDef ddef) throws OlapException
        {
            this(cc, ddef.getName());
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


    static class _Hierarchy extends MetadataElementBase implements Hierarchy
    {
        final _Dimension dimension;
        final _NamedList<_Level,Level> levels = new _NamedList<>();
        _Member defaultMember;


        _Hierarchy(CachedCube cc, _Dimension dimension, Hierarchy h) throws OlapException
        {
            super(cc, h, null);
            this.dimension = dimension;
        }


        _Hierarchy(CachedCube cc, _Dimension dimension, String name) throws OlapException
        {
            super(cc, dimension.getName().equals(name) ? name : dimension.getName() + "." + name, null);
            this.dimension = dimension;
        }


        _Hierarchy(CachedCube cc, _Dimension dimension, RolapCubeDef.HierarchyDef h) throws OlapException
        {
            this(cc, dimension, h.getName());
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

    public static class _Level extends MetadataElementBase implements Level
    {
        final String uniqueNameStr;
        final int depth;
        final _Hierarchy hierarchy;
        final Type levelType;
        JdbcType jdbcType = JdbcType.VARCHAR;
        final _NamedList<_Member,Member> members = new _UniqueNamedList<>();
        final ArrayListMap.FindMap<String> memberPropertiesFindMap= new ArrayListMap.FindMap<>(new HashMap<String,Integer>());
        final _NamedList<_Property,Property> memberProperties = new _NamedList<>();

        // temporary pointer, used in olap4j loading
        Level orig;

        _Level(CachedCube cc, _Hierarchy h, Level l) throws OlapException
        {
            super(cc, l, null);

            this.depth = l.getDepth();
            this.hierarchy = h;
            this.levelType = l.getLevelType();
            this.orig = l;
            this.uniqueNameStr = super.getUniqueName();
        }

        // Type == ALL
        _Level(CachedCube cc, _Hierarchy h, Type t) throws OlapException
        {
            super(cc, "(All)", h);
            if (t != Type.ALL)
                throw new IllegalArgumentException();
            this.depth = 0;
            this.hierarchy = h;
            this.levelType = t;
            this.orig = null;
            this.uniqueNameStr = super.getUniqueName();
        }


        _Level(CachedCube cc, _Hierarchy h, String name, int depth) throws OlapException
        {
            super(cc, name, h);
            this.depth = depth;
            this.hierarchy = h;
            this.levelType = Type.REGULAR;
            this.orig = null;
            this.uniqueNameStr = super.getUniqueName();
        }


        _Level(CachedCube cc, _Hierarchy h, RolapCubeDef.LevelDef l, int depth) throws OlapException
        {
            this(cc, h, l.getName(), depth);
        }


        @Override
        public String getUniqueName()
        {
            return uniqueNameStr;
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
         * @throws org.olap4j.OlapException
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

    public static class _Member extends MetadataElementBase implements Member
    {
        final boolean all;
        final _Level level;
        Type memberType;
        final ArrayList<Member> childMembers;
        Member _parent;
        final Map<String,Pair<Object,String>> _properties;
        int ordinal = -1;

        // this is the only not thread-safe member, this is built lazily as needed
        Map<Object,_Member> _keyMap;


        // Olap4JCachedCubeFactory orig is to facilitate sorting by
        Member orig = null;

        // RolapCachedCubeFactory, ordinalValue can be set to null after int ordinal is computed
        Comparable keyValue;
        Comparable ordinalValue;


        // Type == ALL
        _Member(CachedCube cc, _Level level, Member.Type type) throws OlapException
        {
            super(cc, "(All)", level.hierarchy);
            if (type != Member.Type.ALL)
                throw new IllegalArgumentException();
            this.level = level;
            this.all = true;
            this.memberType = Type.ALL;
            childMembers = new ArrayList<>();
            _properties = null;
        }


        public _Member(CachedCube cc, _Level level, _Member parent, String name, boolean isLeaf) throws OlapException
        {
            super(cc, name, level.depth<2 ? level.hierarchy : parent);
            this.level = level;
            this.all = false;
            this.memberType = Type.REGULAR;
            childMembers = isLeaf ? null : new ArrayList<Member>();
            _properties = null;
            this._parent = parent;
            if (null != parent)
                parent.childMembers.add(this);
        }


        _Member(CachedCube cc, _Level level, _Level parentLevel, Member m, boolean isLeaf) throws OlapException
        {
            // TODO this is the case where we could pass in the parent member to save space on cached strings
            super(cc, m, null);
            this.level = level;
            this.all = m.isAll();
            this.memberType = m.getMemberType();
            this.orig = m;
            // don't allocate array for lowest level
            this.childMembers = isLeaf ? null : new ArrayList<Member>();

            if (null != parentLevel)
            {
                _parent = parentLevel.members.get(m.getParentMember().getUniqueName());
                if (null == _parent)
                {
                    throw new IllegalStateException("Unable to load " + m.getParentMember().getUniqueName() + ". This might be caused by a trailing space or an improperly encoded value.");
                }
                ((_Member)_parent).childMembers.add(this);
            }

            ArrayListMap<String,Pair<Object,String>> map = new ArrayListMap<>(level.memberPropertiesFindMap);
            for (Property p : level.memberProperties)
            {
                Object value = m.getPropertyValue(p);
                if (null == value)
                    continue;
                String formatted = m.getPropertyFormattedValue(p);
                map.put(p.getName(), new Pair<>(value,formatted));
            }
            _properties = map.isEmpty() ? null : map;
        }


        public Object getKeyValue() throws OlapException
        {
            if (null == _properties)
            {
                return keyValue;
            }
            else
            {
                return _properties.get("KEY");
            }
        }


        public List<? extends Member> getChildMembersArray() // well not array actually, but fast list
        {
            List<? extends Member> list = childMembers;
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


        public _Member getChildMemberByKey(Object key)
        {
            if (null == _keyMap)
                throw new IllegalStateException("Factory did not construct a keymap");
            return _keyMap.get(key);
        }


        @Override
        public int getChildMemberCount() throws OlapException
        {
            return childMembers.size();
        }

        @Override
        public Member getParentMember()
        {
            return _parent;
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
            return false;
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
            Pair<Object,String> p = _properties.get(property.getName());
            if (null == p)
                return null;
            return p.first;
        }

        @Override
        public String getPropertyFormattedValue(Property property) throws OlapException
        {
            if (null == _properties)
                return null;
            Pair<Object,String> p = _properties.get(property.getName());
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
            return false;
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


    public static class _NotNullMember extends _Member
    {
        _NotNullMember(CachedCube cc, _Level level, _Member parent, boolean isLeaf) throws OlapException
        {
            super(cc, level, parent, "#notnull", isLeaf);
        }

        @Override
        public boolean isCalculated()
        {
            return true;
        }

        @Override
        public boolean isHidden()
        {
            return true;
        }
    }


    public static class _Measure extends _Member implements Measure
    {
        _Measure(CachedCube cc, _Level l, _Level parentLevel, Measure m) throws OlapException
        {
            super(cc, l,parentLevel,m,true);
            memberType = Type.MEASURE;
        }

        _Measure(CachedCube cc, _Level l, String name) throws OlapException
        {
            super(cc, l, null, name, true);
            memberType = Type.MEASURE;
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


    public static class _Property extends MetadataElementBase implements Property
    {
        final Datatype datatype;
        final Set<TypeFlag> type;
        final ContentType contentType;
        final boolean visible;

        _Property(CachedCube cc, Property p)
        {
            super(cc, p, null);
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


    public static class _NamedList<T extends org.olap4j.impl.Named,MDE> extends NamedListImpl<T>
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

        public boolean isReadOnly()
        {
            return readonly;
        }

        private void recompute()
        {
            if (readonly)
                throw new IllegalStateException();
            indexMap = new CaseInsensitiveHashMap<>();
            for (int i=0 ; i<size() ; i++)
            {
                T t = get(i);
                Integer prev = indexMap.put(getName(t), i);
                if (null != prev)
                {
                    recomputeCaseSensitive();
                    return;
                }
            }
            assert indexMap.size() == this.size();
        }


        private void recomputeCaseSensitive()
        {
            if (readonly)
                throw new IllegalStateException();
            indexMap = new HashMap<>();
            for (int i=0 ; i<size() ; i++)
            {
                T t = get(i);
                Integer prev = indexMap.put(getName(t), i);
                if (null != prev)
                {
                    throw new IllegalStateException("Found cube member with duplicate name: " + getName(t) + "\n" +
                            "This is likely due to a misconfiguration of the cube definition involving 'column', 'nameColumn', and 'ordinalColumn'"
                        );
                }
            }
            assert indexMap.size() == this.size();
        }


        // cant call this sort() and be safe in both java 7 and 8
        void _sort(Comparator<? super T> c)
        {
            if (readonly)
                throw new IllegalStateException();
            indexMap = null;
            sort(c);
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
    public static class _UniqueNamedList<T extends org.olap4j.impl.Named,MDE> extends _NamedList<T,MDE>
    {
        @Override
        protected String getName(T t)
        {
            return ((MetadataElement)t).getUniqueName();
        }
    }


    public static class _EmptyNamedList<MDE> extends _NamedList<Named,MDE>
    {
        public boolean add(Named n)
        {
            throw new UnsupportedOperationException();
        }
    }


    static final NamedList<Member> emptyMemberList = (new _EmptyNamedList<Member>() {
        public void _sort(Comparator<? super Named> c)
        {
        }
    }).recast();


    static class KeyMap extends AbstractMap<Object,_Member>
    {
        final JdbcType type;
        final Map<Object,_Member> impl;

        public static KeyMap create(JdbcType type, Collection<Member> members) throws OlapException
        {
            Map<Object,_Member> map;

            if (type.isText())
                map = (Map<Object,_Member>)(Map)new CaseInsensitiveHashMap<_Member>();
            else
                map = new HashMap<>();

            for (Member M : members)
            {
                _Member m = (_Member)M;
                if (m.isCalculated())
                    continue;
                _Member prev = map.put(convertKey(type, m.getKeyValue()), m);
                if (null != prev)
                {
                    // two possibilities, duplicate key which is an error, or case-sensitivy problem
                    if (!type.isText())
                        throw new IllegalStateException("Duplicate child member key: " + String.valueOf(m.getKeyValue()));
                    break;  // deal with this later
                }
            }
            if (map.size() == members.size())
                return new KeyMap(type, map);

            // try again with case sensitive map
            map = new HashMap<>();
            for (Member M : members)
            {
                _Member m = (_Member)M;
                if (m.isCalculated())
                    continue;
                _Member prev = map.put(convertKey(type, m.getKeyValue()), m);
                if (null != prev)
                    throw new IllegalStateException("Duplicate child member key: " + String.valueOf(m.getKeyValue()));
            }
            return new KeyMap(type, map);
        }

        private KeyMap(JdbcType type, Map<Object,_Member> map) throws OlapException
        {
            this.type = type;
            this.impl = map;
        }

        static Object convertKey(JdbcType type, Object rawKey)
        {
            Object key;
            if (null == rawKey && type.isText())
                key = "#null";
            else
                key = type.convert(rawKey);
            return key;
        }

        Object convertKey(Object rawKey)
        {
            return convertKey(type, rawKey);
        }

        @Override
        public _Member get(Object key)
        {
            return impl.get(convertKey(key));
        }

        @Override
        public _Member put(Object key, _Member value)
        {
            return impl.put(convertKey(key), value);
        }

        @Override
        public Set<Entry<Object, _Member>> entrySet()
        {
            return impl.entrySet();
        }
    }


    // during cube construction we want to make sure we share strings as much as possible
    HashMap<String,String> strings = new HashMap<>();
    String intern(String s)
    {
        String i = strings.get(s);
        if (null != i)
        {
            if (i == s)
                return i;
            return i;
        }
        i = new String(s);
        strings.put(i,i);
        return i;
    }
}
