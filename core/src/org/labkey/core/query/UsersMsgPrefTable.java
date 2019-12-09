/*
 * Copyright (c) 2011-2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.core.query;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.message.settings.MessageConfigService;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.ReadPermission;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/*
  User: klum
  Date: Feb 2, 2011
  Time: 1:38:38 PM
 */

/**
 * Table of users and their associated messaging preferences. This may not be the best place for this table
 * its here because it contains all of the columns of the users table plus columns for messaging preferences.
 *
 * Only exposes folder-level user settings, not subforum subscriptions.
 */
public class UsersMsgPrefTable extends UsersTable
{
    public UsersMsgPrefTable(UserSchema schema, TableInfo tInfo)
    {
        super(schema, tInfo);
    }

    @Override
    public void addColumns()
    {
        super.addColumns();

        // Add a column for each registered email ConfigTypeProvider
        MessageConfigService.get().getConfigTypes().stream()
            .map(p->new EmailSettingsColumn(p.getSettingsColumnName(), p.getType(), this))
            .forEach(esc->{
                addColumn(esc);
                esc.setDisplayColumnFactory(NotificationSettingColumn::new);
            });

        // add all of the active users who have read permission to this container to an in clause, this avoids
        // having to do the permission checking at render time and fixes the pagination display issues
        List<Integer> userIds = new ArrayList<>();

        for (User user : UserManager.getActiveUsers())
        {
            if (getUserSchema().getContainer().hasPermission(user, ReadPermission.class))
                userIds.add(user.getUserId());
        }

        if (!userIds.isEmpty())
            addInClause(getRealTable().getColumn("UserId"), userIds);

        setDefaultVisibleColumns(getDefaultColumns());
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        return null;
    }

    private List<FieldKey> getDefaultColumns()
    {
        List<FieldKey> columns = new ArrayList<>();

        columns.add(FieldKey.fromParts("UserId"));
        columns.add(FieldKey.fromParts("DisplayName"));
        columns.add(FieldKey.fromParts("FirstName"));
        columns.add(FieldKey.fromParts("LastName"));
        columns.add(FieldKey.fromParts("Email"));
        columns.add(FieldKey.fromParts("LastLogin"));
        columns.add(FieldKey.fromParts("Groups"));

        MessageConfigService.get().getConfigTypes()
            .forEach(p->columns.add(FieldKey.fromParts(p.getSettingsColumnName())));

        return columns;
    }

    public static class EmailSettingsColumn extends ExprColumn
    {
        private final String _emailPrefsJoin;
        private final String _emailOptionsJoin;
        private final TableInfo _emailPrefsTable;
        private final TableInfo _emailOptionsTable;
        private final Container _container;
        private final String _type;

        public EmailSettingsColumn(String name, String type, FilteredTable parent)
        {
            super(parent, name, new SQLFragment(), JdbcType.VARCHAR);

            _type = type;
            _container = parent.getContainer();
            _emailPrefsTable = CoreSchema.getInstance().getTableInfoEmailPrefs();
            _emailOptionsTable = CoreSchema.getInstance().getTableInfoEmailOptions();

            // set up the join aliases
            _emailPrefsJoin = name + "$" + "EmailPrefsJoin$";
            _emailOptionsJoin = name + "$" + "EmailOptionsJoin$";

            SQLFragment sql = new SQLFragment();
            sql.append(ExprColumn.STR_TABLE_ALIAS).append("$").append(_emailOptionsJoin).append(".EmailOption\n");
            setValueSQL(sql);
        }

        @Override
        public void declareJoins(String parentAlias, Map<String, SQLFragment> map)
        {
            super.declareJoins(parentAlias, map);

            String tableAlias = parentAlias + "$" + _emailPrefsJoin;
            String tableOptionsAlias = parentAlias + "$" + _emailOptionsJoin;
            if (map.containsKey(tableAlias))
                return;

            SQLFragment joinSql = new SQLFragment();

            joinSql.append(" LEFT JOIN ").append(_emailPrefsTable, tableAlias);
            joinSql.append(" ON ").append(tableAlias).append(".UserId = ").append(parentAlias).append(".UserId");
            joinSql.append(" AND ").append(tableAlias).append(".Container = ").append("'").append(_container.getId()).append("'");
            // Filter to only show container-level subscriptions, not subforums
            joinSql.append(" AND ").append(tableAlias).append(".SrcIdentifier = ").append("'").append(_container.getId()).append("'");
            joinSql.append(" AND ").append(tableAlias).append(".Type = ").append("'").append(_type).append("'");
            joinSql.append(" LEFT JOIN ").append(_emailOptionsTable, tableOptionsAlias);
            joinSql.append(" ON ").append(tableAlias).append(".EmailOptionId = ").append(tableOptionsAlias).append(".EmailOptionId");

            map.put(tableAlias, joinSql);
        }
    }

    public static class NotificationSettingColumn extends DataColumn
    {
        public NotificationSettingColumn(ColumnInfo column)
        {
            super(column);
        }

        @Override
        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            Object value = getValue(ctx);

            if (value == null)
                out.write("&lt;folder&nbsp;default&gt;");
            else
                super.renderGridCellContents(ctx, out);
        }
    }
}
