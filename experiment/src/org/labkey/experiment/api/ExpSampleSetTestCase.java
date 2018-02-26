/*
 * Copyright (c) 2016-2017 LabKey Corporation
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
package org.labkey.experiment.api;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.test.TestWhen;
import org.labkey.api.util.TestContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * User: kevink
 * Date: 11/24/16
 */
@TestWhen(TestWhen.When.BVT)
public class ExpSampleSetTestCase
{
    Container c;

    @Before
    public void setUp() throws Exception
    {
        // NOTE: We need to use a project to create the DataClass so we can insert rows into sub-folders
        c = ContainerManager.getForPath("_testSampleSet");
        if (c != null)
            ContainerManager.deleteAll(c, TestContext.get().getUser());
        c = ContainerManager.createContainer(ContainerManager.getRoot(), "_testSampleSet");
    }

    @After
    public void tearDown() throws Exception
    {
        ContainerManager.deleteAll(c, TestContext.get().getUser());
    }

    // idCols all null, nameExpression null, no 'name' property -- fail
    @Test
    public void idColsUnset_nameExpressionNull_noNameProperty() throws Exception
    {
        final User user = TestContext.get().getUser();

        try
        {
            List<GWTPropertyDescriptor> props = new ArrayList<>();
            props.add(new GWTPropertyDescriptor("notName", "string"));
            props.add(new GWTPropertyDescriptor("age", "int"));

            final ExpSampleSet ss = ExperimentService.get().createSampleSet(c, user,
                    "Samples", null, props, Collections.emptyList(),
                    -1, -1, -1, -1, null, null);
            fail("Expected exception");
        }
        catch (ExperimentException ee)
        {
            assertEquals("Either a 'Name' property or an index for idCol1 is required", ee.getMessage());
        }
    }

    // idCols all null, nameExpression null, has 'name' property -- ok
    @Test
    public void idColsUnset_nameExpressionNull_hasNameProperty() throws Exception
    {
        final User user = TestContext.get().getUser();

        List<GWTPropertyDescriptor> props = new ArrayList<>();
        props.add(new GWTPropertyDescriptor("name", "string"));
        props.add(new GWTPropertyDescriptor("age", "int"));

        final ExpSampleSet ss = ExperimentService.get().createSampleSet(c, user,
                "Samples", null, props, Collections.emptyList(),
                -1, -1, -1, -1, null, null);

        ExpMaterial sample = ss.getSample(c, "bob");
        assertNull(sample);

        UserSchema schema = QueryService.get().getUserSchema(user, c, SchemaKey.fromParts("Samples"));
        TableInfo table = schema.getTable("Samples");
        QueryUpdateService svc = table.getUpdateService();

        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(CaseInsensitiveHashMap.of("name", "bob", "age", 10));

        BatchValidationException errors = new BatchValidationException();
        svc.insertRows(user, c, rows, errors, null, null);
        if (errors.hasErrors())
            throw errors;

        sample = ss.getSample(c, "bob");
        assertNotNull(sample);
        assertEquals("bob", sample.getName());
    }

    // idCols all null, nameExpression not null, has 'name' property -- ok
    @Test
    public void idColsUnset_nameExpression_hasNameProperty() throws Exception
    {
        final User user = TestContext.get().getUser();

        List<GWTPropertyDescriptor> props = new ArrayList<>();
        props.add(new GWTPropertyDescriptor("name", "string"));
        props.add(new GWTPropertyDescriptor("prop", "string"));
        props.add(new GWTPropertyDescriptor("age", "int"));

        final String nameExpression = "S-${prop}.${age}";

        final ExpSampleSet ss = ExperimentService.get().createSampleSet(c, user,
                "Samples", null, props, Collections.emptyList(),
                -1, -1, -1, -1, nameExpression, null);
    }

    // idCols not null, nameExpression null, no 'name' property -- ok
    @Test
    public void idColsSet_nameExpressionNull_noNameProperty() throws Exception
    {
        final User user = TestContext.get().getUser();

        List<GWTPropertyDescriptor> props = new ArrayList<>();
        props.add(new GWTPropertyDescriptor("prop", "string"));
        props.add(new GWTPropertyDescriptor("age", "int"));

        final ExpSampleSet ss = ExperimentService.get().createSampleSet(c, user,
                "Samples", null, props, Collections.emptyList(),
                0, 1, -1, -1, null, null);

        final String expectedName1 = "bob";
        final String expectedName2 = "red-11";
        ExpMaterial sample1 = ss.getSample(c, expectedName1);
        assertNull(sample1);
        ExpMaterial sample2 = ss.getSample(c, expectedName2);
        assertNull(sample2);

        UserSchema schema = QueryService.get().getUserSchema(user, c, SchemaKey.fromParts("Samples"));
        TableInfo table = schema.getTable("Samples");
        QueryUpdateService svc = table.getUpdateService();

        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(CaseInsensitiveHashMap.of("name", "bob", "prop", "blue", "age", 10));
        rows.add(CaseInsensitiveHashMap.of("prop", "red", "age", 11));

        BatchValidationException errors = new BatchValidationException();
        svc.insertRows(user, c, rows, errors, null, null);
        if (errors.hasErrors())
            throw errors;

        sample1 = ss.getSample(c, expectedName1);
        assertEquals(expectedName1, sample1.getName());
        sample2 = ss.getSample(c, expectedName2);
        assertEquals(expectedName2, sample2.getName());
    }

    // idCols not null, nameExpression null, 'name' property (not used) -- fail **
    @Test
    public void idColsSet_nameExpressionNull_hasUnusedNameProperty() throws Exception
    {
        final User user = TestContext.get().getUser();

        try
        {
            List<GWTPropertyDescriptor> props = new ArrayList<>();
            props.add(new GWTPropertyDescriptor("name", "string"));
            props.add(new GWTPropertyDescriptor("age", "int"));

            final ExpSampleSet ss = ExperimentService.get().createSampleSet(c, user,
                    "Samples", null, props, Collections.emptyList(),
                    1, -1, -1, -1, null, null);
            fail("Expected exception");
        }
        catch (ExperimentException ee)
        {
            assertEquals("Either a 'Name' property or idCols can be used, but not both", ee.getMessage());
        }
    }

    // idCols not null, nameExpression null, 'name' property (used) -- ok
    @Test
    public void idColsSet_nameExpressionNull_hasNameProperty() throws Exception
    {
        final User user = TestContext.get().getUser();

        List<GWTPropertyDescriptor> props = new ArrayList<>();
        props.add(new GWTPropertyDescriptor("name", "string"));
        props.add(new GWTPropertyDescriptor("prop", "string"));
        props.add(new GWTPropertyDescriptor("age", "int"));

        final ExpSampleSet ss = ExperimentService.get().createSampleSet(c, user,
                "Samples", null, props, Collections.emptyList(),
                0, -1, -1, -1, null, null);

        final String expectedName1 = "bob";
        ExpMaterial sample1 = ss.getSample(c, expectedName1);
        assertNull(sample1);

        UserSchema schema = QueryService.get().getUserSchema(user, c, SchemaKey.fromParts("Samples"));
        TableInfo table = schema.getTable("Samples");
        QueryUpdateService svc = table.getUpdateService();

        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(CaseInsensitiveHashMap.of("name", "bob", "prop", "blue", "age", 10));

        BatchValidationException errors = new BatchValidationException();
        svc.insertRows(user, c, rows, errors, null, null);
        if (errors.hasErrors())
            throw errors;

        sample1 = ss.getSample(c, expectedName1);
        assertEquals(expectedName1, sample1.getName());

        // try to insert without a value for 'name' property results in an error
        rows = new ArrayList<>();
        rows.add(CaseInsensitiveHashMap.of("prop", "red", "age", 11));

        errors = new BatchValidationException();
        svc.insertRows(user, c, rows, errors, null, null);
        assertTrue(errors.hasErrors());
        assertTrue(errors.getMessage().contains("Name is required for Sample on row 1"));
    }

    // idCols not null, nameExpression not null, 'name' property (not used) -- fail
    @Test
    public void idColsSet_nameExpression_hasUnusedNameProperty() throws Exception
    {
        final User user = TestContext.get().getUser();

        try
        {
            List<GWTPropertyDescriptor> props = new ArrayList<>();
            props.add(new GWTPropertyDescriptor("name", "string"));
            props.add(new GWTPropertyDescriptor("age", "int"));

            final ExpSampleSet ss = ExperimentService.get().createSampleSet(c, user,
                    "Samples", null, props, Collections.emptyList(),
                    1, -1, -1, -1, "S-${name}.${age}", null);
            fail("Expected exception");
        }
        catch (ExperimentException ee)
        {
            assertEquals("Name expression cannot be used with id columns", ee.getMessage());
        }
    }


    @Test
    public void testNameExpression() throws Exception
    {
        final User user = TestContext.get().getUser();

        // setup
        List<GWTPropertyDescriptor> props = new ArrayList<>();
        props.add(new GWTPropertyDescriptor("name", "string"));
        props.add(new GWTPropertyDescriptor("prop", "string"));
        props.add(new GWTPropertyDescriptor("age", "int"));

        final String nameExpression = "S-${prop}.${age}";

        final ExpSampleSet ss = ExperimentService.get().createSampleSet(c, user,
                "Samples", null, props, Collections.emptyList(),
                -1, -1, -1, -1, nameExpression, null);

        final String expectedName1 = "bob";
        final String expectedName2 = "S-red.11";
        ExpMaterial sample1 = ss.getSample(c, expectedName1);
        assertNull(sample1);
        ExpMaterial sample2 = ss.getSample(c, expectedName2);
        assertNull(sample2);

        UserSchema schema = QueryService.get().getUserSchema(user, c, SchemaKey.fromParts("Samples"));
        TableInfo table = schema.getTable("Samples");
        QueryUpdateService svc = table.getUpdateService();

        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(CaseInsensitiveHashMap.of("name", "bob", "prop", "blue", "age", 10));
        rows.add(CaseInsensitiveHashMap.of("prop", "red", "age", 11));

        BatchValidationException errors = new BatchValidationException();
        svc.insertRows(user, c, rows, errors, null, null);
        if (errors.hasErrors())
            throw errors;

        sample1 = ss.getSample(c, expectedName1);
        assertEquals(expectedName1, sample1.getName());
        sample2 = ss.getSample(c, expectedName2);
        assertEquals(expectedName2, sample2.getName());
    }

    @Test
    public void testAddUniqueSuffixForDuplicateNames() throws Exception
    {
        final User user = TestContext.get().getUser();

        // setup
        List<GWTPropertyDescriptor> props = new ArrayList<>();
        props.add(new GWTPropertyDescriptor("name", "string"));
        props.add(new GWTPropertyDescriptor("prop", "string"));
        props.add(new GWTPropertyDescriptor("age", "int"));

        final String nameExpression = "S-${prop}-${age}";

        final ExpSampleSet ss = ExperimentService.get().createSampleSet(c, user,
                "Samples", null, props, Collections.emptyList(),
                -1, -1, -1, -1, nameExpression, null);

        UserSchema schema = QueryService.get().getUserSchema(user, c, SchemaKey.fromParts("Samples"));
        TableInfo table = schema.getTable("Samples");
        QueryUpdateService svc = table.getUpdateService();

        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(CaseInsensitiveHashMap.of("prop", "red", "age", 10));
        rows.add(CaseInsensitiveHashMap.of("prop", "red", "age", 10));
        rows.add(CaseInsensitiveHashMap.of("prop", "red", "age", 10));

        // Validate duplicate names result in an error
        BatchValidationException errors = new BatchValidationException();
        svc.insertRows(user, c, rows, errors, null, null);
        assertTrue(errors.hasErrors());
        assertTrue("Expected \"Duplicate name 'S-red-10' on row 2\" but got: " + errors.getMessage(), errors.getMessage().contains("Duplicate name 'S-red-10' on row 2"));

        // Turn on unique suffix behavior
        Map<Enum, Object> configParams = new HashMap<>();
        configParams.put(SampleSetUpdateService.Options.AddUniqueSuffixForDuplicateNames, true);

        errors = new BatchValidationException();
        svc.insertRows(user, c, rows, errors, configParams, null);
        assertFalse(errors.hasErrors());

        String expectedName1 = "S-red-10";
        String expectedName2 = "S-red-10.1";
        String expectedName3 = "S-red-10.2";

        ExpMaterial sample1 = ss.getSample(c, expectedName1);
        assertEquals(expectedName1, sample1.getName());
        ExpMaterial sample2 = ss.getSample(c, expectedName2);
        assertEquals(expectedName2, sample2.getName());
        ExpMaterial sample3 = ss.getSample(c, expectedName3);
        assertEquals(expectedName3, sample3.getName());
    }
}
