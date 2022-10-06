/*
 * Copyright (c) 2014-2019 LabKey Corporation
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
package org.labkey.list.model;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.util.HtmlString;

import java.util.Arrays;
import java.util.Set;

/**
 * Created by Joe on 8/19/2014.
 */
public class ListManagerTable extends FilteredTable<ListManagerSchema>
{
    public ListManagerTable(ListManagerSchema userSchema, TableInfo table, ContainerFilter cf)
    {
        super(table, userSchema, cf);

        addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("ListID")));
        {
            var column = addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("Name")));

            // Lists can contain data that spans multiple folders.
            // Override the container context for the details URL to always be the current folder.
            if (column.getURL() instanceof DetailsURL detailsURL)
                detailsURL.setContainerContext(getContainer());
        }
        addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("Description")));

        {
            var column = addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("Container")));
            column.setLabel("Folder");
        }
        addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("Created")));
        addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("CreatedBy")));
        addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("Modified")));
        addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("ModifiedBy")));
        addWrapColumn( _rootTable.getColumn(FieldKey.fromParts("Category")));
        MutableColumnInfo sharingCol = addWrapColumn("Sharing", _rootTable.getColumn(FieldKey.fromParts("Category")));
        sharingCol.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            @Override
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new ListCategoryColumn(colInfo);
            }

            class ListCategoryColumn extends DataColumn
            {
                public ListCategoryColumn(ColumnInfo col) { super(col, false); }

                @Override
                public Object getValue(RenderContext ctx)
                {
                    String category = (String) super.getValue(ctx);

                    if (ListDefinition.Category.PublicPicklist.toString().equals(category))
                        return "public";
                    else if (ListDefinition.Category.PrivatePicklist.toString().equals(category))
                        return "private";
                    return null;
                }

                @Override
                public Object getDisplayValue(RenderContext ctx)
                {
                    return this.getValue(ctx);
                }

                @Override
                public @NotNull HtmlString getFormattedHtml(RenderContext ctx)
                {
                    return HtmlString.of(getDisplayValue(ctx));
                }
            }
        });

        MutableColumnInfo countCol = addWrapColumn("ItemCount", _rootTable.getColumn(FieldKey.fromParts("ListID")));
        countCol.setHidden(true);
        countCol.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            @Override
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new ListItemCountColumn(colInfo);
            }

            class ListItemCountColumn extends DataColumn
            {
                public ListItemCountColumn(ColumnInfo col)
                {
                    super(col, false);
                }

                @Override
                public void addQueryFieldKeys(Set<FieldKey> keys)
                {
                    super.addQueryFieldKeys(keys);
                    keys.add(FieldKey.fromParts("ListID"));
                    keys.add(FieldKey.fromParts("Container"));
                }

                @Override
                public Object getValue(RenderContext ctx)
                {
                    Integer listId = (Integer) ctx.get("ListID");
                    String listContainerId = (String) ctx.get("Container");
                    Container listContainer = ContainerManager.getForId(listContainerId);
                    if (listId == null || listContainer == null)
                        return 0;

                    ListDefinition list = ListService.get().getList(listContainer, listId);
                    if (list == null)
                        return 0;

                    User user = userSchema.getUser();
                    ContainerFilter cf = ListService.get().getPicklistContainerFilter(getContainer(), user, list);
                    if (cf == null)
                        cf = getContainerFilter();

                    TableInfo listTable = list.getTable(user, listContainer, cf);
                    return new TableSelector(listTable).getRowCount();
                }

                @Override
                public Object getDisplayValue(RenderContext ctx)
                {
                    return this.getValue(ctx);
                }

                @Override
                public @NotNull HtmlString getFormattedHtml(RenderContext ctx)
                {
                    return HtmlString.of(getDisplayValue(ctx));
                }
            }
        });

        setDefaultVisibleColumns(Arrays.asList(
            FieldKey.fromParts("Name"),
            FieldKey.fromParts("Description"),
            FieldKey.fromParts("Container")
        ));
    }

    @Override
    public String getPublicName()
    {
        return ListManagerSchema.LIST_MANAGER;
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        return getContainer().hasPermission(this.getClass().getName() + " " + getName(), user, perm);
    }
}
