package org.labkey.api.data;

import org.apache.commons.beanutils.ConversionException;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.etl.DataIteratorBuilder;
import org.labkey.api.etl.DataIteratorContext;
import org.labkey.api.etl.StandardETL;
import org.labkey.api.query.AbstractQueryUpdateService;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.SimpleQueryUpdateService;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.Permission;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: kevink
 * Date: 5/18/13
 */
//public class ExtendedTableUpdateService extends DefaultQueryUpdateService
public class ExtendedTableUpdateService extends SimpleQueryUpdateService
{
    private final AbstractQueryUpdateService _baseTableUpdateService;

    public ExtendedTableUpdateService(SimpleUserSchema.SimpleTable queryTable, TableInfo dbTable, AbstractQueryUpdateService baseQUS)
    {
        super(queryTable, dbTable);
        _baseTableUpdateService = baseQUS;
    }

    protected AbstractQueryUpdateService getBaseTableUpdateService()
    {
        return _baseTableUpdateService;
    }

    @Override
    protected Map<String, Object> _select(Container container, Object[] keys) throws SQLException, ConversionException
    {
        return super._select(container, keys);
    }

    @Override
    public List<Map<String, Object>> insertRows(User user, Container container, List<Map<String, Object>> rows, BatchValidationException errors, Map<String, Object> extraScriptContext) throws DuplicateKeyException, QueryUpdateServiceException, SQLException
    {
        return super.insertRows(user, container, rows, errors, extraScriptContext);
    }

    @Override
    protected Map<String, Object> _insert(User user, Container c, Map<String, Object> row) throws SQLException, ValidationException
    {
        //Map<String, Object> baseRow = _baseTableUpdateService.insertRows(user, c, Collections.singletonList(row));

        return super._insert(user, c, row);
    }

    @Override
    protected Map<String, Object> _update(User user, Container c, Map<String, Object> row, Map<String, Object> oldRow, Object[] keys) throws SQLException, ValidationException
    {
        return super._update(user, c, row, oldRow, keys);
    }

    @Override
    protected void _delete(Container c, Map<String, Object> row) throws SQLException, InvalidKeyException
    {
        super._delete(c, row);
    }


    @Override
    public DataIteratorBuilder createImportETL(User user, Container container, DataIteratorBuilder data, DataIteratorContext context)
    {
//        DataIteratorBuilder baseETL = _baseTableUpdateService.createImportETL(user, container, data, context);
//
//        DataIteratorBuilder thisETL = super.createImportETL(user, container, data, context);
//
//        DataIteratorBuilder
//        return etl;

        return super.createImportETL(user, container, data, context);
    }
}
