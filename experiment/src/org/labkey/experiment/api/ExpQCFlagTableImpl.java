/*
 * Copyright (c) 2006-2014 LabKey Corporation
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
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.query.ExpQCFlagTable;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.UserIdForeignKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;

import java.util.Map;

public class ExpQCFlagTableImpl extends ExpTableImpl<ExpQCFlagTable.Column> implements ExpQCFlagTable
{
    private ExpProtocol _assayProtocol;
    private Map<String, String> _columnMapping = new CaseInsensitiveHashMap<>();

    public ExpQCFlagTableImpl(String name, UserSchema schema)
    {
        super(name, ExperimentServiceImpl.get().getTinfoAssayQCFlag(), schema, new ExpProtocolApplicationImpl(new ProtocolApplication()));
    }

    public ColumnInfo createColumn(String alias, Column column)
    {
        switch (column)
        {
            case RowId:
                ColumnInfo rowIdColumnInfo = wrapColumn(alias, _rootTable.getColumn("RowId"));
                rowIdColumnInfo.setHidden(true);
                return rowIdColumnInfo;
            case Run:
                _columnMapping.put("RunId", column.name());
                ColumnInfo runColumnInfo = wrapColumn(alias, _rootTable.getColumn("RunId"));
                runColumnInfo.setFk(getExpSchema().getRunIdForeignKey());
                return runColumnInfo;
            case FlagType:
                return wrapColumn(alias, _rootTable.getColumn("FlagType"));
            case Description:
                return wrapColumn(alias, _rootTable.getColumn("Description"));
            case Comment:
                return wrapColumn(alias, _rootTable.getColumn("Comment"));
            case Enabled:
                return wrapColumn(alias, _rootTable.getColumn("Enabled"));
            case Created:
                return wrapColumn(alias, _rootTable.getColumn("Created"));
            case CreatedBy:
                ColumnInfo createdByColumn = wrapColumn(alias, _rootTable.getColumn("CreatedBy"));
                createdByColumn.setFk(new UserIdForeignKey(getUserSchema()));
                return createdByColumn;
            case Modified:
                return wrapColumn(alias, _rootTable.getColumn("Modified"));
            case ModifiedBy:
                ColumnInfo modifiedByColumn = wrapColumn(alias, _rootTable.getColumn("ModifiedBy"));
                modifiedByColumn.setFk(new UserIdForeignKey(getUserSchema()));
                return modifiedByColumn;
            case IntKey1:
                _columnMapping.put(column.name(), alias);
                return wrapColumn(alias, _rootTable.getColumn("IntKey1"));
            case IntKey2:
                _columnMapping.put(column.name(), alias);
                return wrapColumn(alias, _rootTable.getColumn("IntKey2"));
            case Key1:
                _columnMapping.put(column.name(), alias);
                return wrapColumn(alias, _rootTable.getColumn("Key1"));
            case Key2:
                _columnMapping.put(column.name(), alias);
                return wrapColumn(alias, _rootTable.getColumn("Key2"));
        }
        throw new IllegalArgumentException("Unknown column " + column);
    }

    public void populate()
    {
        addColumn(Column.RowId);
        addColumn(Column.Run);
        addColumn(Column.FlagType);
        addColumn(Column.Description);
        setTitleColumn(Column.Description.toString());
        addColumn(Column.Comment);
        addColumn(Column.Enabled);
        addColumn(Column.Created);
        addColumn(Column.CreatedBy);
        addColumn(Column.Modified);
        addColumn(Column.ModifiedBy);
    }

    public void setAssayProtocol(ExpProtocol protocol)
    {
        if (_assayProtocol != null && !_assayProtocol.equals(protocol))
        {
            throw new IllegalStateException("Cannot change assay protocol when it has already been set to " + _assayProtocol.getLSID());
        }
        _assayProtocol = protocol;

        getColumn(Column.Run).setFk(new LookupForeignKey("RowId")
        {
            @Override
            public TableInfo getLookupTableInfo()
            {
                AssayProvider provider = AssayService.get().getProvider(_assayProtocol);
                return provider.createProtocolSchema(_userSchema.getUser(), _userSchema.getContainer(), _assayProtocol, null).createRunsTable();
            }
        });
        SQLFragment protocolSQL = new SQLFragment("RunId IN (SELECT er.RowId FROM ");
        protocolSQL.append(ExperimentService.get().getTinfoExperimentRun(), "er");
        protocolSQL.append(" WHERE ProtocolLSID = ?)");
        protocolSQL.add(_assayProtocol.getLSID());
        addCondition(protocolSQL, FieldKey.fromParts("ProtocolLSID"));
    }

    @Override
    protected void applyContainerFilter(ContainerFilter filter)
    {
        FieldKey containerFieldKey = FieldKey.fromParts("Container");
        clearConditions(containerFieldKey);
        SQLFragment sql = new SQLFragment("RunId IN (SELECT er.RowId FROM ");
        sql.append(ExperimentService.get().getTinfoExperimentRun(), "er");
        sql.append(" WHERE ");
        sql.append(filter.getSQLFragment(getSchema(), new SQLFragment("er.Container"), getContainer()));
        sql.append(")");

        addCondition(sql, containerFieldKey);
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        return getContainer().hasPermission(user, perm);
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        return new UpdateService(this);
    }

    public class UpdateService extends DefaultQueryUpdateService
    {
        public UpdateService(TableInfo queryTable)
        {
            super(queryTable, ExperimentService.get().getTinfoAssayQCFlag(), _columnMapping);
        }
    }
}
