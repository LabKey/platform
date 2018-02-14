/*
 * Copyright (c) 2010-2017 LabKey Corporation
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
package org.labkey.api.study.assay;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerFilterable;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DelegatingContainerFilter;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.OORDisplayColumnFactory;
import org.labkey.api.data.Parameter;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.StatementUtils;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.UpdateableTableInfo;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.dataiterator.TableInsertDataIterator;
import org.labkey.api.exp.MvColumn;
import org.labkey.api.exp.PropertyColumn;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.RawValueColumn;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.RowIdForeignKey;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * User: jeckels
 * Date: Dec 14, 2010
 */
public class AssayResultTable extends FilteredTable<AssayProtocolSchema> implements UpdateableTableInfo
{
    protected final ExpProtocol _protocol;
    protected final AssayProvider _provider;
    private final Domain _resultsDomain;

    private static final String RUN_ID_ALIAS = "Run";

    public AssayResultTable(AssayProtocolSchema schema, boolean includeCopiedToStudyColumns)
    {
        super(StorageProvisioner.createTableInfo(schema.getProvider().getResultsDomain(schema.getProtocol())), schema);
        _protocol = _userSchema.getProtocol();
        _provider = _userSchema.getProvider();

        _resultsDomain = _provider.getResultsDomain(_protocol);

        setDescription("Contains all of the results (and may contain raw data as well) for the " + _protocol.getName() + " assay definition");
        setName(AssayProtocolSchema.DATA_TABLE_NAME);
        setPublicSchemaName(_userSchema.getSchemaName());

        List<FieldKey> visibleColumns = new ArrayList<>();

        ColumnInfo specimenIdCol = null;
        boolean foundTargetStudyCol = false;

        for (ColumnInfo baseColumn : getRealTable().getColumns())
        {
            ColumnInfo col;

            if (getRealTable().getColumn(baseColumn.getName() + OORDisplayColumnFactory.OOR_INDICATOR_COLUMN_SUFFIX) != null)
            {
                // If this is the value column that goes with an OORIndicator, add the special OOR options
                col = OORDisplayColumnFactory.addOORColumns(this, baseColumn, getRealTable().getColumn(baseColumn.getName() +
                        OORDisplayColumnFactory.OOR_INDICATOR_COLUMN_SUFFIX));

            }
            else if (baseColumn.getName().toLowerCase().endsWith(OORDisplayColumnFactory.OOR_INDICATOR_COLUMN_SUFFIX.toLowerCase()) &&
                    getRealTable().getColumn(baseColumn.getName().substring(0, baseColumn.getName().length() - OORDisplayColumnFactory.OOR_INDICATOR_COLUMN_SUFFIX.length())) != null)
            {
                // If this is an OORIndicator and there's a matching value column in the same table, don't add this column
                col = null;
            }
            else if (baseColumn.isMvIndicatorColumn())
            {
                col = null;     // skip and instead add AliasedColumn below
            }
            else
            {
                col = wrapColumn(baseColumn);

                if (AbstractTsvAssayProvider.ROW_ID_COLUMN_NAME.equalsIgnoreCase(col.getName()) || AbstractTsvAssayProvider.DATA_ID_COLUMN_NAME.equalsIgnoreCase(col.getName()))
                {
                    col.setHidden(true);
                }
                if (AbstractTsvAssayProvider.ROW_ID_COLUMN_NAME.equalsIgnoreCase(col.getName()))
                {
                    col.setKeyField(true);
                    col.setFk(new RowIdForeignKey(col));
                }

                DomainProperty domainProperty = _resultsDomain.getPropertyByName(baseColumn.getName());
                if (domainProperty != null)
                {
                    col.setName(domainProperty.getName());
                    PropertyDescriptor pd = domainProperty.getPropertyDescriptor();
                    FieldKey pkFieldKey = new FieldKey(null, AbstractTsvAssayProvider.ROW_ID_COLUMN_NAME);
                    PropertyColumn.copyAttributes(_userSchema.getUser(), col, pd, schema.getContainer(), _userSchema.getSchemaPath(), getPublicName(), pkFieldKey);

                    ExpSampleSet ss = DefaultAssayRunCreator.getLookupSampleSet(domainProperty, getContainer());
                    if (ss != null || DefaultAssayRunCreator.isLookupToMaterials(domainProperty))
                    {
                        col.setFk(new ExpSchema(_userSchema.getUser(), _userSchema.getContainer()).getMaterialIdForeignKey(ss, domainProperty));
                    }
                }
                addColumn(col);

                if (col.getMvColumnName() != null)
                {
                    ColumnInfo rawValueCol = createRawValueColumn(baseColumn, col, RawValueColumn.RAW_VALUE_SUFFIX, "Raw Value", "This column contains the raw value itself, regardless of any missing value indicators that may have been set.");
                    addColumn(rawValueCol);

                    Domain domain = schema.getProvider().getResultsDomain(schema.getProtocol());
                    PropertyDescriptor pd = domain.getPropertyByName(col.getName()).getPropertyDescriptor();
                    ColumnInfo mvColumn = new AliasedColumn(this, col.getName() + MvColumn.MV_INDICATOR_SUFFIX,
                                                            StorageProvisioner.getMvIndicatorColumn(getRealTable(), pd, "No MV column found for '" + col.getName() + "' in list '" + getName() + "'"));
                    // MV indicators are strings
                    mvColumn.setLabel(col.getLabel() + " MV Indicator");
                    mvColumn.setSqlTypeName("VARCHAR");
                    mvColumn.setPropertyURI(col.getPropertyURI());
                    mvColumn.setNullable(true);
                    mvColumn.setUserEditable(false);
                    mvColumn.setHidden(true);
                    mvColumn.setMvIndicatorColumn(true);
                    addColumn(mvColumn);
                    col.setMvColumnName(FieldKey.fromParts(mvColumn.getFieldKey()));        // So we find it correctly for display
                }

                if (AbstractAssayProvider.TARGET_STUDY_PROPERTY_NAME.equals(col.getName()))
                    foundTargetStudyCol = true;

                if (AbstractAssayProvider.SPECIMENID_PROPERTY_NAME.equalsIgnoreCase(col.getName()))
                    specimenIdCol = col;
            }

            if (col != null && !col.isHidden() && !col.isUnselectable() && !col.isMvIndicatorColumn())
                visibleColumns.add(col.getFieldKey());
        }

        configureSpecimensLookup(specimenIdCol, foundTargetStudyCol);

        ColumnInfo dataColumn = getColumn("DataId");
        dataColumn.setLabel("Data");
        dataColumn.setFk(new ExpSchema(_userSchema.getUser(), _userSchema.getContainer()).getDataIdForeignKey());
        dataColumn.setUserEditable(false);
        dataColumn.setShownInUpdateView(false);
        dataColumn.setShownInUpdateView(false);

        getColumn("RowId").setShownInUpdateView(false);

        SQLFragment runIdSQL = new SQLFragment();
        runIdSQL.append(ExprColumn.STR_TABLE_ALIAS);
        runIdSQL.append(".");
        runIdSQL.append(RUN_ID_ALIAS);
        ExprColumn runColumn = new ExprColumn(this, RUN_ID_ALIAS, runIdSQL, JdbcType.INTEGER);
        runColumn.setFk(new QueryForeignKey(_userSchema, null, AssayProtocolSchema.RUNS_TABLE_NAME, null, null)
        {
            @Override
            public void propagateContainerFilter(ColumnInfo foreignKey, TableInfo lookupTable)
            {
                // Can't rely on normal container filter propagation since assay-backed datasets will have different
                // container filters on the dataset table compared with the assay result table from which they are
                // adopting columns
                if (lookupTable.supportsContainerFilter())
                {
                    ((ContainerFilterable)lookupTable).setContainerFilter(new DelegatingContainerFilter(AssayResultTable.this));
                }
            }
        });
        runColumn.setUserEditable(false);
        runColumn.setShownInInsertView(false);
        runColumn.setShownInUpdateView(false);
        addColumn(runColumn);

        Domain runDomain = _provider.getRunDomain(_protocol);
        for (DomainProperty prop : runDomain.getProperties())
        {
            if (!prop.isHidden())
                visibleColumns.add(FieldKey.fromParts("Run", prop.getName()));
        }

        for (DomainProperty prop : _provider.getBatchDomain(_protocol).getProperties())
        {
            if (!prop.isHidden())
                visibleColumns.add(FieldKey.fromParts("Run", AssayService.BATCH_COLUMN_NAME, prop.getName()));
        }

        if (includeCopiedToStudyColumns)
        {
            Set<String> studyColumnNames = schema.addCopiedToStudyColumns(this, false);
            for (String columnName : studyColumnNames)
            {
                visibleColumns.add(new FieldKey(null, columnName));
            }
        }

        ColumnInfo folderCol = getColumn("Folder");
        if (folderCol == null)
        {
            // Insert a folder/container column so that we can build up the right URL for links to this row of data 
            SQLFragment folderSQL = new SQLFragment("(SELECT Container FROM exp.Data d WHERE d.RowId = " + ExprColumn.STR_TABLE_ALIAS + ".DataId)");
            folderCol = new ExprColumn(this, "Folder", folderSQL, JdbcType.VARCHAR);
            // This can usually be treated as a normal VARCHAR, but remember that it's actually a custom type
            // for places like multi valued columns
            folderCol.setSqlTypeName("entityid");
            folderCol.setHidden(true);
            folderCol.setUserEditable(false);
            folderCol.setShownInInsertView(false);
            folderCol.setShownInUpdateView(false);
            addColumn(folderCol);
            ContainerForeignKey.initColumn(folderCol, _userSchema);
        }

        setDefaultVisibleColumns(visibleColumns);
    }

    private void configureSpecimensLookup(ColumnInfo specimenIdCol, boolean foundTargetStudyCol)
    {
        // Add FK to specimens
        if (specimenIdCol != null && specimenIdCol.getFk() == null)
        {
            if (!foundTargetStudyCol)
            {
                for (DomainProperty runDP : _provider.getRunDomain(_protocol).getProperties())
                {
                    if (AbstractAssayProvider.TARGET_STUDY_PROPERTY_NAME.equals(runDP.getName()))
                    {
                        foundTargetStudyCol = true;
                        break;
                    }
                }
            }

            if (!foundTargetStudyCol)
            {
                for (DomainProperty batchDP : _provider.getBatchDomain(_protocol).getProperties())
                {
                    if (AbstractAssayProvider.TARGET_STUDY_PROPERTY_NAME.equals(batchDP.getName()))
                    {
                        foundTargetStudyCol = true;
                        break;
                    }
                }
            }

            specimenIdCol.setDisplayColumnFactory(new DisplayColumnFactory()
            {
                @Override
                public DisplayColumn createRenderer(ColumnInfo colInfo)
                {
                    DataColumn result = new DataColumn(colInfo)
                    {
                        @Override
                        public String renderURL(RenderContext ctx)
                        {
                            // Don't make a url unless there's a specimen in the target study
                            FieldKey specimenIdKey = FieldKey.fromParts("SpecimenId", "RowId");
                            if (null != ctx.get(specimenIdKey))
                                return super.renderURL(ctx);
                            return null;
                        }
                    };
                    result.setInputType("text");
                    return result;
                }
            });

            if (foundTargetStudyCol)
            {
                specimenIdCol.setFk(new SpecimenForeignKey(_userSchema, _provider, _protocol));
                specimenIdCol.setURL(specimenIdCol.getFk().getURL(specimenIdCol));
                specimenIdCol.setDisplayColumnFactory(ColumnInfo.NOLOOKUP_FACTORY);
            }
        }
    }

    @Override
    protected void applyContainerFilter(ContainerFilter filter)
    {
        // There isn't a container column directly on this table so do a special filter
        if (getContainer() != null)
        {
            FieldKey containerColumn = FieldKey.fromParts("Run", "Folder");
            clearConditions(containerColumn);
            addCondition(filter.getSQLFragment(getSchema(), new SQLFragment("(SELECT d.Container FROM exp.Data d WHERE d.RowId = DataId)"), getContainer()), containerColumn);
        }
    }

    @NotNull
    @Override
    public SQLFragment getFromSQL(String alias)
    {
        SQLFragment result = new SQLFragment();
        result.append("(SELECT innerResults.*, innerData.RunId AS " );
        result.append(RUN_ID_ALIAS);
        result.append(" FROM\n");
        result.append(super.getFromSQL("innerResults"));
        result.append("\nINNER JOIN ");
        result.append(ExperimentService.get().getTinfoData(), "innerData");
        result.append(" ON (innerData.RowId = innerResults.DataId)) ");
        result.append(alias);
        return result;
    }

    @Override
    public Domain getDomain()
    {
        return _resultsDomain;
    }

    private ColumnInfo createRawValueColumn(ColumnInfo baseColumn, ColumnInfo col, String nameSuffix, String labelSuffix, String descriptionSuffix)
    {
        ColumnInfo rawValueCol = new AliasedColumn(baseColumn.getName() + nameSuffix, col);
        rawValueCol.setDisplayColumnFactory(ColumnInfo.DEFAULT_FACTORY);
        rawValueCol.setLabel(baseColumn.getLabel() + " " + labelSuffix);
        String description = baseColumn.getDescription();
        if (description == null)
        {
            description = "";
        }
        else
        {
            description += " ";
        }
        description += descriptionSuffix;
        rawValueCol.setDescription(description);
        rawValueCol.setUserEditable(false);
        rawValueCol.setHidden(true);
        rawValueCol.setRawValueColumn(true);
        rawValueCol.setMvColumnName(null); // This column itself does not allow QC
        rawValueCol.setNullable(true); // Otherwise we get complaints on import for required fields
        return rawValueCol;
    }

    @Override
    protected ColumnInfo resolveColumn(String name)
    {
        ColumnInfo result = super.resolveColumn(name);
        
        if ("Properties".equalsIgnoreCase(name))
        {
            // Hook up a column that joins back to this table so that the columns formerly under the Properties
            // node when this was OntologyManager-backed can still be queried there
            result = wrapColumn("Properties", getRealTable().getColumn("RowId"));
            result.setIsUnselectable(true);
            LookupForeignKey fk = new LookupForeignKey("RowId")
            {
                @Override
                public TableInfo getLookupTableInfo()
                {
                    return new AssayResultTable(_userSchema, false);
                }
            };
            fk.setPrefixColumnCaption(false);
            result.setFk(fk);
        }

        return result;
    }

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

    @Override
    public ObjectUriType getObjectUriType()
    {
        return ObjectUriType.schemaColumn;
    }

    @Override
    public String getObjectURIColumnName()
    {
        return null;
    }

    @Override
    public String getObjectIdColumnName()
    {
        return null;
    }

    @Override
    public CaseInsensitiveHashMap<String> remapSchemaColumns()
    {
        return null;
    }

    @Override
    public CaseInsensitiveHashSet skipProperties()
    {
        return null;
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        return (DeletePermission.class.isAssignableFrom(perm) || UpdatePermission.class.isAssignableFrom(perm) || ReadPermission.class.isAssignableFrom(perm)) &&
                _provider.isEditableResults(_protocol) &&
                _userSchema.getContainer().hasPermission(user, perm);
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        return new AssayResultUpdateService(_userSchema, this);
    }

    @Override
    public DataIteratorBuilder persistRows(DataIteratorBuilder data, DataIteratorContext context)
    {
        return TableInsertDataIterator.create(data, this, null, context);
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
    public FieldKey getContainerFieldKey()
    {
        return new FieldKey(null, "Folder");
    }
}
