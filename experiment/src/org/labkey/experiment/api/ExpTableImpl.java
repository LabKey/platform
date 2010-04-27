/*
 * Copyright (c) 2006-2010 LabKey Corporation
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

package org.labkey.experiment.api;

import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.*;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.exp.query.ExpTable;
import org.labkey.api.query.*;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.view.ActionURL;
import org.labkey.experiment.api.flag.FlagColumnRenderer;
import org.labkey.experiment.api.flag.FlagForeignKey;

import java.util.Map;
import java.util.TreeMap;

abstract public class ExpTableImpl<C extends Enum> extends FilteredTable implements ExpTable<C>
{
    protected final UserSchema _schema;
    private final ExpObjectImpl _objectType;

    protected ExpTableImpl(String name, TableInfo rootTable, UserSchema schema, ExpObjectImpl objectType)
    {
        super(rootTable, schema.getContainer());
        _objectType = objectType;
        setName(name);
        _schema = schema;
    }

    @Override
    protected ColumnInfo resolveColumn(String name)
    {
        ColumnInfo result = super.resolveColumn(name);
        if (result == null && "Container".equalsIgnoreCase(name))
        {
            return getColumn("Folder");
        }
        for (ColumnInfo columnInfo : getColumns())
        {
            if (name.equalsIgnoreCase(columnInfo.getLabel()))
            {
                return columnInfo;
            }
        }
        return result;
    }

    protected ColumnInfo addContainerColumn(C containerCol, ActionURL url)
    {
        ColumnInfo result = addColumn(containerCol);
        ContainerForeignKey.initColumn(result, url);
        return result;
    }

    final public ColumnInfo addColumn(C column)
    {
        return addColumn(column.toString(), column);
    }

    final public ColumnInfo addColumn(String alias, C column)
    {
        ColumnInfo ret = createColumn(alias, column);
        addColumn(ret);
        return ret;
    }

    public ColumnInfo getColumn(C column)
    {
        for (ColumnInfo info : getColumns())
        {
            if (info instanceof ExprColumn && info.getAlias().equals(column.toString()))
            {
                return info;
            }
        }
        return null;
    }

    protected ColumnInfo doAdd(ColumnInfo column)
    {
        addColumn(column);
        return column;
    }

    public ColumnInfo createPropertyColumn(String name)
    {
        return wrapColumn(name, getLSIDColumn());
    }

    public ColumnInfo createUserColumn(String name, ColumnInfo userIdColumn)
    {
        ColumnInfo ret = wrapColumn(name, userIdColumn);
        ret.setFk(new UserIdForeignKey());
        return ret;
    }

    public String urlFlag(boolean flagged)
    {
        assert _objectType != null : "No ExpObject configured for ExpTable type: " + getClass();
        return _objectType.urlFlag(flagged);
    }

    protected ColumnInfo getLSIDColumn()
    {
        return _rootTable.getColumn("LSID");
    }

    protected ColumnInfo createFlagColumn(String alias)
    {
        ColumnInfo ret = wrapColumn(alias, getLSIDColumn());
        ret.setFk(new FlagForeignKey(urlFlag(true), urlFlag(false), _schema.getUser()));
        ret.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new FlagColumnRenderer(colInfo);
            }
        });
        ret.setDescription("Contains a reference to a user-editable comment about this row");
        ret.setNullable(true);
        ret.setInputType("text");
        return ret;
    }

    public void addRowIdCondition(SQLFragment condition)
    {
        SQLFragment sqlCondition = new SQLFragment("RowId ");
        sqlCondition.append(condition);
        addCondition(sqlCondition);
    }

    public void addLSIDCondition(SQLFragment condition)
    {
        SQLFragment sqlCondition = new SQLFragment("LSID ");
        sqlCondition.append(condition);
        addCondition(sqlCondition);
    }

    public boolean hasPermission(User user, Class<? extends Permission> perm)
    {
        if (getUpdateService() != null)
            return DeletePermission.class.isAssignableFrom(perm) && _schema.getContainer().hasPermission(user, perm);
        return false;
    }

    public ColumnInfo addPropertyColumns(String categoryDescription, PropertyDescriptor[] pds, QuerySchema schema)
    {
        ColumnInfo colProperty = wrapColumn(categoryDescription, getLSIDColumn()); 
        Map<String, PropertyDescriptor> map = new TreeMap<String, PropertyDescriptor>();
        for(PropertyDescriptor pd : pds)
        {
            map.put(pd.getName(), pd);
        }
        colProperty.setFk(new PropertyForeignKey(map, schema));
        colProperty.setIsUnselectable(true);
        addColumn(colProperty);

        return colProperty;
    }

    public ExpSchema getExpSchema()
    {
        if (_schema instanceof ExpSchema)
        {
            return (ExpSchema)_schema;
        }
        ExpSchema schema = new ExpSchema(_schema.getUser(), _schema.getContainer());
        schema.setContainerFilter(getContainerFilter());
        return schema;
    }

    @Override
    public String getPublicSchemaName()
    {
        return _schema.getSchemaName();
    }
}
