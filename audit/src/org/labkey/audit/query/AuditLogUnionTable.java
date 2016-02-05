/*
 * Copyright (c) 2013-2016 LabKey Corporation
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
package org.labkey.audit.query;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.AuditTypeProvider;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.VirtualTable;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.UserIdForeignKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.audit.AuditSchema;

import java.util.Map;

/**
 * User: klum
 * Date: 8/13/13
 */
public class AuditLogUnionTable extends FilteredTable<AuditQuerySchema>
{
    public AuditLogUnionTable(AuditQuerySchema schema)
    {
        super(createVirtualTable(schema), schema);
        wrapAllColumns(true);
    }

    private static TableInfo createVirtualTable(AuditQuerySchema schema)
    {
        return new AuditUnionTable(schema);
    }

    private static class AuditUnionTable extends VirtualTable
    {
        private SQLFragment _query;

        public AuditUnionTable(@NotNull UserSchema schema)
        {
            super(AuditSchema.getInstance().getSchema(), AuditQuerySchema.AUDIT_TABLE_NAME, schema);

            _query = new SQLFragment();
            _query.appendComment("<AuditUnionTableInfo>", getSchema().getSqlDialect());
            setInsertURL(LINK_DISABLER);
            String union = "";

            for (AuditTypeProvider provider : AuditLogService.get().getAuditProviders())
            {
                // issue: 14463, exclude dataset audit events from the general query
                if (provider.getEventName().equals("DatasetAuditEvent"))
                    continue;

                _query.append(union).append("\n");

                TableInfo table = provider.createTableInfo(schema);
                Map<FieldKey, String> legacyMap = provider.legacyNameMap();

                _query.append("SELECT\n");
                _query.append("  CreatedBy,\n");
                _query.append("  ImpersonatedBy,\n");
                _query.append("  RowId,\n");
                _query.append("  Created AS Date,\n");
                _query.append("  EventType,\n");
                _query.append("  Comment,\n");
                _query.append("  Container AS ContainerId,\n");
                _query.append("  ProjectId,\n");

                // unsupported, but provided for legacy
                _query.append("  NULL AS EntityId,\n");
                _query.append("  NULL AS Lsid,\n");

                // need to map new column names to legacy names
                addLegacyName(_query, "Key1", legacyMap, ",\n");
                addLegacyName(_query, "Key2", legacyMap, ",\n");
                addLegacyName(_query, "Key3", legacyMap, ",\n");
                addLegacyName(_query, "IntKey1", legacyMap, ",\n");
                addLegacyName(_query, "IntKey2", legacyMap, ",\n");
                addLegacyName(_query, "IntKey3", legacyMap, "\n");

                _query.append("FROM ").append(table, "X").append("\n");

                union = "UNION";
            }
            _query.appendComment("</AuditUnionTableInfo>", getSchema().getSqlDialect());

            ColumnInfo createdByCol = new ColumnInfo("CreatedBy", this);
            createdByCol.setJdbcType(JdbcType.INTEGER);
            UserIdForeignKey.initColumn(createdByCol);
            addColumn(createdByCol);

            ColumnInfo impersonatedByCol = new ColumnInfo("ImpersonatedBy", this);
            impersonatedByCol.setJdbcType(JdbcType.INTEGER);
            UserIdForeignKey.initColumn(impersonatedByCol);
            addColumn(impersonatedByCol);

            ColumnInfo rowIdCol = new ColumnInfo("RowId", this);
            rowIdCol.setKeyField(true);
            rowIdCol.setJdbcType(JdbcType.INTEGER);
            addColumn(rowIdCol);

            ColumnInfo dateCol = new ColumnInfo("Date", this);
            dateCol.setJdbcType(JdbcType.DATE);
            addColumn(dateCol);

            ColumnInfo eventTypeCol = new ColumnInfo("EventType", this);
            eventTypeCol.setJdbcType(JdbcType.VARCHAR);
            addColumn(eventTypeCol);

            ColumnInfo commentCol = new ColumnInfo("Comment", this);
            commentCol.setJdbcType(JdbcType.VARCHAR);
            addColumn(commentCol);

            ColumnInfo containerCol = new ColumnInfo("ContainerId", this);
            containerCol.setJdbcType(JdbcType.VARCHAR);
            ContainerForeignKey.initColumn(containerCol, schema);
            addColumn(containerCol);

            ColumnInfo projIdCol = new ColumnInfo("ProjectId", this);
            projIdCol.setJdbcType(JdbcType.VARCHAR);
            ContainerForeignKey.initColumn(projIdCol, schema);
            addColumn(projIdCol);

            ColumnInfo entityIdCol = new ColumnInfo("EntityId", this);
            entityIdCol.setJdbcType(JdbcType.VARCHAR);
            addColumn(entityIdCol);

            ColumnInfo lsidCol = new ColumnInfo("Lsid", this);
            lsidCol.setJdbcType(JdbcType.VARCHAR);
            addColumn(lsidCol);

            ColumnInfo key1Col = new ColumnInfo("Key1", this);
            key1Col.setJdbcType(JdbcType.VARCHAR);
            addColumn(key1Col);

            ColumnInfo key2Col = new ColumnInfo("Key2", this);
            key2Col.setJdbcType(JdbcType.VARCHAR);
            addColumn(key2Col);

            ColumnInfo key3Col = new ColumnInfo("Key3", this);
            key3Col.setJdbcType(JdbcType.VARCHAR);
            addColumn(key3Col);

            ColumnInfo intKey1Col = new ColumnInfo("IntKey1", this);
            intKey1Col.setJdbcType(JdbcType.INTEGER);
            addColumn(intKey1Col);

            ColumnInfo intKey2Col = new ColumnInfo("IntKey2", this);
            intKey2Col.setJdbcType(JdbcType.INTEGER);
            addColumn(intKey2Col);

            ColumnInfo intKey3Col = new ColumnInfo("IntKey3", this);
            intKey3Col.setJdbcType(JdbcType.INTEGER);
            addColumn(intKey3Col);
        }

        private void addLegacyName(SQLFragment sql, String fieldName, Map<FieldKey, String> legacyMap, String delim)
        {
            FieldKey field = FieldKey.fromParts(fieldName);

            if (legacyMap.containsKey(field))
                sql.append("\'").append(legacyMap.get(field)).append("\'").append(" AS ").append(fieldName);
            else
                sql.append("NULL AS ").append(fieldName);

            sql.append(delim);
        }

        @Override
        public String getSelectName()
        {
            return null;
        }

        @NotNull
        @Override
        public SQLFragment getFromSQL()
        {
            return _query;
        }
    }

    private boolean isGuest(UserPrincipal user)
    {
        return user instanceof User && user.isGuest();
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        // Don't allow deletes or updates for audit events, and don't let guests insert
        return ((perm.equals(InsertPermission.class) && !isGuest(user)) || perm.equals(ReadPermission.class)) &&
                getContainer().hasPermission(user, perm);
    }

    @Nullable
    @Override
    public QueryUpdateService getUpdateService()
    {
        return new AuditLogUpdateService(this);
    }
}
