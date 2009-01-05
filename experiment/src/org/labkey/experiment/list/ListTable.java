/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.experiment.list;

import org.labkey.api.data.*;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.query.*;
import org.labkey.api.security.ACL;
import org.labkey.api.security.User;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ActionURL;
import org.labkey.experiment.controllers.list.AttachmentDisplayColumn;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
        _list = listDef;
        AliasManager aliasManager = new AliasManager(ListManager.get().getSchema());
        addCondition(getRealTable().getColumn("ListId"), listDef.getListId());

        // All columns visible by default, except for auto-increment integer
        List<FieldKey> defaultVisible = new ArrayList<FieldKey>();
        ColumnInfo colKey = wrapColumn(listDef.getKeyName(), getRealTable().getColumn("Key"));
        colKey.setAlias(aliasManager.decideAlias(colKey.getName()));
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
            ColumnInfo column = new ExprColumn(this, property.getName(),
                    PropertyForeignKey.getValueSql(colObjectId.getValueSql(ExprColumn.STR_TABLE_ALIAS), property.getValueSQL(), property.getPropertyId(), false), property.getSqlType());
            column.setAlias(aliasManager.decideAlias(column.getName()));
            column.setScale(property.getScale());
            column.setInputType(property.getInputType());
            column.setDescription(property.getDescription());
            property.initColumn(user, column);
            safeAddColumn(column);
            defaultVisible.add(FieldKey.fromParts(column.getName()));

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
        }

        setDefaultVisibleColumns(defaultVisible);

        // Do separate pass for safety -- we must wait until column initialization is complete (FKs are set, etc.) to
        // check getInputType(), otherwise it may stash an incorrect, intermediate result.
        for (ColumnInfo col : getColumns())
        {
            if ("file".equals(col.getInputType()))
            {
                col.setDisplayColumnFactory(new DisplayColumnFactory() {
                    public DisplayColumn createRenderer(final ColumnInfo colInfo)
                    {
                        return new AttachmentDisplayColumn(colInfo);
                    }
                });
            }
        }

        setTitleColumn(findTitleColumn(listDef, colKey));

        // Make EntityId column available so AttachmentDisplayColumn can request it as a dependency
        // Do this last so the column doesn't get selected as title column, etc.
        ColumnInfo colEntityId = wrapColumn(getRealTable().getColumn("EntityId"));
        addColumn(colEntityId);
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

    public boolean hasPermission(User user, int perm)
    {
        if ((perm & ~ACL.PERM_DELETE) != 0)
            return false;
        if ((perm & ACL.PERM_DELETE) != 0 && !_list.getContainer().hasPermission(user, ACL.PERM_DELETE))
        {
            return false;
        }
        return true;
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
}
