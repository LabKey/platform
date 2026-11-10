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

import org.jetbrains.annotations.Nullable;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.triggers.Trigger;
import org.labkey.api.dataiterator.MapDataIterator;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.TestContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        insertSample(st);
        mergeIntField(table(st, null), "S-1");
        assertOmittedFieldKept(st);
    }

    @Test
    public void mergeWithTriggerKeepsOmittedColumns() throws Exception
    {
        ExpSampleType st = createSampleType("MergeTrigger");
        insertSample(st);
        mergeIntField(table(st, new Trigger() {}), "S-1");
        assertOmittedFieldKept(st);
    }

    @Test
    public void mergeRoutesExistingSamplesToUpdateHooks() throws Exception
    {
        ExpSampleType st = createSampleType("MergeRouted");
        insertSample(st);
        RecordingTrigger trigger = new RecordingTrigger();
        mergeIntField(table(st, trigger), "S-1", "S-2");

        assertEquals(Set.of("beforeUpdate S-1 old x", "beforeInsert S-2", "afterUpdate S-1", "afterInsert S-2"), Set.copyOf(trigger.events));
        assertEquals(4, trigger.events.size());
        assertOmittedFieldKept(st);
        assertEquals("computed", getSample(st, "S-2").get(TEXT_FIELD));
    }

    /** Opts into MERGE routing and manages the text field on insert */
    private static class RecordingTrigger implements Trigger
    {
        final List<String> events = new ArrayList<>();

        @Override
        public boolean isMergeRoutedByExistingRecord()
        {
            return true;
        }

        @Override
        public @Nullable ManagedColumns getManagedColumns()
        {
            return new ManagedColumns(Set.of(TEXT_FIELD), Set.of(), null);
        }

        @Override
        public void beforeInsert(TableInfo table, Container c, User user, @Nullable QueryUpdateService.InsertOption insertOption, @Nullable Map<String, Object> newRow, ValidationException errors, Map<String, Object> extraContext)
        {
            assertNotNull(newRow);
            events.add("beforeInsert " + newRow.get("name"));
            newRow.put(TEXT_FIELD, "computed");
        }

        @Override
        public void beforeUpdate(TableInfo table, Container c, User user, @Nullable QueryUpdateService.InsertOption insertOption, @Nullable Map<String, Object> newRow, @Nullable Map<String, Object> oldRow, ValidationException errors, Map<String, Object> extraContext)
        {
            assertNotNull(newRow);
            assertNotNull(oldRow);
            events.add("beforeUpdate " + newRow.get("name") + " old " + new CaseInsensitiveHashMap<>(oldRow).get(TEXT_FIELD));
        }

        @Override
        public void afterInsert(TableInfo table, Container c, User user, @Nullable Map<String, Object> newRow, ValidationException errors, Map<String, Object> extraContext)
        {
            assertNotNull(newRow);
            events.add("afterInsert " + newRow.get("name"));
        }

        @Override
        public void afterUpdate(TableInfo table, Container c, User user, @Nullable Map<String, Object> newRow, @Nullable Map<String, Object> oldRow, ValidationException errors, Map<String, Object> extraContext)
        {
            assertNotNull(newRow);
            assertNotNull(oldRow);
            events.add("afterUpdate " + newRow.get("name"));
        }
    }

    private ExpSampleType createSampleType(String name) throws Exception
    {
        List<GWTPropertyDescriptor> props = List.of(
            new GWTPropertyDescriptor("name", "string"),
            new GWTPropertyDescriptor(INT_FIELD, "int"),
            new GWTPropertyDescriptor(TEXT_FIELD, "string"));
        return SampleTypeService.get().createSampleType(_c, _user, name, null, props, Collections.emptyList(), -1, -1, -1, -1, null);
    }

    /** Cached tables are locked, so only one built for write accepts another trigger factory */
    private TableInfo table(ExpSampleType st, @Nullable Trigger trigger)
    {
        UserSchema schema = QueryService.get().getUserSchema(_user, _c, SamplesSchema.SCHEMA_NAME);
        assertNotNull(schema);
        TableInfo table = schema.getTable(st.getName(), null, true, trigger != null);
        assertNotNull(table);
        if (trigger != null)
        {
            AbstractTableInfo writable = (AbstractTableInfo) table;
            writable.addTriggerFactory((_, _, _) -> List.of(trigger));
            writable.resetTriggers(_c);
            assertTrue(writable.hasTriggers(_c));
        }

        return table;
    }

    private QueryUpdateService updateService(TableInfo table)
    {
        QueryUpdateService qus = table.getUpdateService();
        assertNotNull(qus);
        return qus;
    }

    private void insertSample(ExpSampleType st) throws Exception
    {
        BatchValidationException errors = new BatchValidationException();
        updateService(table(st, null)).insertRows(_user, _c, List.of(CaseInsensitiveHashMap.of("name", "S-1", INT_FIELD, 1, TEXT_FIELD, "x")), errors, null, null);
        if (errors.hasErrors())
            throw errors;
    }

    /** Writes the int field only, for each sample name */
    private void mergeIntField(TableInfo table, String... names) throws Exception
    {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String name : names)
            rows.add(CaseInsensitiveHashMap.of("name", name, INT_FIELD, 2));

        BatchValidationException errors = new BatchValidationException();
        int count = updateService(table).mergeRows(_user, _c, MapDataIterator.of(rows), errors, null, null);
        if (errors.hasErrors())
            throw errors;
        assertEquals(names.length, count);
    }

    private Map<String, Object> getSample(ExpSampleType st, String name)
    {
        Map<String, Object> row = new TableSelector(table(st, null), new SimpleFilter(FieldKey.fromParts("name"), name), null).getMap();
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
