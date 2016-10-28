/*
 * Copyright (c) 2012-2016 LabKey Corporation
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
import org.labkey.announcements.model.AnnouncementModel;
import org.labkey.api.announcements.CommSchema;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.AbstractQueryUpdateService;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.util.Pair;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * User: jeckels
 * Date: Feb 6, 2012
 */
public class AnnouncementSubscriptionTable extends AbstractSubscriptionTable
{
    public AnnouncementSubscriptionTable(AnnouncementSchema schema)
    {
        super(CommSchema.getInstance().getTableInfoUserList(), schema);

        ColumnInfo announcementColumn = wrapColumn("Announcement", getRealTable().getColumn("MessageId"));
        addColumn(announcementColumn);
        announcementColumn.setFk(new LookupForeignKey("RowId")
        {
            @Override
            public TableInfo getLookupTableInfo()
            {
                AnnouncementTable result = _userSchema.createAnnouncementTable();
                result.addCondition(new SimpleFilter(FieldKey.fromParts("Parent"), null, CompareType.ISBLANK));
                return result;
            }
        });

        applyContainerFilter(getContainerFilter());
    }

    @Override
    protected void applyContainerFilter(ContainerFilter filter)
    {
        // We need to filter on the announcement's container, since we don't have a container column directly on userlist
        getFilter().deleteConditions(FieldKey.fromParts("Container"));

        SQLFragment sql = new SQLFragment("MessageId IN (SELECT RowId FROM ");
        sql.append(CommSchema.getInstance().getTableInfoAnnouncements(), "a");
        sql.append(" WHERE ");
        sql.append(filter.getSQLFragment(getSchema(), new SQLFragment("a.Container"), getContainer()));
        sql.append(")");

        addCondition(sql, FieldKey.fromParts("Container"));
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        return new AnnouncementSubscriptionUpdateService(this);
    }

    private class AnnouncementSubscriptionUpdateService extends AbstractQueryUpdateService
    {
        protected AnnouncementSubscriptionUpdateService(TableInfo queryTable)
        {
            super(queryTable);
        }

        private Pair<User, AnnouncementModel> getTargets(Map<String, Object> row, User user) throws InvalidKeyException
        {
            User targetUser = getTargetUser(row, user);

            Object announcementId = row.get("Announcement");
            if (announcementId == null)
            {
                throw new InvalidKeyException("Announcement is required");
            }
            try
            {
                AnnouncementModel announcement = AnnouncementManager.getAnnouncement(null, Integer.parseInt(announcementId.toString()));
                if (announcement == null)
                {
                    throw new InvalidKeyException("No such Announcement: " + announcementId);
                }

                ensurePermission(user, targetUser, announcement.lookupContainer());

                return new Pair<>(targetUser, announcement);
            }
            catch (NumberFormatException e)
            {
                throw new InvalidKeyException("Invalid Announcement value: " + announcementId);
            }
        }

        @Override
        protected Map<String, Object> getRow(User user, Container container, Map<String, Object> keys) throws InvalidKeyException, QueryUpdateServiceException
        {
            Pair<User, AnnouncementModel> targets = getTargets(keys, user);
            SimpleFilter filter = createFilter(targets);
            return new TableSelector(AnnouncementSubscriptionTable.this, filter, null).getMap();
        }

        private SimpleFilter createFilter(Pair<User, AnnouncementModel> targets)
        {
            SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("User"), targets.getKey().getUserId());
            filter.addCondition(FieldKey.fromParts("Announcement"), targets.getValue().getRowId());
            return filter;
        }

        @Override
        protected Map<String, Object> insertRow(User user, Container container, Map<String, Object> row) throws DuplicateKeyException, ValidationException, QueryUpdateServiceException
        {
            try
            {
                Pair<User, AnnouncementModel> targets = getTargets(row, user);

                if (getRow(user, container, row) != null)
                {
                    throw new DuplicateKeyException("There is already a row for " + targets.getKey() + " and announcement " + targets.getValue().getRowId());
                }

                Map<String, Object> insertMap = createDatabaseMap(targets);

                Table.insert(user, getRealTable(), insertMap);

                return getRow(user, container, row);
            }
            catch (InvalidKeyException e)
            {
                throw new QueryUpdateServiceException(e);
            }

        }

        /** Translate to the real database column names */
        private Map<String, Object> createDatabaseMap(Pair<User, AnnouncementModel> targets)
        {
            Map<String, Object> insertMap = new HashMap<>();
            insertMap.put("UserId", targets.getKey().getUserId());
            insertMap.put("MessageId", targets.getValue().getRowId());
            return insertMap;
        }

        @Override
        protected Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, @NotNull Map<String, Object> oldRow) throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
        {
            Map<String, Object> existingRow = getRow(user, container, oldRow);
            if (existingRow != null)
            {
                Pair<User, AnnouncementModel> oldTargets = getTargets(oldRow, user);
                Pair<User, AnnouncementModel> newTargets = getTargets(row, user);
                Map<String, Object> pks = new CaseInsensitiveHashMap<>();
                pks.put("UserId", oldTargets.getKey().getUserId());
                pks.put("MessageId", oldTargets.getValue().getRowId());
                Table.update(user, getRealTable(), createDatabaseMap(newTargets), pks);
            }

            return getRow(user, container, row);
        }

        @Override
        protected Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRow) throws InvalidKeyException, QueryUpdateServiceException, SQLException
        {
            Pair<User, AnnouncementModel> targets = getTargets(oldRow, user);
            Table.delete(getRealTable(), new SimpleFilter(FieldKey.fromParts("UserId"), targets.getKey().getUserId()).addCondition(FieldKey.fromParts("MessageId"), targets.getValue().getRowId()));

            return oldRow;
        }
    }
}
