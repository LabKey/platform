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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveTreeMap;
import org.labkey.api.data.JdbcType;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryService;
import org.labkey.query.olap.rolap.RolapCubeDef;
import org.olap4j.OlapException;
import org.olap4j.metadata.Dimension;
import org.olap4j.metadata.Level;
import org.olap4j.metadata.Member;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;

import static org.labkey.query.olap.rolap.RolapCubeDef.DimensionDef;
import static org.labkey.query.olap.rolap.RolapCubeDef.HierarchyDef;
import static org.labkey.query.olap.rolap.RolapCubeDef.LevelDef;
import static org.labkey.query.olap.rolap.RolapCubeDef.MeasureDef;



/**
 * Construct a CachedCube given a RolapCubeDef
 */
public class RolapCachedCubeFactory
{
    final public RolapCubeDef rolap;
    final public QuerySchema schema;


    public RolapCachedCubeFactory(RolapCubeDef rolap, QuerySchema s) throws SQLException
    {
        this.rolap = rolap;
        this.schema = s.getSchema(rolap.getSchemaName());

        if (null == schema)
            throw new SQLException("Schema not found: " + rolap.getSchemaName());
    }


    /**
     * Not much to see here, just loop through the dimensions and hierarchies to load members
     *
     * @return
     * @throws SQLException
     */
    public CachedCube createCachedCube() throws SQLException
    {
        CachedCube cube = new CachedCube(rolap.getName());

        cube.annotations = new CaseInsensitiveTreeMap<>();
        cube.annotations.putAll(rolap.getAnnotations());

        generateMeasuresDimension(cube);

        for (DimensionDef ddef : rolap.getDimensions())
        {
            CachedCube._Dimension d = new CachedCube._Dimension(cube, ddef);
            cube.dimensions.add(d);

            for (HierarchyDef hdef : ddef.getHierarchies())
            {
                CachedCube._Hierarchy h = new CachedCube._Hierarchy(cube, d, hdef);
                d.hierarchies.add(h);

                CachedCube._Level l = new CachedCube._Level(cube, h, Level.Type.ALL);
                h.levels.add(l);

                for (LevelDef ldef : hdef.getLevels())
                {
                    l = new CachedCube._Level(cube, h, ldef, h.levels.size());
                    h.levels.add(l);
                }

                generateHierarchyMembers(cube, hdef, h);
                h.levels.seal();
            }
            d.hierarchies.seal();
        }

        cube.dimensions.seal();
        cube.strings = null;

        return cube;
    }


    void generateMeasuresDimension(CachedCube cube) throws SQLException
    {
        CachedCube._Dimension dMeasures = new CachedCube._Dimension(cube, "Measures");
        dMeasures.dimensionType = Dimension.Type.MEASURE;
        cube.dimensions.add(dMeasures);
        CachedCube._Hierarchy hMeasures = new CachedCube._Hierarchy(cube, dMeasures, "Measures");
        dMeasures.hierarchies.add(hMeasures);
        dMeasures.hierarchies.seal();
        CachedCube._Level lMeasures = new CachedCube._Level(cube, hMeasures,"MeasuresLevel", 0);
        hMeasures.levels.add(lMeasures);
        hMeasures.levels.seal();
        for (MeasureDef measureDef : rolap.getMeasures())
        {
            if (measureDef.getName().startsWith("_"))
                continue;
            CachedCube._Measure m = new CachedCube._Measure(cube, lMeasures, measureDef.getName());
            lMeasures.members.add(m);
        }
        lMeasures.members.seal();
    }


    /**
     * Generate a result with all the data from the dimension table for this hierarchy
     * The results are sorted so we can save a lot of effort by looking for the first
     * level in the hierarchy with a "break" or change in data value, and pick up there
     * creating new members.  We remember the members before the break, and don't need
     * to look them up, or create them.
     */
    void generateHierarchyMembers(CachedCube cube, HierarchyDef hdef, CachedCube._Hierarchy h) throws SQLException
    {
        CachedCube._Level allLevel = (CachedCube._Level)h.getLevels().get(0);
        CachedCube._Member allMember = new CachedCube._Member(cube, allLevel, Member.Type.ALL);
        allLevel.members.add(allMember);

        // hdef.getLevels() does not include all Level, so need to add null to make things match
        ArrayList<CachedCube._Level> levelList = h.levels;
        ArrayList<LevelDef> levelDefList = new ArrayList<>();
        levelDefList.add(null);
        levelDefList.addAll(hdef.getLevels());
        assert levelDefList.size() == levelList.size();

        int levelCount = levelDefList.size();
        ArrayList<String> namesPrevious = new ArrayList<>(levelDefList.size());
        ArrayList<String> namesCurrent = new ArrayList<>(levelDefList.size());
        ArrayList<CachedCube._Member> membersCurrent = new ArrayList<>(levelDefList.size());

        namesPrevious.add(allMember.getName());
        namesCurrent.add(allMember.getName());
        membersCurrent.add(allMember);

        for (int l=1 ; l<levelCount ; l++)
        {
            namesPrevious.add(null);
            namesCurrent.add(null);
            membersCurrent.add(null);
        }

        CaseInsensitiveHashMap<CachedCube._Member> uniqueNameMap = new CaseInsensitiveHashMap<>();

        String hierarchySql = rolap.getMembersSQL(hdef);
        try (ResultSet rs = QueryService.get().select(schema, hierarchySql, null, true, false))
        {
            // compute jdbcType for all key columns
            for (int l = 1; l < levelCount; l++)
            {
                CachedCube._Level level = levelList.get(l);
                LevelDef ldef = levelDefList.get(l);
                level.jdbcType = ldef.computeKeyType(rs);
            }

            while (rs.next())
            {
                // find first level where value is different than in the previous row
                int breakLevel = 0;
                for (int l = 1; l < levelCount; l++)
                {
                    String name = levelDefList.get(l).getMembeNameFromResult(rs);
                    namesCurrent.set(l, name);
                    if (breakLevel == 0 && !StringUtils.equalsIgnoreCase(namesCurrent.get(l), namesPrevious.get(l)))
                        breakLevel = l;
                }

                if (breakLevel == 0)
                    continue;

                // allocate new Members as needed
                for (int l = breakLevel; l < levelCount; l++)
                {
                    CachedCube._Level level = levelList.get(l);
                    LevelDef levelDef = levelDefList.get(l);
                    CachedCube._Member parent = membersCurrent.get(l - 1);
                    String name = namesCurrent.get(l);

                    String uniqueName;
                    if (l == 1)
                        uniqueName = h.getUniqueName() + ".[" + name + "]";
                    else
                        uniqueName = parent.getUniqueName() + ".[" + name + "]";

                    // it's possible we've seen this member before if we're sorting case-sensitve, for instance
                    // NOTE: There is an assumption there that testing uniqueName equality is the same as testing key equality.
                    // This should be the case for the cube to be useful, and it's easier to do one string compare than
                    // comparing arrays of objects
                    CachedCube._Member m = uniqueNameMap.get(uniqueName);
                    if (null != m)
                    {
                        assert m._parent == membersCurrent.get(l - 1);
                        membersCurrent.set(l, m);
                        continue;
                    }
                    // need to create a new member! yeah

                    m = new CachedCube._Member(cube, level, parent, name, levelDef.isLeaf());
                    m.keyValue = (Comparable)levelDef.getKeyValue(rs);
                    if (m.keyValue instanceof String)
                        m.keyValue = cube.intern((String)m.keyValue);
                    m.ordinalValue = (Comparable)levelDef.getOrindalValue(rs);

                    uniqueNameMap.put(uniqueName, m);
                    membersCurrent.set(l, m);
                    level.members.add(m);
                }

                ArrayList t = namesPrevious;
                namesPrevious = namesCurrent;
                namesCurrent = t;
            }
        }
        catch (SQLException|QueryParseException|AssertionError x)
        {
            throw x;
        }

        // add #NOTNULL members
        CachedCube._Member parent = (CachedCube._Member)levelList.get(0).getMembers().get(0);
        for (int l = 1; l < levelCount; l++)
        {
            CachedCube._Level level = levelList.get(l);
            LevelDef ldef = levelDefList.get(l);
            CachedCube._Member notNullMember = new CachedCube._NotNullMember(cube, level, parent, ldef.isLeaf());
            level.members.add(notNullMember);
            parent = notNullMember;
        }

        orderMembers(h);

        for (Level l : h.getLevels())
        {
            ((CachedCube._Level)l).members.seal();
        }
    }


    /*
     * SORTING
     */

    static void orderMembers(CachedCube._Hierarchy h) throws OlapException
    {
        for (Level L : h.getLevels())
        {
            CachedCube._Level l = (CachedCube._Level) L;
            orderLevelMembers(l.orig, l.members);
        }
        for (Level L : h.getLevels())
        {
            CachedCube._Level l = (CachedCube._Level) L;
            if (l.getDepth() != h.getLevels().size() - 1)
                orderChildMembers(l.members);
        }
        // free memory associated with original ordinalValue
        for (Level L : h.getLevels())
        {
            CachedCube._Level l = (CachedCube._Level) L;
            for (Member m : l.getMembers())
                ((CachedCube._Member)m).ordinalValue = null;
        }
    }


    static void orderLevelMembers(Level l, CachedCube._NamedList<CachedCube._Member, Member> list)
    {
        list._sort(MEMBER_COMPARATOR);
        for (int i = 0; i < list.size(); i++)
            list.get(i).ordinal = i;
    }


    static void orderChildMembers(CachedCube._NamedList<CachedCube._Member, Member> list) throws OlapException
    {
        for (CachedCube._Member m : list)
        {
            if (null == m.childMembers || m.childMembers.isEmpty())
                continue;
            m.childMembers.sort(MEMBER_COMPARATOR);
            // We also create a map using the KEY value
            JdbcType childType = ((CachedCube._Member) m.childMembers.get(0)).level.jdbcType;
            m._keyMap = CachedCube.KeyMap.create(childType, m.childMembers);
        }
    }

    static MemberComparator MEMBER_COMPARATOR = new MemberComparator();

    static class MemberComparator implements Comparator<Member>
    {
        @Override
        public int compare(Member m1, Member m2)
        {
            int ret;
            ret = _compare((CachedCube._Member)m1,(CachedCube._Member)m2);
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


        public int _compare(CachedCube._Member m1, CachedCube._Member m2)
        {
            int o = m1.ordinal - m2.ordinal;
            if (0 != o)
                return o;

            CachedCube._Member p1 = (CachedCube._Member)m1.getParentMember();
            CachedCube._Member p2 = (CachedCube._Member)m2.getParentMember();
            assert (null==p1) == (null==p2);
            if (null != p1 && null != p2 && p1 != p2)
            {
                int p = _compare(p1,p2);
                if (0 != p)
                    return p;
            }

            if (m1.isCalculated() !=  m2.isCalculated())
                return m1.isCalculated() ?  1 : -1;

            o = compareKey(m1.ordinalValue, m2.ordinalValue, null);
            if (0 != o)
                return o;
            o = compareKey(m1.keyValue, m2.keyValue, null);
            if (0 != o)
                return o;
            return compareKey(m1.getName(), m2.getName(), "#null");
        }
    }


    static int compareKey(Comparable k1, Comparable k2, @Nullable String nullString)
    {
        if (null != nullString)
        {
            if (k1 instanceof String && StringUtils.equals((String)k1,nullString))
                k1 = null;
            if (k2 instanceof String && StringUtils.equals((String)k2,nullString))
                k2 = null;
        }

        if (k1 == k2)
            return 0;
        if (k1 == null)
            return -1;
        if (k2 == null)
            return 1;
        if (k1 instanceof String && k2 instanceof String)
        {
            return ((String) k1).compareToIgnoreCase((String) k2);
        }
        if (k1.getClass() == k2.getClass())
        {
            return k1.compareTo(k2);
        }
        assert false;
        return k1.compareTo(k2);
    }
}
