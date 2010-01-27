/*
 * Copyright (c) 2007-2009 LabKey Corporation
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

import org.labkey.api.data.*;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.PropertyColumn;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.*;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ActionURL;
import org.labkey.list.view.AttachmentDisplayColumn;
import org.labkey.list.view.ListController;

import java.util.*;

public class ListTable extends FilteredTable
{
    static public TableInfo getIndexTable(ListDefinition.KeyType keyType)
    {
        switch (keyType)
        {
            case Integer:
            case AutoIncrementInteger:
                return ListManager.get().getTinfoIndexInteger();
            case Varchar:
                return ListManager.get().getTinfoIndexVarchar();
            default:
                return null;
        }
    }

    private ListDefinition _list;

    public ListTable(User user, ListDefinition listDef)
    {
        super(getIndexTable(listDef.getKeyType()));
        setName(listDef.getName());
        setDescription(listDef.getDescription());
        _list = listDef;
        addCondition(getRealTable().getColumn("ListId"), listDef.getListId());

        // All columns visible by default, except for auto-increment integer
        List<FieldKey> defaultVisible = new ArrayList<FieldKey>();
        ColumnInfo colKey = wrapColumn(listDef.getKeyName(), getRealTable().getColumn("Key"));
        colKey.setKeyField(true);
        colKey.setInputType("text");
        colKey.setInputLength(-1);
        colKey.setScale(30);
        if (listDef.getKeyType().equals(ListDefinition.KeyType.AutoIncrementInteger))
        {
            colKey.setUserEditable(false);
            colKey.setAutoIncrement(true);
        }
        else
        {
            defaultVisible.add(FieldKey.fromParts(colKey.getName()));
        }
        addColumn(colKey);
        ColumnInfo colObjectId = wrapColumn(getRealTable().getColumn("ObjectId"));
        for (DomainProperty property : listDef.getDomain().getProperties())
        {
            PropertyColumn column = new PropertyColumn(property.getPropertyDescriptor(), colObjectId, null, user);
            column.setParentIsObjectId(true);
            column.setReadOnly(false);
            column.setScale(property.getScale()); // UNDONE: PropertyDescriptor does not have getScale() so have to set here, move to PropertyColumn
            safeAddColumn(column);
            if (property.isMvEnabled())
            {
                MVDisplayColumnFactory.addMvColumns(this, column, property, colObjectId, user);
            }
            if (!property.isHidden())
            {
                defaultVisible.add(FieldKey.fromParts(column.getName()));
            }

            if (property.getPropertyDescriptor().getPropertyType() == PropertyType.MULTI_LINE)
            {
                column.setDisplayColumnFactory(new DisplayColumnFactory() {
                    public DisplayColumn createRenderer(ColumnInfo colInfo)
                    {
                        DataColumn dc = new DataColumn(colInfo);
                        dc.setPreserveNewlines(true);
                        return dc;
                    }
                });
            }
            else if (property.getPropertyDescriptor().getPropertyType() == PropertyType.ATTACHMENT)
            {
                column.setDisplayColumnFactory(new DisplayColumnFactory() {
                    public DisplayColumn createRenderer(final ColumnInfo colInfo)
                    {
                        return new AttachmentDisplayColumn(colInfo);
                    }
                });
            }
        }

        setDefaultVisibleColumns(defaultVisible);

        setTitleColumn(findTitleColumn(listDef, colKey));

        // Make EntityId column available so AttachmentDisplayColumn can request it as a dependency
        // Do this last so the column doesn't get selected as title column, etc.
        ColumnInfo colEntityId = wrapColumn(getRealTable().getColumn("EntityId"));
        addColumn(colEntityId);

        DetailsURL gridURL = new DetailsURL(_list.urlShowData(), Collections.<String, String>emptyMap());
        setGridURL(gridURL);

        DetailsURL insertURL = new DetailsURL(_list.urlFor(ListController.Action.insert), Collections.<String, String>emptyMap());
        setInsertURL(insertURL);

        DetailsURL updateURL = new DetailsURL(_list.urlUpdate(null, null), Collections.singletonMap("pk", _list.getKeyName()));
        setUpdateURL(updateURL);

        DetailsURL detailsURL = new DetailsURL(_list.urlDetails(null), Collections.singletonMap("pk", _list.getKeyName()));
        setDetailsURL(detailsURL);
    }


    @Override
    public boolean hasContainerContext()
    {
        return null != _list && null != _list.getContainer();
    }

    @Override
    public Container getContainer(Map m)
    {
        return _list.getContainer();
    }


    @Override
    public StringExpression getDetailsURL(Set<FieldKey> columns, Container container)
    {
        return super.getDetailsURL(columns, container);
    }

    private String findTitleColumn(ListDefinition listDef, ColumnInfo colKey)
    {
        if (listDef.getTitleColumn() != null)
        {
            ColumnInfo titleColumn = getColumn(listDef.getTitleColumn());

            if (titleColumn != null)
                return titleColumn.getName();
        }

        // Title column setting is <AUTO> -- select the first string column
        for (ColumnInfo column : getColumns())
            if (column.isStringType())
                return column.getName();

        // No string columns -- fall back to pk (see issue #5452)
        return colKey.getName();
    }

    public ListDefinition getList()
    {
        return _list;
    }

    public boolean hasPermission(User user, Class<? extends Permission> perm)
    {
        return _list.getContainer().hasPermission(user, perm);
    }

    public ActionURL delete(User user, ActionURL srcURL, QueryUpdateForm form)
    {
        Set<String> ids = DataRegionSelection.getSelected(form.getViewContext(), true);
        try
        {
            _list.deleteListItems(user, ids);
        }
        catch (Exception e)
        {
            throw UnexpectedException.wrap(e);
        }
        return srcURL;
    }

    public String getPublicName()
    {
        return _list.getName();
    }

    public String getPublicSchemaName()
    {
        return ListSchema.NAME;
    }

    @Override
    public boolean isMetadataOverrideable()
    {
        return false;
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        return new ListQueryUpdateService(getList());
    }
}
