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

import org.jetbrains.annotations.NotNull;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.SampleTimelineAuditEvent;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.dataiterator.MapDataIterator;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.security.User;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.TestContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.labkey.api.audit.AuditHandler.USER_PROVIDED_DATA_PREFIX;
import static org.labkey.api.exp.query.ExpMaterialTable.Column.StoredAmount;
import static org.labkey.api.exp.query.ExpMaterialTable.Column.Units;

/**
 * GH Issue 1640: DetailedAuditLogDataIterator flushes audit events every 1,001 rows, and each sample's
 * provided amount must stay with that sample across flushes.
 */
public class SampleProvidedAmountAuditTestCase extends Assert
{
    private static final int ROW_COUNT = 2500; // spans three audit batches
    private static final String PROVIDED_AMOUNT_KEY = USER_PROVIDED_DATA_PREFIX + StoredAmount.label();

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
    public void testInsertKeepsProvidedAmountWithRow() throws Exception
    {
        ExpSampleType st = createSampleType("ProvidedAmountInsert");
        Map<String, Integer> expected = new HashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 1; i <= ROW_COUNT; i++)
        {
            rows.add(sampleRow("S-" + i, i));
            expected.put("S-" + i, i);
        }

        BatchValidationException errors = new BatchValidationException();
        getUpdateService(st).insertRows(_user, _c, rows, errors, null, null);
        if (errors.hasErrors())
            throw errors;

        assertProvidedAmounts(st, expected);
    }

    @Test
    public void testMergeKeepsProvidedAmountWithRow() throws Exception
    {
        ExpSampleType st = createSampleType("ProvidedAmountMerge");
        int existingCount = ROW_COUNT / 2;
        List<Map<String, Object>> existing = new ArrayList<>();
        for (int i = 1; i <= existingCount; i++)
            existing.add(sampleRow("S-" + i, i));

        BatchValidationException errors = new BatchValidationException();
        getUpdateService(st).insertRows(_user, _c, existing, errors, null, null);
        if (errors.hasErrors())
            throw errors;

        // The first half updates existing samples and the second half inserts new ones, all in one merge
        Map<String, Integer> expected = new HashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 1; i <= ROW_COUNT; i++)
        {
            int amount = ROW_COUNT + i;
            rows.add(sampleRow("S-" + i, amount));
            expected.put("S-" + i, amount);
        }

        getUpdateService(st).mergeRows(_user, _c, MapDataIterator.of(rows), errors, null, null);
        if (errors.hasErrors())
            throw errors;

        assertProvidedAmounts(st, expected);
    }

    private static Map<String, Object> sampleRow(String name, int amount)
    {
        return CaseInsensitiveHashMap.of("name", name, StoredAmount.name(), amount, Units.name(), "uL");
    }

    /** Checks the provided amount on each sample's most recent timeline event. */
    private void assertProvidedAmounts(ExpSampleType st, Map<String, Integer> expected)
    {
        AbstractAuditTypeProvider provider = (AbstractAuditTypeProvider) AuditLogService.get().getAuditProvider(SampleTimelineAuditEvent.EVENT_TYPE);
        TableInfo table = provider.createStorageTableInfo();
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("SampleTypeId"), st.getRowId());

        Map<String, String> latestMetadata = new HashMap<>();
        new TableSelector(table, PageFlowUtil.set("RowId", "SampleName", "Metadata"), filter, new Sort("RowId"))
            .forEach(rs -> latestMetadata.put(rs.getString("SampleName"), rs.getString("Metadata")));

        assertEquals("Timeline event count", expected.size(), latestMetadata.size());
        for (Map.Entry<String, Integer> entry : expected.entrySet())
        {
            String sampleName = entry.getKey();
            String metadata = latestMetadata.get(sampleName);
            assertNotNull("No timeline metadata for " + sampleName, metadata);

            String provided = PageFlowUtil.mapFromQueryString(metadata).get(PROVIDED_AMOUNT_KEY);
            assertNotNull("No provided amount for " + sampleName + ": " + metadata, provided);

            String[] parts = provided.split(" ");
            assertEquals("Provided amount for " + sampleName, entry.getValue(), Double.parseDouble(parts[0]), 0.0);
            assertEquals("Provided units for " + sampleName, "uL", parts[1]);
        }
    }

    private ExpSampleType createSampleType(String name) throws Exception
    {
        List<GWTPropertyDescriptor> props = List.of(new GWTPropertyDescriptor("name", "string"));
        return SampleTypeService.get().createSampleType(_c, _user, name, null, props, Collections.emptyList(), -1, -1, -1, -1, null, null, null, null, null, "mL");
    }

    private @NotNull QueryUpdateService getUpdateService(ExpSampleType st)
    {
        TableInfo table = QueryService.get().getUserSchema(_user, _c, SamplesSchema.SCHEMA_NAME).getTable(st.getName());
        QueryUpdateService service = table == null ? null : table.getUpdateService();
        if (service == null)
            throw new IllegalArgumentException("No QueryUpdateService found for table " + st.getName());

        return service;
    }
}
