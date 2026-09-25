/*
 * Copyright (c) 2012-2026 LabKey Corporation
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
package org.labkey.api.query;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DatabaseTableType;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TestSchema;
import org.labkey.api.data.UpdateableTableInfo;
import org.labkey.api.dataiterator.DataIterator;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.dataiterator.DataIteratorUtil;
import org.labkey.api.dataiterator.LoggingDataIterator;
import org.labkey.api.dataiterator.MapDataIterator;
import org.labkey.api.dataiterator.SimpleTranslator;
import org.labkey.api.exp.PropertyColumn;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.query.SimpleUserSchema.SimpleTable;
import org.labkey.api.security.User;
import org.labkey.api.settings.OptionalFeatureService;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.TestContext;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.Assume.assumeFalse;
import static org.labkey.api.query.QueryService.USE_ROW_BY_ROW_UPDATE;
import static org.labkey.api.query.QueryUpdateService.ConfigParameters.PreferPKOverObjectUriAsKey;

/**
 * User: kevink
 */
public class SimpleQueryUpdateService extends DefaultQueryUpdateService
{
    public SimpleQueryUpdateService(final SimpleTable queryTable, TableInfo dbTable)
    {
        this(queryTable, dbTable, new SimpleDomainUpdateHelper(queryTable));
    }

    public SimpleQueryUpdateService(final SimpleTable queryTable, TableInfo dbTable, DomainUpdateHelper helper)
    {
        super(queryTable, dbTable, helper);
    }

    @Override
    public int importRows(User user, Container container, DataIteratorBuilder rows, BatchValidationException errors, @Nullable Map<Enum, Object> configParameters, Map<String, Object> extraScriptContext)
    {
        recordDataIteratorUsed(configParameters);
        var count = _importRowsUsingDIB(user, container, rows, null, getDataIteratorContext(errors, InsertOption.IMPORT, configParameters), extraScriptContext);
        afterInsertUpdate(count, errors);
        return count;
    }

    @Override
    public int mergeRows(User user, Container container, DataIteratorBuilder rows, BatchValidationException errors, @Nullable Map<Enum, Object> configParameters, Map<String, Object> extraScriptContext)
    {
        recordDataIteratorUsed(configParameters);
        var count = _importRowsUsingDIB(user, container, rows, null, getDataIteratorContext(errors, InsertOption.MERGE, configParameters), extraScriptContext);
        afterInsertUpdate(count, errors);
        return count;
    }

    @Override
    public List<Map<String, Object>> insertRows(User user, Container container, List<Map<String, Object>> rows, BatchValidationException errors, @Nullable Map<Enum, Object> configParameters, @Nullable Map<String, Object> extraScriptContext) throws DuplicateKeyException, QueryUpdateServiceException, SQLException
    {
        recordDataIteratorUsed(configParameters);
        return super._insertRowsUsingDIB(user, container, rows, getDataIteratorContext(errors, InsertOption.INSERT, configParameters), extraScriptContext);
    }

    protected boolean supportUpdateUsingDIB()
    {
        return true;
    }

    private boolean shouldUpdateUsingDIB(Container container, List<Map<String, Object>> rows, List<Map<String, Object>> oldKeys, @Nullable Map<Enum, Object> configParameters)
    {
        if (oldKeys != null) // could be called by updateChangingKeys
            return false;

        if (OptionalFeatureService.get().isFeatureEnabled(USE_ROW_BY_ROW_UPDATE))
            return false;

        if (!supportUpdateUsingDIB())
            return false;

        if (getQueryTable() == null)
            return false;

        TableInfo table = getQueryTable().getSchemaTableInfo();

        if (table.getTableType() != DatabaseTableType.TABLE || null == table.getMetaDataIdentifier())
            return false;

        if (getQueryTable().hasTriggers(container)) // dib not yet supported for simple tables with triggers
            return false;

        return hasUniformKeys(rows);
    }

    private boolean shouldPreferPKOverObjectUriAsUpdateKey(@NotNull List<Map<String, Object>> rows)
    {
        String objectURIColumnName = getQueryTable().getObjectUriType() == UpdateableTableInfo.ObjectUriType.schemaColumn
                ? getQueryTable().getObjectURIColumnName()
                : "objecturi";

        if (objectURIColumnName != null && getQueryTable().getColumn(objectURIColumnName) != null)
        {
            boolean hasObjectUriValue = false;
            Object objectUri = rows.getFirst().get(objectURIColumnName);
            if (objectUri != null)
                hasObjectUriValue = !StringUtils.isEmpty((String) objectUri);

            return !hasObjectUriValue;
        }

        return true;
    }

    @Override
    public List<Map<String, Object>> updateRows(User user, Container container, List<Map<String, Object>> rows, List<Map<String, Object>> oldKeys,
                                                BatchValidationException errors, @Nullable Map<Enum, Object> configParameters, Map<String, Object> extraScriptContext)
            throws InvalidKeyException, BatchValidationException, QueryUpdateServiceException, SQLException
    {
        if (shouldUpdateUsingDIB(container, rows, oldKeys, configParameters))
        {
            recordDataIteratorUsed(configParameters);
            DataIteratorContext context = getDataIteratorContext(errors, InsertOption.UPDATE, configParameters);
            context.putConfigParameter(PreferPKOverObjectUriAsKey, shouldPreferPKOverObjectUriAsUpdateKey(rows));
            return super._updateRowsUsingDIB(user, container, rows, context, extraScriptContext);
        }

        return super.updateRows(user, container, rows, oldKeys, errors, configParameters, extraScriptContext);
    }

    @Override
    protected SimpleTable getQueryTable()
    {
        return (SimpleTable)super.getQueryTable();
    }

    @Override
    public DataIteratorBuilder createImportDIB(User user, Container container, final DataIteratorBuilder data, DataIteratorContext context)
    {
        // Create object uri if column is present and domain is not empty.
        final ColumnInfo objectUriColumn = getQueryTable().getObjectUriColumn();
        final Domain domain = getQueryTable().getDomain();

        DataIteratorBuilder ret = data;
        if (objectUriColumn != null && domain != null && !domain.getProperties().isEmpty())
        {
            ret = new DataIteratorBuilder()
            {
                @Override
                public DataIterator getDataIterator(DataIteratorContext context)
                {
                    DataIterator it = data.getDataIterator(context);

                    Map<String,Integer> colMap = DataIteratorUtil.createColumnNameMap(it);
                    Integer objectUriIndex = colMap.get(objectUriColumn.getName());
                    SimpleTranslator out = new SimpleTranslator(it, context);
                    for (int i=1 ; i<=it.getColumnCount() ; i++)
                    {
                        if (null != objectUriIndex && i == objectUriIndex)
                            out.addCoalesceColumn(objectUriColumn.getName(), i, ()->getQueryTable().createObjectURI());
                        else
                            out.addColumn(i);
                    }
                    if (null == objectUriIndex)
                        out.addColumn(objectUriColumn, (Supplier)()->getQueryTable().createObjectURI());
                    return LoggingDataIterator.wrap(out);
                }
            };
        }

        return super.createImportDIB(user, container, ret, context);
    }


    // TODO kill DomainUpdateHelper! Should be replaced by DataIterator
    public static class SimpleDomainUpdateHelper implements DomainUpdateHelper
    {
        SimpleTable _queryTable;

        public SimpleDomainUpdateHelper(SimpleTable queryTable)
        {
            _queryTable = queryTable;
        }

        @Override
        public Domain getDomain()
        {
            return _queryTable.getDomain();
        }

        @Override
        public ColumnInfo getObjectUriColumn()
        {
            return _queryTable.getObjectUriColumn();
        }

        @Override
        public String createObjectURI()
        {
            return _queryTable.createObjectURI();
        }

        @Override
        public Iterable<PropertyColumn> getPropertyColumns()
        {
            return _queryTable.getPropertyColumns();
        }

        @Override
        public Container getDomainContainer(Container c)
        {
            return _queryTable.getDomainContainer();
        }

        @Override
        public Container getDomainObjContainer(Container c)
        {
            return c;
        }
    }


    public static class TestCase extends Assert
    {
        private Container _c;
        private User _user;

        /** Records each afterInsertUpdate() call; the 3-arg form delegates to the 2-arg form, so count there. */
        private static class CountingUpdateService extends SimpleQueryUpdateService
        {
            final List<Integer> counts = new ArrayList<>();
            Boolean lastIsUpdate;

            CountingUpdateService(SimpleTable<?> table)
            {
                super(table, table.getRealTable());
            }

            @Override
            protected void afterInsertUpdate(int count, BatchValidationException errors, boolean isUpdate)
            {
                lastIsUpdate = isUpdate;
                super.afterInsertUpdate(count, errors, isUpdate);
            }

            @Override
            protected void afterInsertUpdate(int count, BatchValidationException errors)
            {
                counts.add(count);
            }

            void reset()
            {
                counts.clear();
                lastIsUpdate = null;
            }
        }

        @Before
        public void setUp()
        {
            _c = JunitUtil.getTestContainer();
            _user = TestContext.get().getUser();
            deleteRows();
        }

        @After
        public void tearDown()
        {
            deleteRows();
        }

        private void deleteRows()
        {
            Table.delete(TestSchema.getInstance().getTableInfoTestTable(), SimpleFilter.createContainerFilter(_c));
        }

        private CountingUpdateService createService()
        {
            SimpleUserSchema schema = new SimpleUserSchema(TestSchema.getInstance().getSchemaName(), null, _user, _c, TestSchema.getInstance().getSchema());
            SimpleTable<SimpleUserSchema> table = new SimpleTable<>(schema, TestSchema.getInstance().getTableInfoTestTable(), null).init();
            return new CountingUpdateService(table);
        }

        private static Map<String, Object> row(String text, int intNotNull)
        {
            Map<String, Object> row = new CaseInsensitiveHashMap<>();
            row.put("Text", text);
            row.put("IntNotNull", intNotNull);
            row.put("DatetimeNotNull", new Date());
            row.put("BitNotNull", true);
            return row;
        }

        @Test
        public void testAfterInsertUpdateFiresOnce() throws Exception
        {
            assumeFalse("Row-by-row update bypasses the DIB update path", OptionalFeatureService.get().isFeatureEnabled(USE_ROW_BY_ROW_UPDATE));
            CountingUpdateService qus = createService();

            BatchValidationException errors = new BatchValidationException();
            List<Map<String, Object>> inserted = qus.insertRows(_user, _c, List.of(row("one", 1), row("two", 2)), errors, null, null);
            assertFalse(errors.getMessage(), errors.hasErrors());
            assertEquals(List.of(2), qus.counts);
            assertEquals(Boolean.FALSE, qus.lastIsUpdate);

            List<Map<String, Object>> updates = new ArrayList<>();
            for (Map<String, Object> r : inserted)
            {
                Map<String, Object> update = row(r.get("Text") + "-updated", 10);
                update.put("RowId", r.get("RowId"));
                updates.add(update);
            }
            // otherwise updateRows() takes the row-by-row path, which never double-fired
            assertTrue(((SimpleQueryUpdateService) qus).shouldUpdateUsingDIB(_c, updates, null, null));
            qus.reset();
            qus.updateRows(_user, _c, updates, null, errors, null, null);
            assertFalse(errors.getMessage(), errors.hasErrors());
            assertEquals(List.of(2), qus.counts);
            assertEquals(Boolean.TRUE, qus.lastIsUpdate);

            qus.reset();
            int imported = qus.importRows(_user, _c, MapDataIterator.of(List.of(row("three", 3))), errors, null, null);
            assertFalse(errors.getMessage(), errors.hasErrors());
            assertEquals(1, imported);
            assertEquals(List.of(1), qus.counts);
        }
    }
}
