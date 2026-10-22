/*
 * Copyright (c) 2026 LabKey Corporation
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
package org.labkey.announcements.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.announcements.model.AnnouncementManager;
import org.labkey.announcements.model.SecureMessageBoardReadPermission;
import org.labkey.announcements.model.Settings;
import org.labkey.api.announcements.CommSchema;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * GH Issue 1637: container filter clause that also applies secure message board read rules, so cross-folder queries
 * show a non-editor only the threads whose member list includes them, mirroring SecureMessageBoardPermissions.
 */
class SecureMessageBoardClause extends SimpleFilter.FilterClause
{
    private final SimpleFilter.FilterClause _containerClause;
    private final SqlDialect _dialect;
    private final FieldKey _containerFieldKey;
    private final SQLFragment _threadEntityIdSql;
    private final User _user;

    /**
     * @param threadEntityIdSql unqualified expression yielding the EntityId of each row's thread
     * @param permission permission the user needs in a folder to see any of its rows
     */
    SecureMessageBoardClause(DbSchema schema, ContainerFilter filter, FieldKey containerFieldKey, Class<? extends Permission> permission, SQLFragment threadEntityIdSql, User user)
    {
        _containerClause = filter.createFilterClause(schema, containerFieldKey, permission);
        _dialect = schema.getSqlDialect();
        _containerFieldKey = containerFieldKey;
        _threadEntityIdSql = threadEntityIdSql;
        _user = user;
    }

    @Override
    public List<FieldKey> getFieldKeys()
    {
        return _containerClause.getFieldKeys();
    }

    @Override
    public String getLabKeySQLWhereClause(Map<FieldKey, ? extends ColumnInfo> columnMap)
    {
        throw new UnsupportedOperationException();
    }

    // IDs of secure folders whose rows this user can't see at all, and those where they see only member-list threads
    private record RestrictedFolders(List<String> hidden, List<String> memberListOnly) {}

    private RestrictedFolders _restricted = null;

    // Computed once per clause, since a query can render the clause several times
    private RestrictedFolders getRestrictedFolders()
    {
        if (null == _restricted)
        {
            List<String> hidden = new ArrayList<>();
            List<String> memberListOnly = new ArrayList<>();

            // There are very few secure message boards in the world, so query for those first instead of querying the
            // settings for every candidate container
            getSecureContainers()
                .filter(c -> !c.hasPermission(_user, SecureMessageBoardReadPermission.class))
                .forEach(c -> {
                    Settings settings = AnnouncementManager.getMessageBoardSettings(c);
                    if (!settings.isSecureOn())
                        return;

                    if (settings.hasMemberList() && c.hasPermission(_user, ReadPermission.class))
                        memberListOnly.add(c.getId());
                    else
                        hidden.add(c.getId());
                });

            _restricted = new RestrictedFolders(hidden, memberListOnly);
        }

        return _restricted;
    }

    @Override
    public SQLFragment toSQLFragment(Map<FieldKey, ? extends ColumnInfo> columnMap, SqlDialect dialect)
    {
        SQLFragment sql = SQLFragment.unsafe("(").append(_containerClause.toSQLFragment(columnMap, dialect)).unsafeAppend(")");

        List<String> hidden = getRestrictedFolders().hidden();
        List<String> memberListOnly = getRestrictedFolders().memberListOnly();

        if (!hidden.isEmpty())
        {
            sql.unsafeAppend(" AND NOT ").append(_containerFieldKey).appendInClause(hidden, _dialect);
        }

        if (!memberListOnly.isEmpty())
        {
            CommSchema comm = CommSchema.getInstance();
            sql.unsafeAppend(" AND (NOT ").append(_containerFieldKey).appendInClause(memberListOnly, _dialect);
            sql.unsafeAppend(" OR ").append(_threadEntityIdSql).unsafeAppend(" IN (SELECT t.EntityId FROM ");
            sql.append(comm.getTableInfoThreads(), "t");
            sql.unsafeAppend(" INNER JOIN ");
            sql.append(comm.getTableInfoMemberList(), "ul");
            sql.unsafeAppend(" ON ul.MessageId = t.LatestId WHERE ul.UserId = ?", _user.getUserId());
            // Lets the planner restrict the Threads view's aggregation to these folders
            sql.unsafeAppend(" AND t.Container").appendInClause(memberListOnly, _dialect);
            sql.unsafeAppend("))");
        }

        return sql;
    }

    private static @NotNull Stream<Container> getSecureContainers()
    {
        return Arrays.stream(PropertyManager.findPropertyEntries(PropertyManager.SHARED_USER, null, AnnouncementManager.MESSAGE_BOARD_SETTINGS, "secure"))
            .filter(entry -> Settings.SECURE_WITH_EMAIL.equals(entry.getValue()) || Settings.SECURE_WITHOUT_EMAIL.equals(entry.getValue()))
            .map(entry -> ContainerManager.getForId(entry.getObjectId()))
            .filter(Objects::nonNull);
    }
}
