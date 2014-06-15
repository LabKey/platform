/*
 * Copyright (c) 2010-2014 LabKey Corporation
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

package org.labkey.experiment.api;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.*;
import org.labkey.api.exp.query.ExpRunGroupMapTable;
import org.labkey.api.query.AbstractQueryUpdateService;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.UserIdQueryForeignKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.util.Pair;
import org.labkey.api.view.UnauthorizedException;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ExpRunGroupMapTableImpl extends ExpTableImpl<ExpRunGroupMapTable.Column> implements ExpRunGroupMapTable
{
    public ExpRunGroupMapTableImpl(String name, UserSchema schema)
    {
        super(name, ExperimentServiceImpl.get().getTinfoRunList(), schema, null);
        addAllowablePermission(InsertPermission.class);
        addAllowablePermission(DeletePermission.class);
    }

    @Override
    public ColumnInfo createColumn(String alias, Column column)
    {
        switch (column)
        {
            case RunGroup:
                ColumnInfo experimentId = wrapColumn(alias, _rootTable.getColumn("ExperimentId"));
                experimentId.setFk(getExpSchema().getRunGroupIdForeignKey(true));
                return experimentId;

            case Run:
                ColumnInfo experimentRunId = wrapColumn(alias, _rootTable.getColumn("ExperimentRunId"));
                experimentRunId.setFk(getExpSchema().getRunIdForeignKey());
                return experimentRunId;

            case Created:
                return wrapColumn(alias, _rootTable.getColumn("Created"));

            case CreatedBy:
                ColumnInfo createdBy = wrapColumn(alias, _rootTable.getColumn("CreatedBy"));
                createdBy.setFk(new UserIdQueryForeignKey(_userSchema.getUser(),_userSchema.getContainer(), true));
                return createdBy;

            default:
                throw new IllegalArgumentException("Unknown column " + column);
        }
    }

    @Override
    public void populate()
    {
        addColumn(Column.RunGroup);
        addColumn(Column.Run);
        addColumn(Column.Created);
        addColumn(Column.CreatedBy);

        List<FieldKey> defaultVisibleColumns = new ArrayList<>();
        defaultVisibleColumns.add(FieldKey.fromParts(Column.RunGroup));
        defaultVisibleColumns.add(FieldKey.fromParts(Column.Run));
        setDefaultVisibleColumns(defaultVisibleColumns);
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        return new RunGroupMapUpdateService(this);
    }

    @NotNull
    @Override
    public SQLFragment getFromSQL(String alias)
    {
        SQLFragment ret = new SQLFragment("(SELECT * FROM (SELECT rl.* FROM ");
        ret.append(ExperimentServiceImpl.get().getTinfoRunList(), "rl");
        ret.append(", ");
        ret.append(ExperimentServiceImpl.get().getTinfoExperimentRun(), "er");
        ret.append(", ");
        ret.append(ExperimentServiceImpl.get().getTinfoExperiment(), "e");
        // Filter out hidden run groups
        ret.append(" WHERE e.Hidden = ? AND rl.ExperimentId = e.RowId AND rl.ExperimentRunId = er.RowId AND ");
        ret.add(false);
        ret.append(getContainerFilter().getSQLFragment(ExperimentServiceImpl.get().getSchema(), new SQLFragment("er.Container"), getExpSchema().getContainer()));
        ret.append(") X ");
        Map<FieldKey, ColumnInfo> columnMap = Table.createColumnMap(getFromTable(), getFromTable().getColumns());
        SQLFragment filterFrag = getFilter().getSQLFragment(_rootTable.getSqlDialect(), columnMap);
        ret.append("\n").append(filterFrag).append(") ").append(alias);

        return ret;
    }

    /**
     * Use the Run's Container to filter the RunList table.
     */
    @Override
    protected void applyContainerFilter(ContainerFilter filter)
    {
        // Handled inside of getFromSQL() for performance reasons
    }


    private class RunGroupMapUpdateService extends AbstractQueryUpdateService
    {
        protected RunGroupMapUpdateService(ExpRunGroupMapTableImpl table)
        {
            super(table);
        }

        /**
         * @return RunId is the first value, RunGroupId is the second
         */
        private Pair<Integer, Integer> getIds(Map<String, Object> values) throws InvalidKeyException
        {
            Integer runId = ConvertHelper.convert(values.get(Column.Run.toString()), Integer.class);
            Integer runGroupId = ConvertHelper.convert(values.get(Column.RunGroup.toString()), Integer.class);

            if (runId == null)
            {
                throw new InvalidKeyException("No value specified for column '" + Column.Run + "'");
            }
            if (runGroupId == null)
            {
                throw new InvalidKeyException("No value specified for column '" + Column.RunGroup + "'");
            }

            return new Pair<>(runId, runGroupId);
        }

        private Pair<ExpRunImpl, ExpExperimentImpl> getObjects(Map<String, Object> values) throws InvalidKeyException, QueryUpdateServiceException
        {
            Pair<Integer, Integer> ids = getIds(values);
            ExpRunImpl run = ExperimentServiceImpl.get().getExpRun(ids.first);
            if (run == null)
            {
                throw new QueryUpdateServiceException("No such run: " + ids.first);
            }
            ExpExperimentImpl runGroup = ExperimentServiceImpl.get().getExpExperiment(ids.second);
            if (runGroup == null)
            {
                throw new QueryUpdateServiceException("No such run group: " + ids.second);
            }
            if (!runGroup.getContainer().hasPermission(getExpSchema().getUser(), ReadPermission.class))
            {
                throw new UnauthorizedException();
            }
            return new Pair<>(run, runGroup);
        }

        @Override
        protected Map<String, Object> getRow(User user, Container container, Map<String, Object> keys) throws InvalidKeyException, QueryUpdateServiceException, SQLException
        {
            Pair<Integer, Integer> ids = getIds(keys);
            SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("ExperimentId"), ids.second);
            filter.addCondition(FieldKey.fromParts("ExperimentRunId"), ids.first);
            Map<String, Object> row = new TableSelector(ExperimentServiceImpl.get().getTinfoRunList(), filter, null).getMap();
            if (row == null)
            {
                return null;
            }
            Map<String, Object> result = new CaseInsensitiveHashMap<>();
            result.put(Column.Run.toString(), ids.first);
            result.put(Column.RunGroup.toString(), ids.second);
            result.put(Column.Created.toString(), row.get(Column.Created.toString()));
            result.put(Column.CreatedBy.toString(), row.get(Column.CreatedBy.toString()));
            return result;
        }

        @Override
        protected Map<String, Object> insertRow(User user, Container container, Map<String, Object> row) throws DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException
        {
            try
            {
                Pair<ExpRunImpl, ExpExperimentImpl> objects = getObjects(row);
                // Users must have update permission for the run
                if (!objects.first.getContainer().hasPermission(getExpSchema().getUser(), UpdatePermission.class))
                {
                    throw new UnauthorizedException();
                }
                objects.second.addRuns(getExpSchema().getUser(), objects.first);
                return getRow(user, container, row);
            }
            catch (InvalidKeyException e)
            {
                throw new QueryUpdateServiceException(e);
            }
        }

        @Override
        protected Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, @NotNull Map<String, Object> oldRow) throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        protected Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRow) throws QueryUpdateServiceException, InvalidKeyException
        {
            Pair<ExpRunImpl, ExpExperimentImpl> objects = getObjects(oldRow);
                // Users must have update permission for the run
            if (!objects.first.getContainer().hasPermission(getExpSchema().getUser(), UpdatePermission.class))
            {
                throw new UnauthorizedException();
            }
            objects.second.removeRun(getExpSchema().getUser(), objects.first);
            return oldRow;
        }
    }
}
