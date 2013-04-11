/*
 * Copyright (c) 2007-2013 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MVDisplayColumnFactory;
import org.labkey.api.data.Parameter;
import org.labkey.api.data.StatementUtils;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.UpdateableTableInfo;
import org.labkey.api.etl.DataIterator;
import org.labkey.api.etl.DataIteratorBuilder;
import org.labkey.api.etl.DataIteratorContext;
import org.labkey.api.etl.LoggingDataIterator;
import org.labkey.api.etl.SimpleTranslator;
import org.labkey.api.etl.TableInsertDataIterator;
import org.labkey.api.etl.ValidatorIterator;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyColumn;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.DetailsURL;
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
import org.labkey.list.view.AttachmentDisplayColumn;
import org.labkey.list.view.ListController;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

public class ListTable extends FilteredTable<ListSchema> implements UpdateableTableInfo
{
    public static TableInfo getIndexTable(ListDefinition.KeyType keyType)
    {
        switch (keyType)
        {
            case Integer:
            case AutoIncrementInteger:
                return OntologyManager.getTinfoIndexInteger();
            case Varchar:
                return OntologyManager.getTinfoIndexVarchar();
            default:
                return null;
        }
    }

    private final ListDefinition _list;
    private final List<FieldKey> _defaultVisibleColumns;

    public ListTable(ListSchema schema, ListDefinition listDef)
    {
        super(getIndexTable(listDef.getKeyType()), schema);
        setName(listDef.getName());
        setDescription(listDef.getDescription());
        _list = listDef;
        addCondition(getRealTable().getColumn("ListId"), listDef.getRowId());
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
            column.setScale(property.getScale()); // UNDONE: PropertyDescriptor does not have getScale() so have to set here, move to PropertyColumn
            safeAddColumn(column);
            defaultColumnsCandidates.add(column);

            if (property.isMvEnabled())
            {
                MVDisplayColumnFactory.addMvColumns(this, column, property, colObjectId, listDef.getContainer(), _userSchema.getUser());
            }

            // UNDONE: Move AttachmentDisplayColumn to API and attach in PropertyColumn.copyAttributes()
            if (property.getPropertyDescriptor().getPropertyType() == PropertyType.ATTACHMENT)
            {
                column.setDisplayColumnFactory(new DisplayColumnFactory() {
                    public DisplayColumn createRenderer(final ColumnInfo colInfo)
                    {
                        return new AttachmentDisplayColumn(colInfo);
                    }
                });
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

        DetailsURL gridURL = new DetailsURL(_list.urlShowData(), Collections.<String, String>emptyMap());
        setGridURL(gridURL);

        DetailsURL insertURL = new DetailsURL(_list.urlFor(ListController.InsertAction.class), Collections.<String, String>emptyMap());
        setInsertURL(insertURL);

        DetailsURL updateURL = new DetailsURL(_list.urlUpdate(null, null), Collections.singletonMap("pk", _list.getKeyName()));
        setUpdateURL(updateURL);

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

    public boolean hasPermission(UserPrincipal user, Class<? extends Permission> perm)
    {
        return _list.getContainer().hasPermission(user, perm);
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
    public QueryUpdateService getUpdateService()
    {
        return new ListQueryUpdateService(this, getList());
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
        // NOTE: it's a little ambiguious how to factor code between persistRows() and createImportETL()
        data = new _DataIteratorBuilder(data, context);
        DataIteratorBuilder ins;
        ins = TableInsertDataIterator.create(data, this, _list.getContainer(), context);
        return ins;
    }


    public class _DataIteratorBuilder implements DataIteratorBuilder
    {
        DataIteratorContext _context;
        final DataIteratorBuilder _in;
        final ListItemImpl.KeyIncrementer _keyIncrementer = ListItemImpl._keyIncrementer;

        _DataIteratorBuilder(@NotNull DataIteratorBuilder in, DataIteratorContext context)
        {
            _context = context;
            _in = in;
        }

        @Override
        public DataIterator getDataIterator(DataIteratorContext context)
        {
            _context = context;
            DataIterator input = _in.getDataIterator(context);
            if (null == input)
                return null;           // Can happen if context has errors

            final SimpleTranslator it = new SimpleTranslator(input, context);

            int keyColumnInput = 0;
            int keyColumnOutput = 0;
            for (int c=1 ; c<=input.getColumnCount() ; c++)
            {
                ColumnInfo col = input.getColumnInfo(c);
                if (StringUtils.equalsIgnoreCase(_list.getKeyName(), col.getName()))
                {
                    keyColumnInput = c;
                    if (_list.getKeyType() == ListDefinition.KeyType.AutoIncrementInteger)
                        continue;
                }
// TODO lists allow a column called "container"! need to start disallowing this!
//                if (StringUtils.equalsIgnoreCase("container", col.getName()))
//                    continue;
                if (StringUtils.equalsIgnoreCase("listid", col.getName()))
                    continue;
                int out = it.addColumn(c);
                if (keyColumnInput==c)
                    keyColumnOutput = out;
            }

            if (_list.getKeyType() == ListDefinition.KeyType.AutoIncrementInteger)
            {
                ColumnInfo keyCol = new ColumnInfo(_list.getKeyName(), JdbcType.INTEGER);
                final int inputKeyColumn = keyColumnInput;
                keyColumnOutput = it.addColumn(keyCol, new Callable()
                {
                    @Override
                    public Object call() throws Exception
                    {
                        Object keyValue = (_context.isForImport() && inputKeyColumn != 0) ? it.getInputColumnValue(inputKeyColumn) : null;
                        return null != keyValue ? keyValue : _keyIncrementer.getNextKey((ListDefinitionImpl)_list);
                    }
                });
            }

// handled as constant in StatementUtils.createStatement()
//            ColumnInfo containerCol = new ColumnInfo("container", JdbcType.GUID);
//            it.addColumn(containerCol, new SimpleTranslator.ConstantColumn(_list.getContainer().getId()));

            ColumnInfo listIdCol = new ColumnInfo("listid", JdbcType.INTEGER);
            it.addColumn(listIdCol, new SimpleTranslator.ConstantColumn(_list.getRowId()));

            DataIterator ret = LoggingDataIterator.wrap(it);

            if (0 != keyColumnOutput && (context.isForImport() || _list.getKeyType() != ListDefinition.KeyType.AutoIncrementInteger))
            {
                ValidatorIterator vi = new ValidatorIterator(ret, context, _list.getContainer(), null);
                vi.addUniqueValidator(keyColumnOutput, DbSchema.get("exp").getSqlDialect().isCaseSensitive());
                ret = vi;
            }
            return ret;
        }
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
}
