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
import org.labkey.api.security.User;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.TestContext;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/** GH Issue 1048: MERGE must leave sample columns the input omits unchanged, with or without a trigger on the table. */
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
        mergeIntField(table(st, false));
        assertOmittedFieldKept(st);
    }

    @Test
    public void mergeWithTriggerKeepsOmittedColumns() throws Exception
    {
        ExpSampleType st = createSampleType("MergeTrigger");
        insertSample(st);
        mergeIntField(table(st, true));
        assertOmittedFieldKept(st);
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
    private TableInfo table(ExpSampleType st, boolean withTrigger)
    {
        UserSchema schema = QueryService.get().getUserSchema(_user, _c, SamplesSchema.SCHEMA_NAME);
        assertNotNull(schema);
        TableInfo table = schema.getTable(st.getName(), null, true, withTrigger);
        assertNotNull(table);
        if (withTrigger)
        {
            AbstractTableInfo writable = (AbstractTableInfo) table;
            writable.addTriggerFactory((_, _, _) -> List.of(new Trigger() {}));
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
        updateService(table(st, false)).insertRows(_user, _c, List.of(CaseInsensitiveHashMap.of("name", "S-1", INT_FIELD, 1, TEXT_FIELD, "x")), errors, null, null);
        if (errors.hasErrors())
            throw errors;
    }

    private void mergeIntField(TableInfo table) throws Exception
    {
        BatchValidationException errors = new BatchValidationException();
        int count = updateService(table).mergeRows(_user, _c, MapDataIterator.of(List.of(CaseInsensitiveHashMap.of("name", "S-1", INT_FIELD, 2))), errors, null, null);
        if (errors.hasErrors())
            throw errors;
        assertEquals(1, count);
    }

    private void assertOmittedFieldKept(ExpSampleType st)
    {
        Map<String, Object> row = new TableSelector(table(st, false), new SimpleFilter(FieldKey.fromParts("name"), "S-1"), null).getMap();
        assertNotNull(row);
        assertEquals(2, ((Number) row.get(INT_FIELD)).intValue());
        assertEquals("MERGE nulled a column it was not given", "x", row.get(TEXT_FIELD));
    }
}
