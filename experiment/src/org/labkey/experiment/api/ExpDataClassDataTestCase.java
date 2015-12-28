package org.labkey.experiment.api;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.etl.DataIteratorContext;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.gwt.client.model.GWTIndex;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.reader.MapLoader;
import org.labkey.api.security.User;
import org.labkey.api.test.TestWhen;
import org.labkey.api.util.TestContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * User: kevink
 * Date: 12/27/15
 */
@TestWhen(TestWhen.When.BVT)
public class ExpDataClassDataTestCase
{
    Container c;

    @Before
    public void setUp() throws Exception
    {
        // NOTE: We need to use a project to create the DataClass so we can insert rows into sub-folders
        c = ContainerManager.getForPath("_testDataClass");
        if (c != null)
            ContainerManager.deleteAll(c, TestContext.get().getUser());
        c = ContainerManager.createContainer(ContainerManager.getRoot(), "_testDataClass");
    }

    @After
    public void tearDown() throws Exception
    {
        ContainerManager.deleteAll(c, TestContext.get().getUser());
    }

    private static List<Map<String, Object>> insertRows(Container c, List<Map<String, Object>> rows)
            throws Exception
    {
        final User user = TestContext.get().getUser();
        final SchemaKey expDataSchemaKey = SchemaKey.fromParts(ExpSchema.SCHEMA_NAME, ExpSchema.NestedSchemas.data.toString());

        BatchValidationException errors = new BatchValidationException();
        //UserSchema schema = QueryService.get().getUserSchema(user, JunitUtil.getTestContainer(), expDataSchemaKey);
        UserSchema schema = QueryService.get().getUserSchema(user, c, expDataSchemaKey);
        TableInfo table = schema.getTable("testing");
        Assert.assertNotNull(table);

        QueryUpdateService qus = table.getUpdateService();
        Assert.assertNotNull(qus);

        List<Map<String, Object>> ret = qus.insertRows(user, c, rows, errors, null, null);
        if (errors.hasErrors())
            throw errors;
        return ret;
    }

    @Test
    public void testDataClass() throws Exception
    {
        final User user = TestContext.get().getUser();
        final Container sub = ContainerManager.createContainer(c, "sub");

        List<GWTPropertyDescriptor> props = new ArrayList<>();
        props.add(new GWTPropertyDescriptor("aa", "int"));
        props.add(new GWTPropertyDescriptor("bb", "string"));

        List<GWTIndex> indices = new ArrayList<>();
        indices.add(new GWTIndex(Arrays.asList("aa"), true));

        String nameExpr = "JUNIT-${genId}-${aa}";

        final ExpDataClassImpl dataClass = ExperimentServiceImpl.get().createDataClass(c, user, "testing", null, props, indices, null, nameExpr);
        Assert.assertNotNull(dataClass);

        final Domain domain = dataClass.getDomain();
        Assert.assertNotNull(domain);

        final String typeURI = domain.getTypeURI();

        final SchemaKey expDataSchemaKey = SchemaKey.fromParts(ExpSchema.SCHEMA_NAME, ExpSchema.NestedSchemas.data.toString());
        UserSchema schema = QueryService.get().getUserSchema(user, c, expDataSchemaKey);
        TableInfo table = schema.getTable("testing");
        Assert.assertNotNull("data class not in query schema", table);

        // test name expression generation
        String expectedName = "JUNIT-1-20";
        {
            List<Map<String, Object>> rows = new ArrayList<>();
            Map<String, Object> row = new CaseInsensitiveHashMap<>();
            row.put("aa", 20);
            row.put("bb", "hi");
            rows.add(row);

            List<Map<String, Object>> ret = null;
            try (DbScope.Transaction tx = table.getSchema().getScope().beginTransaction())
            {
                ret = insertRows(c, rows);
                tx.commit();
            }

            Assert.assertEquals(1, ret.size());
            Assert.assertEquals(1, ret.get(0).get("genId"));
            Assert.assertEquals(expectedName, ret.get(0).get("name"));

            Integer rowId = (Integer) ret.get(0).get("RowId");
            ExpData data = ExperimentService.get().getExpData(rowId);
            ExpData data1 = ExperimentService.get().getExpData(dataClass, "JUNIT-1-20");
            Assert.assertEquals(data, data1);

            TableSelector ts = new TableSelector(table);
            Assert.assertEquals(1L, ts.getRowCount());
        }

        // test insert duplicate values for 'aa' column fails
        {
            List<Map<String, Object>> rows = new ArrayList<>();
            Map<String, Object> row = new CaseInsensitiveHashMap<>();
            row.put("aa", 20);
            row.put("bb", "bye");
            rows.add(row);

            try (DbScope.Transaction tx = table.getSchema().getScope().beginTransaction())
            {
                insertRows(c, rows);
                tx.commit();
                Assert.fail("Expected exception");
            }
            catch (BatchValidationException e)
            {
                // ok, expected
            }

            TableSelector ts = new TableSelector(table);
            Assert.assertEquals(1L, ts.getRowCount());
            Assert.assertEquals(1, dataClass.getDatas().size());
        }

        // test insert into sub-folder
        String expectedSubName = "JUNIT-3-30";
        {
            List<Map<String, Object>> rows = new ArrayList<>();
            Map<String, Object> row = new CaseInsensitiveHashMap<>();
            row.put("aa", 30);
            row.put("container", sub);
            row.put("bb", "bye");
            rows.add(row);

            List<Map<String, Object>> ret;
            try (DbScope.Transaction tx = table.getSchema().getScope().beginTransaction())
            {
                ret = insertRows(sub, rows);
                tx.commit();
            }

            Assert.assertEquals(1, ret.size());
            Assert.assertEquals(sub.getId(), ret.get(0).get("container"));

            ExpData data = ExperimentService.get().getExpData(dataClass, expectedSubName);
            Assert.assertNotNull(data);
            Assert.assertEquals(sub, data.getContainer());

            // TODO: Why is my filter not working?
//            SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("container"), Arrays.asList(c, sub), CompareType.IN);
//            TableSelector ts = new TableSelector(table, filter, null);
//            Assert.assertEquals(2L, ts.getRowCount());

            Assert.assertEquals(2, dataClass.getDatas().size());
        }

        // test truncate rows deletes all rows from all containers
        {
            int count;
            try (DbScope.Transaction tx = table.getSchema().getScope().beginTransaction())
            {
                // TODO: truncate rows API doesn't support truncating from all containers
                //count = table.getUpdateService().truncateRows(user, c, null, null);
                count = ExperimentServiceImpl.get().truncateDataClass(dataClass, null);
                tx.commit();
            }
            Assert.assertEquals(2, count);

            Assert.assertEquals(0, dataClass.getDatas().size());
            Assert.assertNull(ExperimentService.get().getExpData(dataClass, expectedName));
            Assert.assertNull(ExperimentService.get().getExpData(dataClass, expectedSubName));
        }

        // test bulk import via loadRows
        {
            List<Map<String, Object>> rows = new ArrayList<>();
            Map<String, Object> row = new CaseInsensitiveHashMap<>();
            row.put("aa", 40);
            row.put("bb", "qq");
            rows.add(row);

            row = new CaseInsensitiveHashMap<>();
            row.put("aa", 50);
            row.put("bb", "zz");
            rows.add(row);

            MapLoader mapLoader = new MapLoader(rows);
            int count = table.getUpdateService().loadRows(user, c, mapLoader, new DataIteratorContext(), null);
            Assert.assertEquals(2, count);
            Assert.assertEquals(2, dataClass.getDatas().size());
        }

        // test delete ExpData deletes DataClass row
        {
            List<? extends ExpData> datas = dataClass.getDatas();
            Assert.assertEquals(2, datas.size());

            datas.get(0).delete(user);
            Assert.assertEquals(1, dataClass.getDatas().size());
        }

        // test delete ExpDataClass
        {
            Assert.assertNotNull(ExperimentService.get().getDataClass(c, "testing"));
            Assert.assertNotNull(PropertyService.get().getDomain(c, typeURI));

            String storageTableName = dataClass.getTinfo().getName();
            DbSchema dbSchema = DbSchema.get("labkey.expdataclass", DbSchemaType.Provisioned);
            TableInfo dbTable = dbSchema.getTable(storageTableName);
            Assert.assertNotNull(dbTable);

            dataClass.delete(user);

            Assert.assertNull(ExperimentService.get().getDataClass(c, "testing"));
            Assert.assertNull(PropertyService.get().getDomain(c, typeURI));

            UserSchema schema1 = QueryService.get().getUserSchema(user, c, expDataSchemaKey);
            Assert.assertNull(schema1.getTable("testing"));

            dbTable = dbSchema.getTable(storageTableName);
            Assert.assertNull(dbTable);
        }
    }

    // Issue 25224: NPE trying to delete a folder with a DataClass with at least one result row in it
    @Test
    public void testContainerDelete() throws Exception
    {
        final User user = TestContext.get().getUser();
        final Container sub = ContainerManager.createContainer(c, "sub");

        List<GWTPropertyDescriptor> props = new ArrayList<>();
        props.add(new GWTPropertyDescriptor("aa", "int"));

        final ExpDataClassImpl dataClass = ExperimentServiceImpl.get().createDataClass(c, user, "testing", null, props, Collections.emptyList(), null, null);
        final int dataClassId = dataClass.getRowId();

        final SchemaKey expDataSchemaKey = SchemaKey.fromParts(ExpSchema.SCHEMA_NAME, ExpSchema.NestedSchemas.data.toString());
        UserSchema schema = QueryService.get().getUserSchema(user, c, expDataSchemaKey);
        TableInfo table = schema.getTable("testing");

        // setup: insert into junit container
        int dataRowId1;
        {
            List<Map<String, Object>> rows = new ArrayList<>();
            Map<String, Object> row = new CaseInsensitiveHashMap<>();
            row.put("name", "first");
            row.put("aa", 20);
            rows.add(row);

            List<Map<String, Object>> ret;
            try (DbScope.Transaction tx = table.getSchema().getScope().beginTransaction())
            {
                ret = insertRows(c, rows);
                tx.commit();
            }

            Assert.assertEquals(1, ret.size());
            dataRowId1 = ((Integer)ret.get(0).get("RowId")).intValue();
        }

        // setup: insert into sub container
        int dataRowId2;
        {
            List<Map<String, Object>> rows = new ArrayList<>();
            Map<String, Object> row = new CaseInsensitiveHashMap<>();
            row.put("name", "second");
            row.put("aa", 30);
            rows.add(row);

            List<Map<String, Object>> ret;
            try (DbScope.Transaction tx = table.getSchema().getScope().beginTransaction())
            {
                ret = insertRows(sub, rows);
                tx.commit();
            }

            Assert.assertEquals(1, ret.size());
            dataRowId2 = ((Integer)ret.get(0).get("RowId")).intValue();
        }

        // test: delete container, ensure everything is removed
        {
            // verify exists
            Assert.assertNotNull(ExperimentService.get().getDataClass(dataClassId));
            Assert.assertNotNull(ExperimentService.get().getExpData(dataRowId1));
            Assert.assertNotNull(ExperimentService.get().getExpData(dataRowId2));

            String storageTableName = dataClass.getTinfo().getName();
            DbSchema dbSchema = DbSchema.get("labkey.expdataclass", DbSchemaType.Provisioned);
            TableInfo dbTable = dbSchema.getTable(storageTableName);
            Assert.assertNotNull(dbTable);

            // delete
            ContainerManager.deleteAll(c, user);

            // verify deleted
            Assert.assertNull(ExperimentService.get().getDataClass(dataClassId));
            Assert.assertNull(ExperimentService.get().getExpData(dataRowId1));
            Assert.assertNull(ExperimentService.get().getExpData(dataRowId2));

            dbTable = dbSchema.getTable(storageTableName);
            Assert.assertNull(dbTable);
        }

    }

}
