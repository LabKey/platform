/*
 * Copyright (c) 2009-2019 LabKey Corporation
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.compliance.TableRules;
import org.labkey.api.compliance.TableRulesManager;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.PHI;
import org.labkey.api.data.ParameterMapStatement;
import org.labkey.api.data.StatementUtils;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.UpdateableTableInfo;
import org.labkey.api.dataiterator.DataIterator;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.dataiterator.SimpleTranslator;
import org.labkey.api.dataiterator.TableInsertDataIteratorBuilder;
import org.labkey.api.dataiterator.ValidatorIterator;
import org.labkey.api.di.DataIntegrationService;
import org.labkey.api.exp.MvColumn;
import org.labkey.api.exp.PropertyColumn;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.RawValueColumn;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.DomainUtil;
import org.labkey.api.inventory.InventoryService;
import org.labkey.api.lists.permissions.ManagePicklistsPermission;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.PdLookupForeignKey;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.column.BuiltInColumnTypes;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.study.assay.FileLinkDisplayColumn;
import org.labkey.api.util.ContainerContext;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.view.ActionURL;
import org.labkey.data.xml.TableType;
import org.labkey.list.controllers.ListController;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static org.labkey.api.inventory.InventoryService.InventoryStatusColumn;

public class ListTable extends FilteredTable<ListQuerySchema> implements UpdateableTableInfo
{
    private final ListDefinition _list;
    private static final Logger LOG = LogManager.getLogger(ListTable.class);

    // NK: Picklists are instances of Lists that are utilized to group Samples. As such, they are utilized
    // in more specific ways than Lists in our system and necessitate a different collection of default
    // columns. Having these columns declared here may not be ideal, however, until Picklists have their own
    // standalone implementation this is the best place for broad support across folders, views, etc.
    private static final String PICKLIST_SAMPLE_ID = "SampleID";
    private static final List<FieldKey> defaultPicklistVisibleColumns = new ArrayList<>();

    static
    {
        defaultPicklistVisibleColumns.add(FieldKey.fromParts(PICKLIST_SAMPLE_ID, "Name"));
        defaultPicklistVisibleColumns.add(FieldKey.fromParts(PICKLIST_SAMPLE_ID, "MaterialExpDate"));
        defaultPicklistVisibleColumns.add(FieldKey.fromParts(PICKLIST_SAMPLE_ID, "LabelColor"));
        defaultPicklistVisibleColumns.add(FieldKey.fromParts(PICKLIST_SAMPLE_ID, "Folder"));
        defaultPicklistVisibleColumns.add(FieldKey.fromParts(PICKLIST_SAMPLE_ID, "SampleSet"));
        defaultPicklistVisibleColumns.add(FieldKey.fromParts(PICKLIST_SAMPLE_ID, "SampleState"));
        defaultPicklistVisibleColumns.add(FieldKey.fromParts(PICKLIST_SAMPLE_ID, "StoredAmount"));
        defaultPicklistVisibleColumns.add(FieldKey.fromParts(PICKLIST_SAMPLE_ID, "Units"));
        defaultPicklistVisibleColumns.add(FieldKey.fromParts(PICKLIST_SAMPLE_ID, InventoryStatusColumn.FreezeThawCount.name()));
        defaultPicklistVisibleColumns.add(FieldKey.fromParts(PICKLIST_SAMPLE_ID, InventoryStatusColumn.StorageStatus.name()));
        defaultPicklistVisibleColumns.add(FieldKey.fromParts(PICKLIST_SAMPLE_ID, InventoryStatusColumn.CheckedOutBy.name()));
        defaultPicklistVisibleColumns.add(FieldKey.fromParts(PICKLIST_SAMPLE_ID, "Created"));
        defaultPicklistVisibleColumns.add(FieldKey.fromParts(PICKLIST_SAMPLE_ID, "CreatedBy"));
        defaultPicklistVisibleColumns.add(FieldKey.fromParts(PICKLIST_SAMPLE_ID, InventoryStatusColumn.StorageLocation.name()));
        defaultPicklistVisibleColumns.add(FieldKey.fromParts(PICKLIST_SAMPLE_ID, InventoryStatusColumn.StorageRow.name()));
        defaultPicklistVisibleColumns.add(FieldKey.fromParts(PICKLIST_SAMPLE_ID, InventoryStatusColumn.StorageCol.name()));
        defaultPicklistVisibleColumns.add(FieldKey.fromParts(PICKLIST_SAMPLE_ID, "isAliquot"));
    }

    public ListTable(ListQuerySchema schema, @NotNull ListDefinition listDef, @NotNull Domain domain, @Nullable ContainerFilter cf)
    {
        super(StorageProvisioner.createTableInfo(domain), schema, cf);
        setName(listDef.getName());
        setDescription(listDef.getDescription());
        _list = listDef;
        List<ColumnInfo> defaultColumnsCandidates = new ArrayList<>();

        assert !getRealTable().getColumns().isEmpty() : "ListTable has not been provisioned properly. The real table does not exist.";

        MutableColumnInfo colKey = null;

        Supplier<Map<DomainProperty, Object>> defaultsSupplier = null;

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
                BuiltInColumnTypes builtin;

                if (listDef.getKeyName().equalsIgnoreCase(name))
                {
                    colKey = wrapColumn(baseColumn);

                    if (null != pd)
                    {
                        colKey.setFieldKey(new FieldKey(null,pd.getName()));
                        colKey.setLabel(pd.getLabel());
                        if (null != pd.getLookupQuery() || null != pd.getConceptURI())
                            colKey.setFk(PdLookupForeignKey.create(schema, pd));
                    }
                    else
                    {
                        LOG.warn(_list.getName() + "." + _list.getKeyName() + " (primary key) " + "has not yet been provisioned.");
                    }

                    colKey.setKeyField(true);
                    colKey.setNullable(false); // Must assure this as it can be set incorrectly via StorageProvisioner
                    colKey.setInputType("text");
                    colKey.setInputLength(-1);

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
                else if (null != (builtin = BuiltInColumnTypes.findBuiltInType(baseColumn)))
                {
                    var column = addWrapColumn(baseColumn);
                    // these columns don't get fixed up from schema.xml like they do for most tables
                    if (BuiltInColumnTypes.Container==builtin)
                    {
                        // TODO: tests expect lower case column name "container"
                        // column.setFieldKey(new FieldKey(null, builtin.name()));
                        column.setLabel("Folder");
                    }
                    else
                    {
                        column.setFieldKey(new FieldKey(null, builtin.name()));
                        column.setLabel(builtin.label);
                    }
                }
                else if (name.equalsIgnoreCase(DataIntegrationService.Columns.TransformImportHash.getColumnName()))
                {
                    var c = wrapColumn(baseColumn);
                    c.setUserEditable(false);
                    c.setShownInInsertView(false);
                    c.setShownInUpdateView(false);
                    c.setHidden(true);
                    addColumn(c);
                }
                else if (name.equalsIgnoreCase("LastIndexed"))
                {
                    var column = addWrapColumn(baseColumn);
                    column.setHidden(true);
                    column.setUserEditable(false);
                }
                // MV indicator columns will be handled by their associated value column
                else if (!baseColumn.isMvIndicatorColumn())
                {
                    assert baseColumn.getParentTable() == getRealTable() : "Column is not from the same \"real\" table";

                    var col = wrapColumn(baseColumn);

                    var ret = new AliasedColumn(this, col.getName(), col);
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
                        col.setFieldKey(new FieldKey(null,pd.getName()));
                        defaultsSupplier = PropertyColumn.copyAttributes(schema.getUser(), col, dp, schema.getContainer(), FieldKey.fromParts("EntityId"), getContainerFilter(), defaultsSupplier);

                        if (pd.isMvEnabled())
                        {
                            // The column in the physical table has a "_MVIndicator" suffix, but we want to expose
                            // it with a "MVIndicator" suffix (no underscore)
                            var mvColumn = new AliasedColumn(this, col.getName() + MvColumn.MV_INDICATOR_SUFFIX,
                                                                    StorageProvisioner.get().getMvIndicatorColumn(getRealTable(), pd, "No MV column found for '" + pd.getName() + "' in list '" + getName() + "'"));
                            // MV indicators are strings
                            mvColumn.setLabel(col.getLabel() + " MV Indicator");
                            mvColumn.setSqlTypeName("VARCHAR");
                            mvColumn.setPropertyURI(col.getPropertyURI());
                            mvColumn.setNullable(true);
                            mvColumn.setUserEditable(false);
                            mvColumn.setHidden(true);
                            mvColumn.setMvIndicatorColumn(true);

                            var rawValueCol = new AliasedColumn(col.getName() + RawValueColumn.RAW_VALUE_SUFFIX, col);
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
                            configureAttachmentURL(col);
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
            var entityId = wrapColumn(colEntityId);
            entityId.setFieldKey(new FieldKey(null,"EntityId"));
            entityId.setLabel("Entity Id");
            entityId.setHidden(true);
            entityId.setUserEditable(false);
            entityId.setShownInInsertView(false);
            entityId.setShownInUpdateView(false);
            addColumn(entityId);
        }

        // NOTE I think may be too early to call this method?
        // We can't tell which columns have PhiColumnBehavior==show until after applyTableRules is called
        boolean canAccessPhi = canUserAccessPhi();

        DetailsURL gridURL = new DetailsURL(_list.urlShowData(_userSchema.getContainer()), Collections.<String, String>emptyMap());
        setGridURL(gridURL);

        if (null != colKey)
            setDetailsURL(new DetailsURL(_list.urlDetails(null, _userSchema.getContainer()), "pk", colKey.getFieldKey()));

        if (!listDef.getAllowUpload() || !canAccessPhi)
            setImportURL(LINK_DISABLER);
        else
        {
            setImportURL(new DetailsURL(listDef.urlImport(_userSchema.getContainer())));
        }

        if (!listDef.getAllowDelete() || !canAccessPhi)
            setDeleteURL(LINK_DISABLER);

        if (!canAccessPhi)
        {
            setInsertURL(LINK_DISABLER);
            setUpdateURL(LINK_DISABLER);
        }

        List<FieldKey> defaultVisible = QueryService.get().getDefaultVisibleColumns(defaultColumnsCandidates);
        List<FieldKey> calculatedFieldKeys = DomainUtil.getCalculatedFieldsForDefaultView(this);
        defaultVisible.addAll(calculatedFieldKeys);
        _defaultVisibleColumns = Collections.unmodifiableList(defaultVisible);

        if (_list.getKeyType() != ListDefinition.KeyType.AutoIncrementInteger)
        {
            setAllowedInsertOption(QueryUpdateService.InsertOption.MERGE);
            setAllowedInsertOption(QueryUpdateService.InsertOption.REPLACE);
            setAllowedInsertOption(QueryUpdateService.InsertOption.UPSERT);
        }

    }

    @Override
    public void overlayMetadata(Collection<TableType> metadata, UserSchema schema, Collection<QueryException> errors)
    {
        super.overlayMetadata(metadata, schema, errors);

        // Reset URLs in case the XML metadata changed the view/download format option for the file
        for (MutableColumnInfo col : getMutableColumns())
        {
            if (col.getPropertyType() == PropertyType.ATTACHMENT)
            {
                configureAttachmentURL(col);
            }
        }
    }

    private void configureAttachmentURL(MutableColumnInfo col)
    {
        ActionURL url = ListController.getDownloadURL(_list, "${EntityId}", "${" + col.getName() + "}");
        if (FileLinkDisplayColumn.AS_ATTACHMENT_FORMAT.equalsIgnoreCase(col.getFormat()))
        {
            url.addParameter("inline", "false");
            col.setURLTargetWindow(null);
        }
        else
        {
            col.setURLTargetWindow("_blank");
        }
        col.setURL(StringExpressionFactory.createURL(url));
    }

    @Override
    public List<FieldKey> getDefaultVisibleColumns()
    {
        if (_list != null && _list.isPicklist())
            return defaultPicklistVisibleColumns;
        return super.getDefaultVisibleColumns();
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
        if (_list == null)
            return null;
        return new ContainerContext.FieldKeyContext(new FieldKey(null, "Container"));
    }

    @Override
    public boolean supportTableRules()
    {
        return true;
    }

    @Override
    protected @NotNull TableRules findTableRules()
    {
        return TableRulesManager.get().getTableRules(getList().getContainer(), getUserSchema().getUser(), getUserSchema().getContainer());
    }

    /**
     * For logging, replace the provisioned table name with the nicer name
     */
    @Override
    public MutableColumnInfo wrapColumn(ColumnInfo underlyingColumn)
    {
        var col = super.wrapColumn(underlyingColumn);
        var logging = underlyingColumn.getColumnLogging();
        if (null != logging)
            col.setColumnLogging(logging.reparent(this));
        return col;
    }

    /**
     * For logging, replace the provisioned table name with the nicer name
     */
    @Override
    public MutableColumnInfo addWrapColumn(ColumnInfo column)
    {
        var col = super.addWrapColumn(column);
        var logging = column.getColumnLogging();
        if (null != logging)
            col.setColumnLogging(logging.reparent(this));
        return col;
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
            if (column.isStringType() && !column.isHidden() && null == column.getFk() && null==BuiltInColumnTypes.findBuiltInType(column))
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
        // currently, picklists don't contain PHI and can always be deleted
        if (_list.isPicklist() && (InsertPermission.class.equals(perm) || UpdatePermission.class.equals(perm) || DeletePermission.class.equals(perm)))
            return getContainer().hasPermission(user, ManagePicklistsPermission.class);

        boolean gate = true;
        if (InsertPermission.class.equals(perm) || UpdatePermission.class.equals(perm))
            gate = canUserAccessPhi();
        else if (DeletePermission.class.equals(perm))
            gate = canUserAccessPhi() && _list.getAllowDelete();
        return gate && getContainer().hasPermission(user, perm);
    }

    @Override
    public String getPublicName()
    {
        return _list.getName();
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

    @Override
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
        return new TableInsertDataIteratorBuilder(data, this)
                .setKeyColumns(new CaseInsensitiveHashSet(getPkColumnNames()));
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
    public ParameterMapStatement insertStatement(Connection conn, User user) throws SQLException
    {
        return StatementUtils.insertStatement(conn, this, getContainer(), user, false, true);
    }

    @Override
    public ParameterMapStatement updateStatement(Connection conn, User user, Set<String> columns)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public ParameterMapStatement deleteStatement(Connection conn)
    {
        throw new UnsupportedOperationException();
    }
}
