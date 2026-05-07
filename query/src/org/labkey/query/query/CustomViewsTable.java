/*
 * Copyright (c) 2008-2026 LabKey Corporation
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
package org.labkey.query.query;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.query.column.BuiltInColumnTypes;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.EditSharedViewPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.util.HtmlString;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.query.QueryUserSchema;
import org.labkey.query.persist.CstmView;
import org.labkey.query.persist.QueryManager;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CustomViewsTable extends FilteredTable<QueryUserSchema>
{
    public CustomViewsTable(@NotNull QueryUserSchema userSchema, ContainerFilter cf)
    {
        super(QueryDbSchema.getInstance().getTableInfoCustomView(), userSchema, cf);

        setName(QueryUserSchema.CUSTOM_VIEWS_TABLE_NAME);
        setDescription("Contains a row for each saved custom view. Available only to administrators.");

        setImportURL(LINK_DISABLER);
        wrapAllColumns(true);
        var customViewIdCol = getMutableColumnOrThrow("CustomViewId");
        customViewIdCol.setKeyField(true);

        getMutableColumnOrThrow("EntityId");
        getMutableColumnOrThrow("Schema").setLabel("Schema Name");
        getMutableColumnOrThrow("Name").setLabel("View Name");

        var ownerCol = getMutableColumnOrThrow("CustomViewOwner");
        ownerCol.setLabel("Owner");
        ownerCol.setConceptURI(BuiltInColumnTypes.USERID_CONCEPT_URI);

        var folderCol = getMutableColumnOrThrow(FieldKey.fromString("Container"));
        folderCol.setLabel("Folder");
        folderCol.setConceptURI(BuiltInColumnTypes.CONTAINERID_CONCEPT_URI);

        getMutableColumnOrThrow("CreatedBy").setConceptURI(BuiltInColumnTypes.USERID_CONCEPT_URI);
        getMutableColumnOrThrow("ModifiedBy").setConceptURI(BuiltInColumnTypes.USERID_CONCEPT_URI);
        getMutableColumnOrThrow("Columns");
        getMutableColumnOrThrow("Filter");
        getMutableColumnOrThrow("Flags").setDisplayColumnFactory(FlagDisplayColumn::new);

        ColumnInfo flagsCol = getRealTable().getColumn("Flags");
        var hiddenCol = new ExprColumn(this, "Hidden",
                new SQLFragment("(CASE WHEN (" + ExprColumn.STR_TABLE_ALIAS + ".Flags & " + QueryManager.FLAG_HIDDEN + ") != 0")
                        .append(" THEN ").append(getSqlDialect().getBooleanTRUE())
                        .append(" ELSE ").append(getSqlDialect().getBooleanFALSE()).append(" END)"),
                JdbcType.BOOLEAN, flagsCol);
        addColumn(hiddenCol);

        var inheritableCol = new ExprColumn(this, "Inheritable",
                new SQLFragment("(CASE WHEN (" + ExprColumn.STR_TABLE_ALIAS + ".Flags & " + QueryManager.FLAG_INHERITABLE + ") != 0")
                        .append(" THEN ").append(getSqlDialect().getBooleanTRUE())
                        .append(" ELSE ").append(getSqlDialect().getBooleanFALSE()).append(" END)"),
                JdbcType.BOOLEAN, flagsCol);
        addColumn(inheritableCol);

        setDefaultVisibleColumns(List.of(
                FieldKey.fromParts("Schema"),
                FieldKey.fromParts("QueryName"),
                FieldKey.fromParts("Name"),
                FieldKey.fromParts("CustomViewOwner"),
                FieldKey.fromParts("Container"),
                FieldKey.fromParts("Hidden"),
                FieldKey.fromParts("Inheritable"),
                FieldKey.fromParts("Created"),
                FieldKey.fromParts("CreatedBy"),
                FieldKey.fromParts("Modified"),
                FieldKey.fromParts("ModifiedBy")
        ));
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        return getContainer().hasPermission(user, AdminPermission.class);
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        return new CustomViewsUpdateService(this, QueryDbSchema.getInstance().getTableInfoCustomView());
    }

    public static class FlagDisplayColumn extends DataColumn
    {
        public FlagDisplayColumn(ColumnInfo col)
        {
            super(col);
        }

        @NotNull
        @Override
        public HtmlString getFormattedHtml(RenderContext ctx)
        {
            var value = getValue(ctx);
            if (value instanceof Integer flag)
            {
                List<String> flags = new ArrayList<>();
                if ((flag & QueryManager.FLAG_INHERITABLE) != 0)
                    flags.add("inherit");
                if ((flag & QueryManager.FLAG_HIDDEN) != 0)
                    flags.add("hidden");

                return HtmlString.of(String.join(", ", flags));
            }
            return super.getFormattedHtml(ctx);
        }
    }

    protected static class CustomViewsUpdateService extends DefaultQueryUpdateService
    {
        public CustomViewsUpdateService(TableInfo queryTable, TableInfo dbTable)
        {
            super(queryTable, dbTable);
        }

        @Override
        protected Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRowMap) throws QueryUpdateServiceException, SQLException, InvalidKeyException
        {
            Integer id = (Integer)oldRowMap.get("customViewId");
            if (id != null)
            {
                var view = QueryManager.get().getCustomView(container, id);
                if (view != null)
                {
                    if (view.getCustomViewOwner() == null)
                    {
                        if (!container.hasPermission(user, EditSharedViewPermission.class))
                            throw new UnauthorizedException();
                    }
                    else
                    {
                        // must be owner or site admin
                        if (!user.hasSiteAdminPermission() && view.getCustomViewOwner().intValue() != user.getUserId())
                            throw new UnauthorizedException();
                    }
                    QueryManager.get().delete(user, view);
                }
            }
            return oldRowMap;
        }

        @Override
        public List<Map<String, Object>> insertRows(User user, Container container, List<Map<String, Object>> rows, BatchValidationException errors, @Nullable Map<Enum, Object> configParameters, Map<String, Object> extraScriptContext)
        {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> row : rows)
            {
                CaseInsensitiveHashMap<Object> rowMap = new CaseInsensitiveHashMap<>(row);
                CstmView view = new CstmView();
                view.setContainerId(container.getId());
                view.setSchema((String) rowMap.get("schema"));
                view.setQueryName((String) rowMap.get("queryName"));
                view.setName((String) rowMap.get("name"));
                if (rowMap.containsKey("customViewOwner"))
                    view.setCustomViewOwner((Integer) rowMap.get("customViewOwner"));
                if (rowMap.containsKey("columns"))
                    view.setColumns((String) rowMap.get("columns"));
                if (rowMap.containsKey("filter"))
                    view.setFilter((String) rowMap.get("filter"));
                if (rowMap.get("flags") instanceof Integer flags)
                    view.setFlags(flags);

                validate(view, container, user);
                result.add(CstmView.toRow(QueryManager.get().insert(user, view)));
            }
            return result;
        }

        @Override
        protected Map<String, Object> _update(User user, Container container, Map<String, Object> row, Map<String, Object> oldRow, Object[] keys) throws SQLException, ValidationException
        {
            Integer id = (Integer) oldRow.get("customViewId");
            CstmView view = QueryManager.get().getCustomView(container, id);
            if (view == null)
                throw new ValidationException("Custom view not found: " + id);
            if (row.containsKey("schema"))
                view.setSchema((String) row.get("schema"));
            if (row.containsKey("queryName"))
                view.setQueryName((String) row.get("queryName"));
            if (row.containsKey("name"))
                view.setName((String) row.get("name"));
            if (row.containsKey("customViewOwner"))
                view.setCustomViewOwner((Integer) row.get("customViewOwner"));
            if (row.containsKey("columns"))
                view.setColumns((String) row.get("columns"));
            if (row.containsKey("filter"))
                view.setFilter((String) row.get("filter"));
            if (row.containsKey("flags") && row.get("flags") instanceof Integer flags)
                view.setFlags(flags);

            validate(view, container, user);
            return CstmView.toRow(QueryManager.get().update(user, view));
        }

        private void validate(CstmView view, Container c, User user)
        {
            if (view == null)
            {
                throw new NotFoundException();
            }
            if (!view.getContainerId().equals(c.getId()))
            {
                throw new UnauthorizedException();
            }
            if (view.getCustomViewOwner() == null)
            {
                if (!c.hasPermission(user, EditSharedViewPermission.class))
                {
                    throw new UnauthorizedException();
                }
            }
            else
            {
                // must be owner or site admin
                if (!user.hasSiteAdminPermission() && view.getCustomViewOwner().intValue() != user.getUserId())
                {
                    throw new UnauthorizedException();
                }
            }
        }
    }
}
