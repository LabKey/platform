package org.labkey.core.query;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.ApiKeyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.UserManagementPermission;
import org.labkey.api.view.UnauthorizedException;

import java.util.List;
import java.util.Map;

import static org.labkey.api.util.IntegerUtils.asInteger;

/**
 * Delete is the only supported operation. Site and application admins can delete any API key; others are restricted to
 * deleting their own.
 */
public class ApiKeysUpdateService extends DefaultQueryUpdateService
{
    public ApiKeysUpdateService(TableInfo tableInfo, TableInfo dbTable)
    {
        super(tableInfo, dbTable);
    }

    @Override
    public List<Map<String, Object>> updateRows(User user, Container container, List<Map<String, Object>> rows, List<Map<String, Object>> oldKeys, BatchValidationException errors, @Nullable Map<Enum, Object> configParameters, Map<String, Object> extraScriptContext)
    {
        throw new UnsupportedOperationException("Updates are not allowed for this table.");
    }

    @Override
    protected Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, @NotNull Map<String, Object> oldRow, boolean allowOwner, boolean retainCreation)
    {
        throw new UnsupportedOperationException("Updates are not allowed for this table.");
    }

    @Override
    public int importRows(User user, Container container, DataIteratorBuilder rows, BatchValidationException errors, @Nullable Map<Enum, Object> configParameters, Map<String, Object> extraScriptContext)
    {
        throw new UnsupportedOperationException("Import is not allowed for this table.");
    }

    @Override
    protected Map<String, Object> insertRow(User user, Container container, Map<String, Object> row)
    {
        throw new UnsupportedOperationException("Insert is not allowed for this table.");
    }

    @Override
    protected int truncateRows(User user, Container container)
    {
        throw new UnsupportedOperationException("Truncation of this table is not supported.");
    }

    @Override
    protected Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRowMap)
    {
        if (oldRowMap == null)
            return null;

        validateDelete(user, oldRowMap);

        // We allow deletion of API keys from any container since they are defined globally,
        // so we skip the container permission check from the base class.
        aliasColumns(_columnMapping, oldRowMap);

        Integer rowId = asInteger(oldRowMap.get("rowId"));
        ApiKeyManager.get().deleteKeys(new SimpleFilter(FieldKey.fromParts("RowId"), rowId));

        return oldRowMap;
    }

    protected void validateDelete(User user, Map<String, Object> row)
    {
        // Site and application admins can delete anyone's API key; others are restricted to deleting their own.
        if (!user.hasRootPermission(UserManagementPermission.class))
        {
            Integer createdById = asInteger(row.get("CreatedBy"));
            if (createdById == null)
                return;
            if (user.getUserId() != createdById)
                throw new UnauthorizedException("You do not have permission to delete the selected row.");
        }
    }
}
