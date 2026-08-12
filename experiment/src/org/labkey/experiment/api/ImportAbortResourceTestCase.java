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
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.dataiterator.DataIterator;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.dataiterator.MapDataIterator;
import org.labkey.api.dataiterator.WrapperDataIterator;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateService.InsertOption;
import org.labkey.api.security.User;
import org.labkey.api.settings.OptionalFeatureService;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.TestContext;
import org.labkey.experiment.ExpDataIterators;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A DataIteratorBuilder that bails out after its input has been built must close that input. The input is
 * frequently a query-backed iterator holding a live ResultSet, its Statement, and a pooled Connection; dropping
 * it strands all three until the ResultSetImpl Cleaner runs at GC, which on a short-scheduled ETL can exhaust
 * the pool. Asserting on close() rather than on pool counts keeps this deterministic - no GC, no timing.
 */
public class ImportAbortResourceTestCase extends Assert
{
    private static Container _c;
    private static User _user;
    private static boolean _restoreAllowRowIdMerge;

    @BeforeClass
    public static void setUp()
    {
        JunitUtil.deleteTestContainer();
        _c = JunitUtil.getTestContainer();
        _user = TestContext.get().getUser();

        // These tests drive the RowId-on-merge rejection, so the server-wide opt-out must be off.
        _restoreAllowRowIdMerge = OptionalFeatureService.get().isFeatureEnabled(ExperimentService.EXPERIMENTAL_FEATURE_ALLOW_ROW_ID_MERGE);
        if (_restoreAllowRowIdMerge)
            OptionalFeatureService.get().setFeatureEnabled(ExperimentService.EXPERIMENTAL_FEATURE_ALLOW_ROW_ID_MERGE, false, _user);
    }

    @AfterClass
    public static void tearDown()
    {
        if (_restoreAllowRowIdMerge)
            OptionalFeatureService.get().setFeatureEnabled(ExperimentService.EXPERIMENTAL_FEATURE_ALLOW_ROW_ID_MERGE, true, _user);
        JunitUtil.deleteTestContainer();
    }

    /** A row source that records every iterator it hands out, so an unclosed one is a deterministic failure. */
    private static class RecordingSource implements DataIteratorBuilder
    {
        private final DataIteratorBuilder _rows;
        private final List<CloseRecordingDataIterator> _built = new ArrayList<>();

        RecordingSource(List<Map<String, Object>> rows)
        {
            _rows = MapDataIterator.of(rows);
        }

        @Override
        public DataIterator getDataIterator(DataIteratorContext context)
        {
            CloseRecordingDataIterator di = new CloseRecordingDataIterator(_rows.getDataIterator(context));
            _built.add(di);
            return di;
        }

        private static class CloseRecordingDataIterator extends WrapperDataIterator
        {
            private boolean _closed = false;

            CloseRecordingDataIterator(DataIterator di)
            {
                super(di);
            }

            @Override
            public void close() throws IOException
            {
                _closed = true;
                super.close();
            }
        }
    }

    private void assertAllSourcesClosed(RecordingSource source)
    {
        assertFalse("source iterator was never built, so this test proves nothing", source._built.isEmpty());
        for (RecordingSource.CloseRecordingDataIterator di : source._built)
            assertTrue("abandoned source DataIterator was left open", di._closed);
    }

    /** Runs a merge expected to abort on a validation error, then asserts the source was closed. */
    private void assertMergeAbortClosesSource(TableInfo table, List<Map<String, Object>> rows, String expectedError) throws Exception
    {
        RecordingSource source = new RecordingSource(rows);
        DataIteratorContext context = new DataIteratorContext();
        context.setInsertOption(InsertOption.MERGE);

        QueryUpdateService qus = table.getUpdateService();
        assertNotNull("no QueryUpdateService for " + table.getName(), qus);

        // loadRows returns 0 whenever the context has errors, so it says nothing about what was written - count the table.
        assertEquals("import reported inserted rows", 0, qus.loadRows(_user, _c, source, context, null));
        assertTrue("expected a validation error", context.getErrors().hasErrors());
        assertTrue("unexpected error: " + context.getErrors().getMessage(),
                context.getErrors().getMessage().contains(expectedError));
        assertEquals("expected the import to abort without inserting", 0L, new TableSelector(table).getRowCount());
        assertAllSourcesClosed(source);
    }

    private TableInfo createDataClassTable(String name) throws Exception
    {
        List<GWTPropertyDescriptor> props = List.of(new GWTPropertyDescriptor("prop", "string"));
        ExperimentServiceImpl.get().createDataClass(_c, _user, name, null, props, Collections.emptyList(), null, null);
        TableInfo table = QueryService.get().getUserSchema(_user, _c, ExpSchema.SCHEMA_EXP_DATA).getTable(name);
        assertNotNull("could not resolve data class table " + name, table);
        return table;
    }

    private TableInfo createSampleTypeTable(String name) throws Exception
    {
        List<GWTPropertyDescriptor> props = List.of(
                new GWTPropertyDescriptor("name", "string"),
                new GWTPropertyDescriptor("prop", "string"));
        ExpSampleType st = SampleTypeService.get().createSampleType(_c, _user, name, null, props, Collections.emptyList(), -1, -1, -1, -1, null);
        TableInfo table = QueryService.get().getUserSchema(_user, _c, SamplesSchema.SCHEMA_NAME).getTable(st.getName());
        assertNotNull("could not resolve sample type table " + name, table);
        return table;
    }

    @Test
    public void testDataClassRowIdOnMergeClosesSource() throws Exception
    {
        TableInfo table = createDataClassTable("AbortRowIdMerge");
        List<Map<String, Object>> rows = List.of(CaseInsensitiveHashMap.of("Name", "D-1", "RowId", 1, "prop", "a"));
        assertMergeAbortClosesSource(table, rows, "RowId is not accepted when merging data");
    }

    @Test
    public void testDataClassLsidOnlyKeyOnMergeClosesSource() throws Exception
    {
        TableInfo table = createDataClassTable("AbortLsidMerge");
        // LSID as the only key column is rejected; Name and RowId are deliberately absent.
        List<Map<String, Object>> rows = List.of(CaseInsensitiveHashMap.of("LSID", "urn:lsid:labkey.com:Data.Folder-1:abort", "prop", "a"));
        assertMergeAbortClosesSource(table, rows, "LSID is no longer accepted as a key for data");
    }

    @Test
    public void testSampleTypeRowIdOnMergeClosesSource() throws Exception
    {
        TableInfo table = createSampleTypeTable("AbortRowIdMergeSamples");
        List<Map<String, Object>> rows = List.of(CaseInsensitiveHashMap.of("Name", "S-1", "RowId", 1, "prop", "a"));
        assertMergeAbortClosesSource(table, rows, "RowId is not accepted when merging samples");
    }

    @Test
    public void testSampleTypeLsidOnlyKeyOnMergeClosesSource() throws Exception
    {
        TableInfo table = createSampleTypeTable("AbortLsidMergeSamples");
        List<Map<String, Object>> rows = List.of(CaseInsensitiveHashMap.of("LSID", "urn:lsid:labkey.com:Sample.Folder-1:abort", "prop", "a"));
        assertMergeAbortClosesSource(table, rows, "LSID is no longer accepted as a key for sample");
    }

    /**
     * The other half of the contract: a builder whose iterator constructor throws must also close its input.
     * AliasDataIteratorBuilder stands in for every ExpDataIterators builder routed through DataIteratorUtil.wrapOrClose.
     */
    @Test
    public void testBuilderThrowClosesSource()
    {
        RecordingSource source = new RecordingSource(List.of(CaseInsensitiveHashMap.of("Name", "D-1", "prop", "a")));
        DataIteratorContext context = new DataIteratorContext();
        context.setInsertOption(InsertOption.UPDATE);

        // A map-backed source doesn't support getExistingRecord(), which the AliasDataIterator ctor rejects on update.
        DataIteratorBuilder builder = new ExpDataIterators.AliasDataIteratorBuilder(source, _c, _user, null, null, false);
        assertThrows(IllegalArgumentException.class, () -> builder.getDataIterator(context));
        assertAllSourcesClosed(source);
    }
}
