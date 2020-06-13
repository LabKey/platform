/*
 * Copyright (c) 2019 LabKey Corporation
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
import org.labkey.api.exp.api.ExpDataClass;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.SampleSetService;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.TestContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThat;

public class UniqueValueCounterTestCase
{
    Container c;

    @Before
    public void setUp()
    {
        c = JunitUtil.getTestContainer();
    }

    @After
    public void tearDown()
    {
        ContainerManager.deleteAll(c, TestContext.get().getUser());
    }


    private static final String counterName = "CounterName";
    private static final String sampSetName = "SampleSetWithSeq";
    private static final String sampSetMetadataWithCounters = "" +
            "<tables xmlns=\"http://labkey.org/data/xml\">\n" +
            "  <table tableName=\"" + sampSetName + "\" tableDbType=\"NOT_IN_DB\">\n" +
            "    <javaCustomizer class=\"org.labkey.experiment.api.CountOfUniqueValueTableCustomizer\">\n" +
            "        <properties>\n" +
            "            <property name=\"counterName\">" + counterName + "</property>\n" +
            "            <property name=\"counterType\">org.labkey.api.data.UniqueValueCounterDefinition</property>\n" +
            "            <property name=\"pairedColumn\">vessel</property>     <!-- one or more -->\n" +
            "            <property name=\"pairedColumn\">one</property>\n" +
            "            <property name=\"pairedColumn\">two</property>\n" +

            "            <!-- attach the counter to one or more columns -->\n" +
            "            <property name=\"attachedColumn\">three</property>  <!-- one or more -->\n" +
            "        </properties>\n" +
            "    </javaCustomizer>\n" +
            "\n" +
            "  </table>\n" +
            "</tables>";


    @Test
    public void sampleSetWithCounter() throws Exception
    {
        final User user = TestContext.get().getUser();

        // setup
        List<GWTPropertyDescriptor> props = new ArrayList<>();
        props.add(new GWTPropertyDescriptor("name", "string"));
        props.add(new GWTPropertyDescriptor("vessel", "string"));
        props.add(new GWTPropertyDescriptor("one", "string"));
        props.add(new GWTPropertyDescriptor("two", "int"));
        props.add(new GWTPropertyDescriptor("three", "int"));
        props.add(new GWTPropertyDescriptor("suffix", "string"));

        final String nameExpression = "${vessel}.${one}.${three}.${suffix}";

        final ExpSampleSet ss = SampleSetService.get().createSampleSet(c, user,
                sampSetName, null, props, emptyList(),
                -1, -1, -1, -1, nameExpression, null);

        UserSchema schema = QueryService.get().getUserSchema(user, c, SchemaKey.fromParts("Samples"));
        QueryDefinition queryDefinition = QueryService.get().getQueryDef(user, c, "Samples", sampSetName);
        if (null == queryDefinition)
        {
            queryDefinition = schema.getQueryDefForTable(sampSetName);
        }

        queryDefinition.setMetadataXml(sampSetMetadataWithCounters);
        queryDefinition.save(user, c);

        TableInfo table = schema.getTable(sampSetName);
        QueryUpdateService svc = table.getUpdateService();

        // GOOD INSERT - combinations of the paired columns
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(CaseInsensitiveHashMap.of("vessel", "STP", "one", 10, "two", 1, "suffix", "SUF1"));
        rows.add(CaseInsensitiveHashMap.of("vessel", "STP", "one", 10, "two", 1, "suffix", "SUF1"));
        rows.add(CaseInsensitiveHashMap.of("vessel", "STP", "one", 10, "two", 1, "suffix", "SUF2"));
        rows.add(CaseInsensitiveHashMap.of("vessel", "STP", "one", 10, "two", 1, "suffix", "SUF2"));
        rows.add(CaseInsensitiveHashMap.of("vessel", "STP", "one", 12, "two", 1, "suffix", "SUF4"));
        rows.add(CaseInsensitiveHashMap.of("vessel", "STP", "one", 12, "two", 1, "suffix", "SUF4"));
        rows.add(CaseInsensitiveHashMap.of("vessel", "STP", "one", 12, "two", 2, "suffix", "SUF4A"));
        rows.add(CaseInsensitiveHashMap.of("vessel", "STP", "one", 12, "two", 2, "suffix", "SUF4A"));

        BatchValidationException errors = new BatchValidationException();
        List<Map<String, Object>> inserted = svc.insertRows(user, c, rows, errors, null, null);
        assertFalse(errors.hasErrors());
        assertEquals(8, inserted.size());
        assertEquals("STP.10.1.SUF1", inserted.get(0).get("name"));
        assertEquals("STP.10.2.SUF1", inserted.get(1).get("name"));
        assertEquals("STP.10.3.SUF2", inserted.get(2).get("name"));
        assertEquals("STP.10.4.SUF2", inserted.get(3).get("name"));
        assertEquals("STP.12.1.SUF4", inserted.get(4).get("name"));
        assertEquals("STP.12.2.SUF4", inserted.get(5).get("name"));
        assertEquals("STP.12.1.SUF4A", inserted.get(6).get("name"));
        assertEquals("STP.12.2.SUF4A", inserted.get(7).get("name"));


        // GOOD INSERT - providing a counter value is ok if it is less than or equal to the current value
        rows = new ArrayList<>();
        rows.add(CaseInsensitiveHashMap.of("vessel", "STP", "one", 12, "two", 2, "suffix", "SUF4B", "three", 1));  // Specify attached field value < current value is ok
        errors = new BatchValidationException();
        inserted = svc.insertRows(user, c, rows, errors, null, null);
        assertFalse(errors.hasErrors());
        assertEquals(1, inserted.size());
        assertEquals("STP.12.1.SUF4B", inserted.get(0).get("name"));


        // GOOD INSERT - not including the attached column 'three' is ok
        rows = new ArrayList<>();
        rows.add(CaseInsensitiveHashMap.of("vessel", "STP", "one", 10, "two", 1, "suffix", "SUF1"));
        errors = new BatchValidationException();
        inserted = svc.insertRows(user, c, rows, errors, null, null);
        assertFalse(errors.hasErrors());
        assertEquals(1, inserted.size());
        assertEquals("STP.10.5.SUF1", inserted.get(0).get("name"));


        // BAD INSERT - Duplicate name
        rows = new ArrayList<>();
        rows.add(CaseInsensitiveHashMap.of("vessel", "STP", "one", 10, "two", 3, "suffix", "SUF1"));        // New sequence will build same name as before
        errors = new BatchValidationException();
        inserted = svc.insertRows(user, c, rows, errors, null, null);
        assertTrue(errors.hasErrors());
        assertTrue("Expected duplicate key violation: " + errors.getMessage(),
               errors.getMessage().contains("duplicate key"));


        // NOTE: This test case doesn't repro for SampleSet because the CoerceDataIterator is run before the CounterDataIteratorBuilder and will include null values for any missing columns
//        // BAD INSERT - Paired columns must be included in the input data
//        rows = new ArrayList<>();
//        rows.add(CaseInsensitiveHashMap.of("vessel", "STP", "suffix", "SUF1", "three", 20));
//        errors = new BatchValidationException();
//        inserted = svc.insertRows(user, c, rows, errors, null, null);
//        assertTrue(errors.hasErrors());
//        assertThat(errors.getMessage(),
//                containsString("Paired column 'one' is required for counter '" + counterName + "'"));


        // BAD INSERT - Paired columns must have a non-null value
        rows = new ArrayList<>();
        rows.add(CaseInsensitiveHashMap.of("vessel", "STP", "one", 10, "two", null, "suffix", "SUF1", "three", 20));
        errors = new BatchValidationException();
        inserted = svc.insertRows(user, c, rows, errors, null, null);
        assertTrue(errors.hasErrors());
        assertThat(errors.getMessage(),
                containsString("Paired column 'two' must not be null for counter '" + counterName + "'"));


        // BAD INSERT - Specifying attached field with value beyond current counter
        rows = new ArrayList<>();
        rows.add(CaseInsensitiveHashMap.of("vessel", "STP", "one", 10, "two", 1, "suffix", "SUF1", "three", 20));
        errors = new BatchValidationException();
        inserted = svc.insertRows(user, c, rows, errors, null, null);
        assertTrue(errors.hasErrors());
        assertThat(errors.getMessage(),
                containsString("Value (20) of paired column 'three' is greater than the current counter value (5) for counter '" + counterName + "'"));
    }



    private static final String dataClassName = "DataClassWithSeq";
    private static final String dataClassMetadataWithCounters = "" +
            "<tables xmlns=\"http://labkey.org/data/xml\">\n" +
            "  <table tableName=\"" + dataClassName + "\" tableDbType=\"NOT_IN_DB\">\n" +
            "    <javaCustomizer class=\"org.labkey.experiment.api.CountOfUniqueValueTableCustomizer\">\n" +
            "        <properties>\n" +
            "            <property name=\"counterName\">" + counterName + "</property>\n" +
            "            <property name=\"counterType\">org.labkey.api.data.UniqueValueCounterDefinition</property>\n" +
            "            <property name=\"pairedColumn\">one</property>     <!-- one or more -->\n" +
            "            <property name=\"pairedColumn\">two</property>" +

            "            <!-- attach the counter to one or more columns -->\n" +
            "            <property name=\"attachedColumn\">three</property>  <!-- one or more -->\n" +
            "        </properties>\n" +
            "    </javaCustomizer>\n" +
            "\n" +
            "  </table>\n" +
            "</tables>";


    @Test
    public void dataClassWithCounter() throws Exception
    {
        final User user = TestContext.get().getUser();

        // setup
        List<GWTPropertyDescriptor> props = new ArrayList<>();
        props.add(new GWTPropertyDescriptor("one", "string"));
        props.add(new GWTPropertyDescriptor("two", "int"));
        props.add(new GWTPropertyDescriptor("three", "int"));

        final String nameExpression = "DC-${one}.${two}.${three}";

        final ExpDataClass dc = ExperimentServiceImpl.get().createDataClass(c, user, dataClassName, null, props, emptyList(), null, nameExpression, null, null);
        assertNotNull(dc);

        UserSchema schema = QueryService.get().getUserSchema(user, c, SchemaKey.fromParts("exp", "data"));
        QueryDefinition queryDefinition = QueryService.get().getQueryDef(user, c, "exp.data", dataClassName);
        if (null == queryDefinition)
        {
            queryDefinition = schema.getQueryDefForTable(dataClassName);
        }

        queryDefinition.setMetadataXml(dataClassMetadataWithCounters);
        queryDefinition.save(user, c);

        TableInfo table = schema.getTable(dataClassName);
        QueryUpdateService svc = table.getUpdateService();

        // GOOD INSERT
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(CaseInsensitiveHashMap.of("one", 10, "two", 1));
        rows.add(CaseInsensitiveHashMap.of("one", 10, "two", 1));
        rows.add(CaseInsensitiveHashMap.of("one", 10, "two", 1));
        rows.add(CaseInsensitiveHashMap.of("one", 10, "two", 1));
        rows.add(CaseInsensitiveHashMap.of("one", 12, "two", 1));
        rows.add(CaseInsensitiveHashMap.of("one", 12, "two", 1));
        rows.add(CaseInsensitiveHashMap.of("one", 12, "two", 2));
        rows.add(CaseInsensitiveHashMap.of("one", 12, "two", 2));

        BatchValidationException errors = new BatchValidationException();
        List<Map<String, Object>> inserted = svc.insertRows(user, c, rows, errors, null, null);
        assertFalse(errors.hasErrors());
        assertEquals(8, inserted.size());
        assertEquals("DC-10.1.1", inserted.get(0).get("name"));
        assertEquals("DC-10.1.2", inserted.get(1).get("name"));
        assertEquals("DC-10.1.3", inserted.get(2).get("name"));
        assertEquals("DC-10.1.4", inserted.get(3).get("name"));
        assertEquals("DC-12.1.1", inserted.get(4).get("name"));
        assertEquals("DC-12.1.2", inserted.get(5).get("name"));
        assertEquals("DC-12.2.1", inserted.get(6).get("name"));
        assertEquals("DC-12.2.2", inserted.get(7).get("name"));


        // GOOD INSERT - not including the attached column 'three' is ok
        rows = new ArrayList<>();
        rows.add(CaseInsensitiveHashMap.of("one", 10, "two", 1));
        errors = new BatchValidationException();
        inserted = svc.insertRows(user, c, rows, errors, null, null);
        assertFalse(errors.hasErrors());
        assertEquals(1, inserted.size());
        assertEquals("DC-10.1.5", inserted.get(0).get("name"));


        // NOTE: This test case doesn't repro for SampleSet because the CoerceDataIterator is run before the CounterDataIteratorBuilder and will include null values for any missing columns
        // BAD INSERT - Paired columns must be included in the input data
        rows = new ArrayList<>();
        rows.add(CaseInsensitiveHashMap.of("two", 20));
        errors = new BatchValidationException();
        inserted = svc.insertRows(user, c, rows, errors, null, null);
        assertTrue(errors.hasErrors());
        assertThat(errors.getMessage(),
                containsString("Paired column 'one' is required for counter '" + counterName + "'"));


    }
}
