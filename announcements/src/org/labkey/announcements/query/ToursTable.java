/*
 * Copyright (c) 2015-2018 LabKey Corporation
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
import org.jetbrains.annotations.Nullable;
import org.labkey.announcements.ToursController;
import org.labkey.announcements.model.TourManager;
import org.labkey.announcements.model.TourModel;
import org.labkey.api.announcements.CommSchema;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.RenderContext;
import org.labkey.api.query.AbstractBeanQueryUpdateService;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.UserIdQueryForeignKey;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.PlatformDeveloperPermission;
import org.labkey.api.security.permissions.ReadPermission;

import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;


/**
 * Created by Marty on 1/16/2015.
 */
public class ToursTable extends FilteredTable<AnnouncementSchema>
{

    public enum Modes {
        OFF(0), RUNONCE(1), RUNALWAYS(2);

        private final int value;

        Modes(int value)
        {
            this.value = value;
        }

        @Override
        public String toString()
        {
            switch(this)
            {
                case RUNONCE:
                    return "Run Once";

                case RUNALWAYS:
                    return "Run Always";

                default:
                    return "Off";
            }
        }

        @Nullable
        static Modes fromValue(int value)
        {
            for (Modes m : Modes.values())
            {
                if (m.value == value)
                    return m;
            }
            return null;
        }
    }

    public ToursTable(AnnouncementSchema schema, ContainerFilter cf)
    {
        super(CommSchema.getInstance().getTableInfoTours(), schema, cf);

        //
        // Handle columns
        //
        wrapAllColumns(true);

        getMutableColumn("EntityId").setHidden(true);
        getMutableColumn("RowId").setHidden(true);
        getMutableColumn("Json").setHidden(true);

        var containerCol = getMutableColumn("Container");
        containerCol.setLabel("Folder");
        ContainerForeignKey.initColumn(containerCol, schema);

        getMutableColumn("CreatedBy").setFk(new UserIdQueryForeignKey(_userSchema, true));
        getMutableColumn("ModifiedBy").setFk(new UserIdQueryForeignKey(_userSchema, true));

        var modeCol = getMutableColumn("Mode");
        modeCol.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            public DisplayColumn createRenderer(final ColumnInfo colInfo)
            {
                DataColumn dc = new DataColumn(colInfo)
                {
                    @Override
                    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
                    {
                        Object value = getValue(ctx);
                        Modes m = Modes.fromValue((Integer)value);
                        if(null != m)
                            out.write(m.toString());
                        else
                            out.write(value.toString());
                    }
                };

                return dc;
            }

        });

        setDescription("Contains one row per tour.");
        setName(AnnouncementSchema.TOURS_TABLE_NAME);
        setPublicSchemaName(AnnouncementSchema.SCHEMA_NAME);

        setInsertURL(new DetailsURL(ToursController.getEditTourURL(getContainer())));

        Map<String,String> params = new HashMap<>();
        params.put("rowid", "rowid");
        setUpdateURL(new DetailsURL(ToursController.getEditTourURL(getContainer()), params));
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        boolean permission;

        if (perm.equals(ReadPermission.class))
            permission = getContainer().hasPermission(user, perm);
        else
            permission = ContainerManager.getRoot().hasPermission(user, PlatformDeveloperPermission.class);

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
        protected Integer keyFromMap(Map<String, Object> map)
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
        protected TourModel get(User user, Container container, Integer key)
        {
            return TourManager.getTour(container, key);
        }

        @Override
        protected TourModel insert(User user, Container container, TourModel bean)
        {
            return TourManager.insertTour(container, user, bean);
        }

        @Override
        protected TourModel update(User user, Container container, TourModel bean, Integer oldKey)
        {
            return TourManager.updateTour(user, bean);
        }

        @Override
        protected void delete(User user, Container container, Integer key)
        {
            TourManager.deleteTour(container, key.intValue());
        }
    }
}
