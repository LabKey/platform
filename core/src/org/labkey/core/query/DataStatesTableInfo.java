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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.qc.DataState;
import org.labkey.api.qc.DataStateHandler;
import org.labkey.api.qc.DataStateManager;
import org.labkey.api.qc.SampleStatusService;
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
            if ("StateType".equalsIgnoreCase(name))
            {
                boolean enabledStatus = SampleStatusService.get().supportsSampleStatus();
                wrappedColumn.setHidden(!enabledStatus);
                wrappedColumn.setShownInDetailsView(enabledStatus);
                // always exclude this column from insert and update as we want users to use the manageSampleStatuses page
                wrappedColumn.setShownInInsertView(false);
                wrappedColumn.setShownInUpdateView(false);
            }
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
            Map<String, DataStateHandler> registeredHandlers = DataStateManager.getInstance().getRegisteredDataHandlers();
            DataState QCToDelete = DataStateManager.getInstance().getStateForRowId(container, (Integer) oldRowMap.get("rowid"));

            for (DataStateHandler handler : registeredHandlers.values())
            {
                if (handler.isStateInUse(container, QCToDelete))
                    return false;
            }
            return true;
        }

        private String validateQCStateChangeAllowed(Map<String, Object> row, Container container)
        {
            Map<String, DataStateHandler> registeredHandlers = DataStateManager.getInstance().getRegisteredDataHandlers();
            DataState qcChanging = DataStateManager.getInstance().getStateForRowId(container, (Integer) row.get("rowid"));

            for (DataStateHandler handler : registeredHandlers.values())
            {
                String errorMsg = handler.getStateChangeError(container, qcChanging, row);
                if (errorMsg != null)
                    return errorMsg;
            }
            return null;
        }

        private boolean validateLabel(Map<String, Object> row, boolean allowMissing)
        {
            String label = (String) row.get("label");
            return (allowMissing && !row.containsKey("label")) || !StringUtils.isBlank(label);
        }

        @Override
        protected Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, @NotNull Map<String, Object> oldRow, boolean allowOwner, boolean retainCreation) throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
        {
            if (!validateLabel(row, true))
                throw new QueryUpdateServiceException("Label cannot be blank.");

            String errorMsg = validateQCStateChangeAllowed(row, container);
            if (errorMsg != null)
                throw new QueryUpdateServiceException(errorMsg);

            Map<String, Object> rowToUpdate;
            try (DbScope.Transaction transaction = CoreSchema.getInstance().getSchema().getScope().ensureTransaction())
            {
                rowToUpdate = super.updateRow(user, container, row, oldRow, allowOwner, retainCreation);
                transaction.addCommitTask(() -> DataStateManager.getInstance().clearCache(container), DbScope.CommitTaskOption.IMMEDIATE, DbScope.CommitTaskOption.POSTCOMMIT);
                transaction.commit();
            }
            return rowToUpdate;
        }

        @Override
        protected Map<String, Object> insertRow(User user, Container container, Map<String, Object> row) throws DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException
        {
            if (!validateLabel(row, false))
                throw new QueryUpdateServiceException("Label cannot be blank.");

            Map<String, Object> rowToInsert;
            try (DbScope.Transaction transaction = CoreSchema.getInstance().getSchema().getScope().ensureTransaction())
            {
                rowToInsert = super.insertRow(user, container, row);
                transaction.addCommitTask(() -> DataStateManager.getInstance().clearCache(container), DbScope.CommitTaskOption.IMMEDIATE, DbScope.CommitTaskOption.POSTCOMMIT);
                transaction.commit();
            }
            return rowToInsert;
        }

        @Override
        protected Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRowMap) throws InvalidKeyException, QueryUpdateServiceException, SQLException
        {
            if (!validateQCStateNotInUse(oldRowMap, container))
                throw new QueryUpdateServiceException("State '" + oldRowMap.get("label") + "' cannot be deleted as it is currently in use.");

            Map<String, Object> rowToDelete;
            try (DbScope.Transaction transaction = CoreSchema.getInstance().getSchema().getScope().ensureTransaction())
            {
                rowToDelete = super.deleteRow(user, container, oldRowMap);
                transaction.addCommitTask(() -> DataStateManager.getInstance().clearCache(container), DbScope.CommitTaskOption.IMMEDIATE, DbScope.CommitTaskOption.POSTCOMMIT);
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
