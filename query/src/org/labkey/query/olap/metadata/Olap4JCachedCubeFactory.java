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

import mondrian.olap.Annotated;
import mondrian.olap.Annotation;
import org.olap4j.OlapException;
import org.olap4j.OlapWrapper;
import org.olap4j.metadata.Cube;
import org.olap4j.metadata.Dimension;
import org.olap4j.metadata.Hierarchy;
import org.olap4j.metadata.Level;
import org.olap4j.metadata.Measure;
import org.olap4j.metadata.Member;
import org.olap4j.metadata.NamedList;
import org.olap4j.metadata.NamedSet;
import org.olap4j.metadata.Property;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import static org.labkey.query.olap.metadata.CachedCube._Dimension;
import static org.labkey.query.olap.metadata.CachedCube._Hierarchy;
import static org.labkey.query.olap.metadata.CachedCube._Level;
import static org.labkey.query.olap.metadata.CachedCube._Member;
import static org.labkey.query.olap.metadata.CachedCube._Measure;
import static org.labkey.query.olap.metadata.CachedCube._NamedList;
import static org.labkey.query.olap.metadata.CachedCube._EmptyNamedList;

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
public class Olap4JCachedCubeFactory
{
    public CachedCube createCachedCube(Cube c) throws SQLException
    {
        CachedCube ret = new CachedCube(c);
        loadDimensions(ret, c);
        ret.strings = null;
        return ret;
    }


    void loadDimensions(CachedCube cube, Cube c) throws SQLException
    {
        for (Dimension d : c.getDimensions())
        {
            cube.dimensions.add(loadDimension(cube,d));
        }
        cube.dimensions.seal();

        Map<String,String> map = new HashMap<>();
        readAnnotations(c,map);
        cube.annotations = Collections.unmodifiableMap(map);
    }


    void readAnnotations(Object olap4j, Map<String,String> map)
    {
        Annotated annotated = null;
        if (olap4j instanceof Annotated)
            annotated = (Annotated)olap4j;
        else if (olap4j instanceof OlapWrapper)
        {
            try
            {
                annotated = ((OlapWrapper) olap4j).unwrap(Annotated.class);
            }
            catch (SQLException x)
            {
                /* */
            }
        }

        if (null != annotated)
        {
            Map<String, Annotation> annotations = annotated.getAnnotationMap();
            for (Map.Entry<String,Annotation> e : annotations.entrySet())
            {
                if (null != e.getValue())
                {
                    Annotation ann = e.getValue();
                    if (null != ann.getValue())
                        map.put(ann.getName(), String.valueOf(ann.getValue()));
                }
            }
        }
    }


    _Dimension loadDimension(CachedCube cube, Dimension d) throws SQLException
    {
        _Dimension ret = new CachedCube._Dimension(cube, d);

        loadHierarchies(cube, ret, d);

        ret.dimensionType = d.getDimensionType();
        Hierarchy def = d.getDefaultHierarchy();
        ret.defaultHierarchy = null == def ? null : ret.hierarchies.get(def.getName());
        return ret;
    }


    void loadHierarchies(CachedCube cube, _Dimension cached, Dimension d) throws SQLException
    {
        for (Hierarchy h : d.getHierarchies())
        {
            cached.hierarchies.add(loadHierarchy(cube, cached, h));
        }
        cached.hierarchies.seal();
    }


    _Hierarchy loadHierarchy(CachedCube cube, _Dimension cached, Hierarchy h) throws SQLException
    {
        _Hierarchy ret = new _Hierarchy(cube, cached, h);

        ArrayList<_Level> list = new ArrayList<>();

        // try top down
        _Level parentLevel = null;
        for (int i =0 ; i < h.getLevels().size() ; i++)
        {
            Level l = h.getLevels().get(i);
            _Level thisLevel = loadLevel(cube, ret, l, parentLevel);
            list.add(thisLevel);
            parentLevel = thisLevel;
        }

        for (_Level l : list)
            ret.levels.add(l);
        ret.levels.seal();

        _Level defaultMemberLevel = ret.levels.get(h.getDefaultMember().getLevel().getName());
        ret.defaultMember = (_Member)defaultMemberLevel.getMembers().get(h.getDefaultMember().getUniqueName());

        for (int lnum = 1 ; lnum < ret.levels.size() ; lnum ++)
        {
            for (Member M : ret.levels.get(lnum).getMembers())
            {
                _Member m = (_Member)M;
                if (null != m.childMembers)
                    m.childMembers.trimToSize();
                assert null != m._parent;
                assert ((_Member)m._parent).childMembers.contains(m);
            }
        }

        // NOTE: the cube configuration related to ordering members (ordinalColumn etc)
        // only apply to the results of MDX queries.  It seems that ordering of members
        // in the cube metadata can not be relied on.  So we sort them ourselves.
        Olap4JCachedCubeFactory.orderMembers(ret);

        for (_Level l : list)
        {
            l.orig = null;
            l.members.seal();
            for (_Member m : l.members)
                m.orig = null;
        }
        return ret;
    }


    _Level loadLevel(CachedCube cube, _Hierarchy cached, Level l, _Level parentLevel) throws SQLException
    {
        _Level ret = new _Level(cube, cached, l);

        for (Property p : l.getProperties())
        {
            String n = p.getName();
            if (n.endsWith("_NAME") || n.startsWith("LEVEL_") || n.startsWith("PARENT_"))
                continue;
            if (n.equals("MEMBER_ORDINAL") || n.equals("MEMBER_TYPE") || n.equals("CHILDREN_CARDINALITY"))
                continue;
            ret.memberProperties.add(new CachedCube._Property(cube, p));
        }

        if ("[Measures]".equals(cached.getDimension().getUniqueName()))
        {
            for (Member m : l.getMembers())
            {
                _Measure cachedM = new _Measure(cube, ret, parentLevel, (Measure) m);
                cachedM.annotations = new TreeMap<>();
                readAnnotations(m, cachedM.annotations);
                ret.members.add(cachedM);
            }
        }
        else
        {
            boolean isLeaf = l.getDepth() == l.getHierarchy().getLevels().size()-1;
            for (Member m : l.getMembers())
            {
                ret.members.add(new _Member(cube, ret, parentLevel, m, isLeaf));
            }
        }
        ret.members.trimToSize();
        return ret;
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
                m.childMembers.sort(ORDINAL_COMPARATOR);
    }


    static final NamedList<NamedSet> emptyNamedSetList = (new _EmptyNamedList<NamedSet>()).recast();

    static final Comparator<Member> ORDINAL_COMPARATOR = Comparator.comparingInt(Member::getOrdinal);


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
            int ret;
            ret = _compare(m1,m2);
//            if (ret < 0)
//            {
//                System.err.println(m1.getUniqueName() + " < " + m2.getUniqueName());
//            }
//            else if (ret > 0)
//            {
//                System.err.println(m2.getUniqueName() + " < " + m1.getUniqueName());
//            }
//            else
//            {
//                System.err.println( m1.getUniqueName() + " = " + m2.getUniqueName() + " *** ");
//            }
            return ret;
        }

        public int _compare(_Member m1, _Member m2)
        {
            try
            {
                // we sort parent levels first, so parent.ordinal should already be set
                _Member p1 = (_Member)m1.getParentMember();
                _Member p2 = (_Member)m2.getParentMember();
                assert (null==p1) == (null==p2);
                if (p1 != p2)
                {
                    int p = _compare(p1,p2);
                    if (0 != p)
                        return p;
                }

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

        // check for special classes (e.g. #null wrapper)
        if (!(k1 instanceof Number) && !(k1 instanceof String) && !(k1 instanceof Date))
            return k1.compareTo(k2);
        else
            return -1 * k2.compareTo(k1);
    }
}
