package org.labkey.query.olap.rolap;

import org.json.JSONObject;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.labkey.api.action.NullSafeBindException;
import org.labkey.api.collections.CaseInsensitiveTreeMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.TestContext;
import org.labkey.query.controllers.OlapController;
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
import org.springframework.validation.BindException;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;


/**
 * Created by matthew on 9/19/14.
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
        SQLFragment sql;

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
        // 4 + #notnull
        assertEquals(4+1,lAssay.getMembers().size());
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
        long start = 0, end = 0;
        start = System.currentTimeMillis();
        OlapConnection conn = null;
        if (OlapController.strategy == OlapController.ImplStrategy.mondrian)
            conn = sd.getConnection(getContainer(), getUser());
        BitSetQueryImpl bitsetquery = new BitSetQueryImpl(getContainer(), getUser(), sd, cube, conn, qquery, errors, true);
        assertFalse(errors.hasErrors());
        try (CellSet cs = bitsetquery.executeQuery())
        {
            for (Position p : cs.getAxes().get(1).getPositions())
            {
                Cell cell = cs.getCell(p);
                Object v = cell.getValue();
                Integer i = null==v ? null : ((Number)v).intValue();
                ret.put(p.getMembers().get(0).getUniqueName(), i);
            }
        }
        end = System.currentTimeMillis();
        return ret;
    }


    @Test
    public void testNoFilters() throws Exception
    {
        if (OlapController.strategy == OlapController.ImplStrategy.mondrian)
            return;

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
        if (OlapController.strategy == OlapController.ImplStrategy.mondrian)
            return;

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
    }


    @Test
    public void testSimpleNullFilter() throws Exception
    {
        if (OlapController.strategy == OlapController.ImplStrategy.mondrian)
            return;

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
}
