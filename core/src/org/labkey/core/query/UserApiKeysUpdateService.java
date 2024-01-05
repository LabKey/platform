package org.labkey.core.query;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.view.UnauthorizedException;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class UserApiKeysUpdateService extends DefaultQueryUpdateService
{
    public UserApiKeysUpdateService(TableInfo tableInfo, TableInfo dbTable)
    {
        super(tableInfo, dbTable);
    }

    @Override
    public List<Map<String, Object>> updateRows(User user, Container container, List<Map<String, Object>> rows, List<Map<String, Object>> oldKeys, BatchValidationException errors, @Nullable Map<Enum, Object> configParameters, Map<String, Object> extraScriptContext) throws InvalidKeyException, BatchValidationException, QueryUpdateServiceException, SQLException
    {
        throw new UnsupportedOperationException("Updates are not allowed for table core.UserApiKeys.");
    }

    @Override
    protected Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, @NotNull Map<String, Object> oldRow, boolean allowOwner, boolean retainCreation) throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        throw new UnsupportedOperationException("Updates are not allowed for table core.UserApiKeys.");
    }

    @Override
    protected Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRowMap) throws QueryUpdateServiceException, SQLException, InvalidKeyException
    {
        if (oldRowMap == null)
            return null;

        validateUser(user, oldRowMap);

        // We allow deletion of API keys from any container since they are defined globally,
        // so we skip the container permission check from the base class.
        aliasColumns(_columnMapping, oldRowMap);

        _delete(container, oldRowMap);
        return oldRowMap;
    }

    @Override
    public int importRows(User user, Container container, DataIteratorBuilder rows, BatchValidationException errors, @Nullable Map<Enum, Object> configParameters, Map<String, Object> extraScriptContext)
    {
        throw new UnsupportedOperationException("Import is not allowed for table core.UserApiKeys.");
    }

    @Override
    protected Map<String, Object> insertRow(User user, Container container, Map<String, Object> row) throws DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        // check if user is the same as the user in the row
        validateUser(user, row);
        return super.insertRow(user, container, row);
    }

    @Override
    protected int truncateRows(User user, Container container) throws QueryUpdateServiceException, SQLException
    {
        throw new UnauthorizedException("You do not have permission to delete the selected row.");
    }

    protected void validateUser(User user, Map<String, Object> row)
    {
        Integer createdById = (Integer) row.get("CreatedBy");
        if (createdById == null)
            return;
        if (user.getUserId() != createdById)
            throw new UnauthorizedException("You do not have permission to delete the selected row.");
    }
}
