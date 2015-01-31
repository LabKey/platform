/*
 * Copyright (c) 2014 LabKey Corporation
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

import org.apache.commons.beanutils.ConvertUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.announcements.model.TourManager;
import org.labkey.announcements.model.TourModel;
import org.labkey.api.announcements.CommSchema;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.query.AbstractBeanQueryUpdateService;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.Group;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;

import java.sql.SQLException;
import java.util.Map;


/**
 * Created by Marty on 1/16/2015.
 */
public class ToursTable extends FilteredTable<AnnouncementSchema>
{

    public ToursTable(AnnouncementSchema schema)
    {
        super(CommSchema.getInstance().getTableInfoTours(), schema);

        //
        // Handle columns
        //
        wrapAllColumns(true);

        getColumn("EntityId").setHidden(true);
        getColumn("RowId").setHidden(true);
        getColumn("Json").setHidden(true);

        ColumnInfo containerCol = getColumn("Container");
        containerCol.setLabel("Folder");
        ContainerForeignKey.initColumn(containerCol, schema);

        setDescription("Contains one row per tour.");
        setName(AnnouncementSchema.TOURS_TABLE_NAME);
        setPublicSchemaName(AnnouncementSchema.SCHEMA_NAME);
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        boolean permission;

        if (perm.equals(ReadPermission.class))
            permission = getContainer().hasPermission(user, perm);
        else if (user instanceof User)
            permission = ((User) user).isDeveloper();
        else
            permission = user.isInGroup(Group.groupDevelopers);

        return permission;
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        return new TourUpdateService();
    }

    private class TourUpdateService extends AbstractBeanQueryUpdateService<TourModel, Integer>
    {
        protected TourUpdateService()
        {
            super(ToursTable.this);
        }

        @Override
        protected TourModel createNewBean()
        {
            return new TourModel();
        }

        @Override
        protected Integer keyFromMap(Map<String, Object> map) throws InvalidKeyException
        {
            Object rowId = map.get("RowId");
            if (rowId != null)
            {
                return (Integer) ConvertUtils.convert(rowId.toString(), Integer.class);
            }
            Object entityId = map.get("EntityId");
            if (entityId != null)
            {
                TourModel model = TourManager.getTour(getContainer(), entityId.toString());
                if (model != null)
                {
                    return model.getRowId();
                }
            }
            return null;
        }

        @Override
        protected TourModel get(User user, Container container, Integer key) throws QueryUpdateServiceException, SQLException
        {
            return TourManager.getTour(container, key);
        }

        @Override
        protected TourModel insert(User user, Container container, TourModel bean) throws ValidationException, DuplicateKeyException, QueryUpdateServiceException, SQLException
        {
            return TourManager.insertTour(container, user, bean);
        }

        @Override
        protected TourModel update(User user, Container container, TourModel bean, Integer oldKey) throws ValidationException, QueryUpdateServiceException, SQLException
        {
            return TourManager.updateTour(user, bean);
        }

        @Override
        protected void delete(User user, Container container, Integer key) throws QueryUpdateServiceException, SQLException
        {
            TourManager.deleteTour(container, key.intValue());
        }
    }
}
