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
package org.labkey.query.olap.rolap;

import org.json.JSONObject;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.labkey.api.action.NullSafeBindException;
import org.labkey.api.collections.CaseInsensitiveTreeMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.TestContext;
import org.labkey.query.olap.BitSetQueryImpl;
import org.labkey.query.olap.OlapSchemaDescriptor;
import org.labkey.query.olap.QubeQuery;
import org.labkey.query.olap.ServerManager;
import org.labkey.query.olap.metadata.CachedCube;
import org.olap4j.Cell;
import org.olap4j.CellSet;
import org.olap4j.OlapConnection;
import org.olap4j.Position;
import org.olap4j.metadata.Cube;
import org.olap4j.metadata.Hierarchy;
import org.olap4j.metadata.Level;
import org.olap4j.metadata.Member;
import org.springframework.validation.BindException;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;


/**
 * Test the ROLAP implementation of BitSetQueryImpl
 */
public class RolapTestCase extends Assert
{
    User getUser()
    {
        return TestContext.get().getUser();
    }

    static Container getContainer()
    {
        return JunitUtil.getTestContainer();
    }


    @AfterClass
    public static void reset()
    {
        ServerManager.cubeDataChanged(JunitUtil.getTestContainer());
    }


    @BeforeClass
    public static void init()
    {
        Container c = getContainer();
        HashSet<Module> am = new HashSet<>(c.getActiveModules());
        if (am.add(ModuleLoader.getInstance().getModule("query")))
            c.setActiveModules(am);
        ServerManager.cubeDataChanged(JunitUtil.getTestContainer());
    }


    @Test
    public void testSchema()
    {
        UserSchema schema = new RolapTestSchema(getUser(), getContainer());
        ArrayList<Map> result;

        TableInfo p = schema.getTable("Participant");
        assertNotNull(p);
        assertNotNull(p.getColumn("ptid"));
        assertNotNull(p.getColumn("gender"));
        result = new TableSelector(p).getArrayList(Map.class);
        assertEquals(8 * 6, result.size());

        TableInfo v = schema.getTable("Visit");
        assertNotNull(v);
        assertNotNull(v.getColumn("visitid"));
        assertNotNull(v.getColumn("label"));
        result = new TableSelector(v).getArrayList(Map.class);
        assertEquals(4, result.size());

        TableInfo s = schema.getTable("Study");
        assertNotNull(s);
        assertNotNull(s.getColumn("studyid"));
        assertNotNull(s.getColumn("type"));
        assertNotNull(s.getColumn("condition"));
        result = new TableSelector(s).getArrayList(Map.class);
        assertEquals(6, result.size());
    }


    CachedCube getCachedCube() throws SQLException, IOException
    {
        UserSchema schema = new RolapTestSchema(getUser(), getContainer());
        OlapSchemaDescriptor d = ServerManager.getDescriptor(getContainer(), "query:/junit");
        assertNotNull(d);
        Cube cube = ServerManager.getCachedCubeRolap(d, getContainer(), getUser(), "JunitCube", schema);
        assertNotNull(cube);
        return (CachedCube)cube;
    }


    @Test
    public void testCachedCube() throws Exception
    {
        Cube cube = getCachedCube();

        Hierarchy hParticipant = cube.getHierarchies().get("Participant");
        assertNotNull(hParticipant);
        Level lAll = hParticipant.getLevels().get(0);
        assertNotNull(lAll);
        assertEquals("(All)", lAll.getName());
        Level lParticipant = hParticipant.getLevels().get(1);
        assertNotNull(lParticipant);
        assertEquals("[Participant].[Participant]",lParticipant.getUniqueName());
        // #notnull
        assertEquals(6*8+1,lParticipant.getMembers().size());

        Hierarchy hStudy = cube.getHierarchies().get("Study");
        Level lStudy = hStudy.getLevels().get(1);
        // #notnull
        assertEquals(6+1,lStudy.getMembers().size());

        Hierarchy hStudyType = cube.getHierarchies().get("Study.Type");
        Level lStudyType = hStudyType.getLevels().get(1);
        // 3 + #notnull
        assertEquals(4,lStudyType.getMembers().size());

        Hierarchy hStudyCondition = cube.getHierarchies().get("Study.Condition");
        Level lStudyCondition = hStudyCondition.getLevels().get(1);
        // 3 + #notnull
        assertEquals(4,lStudyCondition.getMembers().size());

        Hierarchy hAssay = cube.getHierarchies().get("Assay");
        assertNotNull(hAssay);
        Level lAssay = hAssay.getLevels().get(1);
        // 5 + #notnull
        assertEquals(5+1,lAssay.getMembers().size());
    }


    Map<String,Integer> oneAxisQuery(String json) throws Exception
    {
        JSONObject query = new JSONObject(json);
        return oneAxisQuery(query);
    }


    Map<String,Integer> oneAxisQuery(JSONObject query) throws Exception
    {
        Cube cube = getCachedCube();
        OlapSchemaDescriptor sd = ServerManager.getDescriptor(getContainer(), "query:/junit");
        assertNotNull(sd);

        BindException errors = new NullSafeBindException(query, "query");
        QubeQuery qquery = new QubeQuery(cube);
        qquery.fromJson(query, errors);
        assertFalse(errors.hasErrors());

        Map<String,Integer> ret = new CaseInsensitiveTreeMap<>();
        OlapConnection conn = null;
        if (sd.usesMondrian())
            sd.getConnection(getContainer(), getUser());
        BitSetQueryImpl bitsetquery = new BitSetQueryImpl(getContainer(), getUser(), sd, cube, conn, qquery, errors);
        assertFalse(errors.hasErrors());
        try (CellSet cs = bitsetquery.executeQuery())
        {
            for (Position p : cs.getAxes().get(1).getPositions())
            {
                Member m = p.getMembers().get(0);
                Cell cell = cs.getCell(p);
                Object v = cell.getValue();
                Integer i = null==v ? null : ((Number)v).intValue();
                assertFalse(ret.containsKey(m.getUniqueName()));
                ret.put(m.getUniqueName(), i);
            }
        }
        return ret;
    }


    void validateOneAxisQueryCounts(String json, Integer... counts) throws Exception
    {
        Map<String,Integer> result = oneAxisQuery(json);
        assertEquals(counts.length, result.size());

        int i=0;
        for (Map.Entry<String,Integer> e : result.entrySet())
        {
            Integer expectedCount = counts[i++];
            String member = e.getKey();
            Integer actualCount = e.getValue();
            assertEquals(member, expectedCount, actualCount);
        }

    }


    @Test
    public void testNoFilters() throws Exception
    {
        Map<String,Integer> cs;

        cs = oneAxisQuery(
                "{\n" +
                "\"onRows\":{\"level\":\"[Participant].[Participant]\"},\n" +
                "\"countDistinctLevel\":\"[Participant].[Participant]\"\n" +
                "}"
        );
        assertEquals(6 * 8, cs.size());
        for (Integer I : cs.values())
            assertEquals((Integer)1, I);


        cs = oneAxisQuery(
                "{\n" +
                "\"onRows\":{\"level\":\"[Participant.Gender].[Gender]\"},\n" +
                "\"countDistinctLevel\":\"[Participant].[Participant]\",\n" +
                "\"showEmpty\":true\n" +
                "}"
        );
        assertEquals(3, cs.size());
        assertEquals((Integer)12, cs.get("[Participant.Gender].[#null]"));
        assertEquals((Integer)16, cs.get("[Participant.Gender].[Female]"));
        assertEquals((Integer)20, cs.get("[Participant.Gender].[Male]"));


        cs = oneAxisQuery(
                "{\n" +
                "\"onRows\":{\"level\":\"[Participant.Species].[Species]\"},\n" +
                "\"countDistinctLevel\":\"[Participant].[Participant]\",\n" +
                "\"showEmpty\":true\n" +
                "}"
        );
        assertEquals(4, cs.size());
        assertEquals((Integer)16, cs.get("[Participant.Species].[#null]"));
        assertEquals((Integer)16, cs.get("[Participant.Species].[Human]"));
        assertEquals((Integer)8, cs.get("[Participant.Species].[Monkey]"));
        assertEquals((Integer)8, cs.get("[Participant.Species].[Mouse]"));


        cs = oneAxisQuery(
                "{\n" +
                "\"onRows\":{\"level\":\"[Study.Type].[Type]\"},\n" +
                "\"countDistinctLevel\":\"[Participant].[Participant]\",\n" +
                "\"showEmpty\":true\n" +
                "}"
        );
        assertEquals(3, cs.size());
        assertEquals((Integer)16, cs.get("[Study.Type].[Interventional]"));
        assertEquals((Integer)16, cs.get("[Study.Type].[Longitudinal]"));
        assertEquals((Integer)16, cs.get("[Study.Type].[Observational]"));
    }


    @Test
    public void testSimpleFilter() throws Exception
    {
        Map<String,Integer> cs;

        cs = oneAxisQuery(
                "{\n" +
                "\"onRows\":{\"level\":\"[Participant].[Participant]\"},\n" +
                "\"countDistinctLevel\":\"[Participant].[Participant]\",\n" +
                "\"showEmpty\":false,\n" +
                "\"countFilter\":\n" +
                "[\n" +
                "    {\"level\":\"[Participant].[Participant]\", \"membersQuery\":{\"level\":\"[Participant.Gender].[Gender]\", \"members\":[\"[Participant.Gender].[Female]\"]}}\n" +
                "]\n" +
                "}"
        );
        assertEquals(16,cs.size());
        for (Integer I : cs.values())
            assertEquals((Integer)1, I);


        cs = oneAxisQuery(
                "{\n" +
                "\"onRows\":{\"level\":\"[Participant.Gender].[Gender]\"},\n" +
                "\"countDistinctLevel\":\"[Participant].[Participant]\",\n" +
                "\"showEmpty\":false,\n" +
                "\"countFilter\":\n" +
                "[\n" +
                "    {\"level\":\"[Participant].[Participant]\", \"membersQuery\":{\"level\":\"[Participant.Gender].[Gender]\", \"members\":[\"[Participant.Gender].[Female]\"]}}\n" +
                "]\n" +
                "}"
        );
        assertEquals(1, cs.size());
        assertEquals((Integer)16, cs.get("[Participant.Gender].[Female]"));


        cs = oneAxisQuery(
                "{\n" +
                "\"onRows\":{\"level\":\"[Participant.Species].[Species]\"},\n" +
                "\"countDistinctLevel\":\"[Participant].[Participant]\",\n" +
                "\"showEmpty\":true,\n" +
                "\"countFilter\":\n" +
                "[\n" +
                "    {\"level\":\"[Participant].[Participant]\", \"membersQuery\":{\"level\":\"[Participant.Gender].[Gender]\", \"members\":[\"[Participant.Gender].[Female]\"]}}\n" +
                "]\n" +
                "}"
        );
        assertEquals(4, cs.size());
        assertEquals((Integer)3, cs.get("[Participant.Species].[#null]"));
        assertEquals((Integer)7, cs.get("[Participant.Species].[Human]"));
        assertEquals((Integer)3, cs.get("[Participant.Species].[Monkey]"));
        assertEquals((Integer)3, cs.get("[Participant.Species].[Mouse]"));


        // female
        cs = oneAxisQuery(
                "{\n" +
                "\"onRows\":{\"level\":\"[Participant.Species].[Species]\"},\n" +
                "\"countDistinctLevel\":\"[Participant].[Participant]\",\n" +
                "\"showEmpty\":true,\n" +
                "\"countFilter\":\n" +
                "[\n" +
                "    {\"level\":\"[Participant].[Participant]\", \"membersQuery\":{\"level\":\"[Participant.Gender].[Gender]\", \"members\":[\"[Participant.Gender].[female]\"]}}\n" +
                "]\n" +
                "}"
        );
        assertEquals(4, cs.size());
        assertEquals((Integer)3, cs.get("[Participant.Species].[#null]"));
        assertEquals((Integer)7, cs.get("[Participant.Species].[Human]"));
        assertEquals((Integer)3, cs.get("[Participant.Species].[Monkey]"));
        assertEquals((Integer)3, cs.get("[Participant.Species].[Mouse]"));


        cs = oneAxisQuery(
                "{\n" +
                "\"onRows\":{\"level\":\"[Study.Type].[Type]\"},\n" +
                "\"countDistinctLevel\":\"[Participant].[Participant]\",\n" +
                "\"showEmpty\":true,\n" +
                "\"countFilter\":\n" +
                "[\n" +
                "    {\"level\":\"[Participant].[Participant]\", \"membersQuery\":{\"level\":\"[Participant.Gender].[Gender]\", \"members\":[\"[Participant.Gender].[Female]\"]}}\n" +
                "]\n" +
                "}"
        );
        assertEquals(3, cs.size());
        assertEquals((Integer)7, cs.get("[Study.Type].[Interventional]"));
        assertEquals((Integer)3, cs.get("[Study.Type].[Longitudinal]"));
        assertEquals((Integer)6, cs.get("[Study.Type].[Observational]"));

        // female
        cs = oneAxisQuery(
                "{\n" +
                "\"onRows\":{\"level\":\"[Study.Type].[Type]\"},\n" +
                "\"countDistinctLevel\":\"[Participant].[Participant]\",\n" +
                "\"showEmpty\":true,\n" +
                "\"countFilter\":\n" +
                "[\n" +
                "    {\"level\":\"[Participant].[Participant]\", \"membersQuery\":{\"level\":\"[Participant.Gender].[Gender]\", \"members\":[\"[Participant.Gender].[female]\"]}}\n" +
                "]\n" +
                "}"
        );
        assertEquals(3, cs.size());
        assertEquals((Integer)7, cs.get("[Study.Type].[Interventional]"));
        assertEquals((Integer)3, cs.get("[Study.Type].[Longitudinal]"));
        assertEquals((Integer)6, cs.get("[Study.Type].[Observational]"));


        // test optimzation for filter that returns all data
        validateOneAxisQueryCounts(
            "{\n" +
                "\"onRows\":{\"level\":\"[Assay].[Name]\"},\n" +
                "\"countFilter\":{\"level\":\"[Study.Type].[Type]\", \"members\":\"members\"},\n" +
                "\"countDistinctLevel\":\"[Participant].[Participant]\",\n" +
                "\"showEmpty\":true\n" +
            "}",
            40, 8, 8, null, 8
        );
    }


    @Test
    public void testSimpleNullFilter() throws Exception
    {
        Map<String,Integer> cs;

        cs = oneAxisQuery(
                "{\n" +
                "\"onRows\":{\"level\":\"[Participant].[Participant]\"},\n" +
                "\"countDistinctLevel\":\"[Participant].[Participant]\",\n" +
                "\"showEmpty\":false,\n" +
                "\"countFilter\":\n" +
                "[\n" +
                "    {\"level\":\"[Participant].[Participant]\", \"membersQuery\":{\"level\":\"[Participant.Gender].[Gender]\", \"members\":[\"[Participant.Gender].[#null]\"]}}\n" +
                "]\n" +
                "}"
        );
        assertEquals(12,cs.size());
        for (Integer I : cs.values())
            assertEquals((Integer)1, I);


        cs = oneAxisQuery(
                "{\n" +
                "\"onRows\":{\"level\":\"[Participant.Gender].[Gender]\"},\n" +
                "\"countDistinctLevel\":\"[Participant].[Participant]\",\n" +
                "\"showEmpty\":false,\n" +
                "\"countFilter\":\n" +
                "[\n" +
                "    {\"level\":\"[Participant].[Participant]\", \"membersQuery\":{\"level\":\"[Participant.Gender].[Gender]\", \"members\":[\"[Participant.Gender].[#null]\"]}}\n" +
                "]\n" +
                "}"
        );
        assertEquals(1, cs.size());
        assertEquals((Integer)12, cs.get("[Participant.Gender].[#null]"));


        cs = oneAxisQuery(
                "{\n" +
                "\"onRows\":{\"level\":\"[Participant.Species].[Species]\"},\n" +
                "\"countDistinctLevel\":\"[Participant].[Participant]\",\n" +
                "\"showEmpty\":true,\n" +
                "\"countFilter\":\n" +
                "[\n" +
                "    {\"level\":\"[Participant].[Participant]\", \"membersQuery\":{\"level\":\"[Participant.Gender].[Gender]\", \"members\":[\"[Participant.Gender].[#null]\"]}}\n" +
                "]\n" +
                "}"
        );
        assertEquals(4, cs.size());
        assertEquals((Integer)9, cs.get("[Participant.Species].[#null]"));
        assertEquals((Integer)1, cs.get("[Participant.Species].[Human]"));
        assertEquals((Integer)1, cs.get("[Participant.Species].[Monkey]"));
        assertEquals((Integer)1, cs.get("[Participant.Species].[Mouse]"));


        cs = oneAxisQuery(
                "{\n" +
                "\"onRows\":{\"level\":\"[Study.Type].[Type]\"},\n" +
                "\"countDistinctLevel\":\"[Participant].[Participant]\",\n" +
                "\"showEmpty\":true,\n" +
                "\"countFilter\":\n" +
                "[\n" +
                "    {\"level\":\"[Participant].[Participant]\", \"membersQuery\":{\"level\":\"[Participant.Gender].[Gender]\", \"members\":[\"[Participant.Gender].[#null]\"]}}\n" +
                "]\n" +
                "}"
        );
        assertEquals(3, cs.size());
        assertEquals((Integer)1, cs.get("[Study.Type].[Interventional]"));
        assertEquals((Integer)9, cs.get("[Study.Type].[Longitudinal]"));
        assertEquals((Integer)2, cs.get("[Study.Type].[Observational]"));
    }


    @Test
    public void testNotNullFilter() throws Exception
    {
        Map<String,Integer> cs;


        // #null counts
        cs = oneAxisQuery(
            "{\n" +
                "\"onRows\":{\"level\":\"[Study].[Name]\"},\n" +
                "\"countDistinctLevel\":\"[Participant].[Participant]\",\n" +
                "\"showEmpty\":true,\n" +
                "\"countFilter\":\n" +
                "[\n" +
                "    {\"level\":\"[Participant].[Participant]\", \"membersQuery\":{\"level\":\"[Participant.Gender].[Gender]\", \"members\":[\"[Participant.Gender].[#null]\"]}}\n" +
                "]\n" +
                "}"
        );
        // break this one out to check order of members in map
        assertEquals(6, cs.size());
        assertNull(cs.get("[Study].[S001]"));
        assertEquals((Integer)1, cs.get("[Study].[S002]"));
        assertEquals((Integer)1, cs.get("[Study].[S003]"));
        assertEquals((Integer)1, cs.get("[Study].[S004]"));
        assertEquals((Integer)1, cs.get("[Study].[S005]"));
        assertEquals((Integer)8, cs.get("[Study].[S006]"));


        // #notnull counts
        validateOneAxisQueryCounts(
            "{\n" +
                "\"onRows\":{\"level\":\"[Study].[Name]\"},\n" +
                "\"countDistinctLevel\":\"[Participant].[Participant]\",\n" +
                "\"showEmpty\":true,\n" +
                "\"countFilter\":\n" +
                "[\n" +
                "    {\"level\":\"[Participant].[Participant]\", \"membersQuery\":{\"level\":\"[Participant.Gender].[Gender]\", \"members\":[\"[Participant.Gender].[#notnull]\"]}}\n" +
                "]\n" +
                "}",
            8, 7, 7, 7, 7, null
        );


        // studies with ANY gender=null
        validateOneAxisQueryCounts(
            "{\n" +
                "\"onRows\":{\"level\":\"[Study].[Name]\"},\n" +
                "\"countDistinctLevel\":\"[Study].[Name]\",\n" +
                "\"showEmpty\":true,\n" +
                "\"countFilter\":\n" +
                "[\n" +
                "        {\"level\":\"[Study].[Name]\", \"membersQuery\":{\"level\":\"[Participant.Gender].[Gender]\", \"members\":[\"[Participant.Gender].[#null]\"]}}\n" +
                "]\n" +
                "}",
            null, 1, 1, 1, 1, 1
        );

        // studies with ANY gender=notnull
        validateOneAxisQueryCounts(
            "{\n" +
                "\"onRows\":{\"level\":\"[Study].[Name]\"},\n" +
                "\"countDistinctLevel\":\"[Study].[Name]\",\n" +
                "\"showEmpty\":true,\n" +
                "\"countFilter\":\n" +
                "[\n" +
                "        {\"level\":\"[Study].[Name]\", \"membersQuery\":{\"level\":\"[Participant.Gender].[Gender]\", \"members\":[\"[Participant.Gender].[#notnull]\"]}}\n" +
                "]\n" +
                "}",
            1, 1, 1, 1, 1, null
        );


        // studies with ONLY null gender
        validateOneAxisQueryCounts(
            "{\n" +
                "\"onRows\":{\"level\":\"[Study].[Name]\"},\n" +
                "\"countDistinctLevel\":\"[Study].[Name]\",\n" +
                "\"showEmpty\":true,\n" +
                "\"countFilter\":\n" +
                "[\n" +
                "    {\"operator\":\"EXCEPT\", \"arguments\":[\n" +
                "        {\"level\":\"[Study].[Name]\", \"membersQuery\":{\"level\":\"[Participant.Gender].[Gender]\", \"members\":[\"[Participant.Gender].[#null]\"]}},\n" +
                "        {\"level\":\"[Study].[Name]\", \"membersQuery\":{\"level\":\"[Participant.Gender].[Gender]\", \"members\":[\"[Participant.Gender].[#notnull]\"]}}\n" +
                "    ]}\n" +
                "]\n" +
                "}",
            null, null, null, null, null, 1
        );

        // studies with ONLY #Notnull gender
        validateOneAxisQueryCounts(
            "{\n" +
                "\"onRows\":{\"level\":\"[Study].[Name]\"},\n" +
                "\"countDistinctLevel\":\"[Study].[Name]\",\n" +
                "\"showEmpty\":true,\n" +
                "\"countFilter\":\n" +
                "[\n" +
                "    {\"operator\":\"EXCEPT\", \"arguments\":[\n" +
                "        {\"level\":\"[Study].[Name]\", \"membersQuery\":{\"level\":\"[Participant.Gender].[Gender]\", \"members\":[\"[Participant.Gender].[#notnull]\"]}},\n" +
                "        {\"level\":\"[Study].[Name]\", \"membersQuery\":{\"level\":\"[Participant.Gender].[Gender]\", \"members\":[\"[Participant.Gender].[#null]\"]}}\n" +
                "    ]}\n" +
                "]\n" +
                "}",
            1, null, null, null, null, null
        );
    }


    @Test
    public void testComplexFilter() throws Exception
    {
        Map<String,Integer> cs;

        // female
        cs = oneAxisQuery(
            "{\n" +
                "\"onRows\":{\"level\":\"[Participant].[Participant]\"},\n" +
                "\"countDistinctLevel\":\"[Participant].[Participant]\",\n" +
                "\"showEmpty\":false,\n" +
                "\"countFilter\":\n" +
                "[\n" +
                "    {\"level\":\"[Participant].[Participant]\", \"membersQuery\":{\"level\":\"[Participant.Gender].[Gender]\", \"members\":[\"[Participant.Gender].[Female]\"]}}\n" +
                "]\n" +
                "}"
        );
        assertEquals(16, cs.size());

        // male
        cs = oneAxisQuery(
            "{\n" +
                "\"onRows\":{\"level\":\"[Participant].[Participant]\"},\n" +
                "\"countDistinctLevel\":\"[Participant].[Participant]\",\n" +
                "\"showEmpty\":false,\n" +
                "\"countFilter\":\n" +
                "[\n" +
                "    {\"level\":\"[Participant].[Participant]\", \"membersQuery\":{\"level\":\"[Participant.Gender].[Gender]\", \"members\":[\"[Participant.Gender].[male]\"]}}\n" +
                "]\n" +
                "}"
        );
        assertEquals(20, cs.size());


        // monkey
        cs = oneAxisQuery(
            "{\n" +
                "\"onRows\":{\"level\":\"[Participant].[Participant]\"},\n" +
                "\"countDistinctLevel\":\"[Participant].[Participant]\",\n" +
                "\"showEmpty\":false,\n" +
                "\"countFilter\":\n" +
                "[\n" +
                "    {\"level\":\"[Participant].[Participant]\", \"membersQuery\":{\"level\":\"[Participant.Species].[Species]\", \"members\":[\"[Participant.Species].[monkey]\"]}}\n" +
                "]\n" +
            "}"
        );
        assertEquals(8, cs.size());


        // male and monkey (implicit AND)
        cs = oneAxisQuery(
            "{\n" +
                "\"onRows\":{\"level\":\"[Participant].[Participant]\"},\n" +
                "\"countDistinctLevel\":\"[Participant].[Participant]\",\n" +
                "\"showEmpty\":false,\n" +
                "\"countFilter\":\n" +
                "[\n" +
                "        {\"level\":\"[Participant].[Participant]\", \"membersQuery\":{\"level\":\"[Participant.Species].[Species]\", \"members\":[\"[Participant.Species].[monkey]\"]}},\n" +
                "        {\"level\":\"[Participant].[Participant]\", \"membersQuery\":{\"level\":\"[Participant.Gender].[Gender]\", \"members\":[\"[Participant.Gender].[Male]\"]}}\n" +
                "]\n" +
            "}"
        );
        assertEquals(4, cs.size());


        // male and monkey (explicit INTERSECT)
        cs = oneAxisQuery(
            "{\n" +
                "\"countDistinctLevel\":\"[Participant].[Participant]\",\n" +
                "\"showEmpty\":false,\n" +
                "\"onRows\":\n" +
                "{ \"operator\":\"INTERSECT\", \"arguments\":\n" +
                "[\n" +
                "  {\"level\":\"[Participant].[Participant]\", \"membersQuery\":{\"level\":\"[Participant.Species].[Species]\", \"members\":[\"[Participant.Species].[monkey]\"]}},\n" +
                "  {\"level\":\"[Participant].[Participant]\", \"membersQuery\":{\"level\":\"[Participant.Gender].[Gender]\", \"members\":[\"[Participant.Gender].[Male]\"]}}\n" +
                "]}\n" +
            "}"
        );
        assertEquals(4, cs.size());

        // female and monkey (explicit INTERSECT)
        cs = oneAxisQuery(
            "{\n" +
                "\"countDistinctLevel\":\"[Participant].[Participant]\",\n" +
                "\"showEmpty\":false,\n" +
                "\"onRows\":\n" +
                "{ \"operator\":\"INTERSECT\", \"arguments\":\n" +
                "[\n" +
                "  {\"level\":\"[Participant].[Participant]\", \"membersQuery\":{\"level\":\"[Participant.Species].[Species]\", \"members\":[\"[Participant.Species].[monkey]\"]}},\n" +
                "  {\"level\":\"[Participant].[Participant]\", \"membersQuery\":{\"level\":\"[Participant.Gender].[Gender]\", \"members\":[\"[Participant.Gender].[female]\"]}}\n" +
                "]}\n" +
                "}"
        );
        assertEquals(3, cs.size());


        // male OR monkey (UNION)
        cs = oneAxisQuery(
            "{\n" +
                "\"countDistinctLevel\":\"[Participant].[Participant]\",\n" +
                "\"showEmpty\":false,\n" +
                "\"onRows\":\n" +
                "{ \"operator\":\"UNION\", \"arguments\":\n" +
                "[\n" +
                "  {\"level\":\"[Participant].[Participant]\", \"membersQuery\":{\"level\":\"[Participant.Species].[Species]\", \"members\":[\"[Participant.Species].[monkey]\"]}},\n" +
                "  {\"level\":\"[Participant].[Participant]\", \"membersQuery\":{\"level\":\"[Participant.Gender].[Gender]\", \"members\":[\"[Participant.Gender].[Male]\"]}}\n" +
                "]}\n" +
                "}"
        );
        assertEquals(24, cs.size());


        // female AND NOT monkey (EXCEPT)
        cs = oneAxisQuery(
            "{\n" +
                "\"countDistinctLevel\":\"[Participant].[Participant]\",\n" +
                "\"showEmpty\":false,\n" +
                "\"onRows\":\n" +
                "{ \"operator\":\"EXCEPT\", \"arguments\":\n" +
                "[\n" +
                "  {\"level\":\"[Participant].[Participant]\", \"membersQuery\":{\"level\":\"[Participant.Gender].[Gender]\", \"members\":[\"[Participant.Gender].[female]\"]}},\n" +
                "  {\"level\":\"[Participant].[Participant]\", \"membersQuery\":{\"level\":\"[Participant.Species].[Species]\", \"members\":[\"[Participant.Species].[monkey]\"]}}\n" +
                "]}\n" +
                "}"
        );
        assertEquals(13, cs.size());
    }


    @Test
    public void testShorthandSyntax() throws Exception
    {
        Map<String,Integer> cs;

        // members:STRING
        cs = oneAxisQuery(
            "{\n" +
                "\"countDistinctLevel\":\"[Participant].[Participant]\",\n" +
                "\"showEmpty\":true,\n" +
                "\"onRows\":{\"level\":\"[Participant].[Participant]\", \"members\":\"[Participant].[P001001]\"}\n" +
            "}"
        );
        assertEquals(1, cs.size());
        assertEquals((Integer)1, cs.get("[Participant].[P001001]"));

        // members:[STRING]
        cs = oneAxisQuery(
            "{\n" +
                "\"countDistinctLevel\":\"[Participant].[Participant]\",\n" +
                "\"showEmpty\":true,\n" +
                "\"onRows\":{\"level\":\"[Participant].[Participant]\", \"members\":[\"[Participant].[P001001]\"]}\n" +
                "}"
        );
        assertEquals(1, cs.size());
        assertEquals((Integer)1, cs.get("[Participant].[P001001]"));

        // members:[STRING,STRING]
        cs = oneAxisQuery(
            "{\n" +
                "\"countDistinctLevel\":\"[Participant].[Participant]\",\n" +
                "\"showEmpty\":true,\n" +
                "\"onRows\":{\"level\":\"[Participant].[Participant]\", \"members\":[\"[Participant].[P001001]\",\"[Participant].[P001002]\"]}\n" +
                "}"
        );
        assertEquals(2, cs.size());
        assertEquals((Integer)1, cs.get("[Participant].[P001001]"));
        assertEquals((Integer)1, cs.get("[Participant].[P001002]"));

        // filter:[] {[Participant].[Participant] membersQuery} not explicitly specificed in the filter
        cs = oneAxisQuery(
            "{\n" +
                "\"onRows\":{\"level\":\"[Participant].[Participant]\"},\n" +
                "\"countDistinctLevel\":\"[Participant].[Participant]\",\n" +
                "\"showEmpty\":false,\n" +
                "\"countFilter\":\n" +
                "[\n" +
                "    {\"level\":\"[Participant.Gender].[Gender]\", \"members\":[\"[Participant.Gender].[Female]\"]}\n" +
                "]\n" +
                "}"
        );
        assertEquals(16,cs.size());
        for (Integer I : cs.values())
            assertEquals((Integer)1, I);

        // filter:{} single filter not wrapped in array
        cs = oneAxisQuery(
            "{\n" +
                "\"onRows\":{\"level\":\"[Participant].[Participant]\"},\n" +
                "\"countDistinctLevel\":\"[Participant].[Participant]\",\n" +
                "\"showEmpty\":false,\n" +
                "\"countFilter\":\n" +
                "    {\"level\":\"[Participant.Gender].[Gender]\", \"members\":[\"[Participant.Gender].[Female]\"]}\n" +
                "}"
        );
        assertEquals(16,cs.size());
        for (Integer I : cs.values())
            assertEquals((Integer)1, I);
    }


    @Test
    public void testDataFilter() throws Exception
    {
        Map<String,Integer> cs;


        // no filter
        validateOneAxisQueryCounts(
            "{\n" +
                "\"onRows\":{\"level\":\"[Assay].[Name]\"},\n" +
                "\"countDistinctLevel\":\"[Participant].[Participant]\",\n" +
                "\"showEmpty\":true\n" +
            "}",
            40, 8, 8, null, 8
        );


        // participant filter, two assays get pulled in because of shared participants
        validateOneAxisQueryCounts(
            "{\n" +
                "\"onRows\":{\"level\":\"[Assay].[Name]\"},\n" +
                "\"countDistinctLevel\":\"[Participant].[Participant]\",\n" +
                "\"showEmpty\":true,\n" +
                "\"countFilter\":\n" +
                "[\n" +
                "    {\"level\":\"[Participant].[Participant]\", \"membersQuery\":{\"level\":\"[Positivity].[Positivity]\", \"members\":\"[Positivity].[1]\"}}\n" +
                "],\n" +
                "\"joinLevel\":\"[ParticipantVisit].[ParticipantVisit]\"\n" +
            "}",
            3, 3, null, null, null
        );


        // participantvisit filter, only assay associated with positivity timepoint is pulled in
        validateOneAxisQueryCounts(
            "{\n" +
                "\"onRows\":{\"level\":\"[Assay].[Name]\"},\n" +
                "\"countDistinctLevel\":\"[Participant].[Participant]\",\n" +
                "\"showEmpty\":true,\n" +
                "\"joinLevel\":\"[ParticipantVisit].[ParticipantVisit]\",\n" +
                "\"whereFilter\":\n" +
                "[\n" +
                "    {\"level\":\"[ParticipantVisit].[ParticipantVisit]\", \"membersQuery\":{\"level\":\"[Positivity].[Positivity]\", \"members\":\"[Positivity].[1]\"}}\n" +
                "]\n" +
            "}",
            3, null, null, null, null
        );
    }


    @Test
    public void testRegressionsUnion() throws Exception
    {
        Map<String,Integer> cs;

        cs = oneAxisQuery(
            "{\n" +
                "\"onRows\":{\"operator\":\"UNION\", \"arguments\": \n" +
                "  [\n" +
                "  {\"level\":\"[Participant].[Participant]\", \"members\":[\"[Participant].[P001001]\"]}\n" +
                "  ]}\n" +
                "}"
        );
        assertEquals(1, cs.size());


        cs = oneAxisQuery(
            "{\n" +
                "\"onRows\":{\"operator\":\"UNION\", \"arguments\": \n" +
                "  [\n" +
                "  {\"level\":\"[Participant].[Participant]\", \"members\":[]}\n" +
                "  ]}\n" +
                "}"
        );
        assertEquals(0, cs.size());

        cs = oneAxisQuery(
            "{\n" +
                "\"onRows\":{\"operator\":\"UNION\", \"arguments\": \n" +
                "  [\n" +
                "  {\"level\":\"[Participant].[Participant]\", \"members\":[\"[Participant].[P001001]\"]},\n" +
                "  {\"level\":\"[Participant].[Participant]\", \"members\":[\"[Participant].[P001002]\"]}\n" +
                "  ]}\n" +
                "}"
        );
        assertEquals(2, cs.size());

        cs = oneAxisQuery(
            "{\n" +
                "\"onRows\":{\"operator\":\"UNION\", \"arguments\": \n" +
                "  [\n" +
                "  {\"level\":\"[Participant].[Participant]\", \"members\":[\"[Participant].[P001001]\"]},\n" +
                "  {\"level\":\"[Participant].[Participant]\", \"members\":[\"[Participant].[P001001]\"]}\n" +
                "  ]}\n" +
                "}"
        );
        assertEquals(1, cs.size());


        cs = oneAxisQuery(
            "{\n" +
                "\"onRows\":{\"operator\":\"UNION\", \"arguments\": \n" +
                "  [\n" +
                "  {\"level\":\"[Participant].[Participant]\", \"members\":[\"[Participant].[P001001]\"]},\n" +
                "  {\"level\":\"[Participant].[Participant]\", \"members\":[]}\n" +
                "  ]}\n" +
                "}"
        );
        assertEquals(1, cs.size());


        cs = oneAxisQuery(
            "{\n" +
                "\"onRows\":{\"operator\":\"UNION\", \"arguments\": \n" +
                "  [\n" +
                "  {\"level\":\"[Participant].[Participant]\", \"members\":[]},\n" +
                "  {\"level\":\"[Participant].[Participant]\", \"members\":[]}\n" +
                "  ]}\n" +
                "}"
        );
        assertEquals(0, cs.size());
    }
}
