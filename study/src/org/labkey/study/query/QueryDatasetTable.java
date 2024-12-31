package org.labkey.study.query;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.Pair;
import org.labkey.study.model.DatasetDefinition;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class QueryDatasetTable extends DatasetTableImpl
{
    QueryDatasetTable(@NotNull StudyQuerySchema schema, ContainerFilter cf, @NotNull DatasetDefinition dsd)
    {
        super(schema, cf, dsd);

        setUpdateURL(null);
        setInsertURL(null);
        setImportURL(null);
        setDeleteURL(null);

        MutableColumnInfo ci = getMutableColumn("_key");
        if (ci != null)
        {
            ci.setHidden(true);
        }
    }

    @Override
    public @NotNull Map<String, Pair<IndexType, List<ColumnInfo>>> getUniqueIndices()
    {
        return Collections.emptyMap();
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        User user = _userSchema.getUser();
        if (!user.hasRootAdminPermission() && !hasPermission(user, ReadPermission.class))
            return null;
        return new QueryDatasetUpdateService(this);
    }

    private static class QueryDatasetUpdateService extends DatasetUpdateService
    {
        public QueryDatasetUpdateService(DatasetTableImpl table)
        {
            super(table);
        }

        @Override
        public int mergeRows(User user, Container container, DataIteratorBuilder rows, BatchValidationException errors, @Nullable Map<Enum, Object> configParameters, Map<String, Object> extraScriptContext)
        {
            throw new UnsupportedOperationException("Query datasets do not support importing rows.");
        }

        @Override
        public int loadRows(User user, Container container, DataIteratorBuilder rows, DataIteratorContext context, @Nullable Map<String, Object> extraScriptContext)
        {
            throw new UnsupportedOperationException("Query datasets do not support importing rows.");
        }

        @Override
        public int importRows(User user, Container container, DataIteratorBuilder rows, BatchValidationException errors, Map<Enum, Object> configParameters, Map<String, Object> extraScriptContext)
        {
            throw new UnsupportedOperationException("Query datasets do not support importing rows.");
        }

        @Override
        public List<Map<String, Object>> insertRows(User user, Container container, List<Map<String, Object>> rows, BatchValidationException errors, @Nullable Map<Enum, Object> configParameters, Map<String, Object> extraScriptContext) throws QueryUpdateServiceException
        {
            throw new UnsupportedOperationException("Query datasets do not support inserting rows.");
        }

        @Override
        public List<Map<String, Object>> updateRows(User user, Container container, List<Map<String, Object>> rows, List<Map<String, Object>> oldKeys, BatchValidationException errors, @Nullable Map<Enum, Object> configParameters, Map<String, Object> extraScriptContext) throws InvalidKeyException, BatchValidationException, QueryUpdateServiceException, SQLException
        {
            throw new UnsupportedOperationException("Query datasets do not support updating rows.");
        }

        @Override
        protected Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, @NotNull Map<String, Object> oldRow, @Nullable Map<Enum, Object> configParameters) throws InvalidKeyException, ValidationException, QueryUpdateServiceException
        {
            throw new UnsupportedOperationException("Query datasets do not support updating rows.");
        }

        @Override
        public List<Map<String, Object>> deleteRows(User user, Container container, List<Map<String, Object>> keys, @Nullable Map<Enum, Object> configParameters, @Nullable Map<String, Object> extraScriptContext) throws InvalidKeyException, BatchValidationException, QueryUpdateServiceException, SQLException
        {
            throw new UnsupportedOperationException("Query datasets do not support deleting rows.");
        }

        @Override
        protected Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRow) throws InvalidKeyException, QueryUpdateServiceException, ValidationException
        {
            throw new UnsupportedOperationException("Query datasets do not support deleting rows.");
        }

        @Override
        protected int truncateRows(User user, Container container)
        {
            throw new UnsupportedOperationException("Query datasets do not support deleting rows.");
        }

        @Override
        public int truncateRows(User user, Container container, @Nullable Map<Enum, Object> configParameters, @Nullable Map<String, Object> extraScriptContext) throws BatchValidationException, QueryUpdateServiceException, SQLException
        {
            throw new UnsupportedOperationException("Query datasets do not support deleting rows.");
        }
    }
}
