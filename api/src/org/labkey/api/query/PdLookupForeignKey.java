/*
 * Copyright (c) 2008-2019 LabKey Corporation
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
import org.labkey.api.data.AbstractForeignKey;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ContainerTable;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.LookupColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.property.Lookup;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.settings.ConceptURIProperties;
import org.labkey.api.util.StringExpression;

public class PdLookupForeignKey extends AbstractForeignKey
{
    User _user;
    PropertyDescriptor _pd;
    Container _currentContainer;
    private Container _targetContainer;

    static public PdLookupForeignKey create(QuerySchema sourceSchema, @NotNull PropertyDescriptor pd)
    {
        return create(sourceSchema, sourceSchema.getUser(), sourceSchema.getContainer(), pd);
    }

    static public PdLookupForeignKey create(QuerySchema sourceSchema, @NotNull User user, @NotNull Container container, @NotNull PropertyDescriptor pd)
    {
        assert container != null : "Container cannot be null";

        Container currentContainer = container;
        Container targetContainer = pd.getLookupContainer() == null ? null : ContainerManager.getForId(pd.getLookupContainer());
        String lookupSchemaName = pd.getLookupSchema();
        String lookupQuery = pd.getLookupQuery();

        // check for conceptURI if the lookup container/schema/query are not already specified
        if (pd.getConceptURI() != null && targetContainer == null && lookupSchemaName == null && lookupQuery == null)
        {
            Lookup lookup = ConceptURIProperties.getLookup(container, pd.getConceptURI());
            if (lookup != null)
            {
                targetContainer = lookup.getContainer();
                lookupSchemaName = lookup.getSchemaName();
                lookupQuery = lookup.getQueryName();
            }
        }
        ContainerFilter cf;
        if ("core".equalsIgnoreCase(lookupSchemaName) && "Containers".equalsIgnoreCase(lookupQuery))
            cf = new ContainerFilter.AllFolders(user);
        else
            cf = new ContainerFilter.SimpleContainerFilterWithUser(user, targetContainer!=null ? targetContainer : container);

        return new PdLookupForeignKey(sourceSchema, currentContainer, user, cf, pd, lookupSchemaName, lookupQuery, targetContainer);
    }


    public PdLookupForeignKey(QuerySchema sourceSchema, Container currentContainer, @NotNull User user, ContainerFilter cf, PropertyDescriptor pd, String lookupSchemaName, String lookupQuery, Container targetContainer)
    {
        super(sourceSchema, cf, lookupSchemaName, lookupQuery, null);
        _pd = pd;
        _user = user;
        assert currentContainer != null : "Container cannot be null";
        _currentContainer = currentContainer;
        _targetContainer = _pd.getLookupContainer() == null ? null : ContainerManager.getForId(_pd.getLookupContainer());
    }


    @Override
    public String getLookupTableName()
    {
        return _tableName;
    }

    @Override
    public String getLookupSchemaName()
    {
        return _lookupSchemaName;
    }

    @Override
    public Container getLookupContainer()
    {
        return _targetContainer;
    }

    public TableInfo getLookupTableInfo()
    {
        if (_lookupSchemaName == null || _tableName == null)
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

        QuerySchema schema;
        if (null != _sourceSchema && _sourceSchema.getContainer().equals(container))
            schema = DefaultSchema.resolve(_sourceSchema, SchemaKey.fromString(_lookupSchemaName));
        else
            schema = QueryService.get().getUserSchema(_user, container, SchemaKey.fromString(_lookupSchemaName));
        if (!(schema instanceof UserSchema))
            return null;

        _tableInfo = schema.getTable(_tableName, _containerFilter);
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
                ExpSchema.SCHEMA_NAME.equalsIgnoreCase(s.getName()) &&
                null != qs && StringUtils.equalsIgnoreCase(qs.getName(), SamplesSchema.SCHEMA_NAME);
        if (isSampleSchema && _pd.getJdbcType().isText())
        {
            if (null != table.getColumn("Name"))
                _columnName = "Name";
        }
    }

    @Override
    public NamedObjectList getSelectList(RenderContext ctx)
    {
        // if the lookup table is core.containers, list all of the containers the user has access to
        if ("core".equalsIgnoreCase(_pd.getLookupSchema()) && "Containers".equalsIgnoreCase(_pd.getLookupQuery()))
        {
            UserSchema schema = QueryService.get().getUserSchema(_user, _currentContainer, "core");
            TableInfo lookupTable = schema.getTable("Containers", new ContainerFilter.AllFolders(_user));
            return ((ContainerTable) lookupTable).getPathSelectList();
        }

        return super.getSelectList(ctx);
    }

    public StringExpression getURL(ColumnInfo parent)
    {
        TableInfo lookupTable = getLookupTableInfo();
        if (lookupTable == null)
            return null;
        String columnName = lookupTable.getPkColumns().get(0).getAlias();
        if (null == columnName)
            return null;
        return LookupForeignKey.getDetailsURL(parent, lookupTable, columnName);
    }
}
