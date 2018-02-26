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

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpDataClass;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.query.ExpDataClassTable;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NotFoundException;
import org.labkey.experiment.controllers.exp.ExperimentController;

import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;

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
                ContainerForeignKey.initColumn(columnInfo, _userSchema, new ActionURL(ExperimentController.ListDataClassAction.class, getContainer()));
                return columnInfo;

            case Description:
            case NameExpression:
            case RowId:
                return wrapColumn(alias, _rootTable.getColumn(column.toString()));

            case Name:
            {
                ColumnInfo c = wrapColumn(alias, getRealTable().getColumn(column.name()));
                c.setShownInUpdateView(false);

                // Since the 'Name' column isn't a real PK column, we can't use the ShowDataClassAction with 'Name' as
                // a parameter for the table's detailsURL.  However, we can use it as the url for this column.
                c.setURL(new DetailsURL(new ActionURL(ExperimentController.ShowDataClassAction.class, _userSchema.getContainer()),
                        Collections.singletonMap("name", "Name")));
                return c;
            }


            case LSID:
            {
                ColumnInfo c = wrapColumn(alias, _rootTable.getColumn(column.toString()));
                c.setHidden(true);
                c.setShownInInsertView(false);
                c.setShownInUpdateView(false);
                c.setUserEditable(false);
                c.setCalculated(true); // So DataIterator won't consider the column as required. See c.isRequiredForInsert()
                return c;
            }

            case Created:
                return wrapColumn(alias, _rootTable.getColumn("Created"));

            case CreatedBy:
                return createUserColumn(alias, _rootTable.getColumn("CreatedBy"));

            case Modified:
                return wrapColumn(alias, _rootTable.getColumn("Modified"));

            case ModifiedBy:
                return createUserColumn(alias, _rootTable.getColumn("ModifiedBy"));

            case SampleSet:
            {
                ColumnInfo col = wrapColumn(alias, _rootTable.getColumn("MaterialSourceId"));
                QueryForeignKey fk = new QueryForeignKey(ExpSchema.SCHEMA_NAME, getContainer(), null, getUserSchema().getUser(), ExpSchema.TableType.SampleSets.name(), "RowId", null);
                col.setFk(fk);
                return col;
            }

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


    @Nullable
    @Override
    public QueryUpdateService getUpdateService()
    {
        return new UpdateService(this);
    }



    private class UpdateService extends DefaultQueryUpdateService
    {
        public UpdateService(ExpDataClassTableImpl queryTable)
        {
            super(queryTable, queryTable.getRealTable(), Collections.singletonMap("materialSourceId", Column.SampleSet.toString()));
        }

        @Override
        protected Map<String, Object> _select(Container container, Object[] keys) throws SQLException, ConversionException
        {
            Integer rowId = (Integer)keys[0];
            if (rowId == null)
                throw new IllegalStateException();

            ExpDataClass dc = ExperimentService.get().getDataClass(rowId);
            if (dc == null)
                throw new NotFoundException("DataClass not found");

            try
            {
                return new CaseInsensitiveHashMap((Map<String,Object>) BeanUtils.describe(dc));
            }
            catch (ReflectiveOperationException e)
            {
                throw new RuntimeException(e.getMessage());
            }
        }

        @Override
        protected Map<String, Object> _insert(User user, Container c, Map<String, Object> row) throws SQLException, ValidationException
        {
            String name = (String)row.get("name");
            if (StringUtils.isBlank(name))
                throw new ValidationException("name required", "name");

            String description = (String)row.get("description");
            String nameExpression = (String)row.get("nameExpression");
            Integer materialSourceId = (Integer)row.get("sampleSet");

            try
            {
                ExpDataClass dc = ExperimentService.get().createDataClass(c, user, name, description,
                        Collections.emptyList(), Collections.emptyList(),
                        materialSourceId, nameExpression, null);
                return new CaseInsensitiveHashMap((Map<String,Object>) BeanUtils.describe(dc));
            }
            catch (ExperimentException | ReflectiveOperationException e)
            {
                throw new ValidationException(e.getMessage());
            }
        }

        @Override
        protected Map<String, Object> _update(User user, Container c, Map<String, Object> row, Map<String, Object> oldRow, Object[] keys) throws SQLException, ValidationException
        {
            Integer rowId = (Integer)keys[0];
            if (rowId == null)
                throw new IllegalStateException();

            ExpDataClass dc = ExperimentService.get().getDataClass(rowId);
            if (dc == null)
                throw new NotFoundException("DataClass not found");

            Map<String, Object> ret = super._update(user, c, row, oldRow, keys);

            ExperimentServiceImpl.get().clearDataClassCache(c);

            ExperimentServiceImpl.get().indexDataClass(dc);

            return ret;
        }

        @Override
        protected void _delete(Container c, Map<String, Object> row) throws InvalidKeyException
        {
            Integer rowId = (Integer)row.get("rowId");
            if (rowId == null)
                throw new InvalidKeyException("DataClass rowId required");

            ExpDataClass dc = ExperimentService.get().getDataClass(rowId);
            if (dc == null)
                throw new NotFoundException("DataClass not found");

            dc.delete(getUserSchema().getUser());
        }
    }

}
