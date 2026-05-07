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
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.column.BuiltInColumnTypes;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.query.CustomQueryDefinitionImpl;
import org.labkey.query.QueryDefinitionImpl;
import org.labkey.query.QueryUserSchema;
import org.labkey.query.persist.QueryDefCache;
import org.labkey.query.persist.QueryManager;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class QueriesTable extends FilteredTable<QueryUserSchema>
{
    public QueriesTable(@NotNull QueryUserSchema userSchema, ContainerFilter cf)
    {
        super(QueryDbSchema.getInstance().getTableInfoQueryDef(), userSchema, cf);

        setName(QueryUserSchema.QUERIES_TABLE_NAME);
        setDescription("Contains a row for each query (or metadata) in the database. Available only to administrators.");

        setImportURL(LINK_DISABLER);
        wrapAllColumns(true);

        getMutableColumnOrThrow("QueryDefId").setKeyField(true);
        var folderCol = getMutableColumnOrThrow(FieldKey.fromString("Container"));
        folderCol.setLabel("Folder");
        folderCol.setConceptURI(BuiltInColumnTypes.CONTAINERID_CONCEPT_URI);

        getMutableColumnOrThrow("CreatedBy").setConceptURI(BuiltInColumnTypes.USERID_CONCEPT_URI);
        getMutableColumnOrThrow("ModifiedBy").setConceptURI(BuiltInColumnTypes.USERID_CONCEPT_URI);
        getMutableColumnOrThrow("Flags").setDisplayColumnFactory(CustomViewsTable.FlagDisplayColumn::new);

        var flagsCol = getRealTable().getColumn("Flags");
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
                FieldKey.fromParts("Name"),
                FieldKey.fromParts("Description"),
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
        return (perm.equals(ReadPermission.class) || perm.equals(DeletePermission.class)) && getContainer().hasPermission(user, AdminPermission.class);
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        return new QueriesUpdateService(this, QueryDbSchema.getInstance().getTableInfoQueryDef());
    }

    protected static class QueriesUpdateService extends DefaultQueryUpdateService
    {
        public QueriesUpdateService(TableInfo queryTable, TableInfo dbTable)
        {
            super(queryTable, dbTable);
        }

        @Override
        protected Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRowMap) throws SQLException, QueryUpdateServiceException, InvalidKeyException
        {
            Container c = getContainer(oldRowMap);
            if (c != null)
            {
                var queryDef = QueryDefCache.getQueryDefById(c, (Integer)oldRowMap.get("queryDefId"));
                if (queryDef != null)
                {
                    QueryDefinitionImpl queryDefImpl = new CustomQueryDefinitionImpl(user, c, queryDef);
                    queryDefImpl.delete(user);
                }
            }
            return oldRowMap;
        }

        private @Nullable Container getContainer(Map<String, Object> row)
        {
            String containerId = (String) row.get("container");
            if (containerId != null)
                return ContainerManager.getForId(containerId);

            return null;
        }
    }
}
