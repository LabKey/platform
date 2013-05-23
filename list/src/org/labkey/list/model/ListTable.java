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
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.StorageProvisioner;
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
import org.labkey.list.controllers.ListController;
import org.labkey.list.view.AttachmentDisplayColumn;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class ListTable extends FilteredTable<ListQuerySchema> implements UpdateableTableInfo
{
    private final ListDefinition _list;
    private final List<FieldKey> _defaultVisibleColumns;

    public ListTable(ListQuerySchema schema, ListDefinition listDef)
    {
        super(StorageProvisioner.createTableInfo(listDef.getDomain(), schema.getDbSchema()));
        setName(listDef.getName());
        setDescription(listDef.getDescription());
        _list = listDef;
        List<ColumnInfo> defaultColumnsCandidates = new ArrayList<>();

        for (ColumnInfo baseColumn : getRealTable().getColumns())
        {
            String name = baseColumn.getName();
            if (listDef.getKeyName().equalsIgnoreCase(name))
            {
                ColumnInfo column = wrapColumn(baseColumn);
                column.setKeyField(true);
                column.setInputType("text");
                column.setInputLength(-1);
                column.setWidth("180");

                // TODO : Can this somehow be asked of the Domain/Kind?
                if (_list.getKeyType().equals(ListDefinition.KeyType.AutoIncrementInteger))
                {
                    column.setAutoIncrement(true);
                    column.setUserEditable(false);
                    column.setHidden(true);
                }

                // TODO: column.setFK()?

                addColumn(column);
                defaultColumnsCandidates.add(column);

                boolean auto = (null == listDef.getTitleColumn());
                setTitleColumn(findTitleColumn(listDef, column), auto);
            }
            else if (name.equalsIgnoreCase("EntityId"))
            {
                ColumnInfo column = wrapColumn(baseColumn);
                column.setHidden(true);
                addColumn(column);
            }
            else if (name.equalsIgnoreCase("Created") || name.equalsIgnoreCase("Modified") ||
                    name.equalsIgnoreCase("CreatedBy") || name.equalsIgnoreCase("ModifiedBy")
                    )
            {
                ColumnInfo c = wrapColumn(baseColumn);
                if (name.equalsIgnoreCase("CreatedBy") || name.equalsIgnoreCase("ModifiedBy"))
                    UserIdQueryForeignKey.initColumn(schema.getUser(), schema.getContainer(), c, true);
                c.setUserEditable(false);
                c.setShownInInsertView(false);
                c.setShownInUpdateView(false);
                addColumn(c);
            }
            else if (name.equalsIgnoreCase("LastIndexed"))
            {
                ColumnInfo column = wrapColumn(baseColumn);
                column.setHidden(true);
                column.setUserEditable(false);
                addColumn(column);
            }
            else
            {
                ColumnInfo column = wrapColumn(baseColumn);
                safeAddColumn(column);
                defaultColumnsCandidates.add(column);

                for (DomainProperty property : listDef.getDomain().getProperties())
                {
                    if (property.getName().equalsIgnoreCase(column.getName()))
                    {
//                        if (property.isMvEnabled())
//                        {
//                            MVDisplayColumnFactory.addMvColumns(this, column, property, colObjectId, listDef.getContainer(), _userSchema.getUser());
//                        }

                        if (property.getPropertyDescriptor().getPropertyType() == PropertyType.ATTACHMENT)
                        {
                            column.setDisplayColumnFactory(new DisplayColumnFactory()
                            {
                                @Override
                                public DisplayColumn createRenderer(ColumnInfo colInfo)
                                {
                                    return new AttachmentDisplayColumn(colInfo);
                                }
                            });
                            column.setInputType("file");
                        }
                    }
                }
            }
        }

        // TODO: Possibly iterate over using getRealTable().getColumns()
//        for (DomainProperty property : listDef.getDomain().getProperties())
//        {
//            if (property.getName().equalsIgnoreCase(colKey.getName()))
//            {
//                colKey.setExtraAttributesFrom(column);
//                continue;
//            }
//
//            column.setParentIsObjectId(true);
//            column.setReadOnly(false);
//            column.setScale(property.getScale()); // UNDONE: PropertyDescriptor does not have getScale() so have to set here, move to PropertyColumn
//            safeAddColumn(column);
//            defaultColumnsCandidates.add(column);
//
//            if (property.isMvEnabled())
//            {
//                MVDisplayColumnFactory.addMvColumns(this, column, property, colObjectId, listDef.getContainer(), _userSchema.getUser());
//            }
//
//            // UNDONE: Move AttachmentDisplayColumn to API and attach in PropertyColumn.copyAttributes()
//            if (property.getPropertyDescriptor().getPropertyType() == PropertyType.ATTACHMENT)
//            {
//                column.setDisplayColumnFactory(new DisplayColumnFactory() {
//                    public DisplayColumn createRenderer(final ColumnInfo colInfo)
//                    {
//                        return new AttachmentDisplayColumn(colInfo);
//                    }
//                });
//            }
//        }

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
        return ListQuerySchema.NAME;
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        return new ListQueryUpdateService(this, this.getRealTable(), getList());
    }

    @Override
    public boolean insertSupported()
    {
        return true;
    }

    @Override
    public boolean updateSupported()
    {
        return true;
    }

    @Override
    public boolean deleteSupported()
    {
        return true;
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
        return null;
    }

    @Override
    public CaseInsensitiveHashMap<String> remapSchemaColumns()
    {
        if (!_list.getKeyName().isEmpty() && !_list.getKeyName().equalsIgnoreCase(ListDomainKind.KEY_FIELD))
        {
            CaseInsensitiveHashMap<String> m = new CaseInsensitiveHashMap<>();
            m.put(ListDomainKind.KEY_FIELD, _list.getKeyName());
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

// handled as constant in StatementUtils.createStatement()
//            ColumnInfo containerCol = new ColumnInfo("container", JdbcType.GUID);
//            it.addColumn(containerCol, new SimpleTranslator.ConstantColumn(_list.getContainer().getId()));

            ColumnInfo listIdCol = new ColumnInfo("listid", JdbcType.INTEGER);
            it.addColumn(listIdCol, new SimpleTranslator.ConstantColumn(_list.getRowId()));

            DataIterator ret = LoggingDataIterator.wrap(it);

            if (0 != keyColumnOutput && (context.getInsertOption().batch || _list.getKeyType() != ListDefinition.KeyType.AutoIncrementInteger))
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
