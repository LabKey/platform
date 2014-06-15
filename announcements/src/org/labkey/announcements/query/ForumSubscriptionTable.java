/*
 * Copyright (c) 2012-2014 LabKey Corporation
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
import org.labkey.api.announcements.CommSchema;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.ContainerManager;
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
import org.labkey.api.query.UserIdQueryForeignKey;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * User: jeckels
 * Date: Feb 6, 2012
 */
public class ForumSubscriptionTable extends AbstractSubscriptionTable
{
    public ForumSubscriptionTable(AnnouncementSchema schema)
    {
        super(CommSchema.getInstance().getTableInfoEmailPrefs(), schema);

        ColumnInfo folderColumn = wrapColumn("Folder", getRealTable().getColumn("Container"));
        addColumn(folderColumn);
        folderColumn.setFk(new ContainerForeignKey(_userSchema));

        ColumnInfo modifiedByColumn = wrapColumn("ModifiedBy", getRealTable().getColumn("LastModifiedBy"));
        addColumn(modifiedByColumn);
        modifiedByColumn.setFk(new UserIdQueryForeignKey(_userSchema.getUser(), getContainer(), true));
        modifiedByColumn.setUserEditable(false);

        ColumnInfo emailOptionColumn = wrapColumn("EmailOption", getRealTable().getColumn("EmailOptionId"));
        emailOptionColumn.setFk(new LookupForeignKey("EmailOptionId")
        {
            @Override
            public TableInfo getLookupTableInfo()
            {
                return _userSchema.createEmailOptionTable();
            }
        });
        addColumn(emailOptionColumn);

        ColumnInfo emailFormatColumn = wrapColumn("EmailFormat", getRealTable().getColumn("EmailFormatId"));
        emailFormatColumn.setFk(new LookupForeignKey("EmailFormatId")
        {
            @Override
            public TableInfo getLookupTableInfo()
            {
                return _userSchema.createEmailFormatTable();
            }
        });
        addColumn(emailFormatColumn);

        addCondition(getRealTable().getColumn("Type"), "messages");

        addWrapColumn(getRealTable().getColumn("SrcIdentifier"));
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        return hasPermission(user, perm, getContainer());
    }

    public boolean hasPermission(UserPrincipal user, Class<? extends Permission> perm, Container container)
    {
        // Guests can't subscribe to anything, or edit anyone else's subscriptions, but they can read the table
        // It'll have no rows for them
        if (user.isGuest() && !ReadPermission.class.equals(perm))
        {
            return false;
        }

        // For authenticated users, if they have read access, that's enough to edit their own subscription level
        // The QueryUpdateService implementation will make sure they have permission to insert/update/delete at the row
        // level.
        return container.hasPermission(user, ReadPermission.class);
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        return new ForumSubscriptionUpdateService(this);
    }

    private static class SubscriptionTarget
    {
        @NotNull private User _user;
        @NotNull private Container _container;
        @NotNull private String _srcIdentifier;

        private SubscriptionTarget(@NotNull User user, @NotNull Container container, @NotNull String srcIdentifier)
        {
            _user = user;
            _container = container;
            _srcIdentifier = srcIdentifier;
        }

        @NotNull
        public User getUser()
        {
            return _user;
        }

        @NotNull
        public Container getContainer()
        {
            return _container;
        }

        @NotNull
        public String getSrcIdentifier()
        {
            return _srcIdentifier;
        }

        public SimpleFilter createUserSchemaFilter()
        {
            return createFilter("User", "Folder");
        }

        public SimpleFilter createDbSchemaFilter()
        {
            SimpleFilter result = createFilter("UserId", "Container");
            result.addCondition(FieldKey.fromParts("Type"), "messages");
            return result;
        }

        private SimpleFilter createFilter(String userColumnName, String containerColumnName)
        {
            SimpleFilter filter = new SimpleFilter(userColumnName, getUser().getUserId());
            filter.addCondition(containerColumnName, getContainer().getEntityId());
            filter.addCondition(FieldKey.fromParts("SrcIdentifier"), _srcIdentifier);
            return filter;
        }
    }

    private class ForumSubscriptionUpdateService extends AbstractQueryUpdateService
    {

        protected ForumSubscriptionUpdateService(TableInfo queryTable)
        {
            super(queryTable);
        }

        private SubscriptionTarget getTargets(Map<String, Object> row, User user, Container container) throws InvalidKeyException
        {
            // Assume that if no User is specified, we should apply it to the current user
            User targetUser = user;
            Object userId = row.get("User");
            if (userId == null)
            {
                userId = row.get("_user");
            }
            if (userId != null)
            {
                try
                {
                    targetUser = UserManager.getUser(Integer.parseInt(userId.toString()));
                }
                catch (NumberFormatException e)
                {
                    throw new InvalidKeyException("Invalid User value: " + userId);
                }
            }

            // Assume that if no Folder is specified, we should apply it to the current container
            Container targetContainer = container;
            Object folderId = row.get("Folder");
            if (folderId != null)
            {
                targetContainer = ContainerManager.getForId(folderId.toString());
                if (targetContainer == null)
                {
                    throw new InvalidKeyException("No such Folder: " + folderId.toString());
                }
            }

            ensurePermission(user, targetUser, targetContainer);

            Object srcIdentifier = row.get("SrcIdentifier");
            if (srcIdentifier == null || "".equals(srcIdentifier))
            {
                srcIdentifier = targetContainer.getId();
            }

            return new SubscriptionTarget(targetUser, targetContainer, srcIdentifier.toString());
        }

        @Override
        protected Map<String, Object> getRow(User user, Container container, Map<String, Object> keys) throws InvalidKeyException, QueryUpdateServiceException
        {
            return new TableSelector(ForumSubscriptionTable.this).getObject(getTargets(keys, user, container).createUserSchemaFilter(), Map.class);
        }

        @Override
        protected Map<String, Object> insertRow(User user, Container container, Map<String, Object> row) throws DuplicateKeyException, ValidationException, QueryUpdateServiceException
        {
            try
            {
                SubscriptionTarget targets = getTargets(row, user, container);

                if (getRow(user, container, row) != null)
                {
                    throw new DuplicateKeyException("There is already a row for " + targets.getUser() + " in " + targets.getContainer().getPath() + " with SrcIdentifier " + targets.getSrcIdentifier());
                }

                Map<String, Object> insertMap = createDatabaseMap(user, row, targets);

                Table.insert(user, CommSchema.getInstance().getTableInfoEmailPrefs(), insertMap);

                return getRow(user, container, row);
            }
            catch (InvalidKeyException e)
            {
                throw new QueryUpdateServiceException(e);
            }

        }

        /** Translate to the real database column names */
        private Map<String, Object> createDatabaseMap(User user, Map<String, Object> row, SubscriptionTarget targets)
        {
            Map<String, Object> insertMap = new HashMap<>();
            insertMap.put("UserId", targets.getUser().getUserId());
            insertMap.put("Container", targets.getContainer().getEntityId());
            insertMap.put("SrcIdentifier", targets.getSrcIdentifier());
            insertMap.put("LastModifiedBy", user.getUserId());
            if (row.containsKey("EmailOption"))
            {
                insertMap.put("EmailOptionId", row.get("EmailOption"));
            }
            if (row.containsKey("EmailFormat"))
            {
                insertMap.put("EmailFormatId", row.get("EmailFormat"));
            }
            insertMap.put("PageTypeId", AnnouncementSchema.PAGE_TYPE_ID);
            return insertMap;
        }

        @Override
        protected Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, @NotNull Map<String, Object> oldRow) throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
        {
            Map<String, Object> existingRow = getRow(user, container, oldRow);
            if (existingRow != null)
            {
                SubscriptionTarget oldTargets = getTargets(oldRow, user, container);
                SubscriptionTarget newTargets = getTargets(row, user, container);
                Map<String, Object> pks = new CaseInsensitiveHashMap<>();
                pks.put("UserId", oldTargets.getUser().getUserId());
                pks.put("Container", oldTargets.getContainer().getEntityId());
                pks.put("Type", "messages");
                pks.put("SrcIdentifier", oldTargets.getSrcIdentifier());
                Table.update(user, CommSchema.getInstance().getTableInfoEmailPrefs(), createDatabaseMap(user, row, newTargets), pks);
            }

            return getRow(user, container, row);
        }

        @Override
        protected Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRow) throws InvalidKeyException
        {
            SubscriptionTarget targets = getTargets(oldRow, user, container);
            Table.delete(CommSchema.getInstance().getTableInfoEmailPrefs(), targets.createDbSchemaFilter());

            return oldRow;
        }
    }
}
