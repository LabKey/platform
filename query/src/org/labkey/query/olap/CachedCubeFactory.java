package org.labkey.query.olap;

import mondrian.olap.Annotated;
import mondrian.olap.Annotation;
import org.jetbrains.annotations.Nullable;
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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
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
            throw new UnsupportedOperationException();
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
            for (int i = h.getLevels().size()-1 ; i>= 0 ; i--)
            {
                Level l = h.getLevels().get(i);
                _Level thisLevel = new _Level(this, l, lowerLevel, hash);
                levels.add(thisLevel);
                lowerLevel = thisLevel;
            }
            _Level l = levels.get(h.getDefaultMember().getLevel().getName());
            defaultMember = l.getMembers().get(h.getDefaultMember().getName());
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
            return null;
        }
    }


    static class _Level extends _MetadataElement implements Level
    {
        final _Hierarchy hierarchy;
        final Level.Type levelType;
        final _NamedList<_Member,Member> members = new _NamedList<>();

        _Level(_Hierarchy h, Level l, @Nullable _Level lowerLevel, _Hash hash) throws OlapException
        {
            super(l);
            hash.add(getUniqueName());
            this.hierarchy = h;
            levelType = l.getLevelType();
            int ordinal = 0;
            if ("[Measures]".equals(getDimension().getUniqueName()))
            {
                for (Member m : l.getMembers())
                {
                    members.add(new _Measure(this, lowerLevel, (Measure)m, ordinal++, hash));
                }
            }
            else
            {
                for (Member m : l.getMembers())
                {
                    members.add(new _Member(this, lowerLevel, m, ordinal++, hash));
                }
            }
        }

        @Override
        public int getDepth()
        {
            return 0;
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
            return null;
        }

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


    static class _Member extends _MetadataElement implements Member
    {
        final boolean all;
        final _Level level;
        final int ordinal;
        final Member.Type memberType;
        final Member[] childMembers;

        _Member(_Level level, _Level lowerLevel, Member m, int o, _Hash hash) throws OlapException
        {
            super(m);
            hash.add(getUniqueName());
            this.level = level;
            this.ordinal = o;
            this.all = m.isAll();
            this.memberType = m.getMemberType();
            Member[] arr = null;
            List<? extends Member> list = m.getChildMembers();
            if (null != lowerLevel && 0 < list.size())
            {
                arr = list.toArray(new Member[list.size()]);
                for (int i=0 ; i<arr.length ; i++)
                {
                    arr[i] = lowerLevel.getMembers().get(arr[i].getName());
                    assert null != arr[i];
                }
            }
            childMembers = arr;
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
            return null;
        }

        @Override
        public String getPropertyFormattedValue(Property property) throws OlapException
        {
            return null;
        }

        @Override
        public void setProperty(Property property, Object o) throws OlapException
        {

        }

        @Override
        public NamedList<Property> getProperties()
        {
            return emptyPropertyList;
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
        _Measure(_Level l, _Level lowerLevel, Measure m, int ordinal, _Hash hash) throws OlapException
        {
            super(l,lowerLevel,m,ordinal,hash);
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


    static class _NamedList<T extends org.olap4j.impl.Named,MDE> extends NamedListImpl<T>
    {
        NamedList<MDE> recast()
        {
            return (NamedList<MDE>)(NamedList)this;
        }
    }

    static final NamedList<Property> emptyPropertyList = (new _NamedList<Named,Property>()).recast();
    static final NamedList<NamedSet> emptyNamedSetList = (new _NamedList<Named,NamedSet>()).recast();
    static final NamedList<Member> emptyMemberList = (new _NamedList<Named,Member>()).recast();
}
