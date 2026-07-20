/*
 * Copyright (c) 2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
package org.labkey.experiment.api;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;

import java.sql.SQLException;
import java.util.Map;

import static org.labkey.api.util.IntegerUtils.asLong;

public class DataColorTable extends FilteredTable<ExpSchema>
{
    public DataColorTable(ExpSchema schema, ContainerFilter cf)
    {
        super(ExperimentServiceImpl.get().getTinfoDataColors(), schema, cf);
        setName(ExpSchema.TableType.DataColors.name());
        for (ColumnInfo baseColumn : _rootTable.getColumns())
        {
            String name = baseColumn.getName();
            if ("Container".equalsIgnoreCase(name))
                continue;
            var col = addWrapColumn(baseColumn);
            if ("RowId".equalsIgnoreCase(name))
                col.setHidden(true);
        }
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        return getContainer().hasPermission(user, perm == ReadPermission.class ? perm : AdminPermission.class);
    }

    @Override
    public @Nullable QueryUpdateService getUpdateService()
    {
        return new DataColorUpdateService(this);
    }

    private static class DataColorUpdateService extends DefaultQueryUpdateService
    {
        public DataColorUpdateService(FilteredTable table)
        {
            super(table, table.getRealTable());
        }

        private boolean isBlankLabel(Map<String, Object> row, boolean allowMissing)
        {
            if (allowMissing && !row.containsKey("label"))
                return false;
            return StringUtils.isBlank((String) row.get("label"));
        }

        private boolean isDuplicateLabel(String label, Container container, int currentRowId)
        {
            for (DataColor color : DataColorManager.getInstance().getColors(container))
            {
                if (color.getRowId() != currentRowId && color.getLabel().equalsIgnoreCase(label))
                    return true;
            }
            return false;
        }

        private long getColorCount(Container container)
        {
            SimpleFilter filter = SimpleFilter.createContainerFilter(container);
            return new TableSelector(ExperimentServiceImpl.get().getTinfoDataColors(), filter, null).getRowCount();
        }

        @Override
        protected Map<String, Object> insertRow(User user, Container container, Map<String, Object> row) throws DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException
        {
            if (isBlankLabel(row, false))
                throw new QueryUpdateServiceException("Label cannot be blank.");
            if (isDuplicateLabel(String.valueOf(row.get("label")), container, -1))
                throw new QueryUpdateServiceException("Label '" + row.get("label") + "' is already in use.");
            if (getColorCount(container) >= DataColorManager.MAX_DATA_COLORS)
                throw new QueryUpdateServiceException("Cannot add more than " + DataColorManager.MAX_DATA_COLORS + " colors.");

            Map<String, Object> inserted;
            try (DbScope.Transaction tx = ExperimentServiceImpl.getExpSchema().getScope().ensureTransaction())
            {
                inserted = super.insertRow(user, container, row);
                tx.addCommitTask(() -> DataColorManager.getInstance().clearCache(container), DbScope.CommitTaskOption.IMMEDIATE, DbScope.CommitTaskOption.POSTCOMMIT);
                tx.commit();
            }
            return inserted;
        }

        @Override
        protected Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, @NotNull Map<String, Object> oldRow, boolean allowOwner, boolean retainCreation) throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
        {
            if (isBlankLabel(row, true))
                throw new QueryUpdateServiceException("Label cannot be blank.");
            int rowId = (int) row.get("rowId");
            if (row.containsKey("label") && isDuplicateLabel(String.valueOf(row.get("label")), container, rowId))
                throw new QueryUpdateServiceException("Label '" + row.get("label") + "' is already in use.");

            Map<String, Object> updated;
            try (DbScope.Transaction tx = ExperimentServiceImpl.getExpSchema().getScope().ensureTransaction())
            {
                updated = super.updateRow(user, container, row, oldRow, allowOwner, retainCreation);
                tx.addCommitTask(() -> DataColorManager.getInstance().clearCache(container), DbScope.CommitTaskOption.IMMEDIATE, DbScope.CommitTaskOption.POSTCOMMIT);
                tx.commit();
            }
            return updated;
        }

        @Override
        protected Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRowMap) throws InvalidKeyException, QueryUpdateServiceException, SQLException
        {
            long rowId = asLong(oldRowMap.get("rowId"));
            if (DataColorManager.getInstance().isInUse(container, rowId))
                throw new QueryUpdateServiceException("This color can't be deleted because it is in use.");

            Map<String, Object> deleted;
            try (DbScope.Transaction tx = ExperimentServiceImpl.getExpSchema().getScope().ensureTransaction())
            {
                deleted = super.deleteRow(user, container, oldRowMap);
                // Also drop any per-data-type exclusion rows that reference this color.
                ExperimentServiceImpl.get().removeDataColorExclusionsForColor(rowId);
                tx.addCommitTask(() -> DataColorManager.getInstance().clearCache(container), DbScope.CommitTaskOption.IMMEDIATE, DbScope.CommitTaskOption.POSTCOMMIT);
                tx.commit();
            }
            return deleted;
        }
    }
}
