/*
 * Copyright (c) 2015-2019 LabKey Corporation
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
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpDataClass;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.query.ExpDataClassTable;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.DataClassReadPermission;
import org.labkey.api.security.permissions.DesignDataClassPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NotFoundException;
import org.labkey.experiment.controllers.exp.ExperimentController;

import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;

import static org.labkey.api.exp.query.ExpSchema.DATA_CLASS_CATEGORY_TABLE;
import static org.labkey.api.exp.query.ExpSchema.SCHEMA_EXP_DATA;
import static org.labkey.api.exp.query.ExpSchema.TableType.SampleSets;

public class ExpDataClassTableImpl extends ExpTableImpl<ExpDataClassTable.Column> implements ExpDataClassTable
{
    protected ExpDataClassTableImpl(String name, UserSchema schema, ContainerFilter cf)
    {
        super(name, ExperimentServiceImpl.get().getTinfoDataClass(), schema, cf);
        addAllowablePermission(DesignDataClassPermission.class);
    }

    @Override
    public MutableColumnInfo createColumn(String alias, Column column)
    {
        switch (column)
        {
            case Folder ->
            {
                var columnInfo = wrapColumn(alias, _rootTable.getColumn("Container"));
                columnInfo.setURL(new DetailsURL(new ActionURL(ExperimentController.ListDataClassAction.class, getContainer())));
                return columnInfo;
            }
            case Description, RowId ->
            {
                return wrapColumn(alias, _rootTable.getColumn(column.toString()));
            }
            case NameExpression ->
            {
                var columnInfo = wrapColumn(alias, _rootTable.getColumn(column.toString()));
                columnInfo.setLabel("Naming Pattern");
                return columnInfo;
            }
            case Name ->
            {
                var c = wrapColumn(alias, getRealTable().getColumn(column.name()));
                c.setShownInUpdateView(false);

                // Since the 'Name' column isn't a real PK column, we can't use the ShowDataClassAction with 'Name' as
                // a parameter for the table's detailsURL.  However, we can use it as the url for this column.
                DetailsURL nameURL = new DetailsURL(new ActionURL(ExperimentController.ShowDataClassAction.class, _userSchema.getContainer()),
                        Collections.singletonMap("name", "Name"));
                nameURL.setContainerContext(getContainer());
                c.setURL(nameURL);

                UserSchema schema = QueryService.get().getUserSchema(_userSchema.getUser(), _userSchema.getContainer(), SCHEMA_EXP_DATA);
                if (schema != null)
                {
                    c.setDisplayColumnFactory(colInfo -> new DataColumn(colInfo)
                    {
                        @Override
                        public Object getDisplayValue(RenderContext ctx)
                        {
                            String name = (String) ctx.get("name");
                            if (name != null)
                            {
                                TableInfo table = schema.getTable(name);
                                if (table != null)
                                    return table.getTitle();
                            }

                            return super.getDisplayValue(ctx);
                        }
                    });
                }

                return c;
            }
            case LSID ->
            {
                var c = wrapColumn(alias, _rootTable.getColumn(column.toString()));
                c.setHidden(true);
                c.setShownInInsertView(false);
                c.setShownInUpdateView(false);
                c.setUserEditable(false);
                c.setCalculated(true); // So DataIterator won't consider the column as required. See c.isRequiredForInsert()
                return c;
            }
            case Created, Modified ->
            {
                return wrapColumn(alias, _rootTable.getColumn(column.name()));
            }
            case CreatedBy, ModifiedBy ->
            {
                return createUserColumn(alias, _rootTable.getColumn(column.name()));
            }
            case SampleSet ->
            {
                var col = wrapColumn(alias, _rootTable.getColumn("MaterialSourceId"));
                var fk = QueryForeignKey.from(this.getUserSchema(), getContainerFilter())
                        .schema(ExpSchema.SCHEMA_NAME, getContainer())
                        .to(SampleSets.name(), Column.RowId.name(), null);
                col.setFk(fk);
                return col;
            }
            case DataCount ->
            {
                return createDataCountColumn(getContainerFilter());
            }
            case Category ->
            {
                var col = wrapColumn(alias, _rootTable.getColumn(column.toString()));
                var fk = QueryForeignKey.from(this.getUserSchema(), getContainerFilter())
                        .schema(ExpSchema.SCHEMA_NAME, getContainer())
                        .to(DATA_CLASS_CATEGORY_TABLE, "Value", null);
                col.setFk(fk);
                return col;
            }
            case ImportAliases ->
            {
                return createImportAliasColumn("ImportAliases", null, "data class");
            }
            default -> throw new IllegalArgumentException("Unknown column " + column);
        }
    }

    // Issue 47919: Support "DataCount" resolving the count of rows across containers.
    // Defaults to table-level container filter.
    private ExprColumn createDataCountColumn(@NotNull ContainerFilter dataCountContainerFilter)
    {
        SQLFragment sql = new SQLFragment("(SELECT COUNT(*) FROM ").append(ExperimentServiceImpl.get().getTinfoData(), "d")
                .append(" WHERE d.classId = ").append(ExprColumn.STR_TABLE_ALIAS + ".rowid")
                .append(" AND ")
                .append(dataCountContainerFilter.getSQLFragment(getSchema(), new SQLFragment("d.container")))
                .append(")");
        ExprColumn sampleCountColumnInfo = new ExprColumn(this, "DataCount", sql, JdbcType.INTEGER);
        sampleCountColumnInfo.setDescription("Contains the number of data currently stored in this data class");
        return sampleCountColumnInfo;
    }

    public void setDataCountContainerFilter(@NotNull ContainerFilter dataCountContainerFilter)
    {
        checkLocked();
        replaceColumn(createDataCountColumn(dataCountContainerFilter), getColumn(FieldKey.fromParts(Column.DataCount)));
    }

    @Override
    protected void populateColumns()
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
        addColumn(Column.Category).setHidden(true);
        addColumn(Column.SampleSet);
        addColumn(Column.DataCount);
        addColumn(Column.ImportAliases);

        setDetailsURL(new DetailsURL(new ActionURL(ExperimentController.ShowDataClassAction.class, _userSchema.getContainer()),
                Collections.singletonMap("rowId", "RowId")));

        setImportURL(AbstractTableInfo.LINK_DISABLER);
        setUpdateURL(AbstractTableInfo.LINK_DISABLER);
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        if (!isAllowedPermission(perm))
            return false;
        if (perm == ReadPermission.class)
            return _userSchema.getContainer().hasPermission(user, DataClassReadPermission.class);
        return _userSchema.getContainer().hasPermission(user, perm);
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
        protected Map<String, Object> _select(Container container, Object[] keys) throws ConversionException
        {
            Integer rowId = (Integer)keys[0];
            if (rowId == null)
                throw new IllegalStateException();

            // Get the DataClass defined in the current container by rowId
            ExpDataClass dc = ExperimentService.get().getDataClass(container, rowId);
            if (dc == null)
                throw new NotFoundException("DataClass not found");

            try
            {
                return new CaseInsensitiveHashMap(BeanUtils.describe(dc));
            }
            catch (ReflectiveOperationException e)
            {
                throw new RuntimeException(e.getMessage());
            }
        }

        @Override
        protected Map<String, Object> _insert(User user, Container c, Map<String, Object> row) throws ValidationException
        {
            String name = (String)row.get("name");
            if (StringUtils.isBlank(name))
                throw new ValidationException("name required", "name");

            String description = (String)row.get("description");
            String nameExpression = (String)row.get("nameExpression");
            Integer materialSourceId = (Integer)row.get("sampleSet");
            String category = (String)row.get("category");

            try
            {
                ExpDataClass dc = ExperimentService.get().createDataClass(c, user, name, description,
                        Collections.emptyList(), Collections.emptyList(),
                        materialSourceId, nameExpression, null, category);
                return new CaseInsensitiveHashMap(BeanUtils.describe(dc));
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

            // Get the DataClass defined in the current container by rowId
            ExpDataClassImpl dc = ExperimentServiceImpl.get().getDataClass(c, rowId);
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

            // Get the DataClass defined in the current container by rowId
            ExpDataClass dc = ExperimentService.get().getDataClass(c, rowId);
            if (dc == null)
                throw new NotFoundException("DataClass not found");

            dc.delete(getUserSchema().getUser());
        }
    }
}
