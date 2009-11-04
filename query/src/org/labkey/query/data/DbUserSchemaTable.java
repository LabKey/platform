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

package org.labkey.query.data;

import org.labkey.api.data.*;
import org.labkey.api.query.*;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.api.module.SimpleModuleUserSchema;
import org.labkey.data.xml.TableType;
import org.labkey.query.controllers.dbuserschema.DbUserSchemaController;
import org.labkey.query.view.DbUserSchema;

import java.util.*;

public class DbUserSchemaTable extends SimpleModuleUserSchema.SimpleModuleTable
{
    TableType _metadata;
    String _containerId;
    Container _container;
    

    public DbUserSchemaTable(DbUserSchema schema, SchemaTableInfo table, TableType metadata)
    {
        super(schema, table);
        _metadata = metadata;
        if (metadata != null)
        {
            loadFromXML(schema, metadata, null);
        }
    }

    @Override
    public DbUserSchema getUserSchema()
    {
        return (DbUserSchema)super.getUserSchema();
    }

    public boolean hasPermission(User user, int perm)
    {
        if (!super.hasPermission(user, perm))
            return false;
        if (!getUserSchema().areTablesEditable())
            return false;
        List<ColumnInfo> columns = getPkColumns();
        // Consider: allow multi-column keys
        if (columns.size() != 1)
        {
            return false;
        }
        return true;
    }

    public void setContainer(String containerId)
    {
        if (containerId == null)
            return;
        ColumnInfo colContainer = getRealTable().getColumn("container");
        if (colContainer != null)
        {
            getColumn("container").setReadOnly(true);
            addCondition(colContainer, containerId);
            _containerId = containerId;
            _container = ContainerManager.getForId(containerId);
        }
    }

    public String getContainerId()
    {
        return _containerId;
    }

    @Override
    protected void addQueryFilters(SimpleFilter filter)
    {
        if (_containerId != null)
            filter.addCondition("container", _containerId);
    }


    @Override
    public boolean hasContainerContext()
    {
        ColumnInfo colContainer = getRealTable().getColumn("container");
        return null == colContainer || null != _container;
    }
    

    @Override
    public Container getContainer(Map context)
    {
        assert null == getRealTable().getColumn("container") || null != _container;
        DbUserSchema s = getUserSchema();
        return null != _container ? _container : s.getContainer();
    }


    public ActionURL urlFor(DbUserSchemaController.Action action)
    {
        ActionURL url = getUserSchema().getContainer().urlFor(action);
        url.addParameter(QueryParam.schemaName.toString(), getUserSchema().getSchemaName());
        url.addParameter(QueryParam.queryName.toString(), getName());
        return url;
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        TableInfo table = getRealTable();
        if (TableInfo.TABLE_TYPE_TABLE != table.getTableType())
            return null;
        if (null == table.getPkColumnNames() || table.getPkColumnNames().size() == 0)
            throw new RuntimeException("The table '" + getName() + "' does not have a primary key defined, and thus cannot be updated reliably. Please define a primary key for this table before attempting an update.");

        return new DefaultQueryUpdateService(this, table);
    }
}
