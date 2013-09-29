package org.labkey.api.ldk.table;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DelegatingContainerFilter;
import org.labkey.api.data.SchemaTableInfo;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * This is designed to wrap a DB table which has a true PK (like an auto-incrementing rowid) and enforce a different
 * column as the PK within a container.
 */
public class ContainerScopedTable extends SimpleUserSchema.SimpleTable
{
    String _newPk;

    public ContainerScopedTable(UserSchema us, TableInfo st, String newPk)
    {
        super(us, st);
        _newPk = newPk;
    }

    public SimpleUserSchema.SimpleTable init()
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
            }
        }

        ColumnInfo newKey = getColumn(_newPk);
        assert newKey != null;

        newKey.setKeyField(true);
        return this;
    }

    @Override
    public ContainerFilter getContainerFilter()
    {
        return super.getContainerFilter() instanceof DelegatingContainerFilter ? super.getContainerFilter() : ContainerFilter.CURRENT;
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        return new UpdateSerivce(this);
    }

    private class UpdateSerivce extends SimpleQueryUpdateService
    {
        public UpdateSerivce(SimpleUserSchema.SimpleTable ti)
        {
            super(ti, ti.getRealTable());
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
            if (!keys.containsKey(pkCol.getName()) || keys.get(pkCol.getName()) == null)
            {
                String pseudoKey = (String)keys.get(_newPk);
                if (pseudoKey != null)
                {
                    SimpleFilter filter = new SimpleFilter(FieldKey.fromString(_newPk), pseudoKey);
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
            Object value = row.get(_newPk);
            if (value != null && rowExists(container, value))
                throw new ValidationException("There is already a record where " + _newPk + " equals " + value);

            return super.insertRow(user, container, row);
        }

        @Override
        public List<Map<String,Object>> insertRows(User user, Container container, List<Map<String, Object>> rows, BatchValidationException errors, @Nullable Map<String, Object> extraScriptContext) throws DuplicateKeyException, QueryUpdateServiceException, SQLException
        {
            int idx = 1;
            for (Map<String,Object> row : rows)
            {
                Object value = row.get(_newPk);
                if (value != null && rowExists(container, value))
                {
                    ValidationException vex = new ValidationException("There is already a record where " + _newPk + " equals " + value);
                    vex.setRowNumber(idx);
                    errors.addRowError(vex);
                }

                idx++;
            }

            return super.insertRows(user, container, rows, errors, extraScriptContext);
        }

        @Override
        protected Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, Map<String, Object> oldRow) throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
        {
            Object oldValue = oldRow.get(_newPk);
            Object newValue = row.get(_newPk);

            if (oldRow != null && newValue != null && !oldValue.equals(newValue) && rowExists(container,  newValue))
                throw new ValidationException("There is already a record where " + _newPk + " equals " + newValue);

            return super.updateRow(user, container, row, oldRow);
        }

        private boolean rowExists(Container c, Object value)
        {
            Container target = c.isWorkbook() ? c.getParent() : c;
            SimpleFilter filter = new SimpleFilter(FieldKey.fromString(_newPk), value, CompareType.EQUAL);
            filter.addClause(ContainerFilter.CURRENT.createFilterClause(_rootTable.getSchema(), getContainerFieldKey(), target));
            TableSelector ts = new TableSelector(_rootTable, Collections.singleton(_newPk), filter, null);
            return ts.getRowCount() > 0;
        }
    }
}
