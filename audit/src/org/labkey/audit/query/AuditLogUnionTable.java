/*
 * Copyright (c) 2013-2019 LabKey Corporation
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
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ContainerFilter;
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
    public AuditLogUnionTable(AuditQuerySchema schema, ContainerFilter cf)
    {
        super(createVirtualTable(schema, cf), schema, null);
        wrapAllColumns(true);
    }

    private static TableInfo createVirtualTable(AuditQuerySchema schema, ContainerFilter cf)
    {
        return new AuditUnionTable(schema, cf);
    }

    private static class AuditUnionTable extends VirtualTable
    {
        private SQLFragment _query;

        public AuditUnionTable(@NotNull UserSchema schema, ContainerFilter cf)
        {
            super(AuditSchema.getInstance().getSchema(), AuditQuerySchema.AUDIT_TABLE_NAME, schema, ContainerFilter.EVERYTHING);

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

                TableInfo table = provider.createTableInfo(schema, cf);
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

            var createdByCol = new BaseColumnInfo("CreatedBy", this, JdbcType.INTEGER);
            UserIdForeignKey.initColumn(createdByCol);
            addColumn(createdByCol);

            var impersonatedByCol = new BaseColumnInfo("ImpersonatedBy", this, JdbcType.INTEGER);
            UserIdForeignKey.initColumn(impersonatedByCol);
            addColumn(impersonatedByCol);

            var rowIdCol = new BaseColumnInfo("RowId", this, JdbcType.INTEGER);
            rowIdCol.setKeyField(true);
            addColumn(rowIdCol);

            var dateCol = new BaseColumnInfo("Date", this, JdbcType.DATE);
            addColumn(dateCol);

            var eventTypeCol = new BaseColumnInfo("EventType", this, JdbcType.VARCHAR);
            addColumn(eventTypeCol);

            var commentCol = new BaseColumnInfo("Comment", this, JdbcType.VARCHAR);
            addColumn(commentCol);

            var containerCol = new BaseColumnInfo("ContainerId", this, JdbcType.VARCHAR);
            ContainerForeignKey.initColumn(containerCol, schema);
            addColumn(containerCol);

            var projIdCol = new BaseColumnInfo("ProjectId", this, JdbcType.VARCHAR);
            ContainerForeignKey.initColumn(projIdCol, schema);
            addColumn(projIdCol);

            var entityIdCol = new BaseColumnInfo("EntityId", this, JdbcType.VARCHAR);
            addColumn(entityIdCol);

            var lsidCol = new BaseColumnInfo("Lsid", this, JdbcType.VARCHAR);
            addColumn(lsidCol);

            var key1Col = new BaseColumnInfo("Key1", this, JdbcType.VARCHAR);
            addColumn(key1Col);

            var key2Col = new BaseColumnInfo("Key2", this, JdbcType.VARCHAR);
            addColumn(key2Col);

            var key3Col = new BaseColumnInfo("Key3", this, JdbcType.VARCHAR);
            addColumn(key3Col);

            var intKey1Col = new BaseColumnInfo("IntKey1", this, JdbcType.INTEGER);
            addColumn(intKey1Col);

            var intKey2Col = new BaseColumnInfo("IntKey2", this, JdbcType.INTEGER);
            addColumn(intKey2Col);

            var intKey3Col = new BaseColumnInfo("IntKey3", this, JdbcType.INTEGER);
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
