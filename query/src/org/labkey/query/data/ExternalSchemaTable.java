/*
 * Copyright (c) 2007-2017 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.*;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.SimpleQueryUpdateService;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.ContainerContext;
import org.labkey.data.xml.TableType;
import org.labkey.query.ExternalSchema;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;


public class ExternalSchemaTable extends SimpleUserSchema.SimpleTable<ExternalSchema> implements UpdateableTableInfo
{
    private Container _container;
    private static final Logger _logger = Logger.getLogger(ExternalSchemaTable.class);

    protected TableType _metadata;

    public ExternalSchemaTable(ExternalSchema schema, TableInfo table, TableType metadata)
    {
        super(schema, table);
        _metadata = metadata;
    }

    public SimpleUserSchema.SimpleTable<ExternalSchema> init()
    {
        super.init();

        try
        {
            //create list to avoid NPE if an error occurs
            Collection<QueryException> errors = new ArrayList<>();
            loadFromXML(getUserSchema(), Collections.singleton(_metadata), errors);
        }
        catch (IllegalArgumentException e)
        {
            _logger.error("Malformed XML for external table: " + getSchema() + "." + getName(), e);
            //otherwise ignore malformed XML
        }
        return this;
    }

    // Disallow container filtering.  At some point in the future we may introduce a 'inherit' bit on
    // external and linked schemas so they are available in sub-folders and become container filterable.
    @Override
    public boolean supportsContainerFilter()
    {
        return false;
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        List<ColumnInfo> columns = getPkColumns();
        return (perm.isAssignableFrom(ReadPermission.class) || (super.hasPermission(user, perm) && getUserSchema().areTablesEditable() && columns.size() > 0));
    }

    public void setContainer(String containerId)
    {
        if (containerId == null)
            return;

        ColumnInfo realContainerColumn = getRealTable().getColumn("container");

        if (realContainerColumn != null)
        {
            ColumnInfo wrappedContainerColumn = getColumn("container");
            if (wrappedContainerColumn != null)
            {
                wrappedContainerColumn.setReadOnly(true);
            }
            addCondition(realContainerColumn, containerId);
            _container = ContainerManager.getForId(containerId);
        }
    }


    @Override
    public ContainerContext getContainerContext()
    {
        assert null == getRealTable().getColumn("container") || null != _container;
        ExternalSchema s = getUserSchema();
        return null != _container ? _container : s.getContainer();
    }


    @Override
    public FieldKey getContainerFieldKey()
    {
        ColumnInfo colContainer = getRealTable().getColumn("container");
        return null==colContainer ? null : colContainer.getFieldKey();
    }


    @Override
    public QueryUpdateService getUpdateService()
    {
        TableInfo table = getRealTable();
        if (DatabaseTableType.TABLE != table.getTableType())
            return null;
        if (null == table.getPkColumnNames() || table.getPkColumnNames().size() == 0)
            return null;

        return new SimpleQueryUpdateService(this, table);
    }

    /*** UpdateableTableInfo ****/

    @Override
    public boolean insertSupported()
    {
        return getUserSchema().areTablesEditable();
    }

    @Override
    public boolean updateSupported()
    {
        return getUserSchema().areTablesEditable();
    }

    @Override
    public boolean deleteSupported()
    {
        return getUserSchema().areTablesEditable();
    }
}
