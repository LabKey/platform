/*
 * Copyright (c) 2015-2016 LabKey Corporation
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
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.exp.query.ExpDataClassTable;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.view.ActionURL;
import org.labkey.experiment.controllers.exp.ExperimentController;

import java.util.Collections;

/**
 * User: kevink
 * Date: 9/21/15
 */
public class ExpDataClassTableImpl extends ExpTableImpl<ExpDataClassTable.Column> implements ExpDataClassTable
{
    protected ExpDataClassTableImpl(String name, UserSchema schema)
    {
        super(name, ExperimentServiceImpl.get().getTinfoDataClass(), schema, new ExpDataClassImpl(new DataClass()));
        addAllowablePermission(InsertPermission.class);
        addAllowablePermission(UpdatePermission.class);
    }

    @Override
    public ColumnInfo createColumn(String alias, Column column)
    {
        switch (column)
        {
            case Folder:
                ColumnInfo columnInfo = wrapColumn(alias, _rootTable.getColumn("Container"));
                ContainerForeignKey.initColumn(columnInfo, _userSchema, new ActionURL(ExperimentController.ShowDataClassAction.class, getContainer()));
                return columnInfo;
            case Description:
            case LSID:
            case Name:
            case NameExpression:
            case RowId:
                return wrapColumn(alias, _rootTable.getColumn(column.toString()));
            case Created:
                return wrapColumn(alias, _rootTable.getColumn("Created"));
            case CreatedBy:
                return createUserColumn(alias, _rootTable.getColumn("CreatedBy"));
            case Modified:
                return wrapColumn(alias, _rootTable.getColumn("Modified"));
            case ModifiedBy:
                return createUserColumn(alias, _rootTable.getColumn("ModifiedBy"));
            case SampleSet:
                ColumnInfo col = wrapColumn(alias, _rootTable.getColumn("MaterialSourceId"));
                QueryForeignKey fk = new QueryForeignKey(ExpSchema.SCHEMA_NAME, getContainer(), null, getUserSchema().getUser(), ExpSchema.TableType.SampleSets.name(), "RowId", null);
                col.setFk(fk);
                return col;
            case DataCount:
            {
                SQLFragment sql = new SQLFragment("(SELECT COUNT(*) FROM ").append(ExperimentServiceImpl.get().getTinfoData(), "d")
                    .append(" WHERE d.classId = ").append(ExprColumn.STR_TABLE_ALIAS + ".rowid)");
                ExprColumn sampleCountColumnInfo = new ExprColumn(this, "DataCount", sql, JdbcType.INTEGER);
                sampleCountColumnInfo.setDescription("Contains the number of data currently stored in this data class");
                return sampleCountColumnInfo;
            }
            default:
                throw new IllegalArgumentException("Unknown column " + column);
        }
    }

    @Override
    public void populate()
    {
        addColumn(Column.RowId).setHidden(true);
        addColumn(Column.Name);
        addColumn(Column.Description);
        addColumn(Column.LSID).setHidden(true);
        addColumn(Column.Created);
        addColumn(Column.CreatedBy);
        addColumn(Column.Modified);
        addColumn(Column.ModifiedBy);
        addContainerColumn(Column.Folder, new ActionURL(ExperimentController.ListDataClassAction.class, getContainer()));
        addColumn(Column.NameExpression).setHidden(true);
        addColumn(Column.SampleSet);
        addColumn(Column.DataCount);

        setDetailsURL(new DetailsURL(new ActionURL(ExperimentController.ShowDataClassAction.class, _userSchema.getContainer()),
                Collections.singletonMap("rowId", "RowId")));

        setDeleteURL(new DetailsURL(new ActionURL(ExperimentController.DeleteDataClassAction.class, _userSchema.getContainer())));
        setInsertURL(new DetailsURL(new ActionURL(ExperimentController.InsertDataClassAction.class, _userSchema.getContainer())));
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        return isAllowedPermission(perm) && _userSchema.getContainer().hasPermission(user, perm);
    }

}
