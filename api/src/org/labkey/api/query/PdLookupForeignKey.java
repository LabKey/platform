/*
 * Copyright (c) 2007-2014 LabKey Corporation
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

package org.labkey.api.query;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.NamedObjectList;
import org.labkey.api.data.*;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.StringExpression;

public class PdLookupForeignKey extends AbstractForeignKey
{
    User _user;
    PropertyDescriptor _pd;
    Container _currentContainer;
    private Container _targetContainer;

    public PdLookupForeignKey(@NotNull User user, @NotNull PropertyDescriptor pd, @NotNull Container container)
    {
        super(pd.getLookupSchema(), pd.getLookupQuery(), null);
        _pd = pd;
        _user = user;
        assert container != null : "Container cannot be null";
        _currentContainer = container;
        _targetContainer = _pd.getLookupContainer() == null ? null : ContainerManager.getForId(_pd.getLookupContainer());
    }

    @Override
    public String getLookupTableName()
    {
        return _pd.getLookupQuery();
    }

    @Override
    public String getLookupSchemaName()
    {
        return _pd.getLookupSchema();
    }



    @Override
    public Container getLookupContainer()
    {
        return _targetContainer;
    }

    public TableInfo getLookupTableInfo()
    {
        if (_pd.getLookupSchema() == null || _pd.getLookupQuery() == null)
            return null;

        TableInfo table;
        if (_targetContainer != null)
        {
            // We're configured to target a specific container
            table = findTableInfo(_targetContainer);
        }
        else
        {
            // First look in the current container
            table = findTableInfo(_currentContainer);
            if (table == null)
            {
                // Fall back to the property descriptor's container - useful for finding lists and other
                // single-container tables
                table = findTableInfo(_pd.getContainer());
                if (table != null)
                {
                    // Remember that we had to get it from a different container
                    _targetContainer = _pd.getContainer();
                }
            }
        }

        if (table == null)
            return null;

        if (table.getPkColumns().size() < 1 || table.getPkColumns().size() > 2)
            return null;

        return table;
    }

    private TableInfo _tableInfo = null;
    private TableInfo findTableInfo(Container container)
    {
        if (container == null)
            return null;

        if (!container.hasPermission(_user, ReadPermission.class))
            return null;

        if (null != _tableInfo)
            return _tableInfo;

        UserSchema schema = QueryService.get().getUserSchema(_user, container, _pd.getLookupSchema());
        if (schema == null)
            return null;

        _tableInfo = schema.getTable(_pd.getLookupQuery());
        return _tableInfo;
    }


    public ColumnInfo createLookupColumn(ColumnInfo parent, String displayField)
    {
        TableInfo table = getLookupTableInfo();
        if (table == null)
            return null;
        if (displayField == null)
        {
            displayField = table.getTitleColumn();
        }
        if (displayField == null)
            return null;

        if (table.supportsContainerFilter() && parent.getParentTable().getContainerFilter() != null)
        {
            ContainerFilterable newTable = (ContainerFilterable)table;

            // Only override if the new table doesn't already have some special filter
            if (newTable.hasDefaultContainerFilter())
                newTable.setContainerFilter(new DelegatingContainerFilter(parent.getParentTable()));
        }

        return LookupColumn.create(parent, table.getColumn(getLookupColumnName()), table.getColumn(displayField), false);
    }


    @Override
    protected void initTableAndColumnNames()
    {
        super.initTableAndColumnNames();

        TableInfo table = getLookupTableInfo();
        if (table == null)
            return;

        DbSchema s = table.getSchema();
        UserSchema qs = table.getUserSchema();
        boolean isSampleSchema = s.getScope()==CoreSchema.getInstance().getScope() &&
                s.getName().equalsIgnoreCase("exp") &&
                null != qs && StringUtils.equalsIgnoreCase(qs.getName(), SamplesSchema.SCHEMA_NAME);
        if (isSampleSchema && _pd.getJdbcType().isText())
        {
            if (null != table.getColumn("Name"))
                _columnName = "Name";
        }
    }


    @Override
    public String getLookupColumnName()
    {
        return super.getLookupColumnName();
    }

    @Override
    public NamedObjectList getSelectList(RenderContext ctx)
    {
        return super.getSelectList(ctx);
    }

    public StringExpression getURL(ColumnInfo parent)
    {
        TableInfo lookupTable = getLookupTableInfo();
        if (lookupTable == null)
            return null;
        String columnName = lookupTable.getPkColumnNames().get(0);
        if (null == columnName)
            return null;
        return LookupForeignKey.getDetailsURL(parent, lookupTable, columnName);
    }
}
