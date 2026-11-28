/*
 * Copyright (c) 2026 LabKey Corporation
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

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.triggers.FillIfMissingTrigger;
import org.labkey.api.data.triggers.RecordingTrigger;
import org.labkey.api.data.triggers.Trigger;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.SampleTypeTestHelper;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateServiceTestUtil;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.TestContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** GH Issue 1048: MERGE must leave sample columns the input omits unchanged, and script triggers see existing samples as updates. */
public class SampleMergeTestCase extends Assert
{
    private static final String INT_FIELD = "A";
    private static final String TEXT_FIELD = "B";

    private static Container _c;
    private static User _user;

    @BeforeClass
    public static void setUp()
    {
        JunitUtil.deleteTestContainer();
        _c = JunitUtil.getTestContainer();
        _user = TestContext.get().getUser();
    }

    @AfterClass
    public static void tearDown()
    {
        JunitUtil.deleteTestContainer();
    }

    @Test
    public void mergeWithoutTriggerKeepsOmittedColumns() throws Exception
    {
        ExpSampleType st = createSampleType("MergeNoTrigger");
        mergeIntField(SampleTypeTestHelper.getTable(_c, _user, st), "S-1");
        assertOmittedFieldKept(st);
    }

    @Test
    public void mergeWithTriggerKeepsOmittedColumns() throws Exception
    {
        ExpSampleType st = createSampleType("MergeTrigger");
        mergeIntField(tableWithTrigger(st, new Trigger() {}), "S-1");
        assertOmittedFieldKept(st);
    }

    @Test
    public void mergeRoutesExistingSamplesToUpdateHooks() throws Exception
    {
        ExpSampleType st = createSampleType("MergeRouted");
        RecordingTrigger trigger = new RecordingTrigger("name", List.of(TEXT_FIELD), true, TEXT_FIELD);
        mergeIntField(tableWithTrigger(st, trigger), "S-1", "S-2");

        trigger.assertEvents("beforeUpdate S-1 old x", "beforeInsert S-2", "afterUpdate S-1", "afterInsert S-2");
        assertOmittedFieldKept(st);
        assertEquals(RecordingTrigger.COMPUTED, getSample(st, "S-2").get(TEXT_FIELD));
    }

    @Test
    public void mergeLetsTriggersFillOmittedColumns() throws Exception
    {
        for (boolean routed : List.of(true, false))
        {
            for (boolean managedColumnsEnabled : List.of(true, false))
            {
                String label = "routed " + routed + ", managed columns enabled " + managedColumnsEnabled;
                ExpSampleType st = createSampleType("MergeFill" + routed + managedColumnsEnabled);
                mergeIntField(tableWithTrigger(st, new FillIfMissingTrigger(TEXT_FIELD, routed, managedColumnsEnabled)), "S-1", "S-2");
                assertEquals(label + ": existing sample lost its stored value", "x", getSample(st, "S-1").get(TEXT_FIELD));
                assertEquals(label + ": new sample lost the trigger's value", FillIfMissingTrigger.FILLED, getSample(st, "S-2").get(TEXT_FIELD));
            }
        }
    }

    /** Creates the sample type with sample S-1 */
    private ExpSampleType createSampleType(String name) throws Exception
    {
        ExpSampleType st = SampleTypeTestHelper.create(_c, _user, name, new GWTPropertyDescriptor(INT_FIELD, "int"), new GWTPropertyDescriptor(TEXT_FIELD, "string"));
        SampleTypeTestHelper.insert(_c, _user, st, List.of(CaseInsensitiveHashMap.of("name", "S-1", INT_FIELD, 1, TEXT_FIELD, "x")));
        return st;
    }

    private TableInfo tableWithTrigger(ExpSampleType st, Trigger trigger)
    {
        UserSchema schema = QueryService.get().getUserSchema(_user, _c, SamplesSchema.SCHEMA_NAME);
        assertNotNull(schema);
        return QueryUpdateServiceTestUtil.withTrigger(schema.getTable(st.getName(), null, true, true), _c, trigger);
    }

    /** Writes the int field only, for each sample name */
    private void mergeIntField(TableInfo table, String... names) throws Exception
    {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String name : names)
            rows.add(CaseInsensitiveHashMap.of("name", name, INT_FIELD, 2));
        assertEquals(names.length, QueryUpdateServiceTestUtil.merge(table, _user, _c, rows));
    }

    private Map<String, Object> getSample(ExpSampleType st, String name)
    {
        Map<String, Object> row = new TableSelector(SampleTypeTestHelper.getTable(_c, _user, st), new SimpleFilter(FieldKey.fromParts("name"), name), null).getMap();
        assertNotNull(row);
        return row;
    }

    private void assertOmittedFieldKept(ExpSampleType st)
    {
        Map<String, Object> row = getSample(st, "S-1");
        assertEquals(2, ((Number) row.get(INT_FIELD)).intValue());
        assertEquals("MERGE nulled a column it was not given", "x", row.get(TEXT_FIELD));
    }
}
