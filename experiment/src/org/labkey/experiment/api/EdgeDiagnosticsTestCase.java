package org.labkey.experiment.api;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.TestContext;
import org.labkey.experiment.controllers.exp.ExperimentController;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class EdgeDiagnosticsTestCase extends Assert
{
    private User _user;
    private ExpMaterial _sampleA;
    private ExpMaterial _sampleB;

    @Before
    public void setUp() throws Exception
    {
        JunitUtil.deleteTestContainer();
        _user = TestContext.get().getUser();
        var container = JunitUtil.getTestContainer();

        List<GWTPropertyDescriptor> props = new ArrayList<>();
        props.add(new GWTPropertyDescriptor("name", "string"));
        var sampleType = SampleTypeService.get().createSampleType(container, _user, "EdgeDiagSamples", null, props, Collections.emptyList(), -1, -1, -1, -1, null);

        UserSchema schema = QueryService.get().getUserSchema(_user, container, SchemaKey.fromParts("Samples"));
        TableInfo table = schema.getTable("EdgeDiagSamples");
        QueryUpdateService svc = table.getUpdateService();
        BatchValidationException errors = new BatchValidationException();
        svc.insertRows(_user, container, List.of(
                CaseInsensitiveHashMap.of("name", "edgeA"),
                CaseInsensitiveHashMap.of("name", "edgeB")
        ), errors, null, null);
        if (errors.hasErrors())
            throw errors;

        _sampleA = sampleType.getSample(container, "edgeA");
        _sampleB = sampleType.getSample(container, "edgeB");
    }

    @After
    public void tearDown()
    {
        JunitUtil.deleteTestContainer();
    }

    @Test
    public void testCycleCheckActionDetectsAndResolves()
    {
        Long idA = _sampleA.getObjectId();
        Long idB = _sampleB.getObjectId();
        DbSchema expSchema = ExperimentService.get().getSchema();
        try
        {
            insertCycleEdges(expSchema, idA, idB);

            List<Long> cycleIds = ExperimentController.CycleCheckAction.detectCycleObjectIds(expSchema);
            assertNotNull("Cycle should be detected", cycleIds);
            assertTrue("Cycle should include idA", cycleIds.contains(idA));
            assertTrue("Cycle should include idB", cycleIds.contains(idB));

            Map<Long, ExpObject> resolved = ExperimentController.CycleCheckAction.resolveCycleObjects(new ContainerFilter.AllFolders(_user), cycleIds);
            assertEquals(_sampleA.getName(), resolved.get(idA).getName());
            assertEquals(_sampleB.getName(), resolved.get(idB).getName());
        }
        finally
        {
            deleteCycleEdges(expSchema, idA, idB);
        }
    }

    @Test
    public void testCheckEdgesActionDetectsCycles()
    {
        Long idA = _sampleA.getObjectId();
        Long idB = _sampleB.getObjectId();
        DbSchema expSchema = ExperimentService.get().getSchema();
        try
        {
            insertCycleEdges(expSchema, idA, idB);

            Collection<Pair<Long, Long>> cycleEdges = ExperimentController.CheckEdgesAction.detectCycleEdges(expSchema);
            assertFalse("Cycle edges should be detected", cycleEdges.isEmpty());
            assertTrue("idA should appear in a cycle edge",
                    cycleEdges.stream().anyMatch(e -> e.first.equals(idA) || e.second.equals(idA)));
            assertTrue("idB should appear in a cycle edge",
                    cycleEdges.stream().anyMatch(e -> e.first.equals(idB) || e.second.equals(idB)));
        }
        finally
        {
            deleteCycleEdges(expSchema, idA, idB);
        }
    }

    private static void insertCycleEdges(DbSchema schema, Long idA, Long idB)
    {
        var executor = new SqlExecutor(schema);
        executor.execute(new SQLFragment("INSERT INTO exp.Edge (FromObjectId, ToObjectId) VALUES (?,?)", idA, idB));
        executor.execute(new SQLFragment("INSERT INTO exp.Edge (FromObjectId, ToObjectId) VALUES (?,?)", idB, idA));
    }

    private static void deleteCycleEdges(DbSchema schema, Long idA, Long idB)
    {
        new SqlExecutor(schema).execute(new SQLFragment(
                "DELETE FROM exp.Edge WHERE (FromObjectId=? AND ToObjectId=?) OR (FromObjectId=? AND ToObjectId=?)",
                idA, idB, idB, idA));
    }
}
