/*
 * Copyright (c) 2017-2019 LabKey Corporation
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
package org.labkey.core.query;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.qc.QCState;
import org.labkey.api.qc.QCStateHandler;
import org.labkey.api.qc.QCStateManager;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;

import java.sql.SQLException;
import java.util.Map;

/**
 * Created by marty on 7/25/2017.
 */
public class DataStatesTableInfo extends FilteredTable<CoreQuerySchema>
{
    public DataStatesTableInfo(CoreQuerySchema schema)
    {
        super(CoreSchema.getInstance().getTableInfoDataStates(), schema);
        for (ColumnInfo baseColumn : _rootTable.getColumns())
        {
            String name = baseColumn.getName();
            if ("Container".equalsIgnoreCase(name))
                continue;
            var wrappedColumn = addWrapColumn(baseColumn);
            if ("RowId".equalsIgnoreCase(name))
                wrappedColumn.setHidden(true);
        }
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        return getContainer().hasPermission(user, perm);
    }

    static class DataStatesService extends DefaultQueryUpdateService
    {
        public DataStatesService(FilteredTable table) { super(table, table.getRealTable()); }

        private boolean validateQCStateNotInUse(Map<String, Object> oldRowMap, Container container)
        {
            Map<String, QCStateHandler> registeredHandlers = QCStateManager.getInstance().getRegisteredQCHandlers();
            QCState QCToDelete = QCStateManager.getInstance().getQCStateForRowId(container, (Integer) oldRowMap.get("rowid"));

            for (QCStateHandler handler : registeredHandlers.values())
            {
                if (handler.isQCStateInUse(container, QCToDelete))
                    return false;
            }
            return true;
        }

        @Override
        protected Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, @NotNull Map<String, Object> oldRow, boolean allowOwner, boolean retainCreation) throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
        {
            Map<String, Object> rowToUpdate;
            try (DbScope.Transaction transaction = CoreSchema.getInstance().getSchema().getScope().ensureTransaction())
            {
                rowToUpdate = super.updateRow(user, container, row, oldRow, allowOwner, retainCreation);
                transaction.addCommitTask(() -> QCStateManager.getInstance().clearCache(container), DbScope.CommitTaskOption.IMMEDIATE, DbScope.CommitTaskOption.POSTCOMMIT);
                transaction.commit();
            }
            return rowToUpdate;
        }

        @Override
        protected Map<String, Object> insertRow(User user, Container container, Map<String, Object> row) throws DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException
        {
            Map<String, Object> rowToInsert;
            try (DbScope.Transaction transaction = CoreSchema.getInstance().getSchema().getScope().ensureTransaction())
            {
                rowToInsert = super.insertRow(user, container, row);
                transaction.addCommitTask(() -> QCStateManager.getInstance().clearCache(container), DbScope.CommitTaskOption.IMMEDIATE, DbScope.CommitTaskOption.POSTCOMMIT);
                transaction.commit();
            }
            return rowToInsert;
        }

        @Override
        protected Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRowMap) throws InvalidKeyException, QueryUpdateServiceException, SQLException
        {
            if (!validateQCStateNotInUse(oldRowMap, container))
                throw new QueryUpdateServiceException("QC state '" + oldRowMap.get("label") + "' cannot be deleted as it is currently in use.");

            Map<String, Object> rowToDelete;
            try (DbScope.Transaction transaction = CoreSchema.getInstance().getSchema().getScope().ensureTransaction())
            {
                rowToDelete = super.deleteRow(user, container, oldRowMap);
                transaction.addCommitTask(() -> QCStateManager.getInstance().clearCache(container), DbScope.CommitTaskOption.IMMEDIATE, DbScope.CommitTaskOption.POSTCOMMIT);
                transaction.commit();
            }
            return rowToDelete;
        }
    }

    @Override
    public @Nullable QueryUpdateService getUpdateService()
    {
        return new DataStatesService(this);
    }
}
