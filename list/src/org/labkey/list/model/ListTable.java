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

package org.labkey.list.model;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.compliance.ComplianceService;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ColumnRenderProperties;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.PHI;
import org.labkey.api.data.Parameter;
import org.labkey.api.data.StatementUtils;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.UpdateableTableInfo;
import org.labkey.api.dataiterator.DataIterator;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.dataiterator.SimpleTranslator;
import org.labkey.api.dataiterator.TableInsertDataIterator;
import org.labkey.api.dataiterator.ValidatorIterator;
import org.labkey.api.exp.MvColumn;
import org.labkey.api.exp.PropertyColumn;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.RawValueColumn;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.PdLookupForeignKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.UserIdQueryForeignKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.util.ContainerContext;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.view.ActionURL;
import org.labkey.list.controllers.ListController;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.labkey.api.gwt.client.ui.PropertyType.PARTICIPANT_CONCEPT_URI;

public class ListTable extends FilteredTable<ListQuerySchema> implements UpdateableTableInfo
{
    private final ListDefinition _list;
    private static final Logger LOG = Logger.getLogger(ListTable.class);
    private final boolean _allowMaxPhi;

    public ListTable(ListQuerySchema schema, @NotNull ListDefinition listDef, @NotNull Domain domain)
    {
        super(StorageProvisioner.createTableInfo(domain), schema);  // domain passed separately to allow @NotNull verification
        setName(listDef.getName());
        setDescription(listDef.getDescription());
        _list = listDef;
        _allowMaxPhi = isMaxPhiAllowed(schema);
        List<ColumnInfo> defaultColumnsCandidates = new ArrayList<>();

        assert getRealTable().getColumns().size() > 0 : "ListTable has not been provisioned properly. The real table does not exist.";

        ColumnInfo colKey = null;

        // We can have a ListDef that has been saved before the domain has been saved (urg)
        if (!domain.getProperties().isEmpty())
        {
            for (ColumnInfo baseColumn : getRealTable().getColumns())
            {
                // Don't include PHI columns in full text search index
                if (schema.getUser().isSearchUser() && !baseColumn.getPHI().isLevelAllowed(PHI.NotPHI))
                    continue;
                
                String name = baseColumn.getName();
                String propertyURI = baseColumn.getPropertyURI();
                DomainProperty dp = null==propertyURI ? null : domain.getPropertyByURI(propertyURI);
                PropertyDescriptor pd = null==dp ? null : dp.getPropertyDescriptor();

                if (listDef.getKeyName().equalsIgnoreCase(name))
                {
                    colKey = wrapColumn(baseColumn);

                    if (null != pd)
                    {
                        colKey.setName(pd.getName());
                        colKey.setLabel(pd.getLabel());
                        if (null != pd.getLookupQuery() || null != pd.getConceptURI())
                            colKey.setFk(new PdLookupForeignKey(schema.getUser(), pd, schema.getContainer()));
                    }
                    else
                    {
                        LOG.warn("" + _list.getName() + "." + _list.getKeyName() + " (primary key) " + "has not yet been provisioned.");
                    }

                    colKey.setKeyField(true);
                    colKey.setNullable(false); // Must assure this as it can be set incorrectly via StorageProvisioner
                    colKey.setInputType("text");
                    colKey.setInputLength(-1);
                    colKey.setWidth("180");

                    if (_list.getKeyType().equals(ListDefinition.KeyType.AutoIncrementInteger))
                    {
                        colKey.setAutoIncrement(true);
                        colKey.setUserEditable(false);
                        colKey.setHidden(true);
                    }

                    addColumn(colKey);
                    defaultColumnsCandidates.add(colKey);
                }
                else if (name.equalsIgnoreCase("EntityId"))
                {
                    continue; // processed at the end
                }
                else if (name.equalsIgnoreCase("Created") || name.equalsIgnoreCase("Modified") ||
                        name.equalsIgnoreCase("CreatedBy") || name.equalsIgnoreCase("ModifiedBy")
                        )
                {
                    ColumnInfo c = wrapColumn(baseColumn);
                    if (name.equalsIgnoreCase("CreatedBy") || name.equalsIgnoreCase("ModifiedBy"))
                    {
                        UserIdQueryForeignKey.initColumn(schema.getUser(), schema.getContainer(), c, true);
                        if (name.equalsIgnoreCase("CreatedBy"))
                        {
                            c.setName("CreatedBy");
                            c.setLabel("Created By");
                        }
                        else
                        {
                            c.setName("ModifiedBy");
                            c.setLabel("Modified By");
                        }
                    }
                    else if (name.equalsIgnoreCase("modified"))
                    {
                        c.setName("Modified");
                        c.setLabel("Modified");
                    }
                    else
                    {
                        c.setName("Created");
                        c.setLabel("Created");
                    }
                    c.setUserEditable(false);
                    c.setShownInInsertView(false);
                    c.setShownInUpdateView(false);
                    addColumn(c);
                }
                else if (name.equalsIgnoreCase("LastIndexed"))
                {
                    ColumnInfo column = addWrapColumn(baseColumn);
                    column.setHidden(true);
                    column.setUserEditable(false);
                }
                else if (name.equalsIgnoreCase("Container"))
                {
                    ColumnInfo folderColumn = wrapColumn(baseColumn);
                    folderColumn.setFk(new ContainerForeignKey(schema));
                    folderColumn.setUserEditable(false);
                    folderColumn.setShownInInsertView(false);
                    folderColumn.setShownInUpdateView(false);
                    folderColumn.setLabel("Folder");
                    addColumn(folderColumn);
                }
                // MV indicator columns will be handled by their associated value column
                else if (!baseColumn.isMvIndicatorColumn())
                {
                    assert baseColumn.getParentTable() == getRealTable() : "Column is not from the same \"real\" table";

                    ColumnInfo col = wrapColumn(baseColumn);

                    ColumnInfo ret = new AliasedColumn(this, col.getName(), col);
                    // Use getColumnNameSet() instead of getColumn() because we don't want to go through the resolveColumn()
                    // codepath, which is potentially expensive and doesn't reflect the "real" columns that are part of this table
                    if (col.isKeyField() && getColumnNameSet().contains(col.getName()))
                    {
                        ret.setKeyField(false);
                    }

                    col = ret;

                    // When copying a column, the hidden bit is not propagated, so we need to do it manually
                    if (baseColumn.isHidden())
                        col.setHidden(true);

                    if (null != pd)
                    {
                        col.setName(pd.getName());
                        PropertyColumn.copyAttributes(schema.getUser(), col, dp, schema.getContainer(), FieldKey.fromParts("EntityId"));

                        if (pd.isMvEnabled())
                        {
                            // The column in the physical table has a "_MVIndicator" suffix, but we want to expose
                            // it with a "MVIndicator" suffix (no underscore)
                            ColumnInfo mvColumn = new AliasedColumn(this, col.getName() + MvColumn.MV_INDICATOR_SUFFIX,
                                                                    StorageProvisioner.getMvIndicatorColumn(getRealTable(), pd, "No MV column found for '" + pd.getName() + "' in list '" + getName() + "'"));
                            // MV indicators are strings
                            mvColumn.setLabel(col.getLabel() + " MV Indicator");
                            mvColumn.setSqlTypeName("VARCHAR");
                            mvColumn.setPropertyURI(col.getPropertyURI());
                            mvColumn.setNullable(true);
                            mvColumn.setUserEditable(false);
                            mvColumn.setHidden(true);
                            mvColumn.setMvIndicatorColumn(true);

                            ColumnInfo rawValueCol = new AliasedColumn(col.getName() + RawValueColumn.RAW_VALUE_SUFFIX, col);
                            rawValueCol.setDisplayColumnFactory(ColumnInfo.DEFAULT_FACTORY);
                            rawValueCol.setLabel(col.getLabel() + " Raw Value");
                            rawValueCol.setUserEditable(false);
                            rawValueCol.setHidden(true);
                            rawValueCol.setMvColumnName(null);   // This version of the column does not show missing values
                            rawValueCol.setNullable(true);       // Otherwise we get complaints on import for required fields
                            rawValueCol.setRawValueColumn(true);

                            addColumn(mvColumn);
                            addColumn(rawValueCol);

                            col.setMvColumnName(FieldKey.fromParts(col.getName() + MvColumn.MV_INDICATOR_SUFFIX));
                        }

                        if (pd.getPropertyType() == PropertyType.ATTACHMENT)
                        {
                            col.setURL(StringExpressionFactory.createURL(
                                ListController.getDownloadURL(listDef, "${EntityId}", "${" + col.getName() + "}")
                            ));
                        }
                    }

                    addColumn(col);
                    defaultColumnsCandidates.add(col);
                }
            }
        }

        if (null != colKey)
        {
            boolean auto = (null == listDef.getTitleColumn());
            setTitleColumn(findTitleColumn(listDef, colKey), auto);
        }

        // Make EntityId column available so AttachmentDisplayColumn can request it as a dependency
        // Do this late so the column doesn't get selected as title column, etc.

        ColumnInfo colEntityId = getRealTable().getColumn(FieldKey.fromParts("EntityId"));
        if (null == colEntityId)
        {
            throw new NullPointerException("List does not have entityid column??: " + listDef.getContainer().getPath() + ", " + listDef.getName());
        }
        else
        {
            ColumnInfo entityId = wrapColumn(colEntityId);
            entityId.setName("EntityId");
            entityId.setLabel("Entity Id");
            entityId.setHidden(true);
            entityId.setUserEditable(false);
            entityId.setShownInInsertView(false);
            entityId.setShownInUpdateView(false);
            addColumn(entityId);
        }

        DetailsURL gridURL = new DetailsURL(_list.urlShowData(_userSchema.getContainer()), Collections.<String, String>emptyMap());
        setGridURL(gridURL);

        if (null != colKey)
            setDetailsURL(new DetailsURL(_list.urlDetails(null, _userSchema.getContainer()), Collections.singletonMap("pk", colKey.getAlias())));

        if (!listDef.getAllowUpload() || !_allowMaxPhi)
            setImportURL(LINK_DISABLER);
        else
        {
            ActionURL importURL = listDef.urlFor(ListController.UploadListItemsAction.class, _userSchema.getContainer());
            setImportURL(new DetailsURL(importURL));
        }

        if (!listDef.getAllowDelete() || !_allowMaxPhi)
            setDeleteURL(LINK_DISABLER);

        if (!_allowMaxPhi)
        {
            setInsertURL(LINK_DISABLER);
            setUpdateURL(LINK_DISABLER);
        }

        _defaultVisibleColumns = Collections.unmodifiableList(QueryService.get().getDefaultVisibleColumns(defaultColumnsCandidates));
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
        return _list != null ? _userSchema.getContainer() : null;
    }

    @Override
    public boolean supportTableRules()
    {
        return true;
    }

    @Override
    public Set<FieldKey> getPHIDataLoggingColumns()
    {
        if (getUserSchema().getUser().isServiceUser())
            return Collections.emptySet();
        else
        {
            Set<FieldKey> loggingColumns = new LinkedHashSet<>();
            if (!getRealTable().getColumns().isEmpty()) // it shouldn't be, as it should at least have the standard tracking columns even on initial creation
            {
                if (_list != null && _list.getKeyName() != null && getRealTable().getColumn(_list.getKeyName()) != null)
                    loggingColumns.add(getRealTable().getColumn(_list.getKeyName()).getFieldKey());
                loggingColumns.addAll(getRealTable().getColumns().stream()
                        .filter(c -> PARTICIPANT_CONCEPT_URI.equals(c.getConceptURI()))
                        .map(ColumnInfo::getFieldKey).collect(Collectors.toSet()));
            }
            return loggingColumns;
        }
    }

    @Override
    public String getPHILoggingComment()
    {
        return getPHIDataLoggingColumns().stream().map(fk -> getColumn(fk).getName())
                .collect(Collectors.joining(", ", "PHI accessed in list. Data shows ", "."));
    }

    /**
     * Return true if the user is allowed the maximum phi level set across all list columns
     */
    private boolean isMaxPhiAllowed(UserSchema schema)
    {
        final PHI[] maxPHI = {PHI.NotPHI};
        getRealTable().getColumns().stream()
                .max(Comparator.comparing(ColumnRenderProperties::getPHI))
                .ifPresent(c -> maxPHI[0] = c.getPHI());

        return maxPHI[0].isLevelAllowed(ComplianceService.get().getMaxAllowedPhi(schema.getContainer(), schema.getUser()));
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
        boolean gate = true;
        if (InsertPermission.class.equals(perm) || UpdatePermission.class.equals(perm))
            gate = _allowMaxPhi;
        else if (DeletePermission.class.equals(perm))
            gate = _allowMaxPhi && _list.getAllowDelete();
        return gate && _list.getContainer().hasPermission(user, perm);
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
        return new ListQueryUpdateService(this, this.getRealTable(), _list);
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
        CaseInsensitiveHashMap<String> m = new CaseInsensitiveHashMap<>();

        for (ColumnInfo col : getColumns())
        {
            if (null != col.getMvColumnName())
                m.put(col.getName() + "_" + MvColumn.MV_INDICATOR_SUFFIX, col.getMvColumnName().getName());
        }

        return m;
    }

    @Override
    public CaseInsensitiveHashSet skipProperties()
    {
        return null;
    }

    @Override
    public DataIteratorBuilder persistRows(DataIteratorBuilder data, DataIteratorContext context)
    {
        // NOTE: it's a little ambiguous how to factor code between persistRows() and createImportDIB()
        data = new _DataIteratorBuilder(data, context);
        return TableInsertDataIterator.create(data, this, _userSchema.getContainer(), context, new CaseInsensitiveHashSet(getPkColumnNames()), null, null);
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

            for (int c = 1; c <= input.getColumnCount(); c++)
            {
                ColumnInfo col = input.getColumnInfo(c);
                if (StringUtils.equalsIgnoreCase(_list.getKeyName(), col.getName()))
                {
                    keyColumnInput = c;
                    if (_list.getKeyType() == ListDefinition.KeyType.AutoIncrementInteger && !context.supportsAutoIncrementKey())
                        continue;
                }

                int out = it.addColumn(c);
                if (keyColumnInput == c)
                    keyColumnOutput = out;
            }

            DataIterator ret = it;

            // Checking within this batch we're not getting duplicates
            if (0 != keyColumnOutput && (context.getInsertOption().batch || _list.getKeyType() != ListDefinition.KeyType.AutoIncrementInteger))
            {
                ValidatorIterator vi = new ValidatorIterator(input, context, _list.getContainer(), null);
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

}
