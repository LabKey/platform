/*
 * Copyright (c) 2016 LabKey Corporation
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
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MVDisplayColumnFactory;
import org.labkey.api.data.Parameter;
import org.labkey.api.data.PkFilter;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.StatementUtils;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.UpdateableTableInfo;
import org.labkey.api.etl.DataIteratorBuilder;
import org.labkey.api.etl.DataIteratorContext;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyColumn;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.UserIdQueryForeignKey;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.util.ContainerContext;
import org.labkey.api.view.ActionURL;
import org.labkey.list.controllers.ListController;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Deprecated
/* package */ class OntologyListTable extends FilteredTable<ListQuerySchema> implements UpdateableTableInfo
{
    public static TableInfo getIndexTable(ListDefinition.KeyType keyType)
    {
        switch (keyType)
        {
            case Integer:
            case AutoIncrementInteger:
                return OntologyManager.getExpSchema().getTable("IndexInteger");
            case Varchar:
                return OntologyManager.getExpSchema().getTable("IndexVarchar");
            default:
                return null;
        }
    }

    private final ListDefinition _list;
    private final List<FieldKey> _defaultVisibleColumns;

    public OntologyListTable(ListQuerySchema schema, ListDefinition listDef)
    {
        super(getIndexTable(listDef.getKeyType()), schema);
        setName(listDef.getName());
        setDescription(listDef.getDescription());
        _list = listDef;
        addCondition(getRealTable().getColumn("ListId"), (Integer) getRowId(_list));
        List<ColumnInfo> defaultColumnsCandidates = new LinkedList<>();

        // All columns visible by default, except for auto-increment integer
        ColumnInfo colKey = wrapColumn(listDef.getKeyName(), getRealTable().getColumn("Key"));
        colKey.setKeyField(true);
        colKey.setInputType("text");
        colKey.setInputLength(-1);
        colKey.setWidth("180");

        if (listDef.getKeyType().equals(ListDefinition.KeyType.AutoIncrementInteger))
        {
            colKey.setUserEditable(false);
            colKey.setAutoIncrement(true);
            colKey.setHidden(true);
        }

        addColumn(colKey);
        defaultColumnsCandidates.add(colKey);

        ColumnInfo colObjectId = wrapColumn(getRealTable().getColumn("ObjectId"));

        for (DomainProperty property : listDef.getDomain().getProperties())
        {
            PropertyColumn column = new PropertyColumn(property.getPropertyDescriptor(), colObjectId, listDef.getContainer(), _userSchema.getUser(), false);

            if (property.getName().equalsIgnoreCase(colKey.getName()))
            {
                colKey.setExtraAttributesFrom(column);
                continue;
            }

            column.setParentIsObjectId(true);
            column.setReadOnly(false);
            safeAddColumn(column);
            defaultColumnsCandidates.add(column);

            if (property.isMvEnabled())
            {
                MVDisplayColumnFactory.addMvColumns(this, column, property, colObjectId, listDef.getContainer(), _userSchema.getUser());
            }

        }

        boolean auto = (null == listDef.getTitleColumn());
        setTitleColumn(findTitleColumn(listDef, colKey), auto);

        // Make EntityId column available so AttachmentDisplayColumn can request it as a dependency
        // Do this late so the column doesn't get selected as title column, etc.
        addColumn("EntityId", true);
        addColumn("LastIndexed", true);

        // Make standard created & modified columns available.
        addColumn("Created", false);
        ColumnInfo createdBy = addColumn("CreatedBy", false);
        UserIdQueryForeignKey.initColumn(_userSchema.getUser(), listDef.getContainer(), createdBy, true);
        addColumn("Modified", false);
        ColumnInfo modifiedBy = addColumn("ModifiedBy", false);
        UserIdQueryForeignKey.initColumn(_userSchema.getUser(), listDef.getContainer(), modifiedBy, true);

        SQLFragment sql = new SQLFragment("CAST(? AS VARCHAR(36))", listDef.getContainer().getId());
        ColumnInfo containerColumn = new ExprColumn(this, "Container", sql, JdbcType.VARCHAR);
        addColumn(containerColumn);

        DetailsURL gridURL = new DetailsURL(_list.urlShowData(), Collections.<String, String>emptyMap());
        setGridURL(gridURL);
        DetailsURL detailsURL = new DetailsURL(_list.urlDetails(null), Collections.singletonMap("pk", _list.getKeyName()));
        setDetailsURL(detailsURL);

        // TODO: I don't see the point in using DetailsURL for constant URLs (insert, import, grid)
        if (!listDef.getAllowUpload())
            setImportURL(LINK_DISABLER);
        else
        {
            ActionURL importURL = listDef.urlFor(ListController.UploadListItemsAction.class);
            setImportURL(new DetailsURL(importURL));
        }

        _defaultVisibleColumns = Collections.unmodifiableList(QueryService.get().getDefaultVisibleColumns(defaultColumnsCandidates));
    }


    private ColumnInfo addColumn(String name, boolean hidden)
    {
        // might be unsafe but fine for migraiton purposes to avoid duplicate columns
        if (_columnMap.containsKey(name))
            return _columnMap.get(name);
        ColumnInfo column = wrapColumn(getRealTable().getColumn(name));
        column.setHidden(hidden);
        return addColumn(column);
    }


    @Override
    public List<FieldKey> getDefaultVisibleColumns()
    {
        return _defaultVisibleColumns;
    }


    @Override
    public Domain getDomain()
    {
        if (null != _list)
            return _list.getDomain();
        return null;
    }

    @Override
    public ContainerContext getContainerContext()
    {
        return _list != null ? _list.getContainer() : null;
    }

    private String findTitleColumn(ListDefinition listDef, ColumnInfo colKey)
    {
        if (listDef.getTitleColumn() != null)
        {
            ColumnInfo titleColumn = getColumn(listDef.getTitleColumn());

            if (titleColumn != null)
                return titleColumn.getName();
        }

        // Title column setting is <AUTO> -- select the first string column that's not a lookup (see #9114)
        for (ColumnInfo column : getColumns())
            if (column.isStringType() && null == column.getFk())
                return column.getName();

        // No non-FK string columns -- fall back to pk (see issue #5452)
        return colKey.getName();
    }

    public ListDefinition getList()
    {
        return _list;
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        return _list.getContainer().hasPermission(user, perm);
    }

    public String getPublicName()
    {
        return _list.getName();
    }

    public String getPublicSchemaName()
    {
        return ListQuerySchema.NAME;
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        throw new UnsupportedOperationException("OntologyListTable no longer provides an UpdateService.");
    }

    // UpdateableTableInfo

    @Override
    public boolean insertSupported()
    {
        return true;
    }

    @Override
    public boolean updateSupported()
    {
        return false;
    }

    @Override
    public boolean deleteSupported()
    {
        return false;
    }

    @Override
    public TableInfo getSchemaTableInfo()
    {
        return getRealTable();
    }

    public ObjectUriType getObjectUriType()
    {
        return UpdateableTableInfo.ObjectUriType.schemaColumn;
    }

    @Override
    public String getObjectURIColumnName()
    {
        return "entityid";
    }

    @Override
    public String getObjectIdColumnName()
    {
        return "objectid";
    }

    @Override
    public CaseInsensitiveHashMap<String> remapSchemaColumns()
    {
        if (!_list.getKeyName().isEmpty() && !_list.getKeyName().equalsIgnoreCase("key"))
        {
            CaseInsensitiveHashMap<String> m = new CaseInsensitiveHashMap<>();
            m.put("key", _list.getKeyName());
            return m;
        }
        return null;
    }

    @Override
    public CaseInsensitiveHashSet skipProperties()
    {
        if (!_list.getKeyName().isEmpty())
            return new CaseInsensitiveHashSet(_list.getKeyName());
        return null;
    }


    @Override
    public DataIteratorBuilder persistRows(DataIteratorBuilder data, DataIteratorContext context)
    {
        throw new UnsupportedOperationException("persistRows is no longer supported on OntologyListTables");
    }

    @Override
    public Parameter.ParameterMap insertStatement(Connection conn, User user) throws SQLException
    {
        return StatementUtils.insertStatement(conn, this, getContainer(), user, false, true);
    }


    @Override
    public Parameter.ParameterMap updateStatement(Connection conn, User user, Set<String> columns) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Parameter.ParameterMap deleteStatement(Connection conn) throws SQLException
    {
        throw new UnsupportedOperationException();
    }


    @Override
    public boolean supportsContainerFilter()
    {
        return false;
    }

    private static Object getRowId(ListDefinition list)
    {
        int listId = list.getListId();
        Container c = list.getContainer();
        SimpleFilter filter = new PkFilter(ListManager.get().getListMetadataTable(), new Object[]{c, listId});
        Map<String, Object> results = new TableSelector(ListManager.get().getListMetadataTable(), filter, null).getObject(Map.class);
        return results.get("rowid");
    }
}
