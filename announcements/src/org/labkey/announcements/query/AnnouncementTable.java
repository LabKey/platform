/*
 * Copyright (c) 2012-2019 LabKey Corporation
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
import org.labkey.announcements.model.AnnouncementManager;
import org.labkey.announcements.model.AnnouncementModel;
import org.labkey.api.announcements.CommSchema;
import org.labkey.api.announcements.DiscussionService;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.AbstractBeanQueryUpdateService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.UserIdQueryForeignKey;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.wiki.WikiRendererDisplayColumn;
import org.labkey.api.wiki.WikiRendererType;
import org.labkey.api.wiki.WikiService;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

/**
 * User: jeckels
 * Date: Feb 5, 2012
 */
public class AnnouncementTable extends FilteredTable<AnnouncementSchema>
{
    private Boolean _secure;

    public AnnouncementTable(AnnouncementSchema schema, ContainerFilter cf)
    {
        // Standard usage omits unapproved announcements
        this(schema, cf, AnnouncementManager.IS_APPROVED_FILTER);
    }

    public AnnouncementTable(AnnouncementSchema schema, ContainerFilter cf, SimpleFilter filter)
    {
        super(CommSchema.getInstance().getTableInfoAnnouncements(), schema, cf);
        addCondition(filter);
        wrapAllColumns(true);
        removeColumn(getColumn("Container"));
        removeColumn(getColumn("Approved"));
        var folderColumn = wrapColumn("Folder", getRealTable().getColumn("Container"));
        folderColumn.setFk(new ContainerForeignKey(_userSchema));
        addColumn(folderColumn);
        setDescription("Contains one row per announcement or reply");
        getMutableColumn("Parent").setFk(new LookupForeignKey(cf,"EntityId", null)
        {
            @Override
            public TableInfo getLookupTableInfo()
            {
                AnnouncementTable result = new AnnouncementTable(_userSchema, getLookupContainerFilter());
                result.addCondition(new SimpleFilter(FieldKey.fromParts("Parent"), null, CompareType.ISBLANK));
                result.setPublic(false);
                return result;
            }
        });
        final var renderTypeColumn = getMutableColumn("RendererType");

        WikiService ws = WikiService.get();

        if (null != ws)
        {
            renderTypeColumn.setFk(new LookupForeignKey("Value")
            {
                @Override
                public TableInfo getLookupTableInfo()
                {
                    return ws.getRendererTypeTable(_userSchema.getUser(), _userSchema.getContainer());
                }
            });
        }

        var bodyColumn = getMutableColumn("Body");
        bodyColumn.setHidden(true);
        bodyColumn.setShownInDetailsView(false);

        var formattedBodyColumn = wrapColumn("FormattedBody", getRealTable().getColumn("Body"));
        formattedBodyColumn.setDisplayColumnFactory(colInfo -> new WikiRendererDisplayColumn(colInfo, renderTypeColumn.getName(), WikiRendererType.TEXT_WITH_LINKS));
        addColumn(formattedBodyColumn);
        formattedBodyColumn.setReadOnly(true);
        formattedBodyColumn.setUserEditable(false);
        formattedBodyColumn.setShownInInsertView(false);
        formattedBodyColumn.setShownInDetailsView(true);

        getMutableColumn("CreatedBy").setFk(new UserIdQueryForeignKey(_userSchema, true));
        getMutableColumn("ModifiedBy").setFk(new UserIdQueryForeignKey(_userSchema, true));
        getMutableColumn("AssignedTo").setFk(new UserIdQueryForeignKey(_userSchema, true));

        setName(AnnouncementSchema.ANNOUNCEMENT_TABLE_NAME);
        setPublicSchemaName(AnnouncementSchema.SCHEMA_NAME);
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        return _userSchema.getContainer().hasPermission(user, perm);
    }

    private boolean isSecure()
    {
        if (_secure == null)
        {
            _secure = DiscussionService.get().getSettings(_userSchema.getContainer()).isSecure();
        }
        return _secure;
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        return new AnnouncementUpdateService();
    }

    private class AnnouncementUpdateService extends AbstractBeanQueryUpdateService<AnnouncementModel, Integer>
    {
        protected AnnouncementUpdateService()
        {
            super(AnnouncementTable.this);
        }

        @Override
        protected AnnouncementModel createNewBean()
        {
            return new AnnouncementModel();
        }

        @Override
        protected Integer keyFromMap(Map<String, Object> map)
        {
            Object rowId = map.get("RowId");
            if (rowId != null)
            {
                return (Integer)ConvertUtils.convert(rowId.toString(), Integer.class);
            }
            Object entityId = map.get("EntityId");
            if (entityId != null)
            {
                AnnouncementModel model = AnnouncementManager.getAnnouncement(getContainer(), entityId.toString());
                if (model != null)
                {
                    return model.getRowId();
                }
            }
            return null;
        }

        @Override
        protected AnnouncementModel get(User user, Container container, Integer key) throws QueryUpdateServiceException
        {
            ensureNotSecure();
            return AnnouncementManager.getAnnouncement(container, key);
        }

        @Override
        protected AnnouncementModel insert(User user, Container container, AnnouncementModel bean) throws QueryUpdateServiceException
        {
            ensureNotSecure();
            try
            {
                return AnnouncementManager.insertAnnouncement(container, user, bean, Collections.emptyList());
            }
            catch (IOException e)
            {
                throw new QueryUpdateServiceException(e);
            }
        }

        @Override
        protected AnnouncementModel update(User user, Container container, AnnouncementModel bean, Integer oldKey) throws QueryUpdateServiceException
        {
            ensureNotSecure();
            try
            {
                return AnnouncementManager.updateAnnouncement(user, bean, Collections.emptyList());
            }
            catch (IOException e)
            {
                throw new QueryUpdateServiceException(e);
            }

        }

        private void ensureNotSecure() throws QueryUpdateServiceException
        {
            if (isSecure())
            {
                throw new QueryUpdateServiceException("Not supported for secure message boards");
            }
        }

        @Override
        protected void delete(User user, Container container, Integer key) throws QueryUpdateServiceException
        {
            ensureNotSecure();
            AnnouncementManager.deleteAnnouncement(container, key.intValue());
        }
    }
}
