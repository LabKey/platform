/*
 * Copyright (c) 2013-2017 LabKey Corporation
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
package org.labkey.api.ldk.table;

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ConvertHelper;
import org.labkey.api.data.DelegatingContainerFilter;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.dataiterator.DataIterator;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.dataiterator.LoggingDataIterator;
import org.labkey.api.dataiterator.SimpleTranslator;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.SimpleQueryUpdateService;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;

import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * This is designed to wrap a DB table which has a true PK (like an auto-incrementing rowid) and enforce a different
 * column as the PK within a container.
 */
public class ContainerScopedTable<SchemaType extends UserSchema> extends CustomPermissionsTable<SchemaType>
{
    private String _pseudoPk;
    private Set<String> _realPKs = new HashSet<>();
    private static final Logger _log = Logger.getLogger(ContainerScopedTable.class);

    public ContainerScopedTable(SchemaType schema, TableInfo st, String newPk)
    {
        super(schema, st);
        _pseudoPk = newPk;
    }

    public ContainerScopedTable<SchemaType> init()
    {
        super.init();

        for (String col : getRealTable().getPkColumnNames())
        {
            ColumnInfo existing = getColumn(col);
            if (existing != null)
            {
                existing.setKeyField(false);
                existing.setUserEditable(false);
                existing.setHidden(true);
                _realPKs.add(col);
            }
        }

        ColumnInfo newKey = getColumn(_pseudoPk);
        assert newKey != null;

        newKey.setKeyField(true);
        return this;
    }

    @NotNull
    @Override
    public ContainerFilter getContainerFilter()
    {
        return super.getContainerFilter() instanceof DelegatingContainerFilter ? super.getContainerFilter() : ContainerFilter.CURRENT;
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        return new UpdateService(this);
    }

    @Override
    public DataIteratorBuilder persistRows(DataIteratorBuilder data, DataIteratorContext context)
    {
        data = new ContainerScopedDataIteratorBuilder(data, context);
        return super.persistRows(data, context);
    }

    protected class UpdateService extends SimpleQueryUpdateService
    {
        private KeyManager _keyManager = new KeyManager();

        public UpdateService(SimpleUserSchema.SimpleTable ti)
        {
            super(ti, ti.getRealTable());
        }

        public UpdateService(SimpleUserSchema.SimpleTable simpleTable, TableInfo table, DomainUpdateHelper helper)
        {
            super(simpleTable, table, helper);
        }

        private ColumnInfo getPkCol()
        {
            assert _rootTable.getPkColumnNames().size() == 1;

            return _rootTable.getPkColumns().get(0);
        }

        @Override
        protected Map<String, Object> getRow(User user, Container container, Map<String, Object> keys) throws InvalidKeyException, QueryUpdateServiceException, SQLException
        {
            ColumnInfo pkCol = getPkCol();
            keys = new HashMap<>(keys);  //create copy
            if (!keys.containsKey(pkCol.getName()) || keys.get(pkCol.getName()) == null)
            {
                Object pseudoKey = keys.get(_pseudoPk);
                if (pseudoKey != null)
                {
                    SimpleFilter filter = new SimpleFilter(FieldKey.fromString(_pseudoPk), pseudoKey);
                    filter.addCondition(getContainerFieldKey(), container.getId());

                    TableSelector ts = new TableSelector(getQueryTable(), Collections.singleton(pkCol.getName()), filter, null);
                    Object[] results = ts.getArray(Object.class);
                    if (results.length == 0)
                        throw new InvalidKeyException("Existing row not found for key: " + pseudoKey);
                    else if (results.length > 1)
                        throw new InvalidKeyException("More than one existing row found key: " + pseudoKey);

                    keys.put(pkCol.getName(), results[0]);
                }
            }

            return super.getRow(user, container, keys);
        }

        @Override
        protected Map<String, Object> insertRow(User user, Container container, Map<String, Object> row) throws DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException
        {
            Object value = row.get(_pseudoPk);
            if (value != null && _keyManager.rowExists(container, value))
                throw new ValidationException("There is already a record where " + _pseudoPk + " equals " + value);

            return super.insertRow(user, container, row);
        }

        @Override
        public List<Map<String,Object>> insertRows(User user, Container container, List<Map<String, Object>> rows, BatchValidationException errors, @Nullable Map<Enum, Object> configParameters, @Nullable Map<String, Object> extraScriptContext) throws DuplicateKeyException, QueryUpdateServiceException, SQLException
        {
            int idx = 1;
            for (Map<String,Object> row : rows)
            {
                Object value = row.get(_pseudoPk);
                if (value != null && _keyManager.rowExists(container, value))
                {
                    ValidationException vex = new ValidationException("There is already a record where " + _pseudoPk + " equals " + value);
                    vex.setRowNumber(idx);
                    errors.addRowError(vex);
                }

                idx++;
            }

            return super.insertRows(user, container, rows, errors, configParameters, extraScriptContext);
        }

        @Override
        protected Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, @NotNull Map<String, Object> oldRow) throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
        {
            Object oldValue = oldRow.get(_pseudoPk);
            Object newValue = row.get(_pseudoPk);

            if (oldRow != null && newValue != null && !oldValue.equals(newValue) && _keyManager.rowExists(container,  newValue))
                throw new ValidationException("There is already a record where " + _pseudoPk + " equals " + newValue);

            return super.updateRow(user, container, row, oldRow);
        }
    }

    private class ContainerScopedDataIteratorBuilder implements DataIteratorBuilder
    {
        DataIteratorContext _context;
        final DataIteratorBuilder _in;

        ContainerScopedDataIteratorBuilder(@NotNull DataIteratorBuilder in, DataIteratorContext context)
        {
            _context = context;
            _in = in;
        }

        @Override
        public DataIterator getDataIterator(DataIteratorContext context)
        {
            _context = context;
            DataIterator input = _in.getDataIterator(context);
            if (null == input)
                return null;           // Can happen if context has errors

            final String containerColName = getContainerFilterColumn();
            final KeyManager keyManager = new KeyManager();
            final SimpleTranslator it = new SimpleTranslator(input, context);
            final Map<String, Integer> inputColMap = new HashMap<String, Integer>();
            for (int idx = 1; idx <= input.getColumnCount(); idx++)
            {
                ColumnInfo col = input.getColumnInfo(idx);
                if (StringUtils.equalsIgnoreCase(_pseudoPk, col.getName()))
                {
                    inputColMap.put(_pseudoPk, idx);
                    continue;
                }

                if (StringUtils.equalsIgnoreCase(getContainerFilterColumn(), col.getName()))
                {
                    inputColMap.put(getContainerFilterColumn(), idx);
                }

                it.addColumn(idx);
            }

            //set the value of the RowId column
            ColumnInfo pseudoPkCol = getColumn(_pseudoPk);
            it.addColumn(pseudoPkCol, new Callable<Object>()
            {
                @Override
                public Object call() throws Exception
                {
                    Container c = null;
                    if (inputColMap.containsKey(containerColName))
                    {
                        String containerId = (String)it.getInputColumnValue(inputColMap.get(containerColName));
                        if (containerId != null)
                            c = ContainerManager.getForId(containerId);
                    }

                    if (c == null)
                    {
                        c = getContainer();
                    }

                    assert c != null;

                    if (inputColMap.containsKey(_pseudoPk))
                    {
                        Object pesudoPkVal = it.getInputColumnValue(inputColMap.get(_pseudoPk));
                        if (pesudoPkVal != null)
                        {
                            if (_context.getInsertOption() != QueryUpdateService.InsertOption.MERGE && keyManager.rowExists(c, pesudoPkVal))
                            {
                                _context.getErrors().addRowError(new ValidationException("A record is already present with value: " + pesudoPkVal));
                            }
                        }

                        return pesudoPkVal;
                    }

                    _context.getErrors().addRowError(new ValidationException("Record is missing the keyfield"));
                    return null;
                }
            });

            return LoggingDataIterator.wrap(it);
        }
    }

    private class KeyManager
    {
        private Set<Object> _encounteredKeys = new HashSet<>();

        public KeyManager()
        {

        }

        public boolean rowExists(Container c, Object key)
        {
            ColumnInfo pkCol = getColumn(FieldKey.fromString(_pseudoPk));
            try
            {
                key = ConvertHelper.convert(key, pkCol.getJavaClass());
            }
            catch (ConversionException e)
            {
                _log.error("Unable to convert value for column: " + _pseudoPk + " to class: " + pkCol.getJavaClass() + " for table: " + getPublicSchemaName() + "." + getName(), e);
                return false;
            }

            boolean exists = _encounteredKeys.contains(key);
            _encounteredKeys.add(key);

            if (exists)
            {
                return exists;
            }

            Container target = c.isWorkbook() ? c.getParent() : c;
            SimpleFilter filter = new SimpleFilter(FieldKey.fromString(_pseudoPk), key, CompareType.EQUAL);
            filter.addClause(ContainerFilter.CURRENT.createFilterClause(_rootTable.getSchema(), getContainerFieldKey(), target));
            TableSelector ts = new TableSelector(_rootTable, Collections.singleton(_pseudoPk), filter, null);

            return ts.getRowCount() > 0;
        }
    }
}
