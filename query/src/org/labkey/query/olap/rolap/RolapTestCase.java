package org.labkey.query.olap.rolap;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.labkey.api.action.NullSafeBindException;
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
import org.labkey.query.olap.OlapSchemaDescriptor;
import org.labkey.query.olap.ServerManager;
import org.olap4j.metadata.Cube;
import org.olap4j.metadata.Hierarchy;
import org.olap4j.metadata.Level;
import org.springframework.validation.BindException;

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
    Container getContainer()
    {
        return JunitUtil.getTestContainer();
    }

    @BeforeClass @AfterClass
    public static void reset()
    {
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


    @Test
    public void testCachedCube() throws Exception
    {
        UserSchema schema = new RolapTestSchema(getUser(), getContainer());
        Container c = getContainer();
        HashSet<Module> am = new HashSet<>(c.getActiveModules());
        am.add(ModuleLoader.getInstance().getModule("query"));
        c.setActiveModules(am);

        OlapSchemaDescriptor d = ServerManager.getDescriptor(getContainer(), "query:/junit");
        assertNotNull(d);

        BindException errors = new NullSafeBindException(new Object(), "Form");
        Cube cube = ServerManager.getCachedCubeRolap(d, getContainer(), getUser(), "JunitCube", schema);

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


//        Hierarchy hVisit = cube.getHierarchies().get("Visit");
//        assertNotNull(hVisit);
//        Level lVisit = hVisit.getLevels().get(1);
//        assertEquals(4+2,lVisit.getMembers().size());

        Hierarchy hAssay = cube.getHierarchies().get("Assay");
        assertNotNull(hAssay);
        Level lAssay = hAssay.getLevels().get(1);
        // 4 + #notnull
        assertEquals(4+1,lAssay.getMembers().size());
    }
}
