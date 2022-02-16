/*
 * Copyright (c) 2019 LabKey Corporation
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
package org.labkey.api.assay;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.OORDisplayColumnFactory;
import org.labkey.api.data.ParameterMapStatement;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.StatementUtils;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.UpdateableTableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.dataiterator.TableInsertDataIteratorBuilder;
import org.labkey.api.exp.MvColumn;
import org.labkey.api.exp.PropertyColumn;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.RawValueColumn;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpSampleType;
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
import org.labkey.api.query.PropertiesDisplayColumn;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.RowIdForeignKey;
import org.labkey.api.query.column.BuiltInColumnTypes;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.assay.SpecimenForeignKey;
import org.labkey.api.study.publish.StudyPublishService;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    public AssayResultTable(AssayProtocolSchema schema, ContainerFilter cf, boolean includeLinkedToStudyColumns)
    {
        super(StorageProvisioner.createTableInfo(schema.getProvider().getResultsDomain(schema.getProtocol())), schema, cf);
        _protocol = _userSchema.getProtocol();
        _provider = _userSchema.getProvider();

        _resultsDomain = _provider.getResultsDomain(_protocol);

        setDescription("Contains all of the results (and may contain raw data as well) for the " + _protocol.getName() + " assay definition");
        setName(AssayProtocolSchema.DATA_TABLE_NAME);
        setPublicSchemaName(_userSchema.getSchemaName());

        List<FieldKey> visibleColumns = new ArrayList<>();

        MutableColumnInfo specimenIdCol = null;
        boolean foundTargetStudyCol = false;

        for (ColumnInfo baseColumn : getRealTable().getColumns())
        {
            MutableColumnInfo col;

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
                    col.setFieldKey(new FieldKey(null, domainProperty.getName()));
                    PropertyDescriptor pd = domainProperty.getPropertyDescriptor();
                    FieldKey pkFieldKey = new FieldKey(null, AbstractTsvAssayProvider.ROW_ID_COLUMN_NAME);
                    PropertyColumn.copyAttributes(_userSchema.getUser(), col, pd, schema.getContainer(), _userSchema.getSchemaPath(), getPublicName(), pkFieldKey);

                    ExpSampleType st = DefaultAssayRunCreator.getLookupSampleType(domainProperty, getContainer(), getUserSchema().getUser());
                    if (st != null || DefaultAssayRunCreator.isLookupToMaterials(domainProperty))
                    {
                        col.setFk(new ExpSchema(_userSchema.getUser(), _userSchema.getContainer()).getMaterialIdForeignKey(st, domainProperty, cf));
                    }
                }
                addColumn(col);

                if (col.getMvColumnName() != null)
                {
                    var rawValueCol = createRawValueColumn(baseColumn, col, RawValueColumn.RAW_VALUE_SUFFIX, "Raw Value", "This column contains the raw value itself, regardless of any missing value indicators that may have been set.");
                    addColumn(rawValueCol);

                    Domain domain = schema.getProvider().getResultsDomain(schema.getProtocol());
                    PropertyDescriptor pd = domain.getPropertyByName(col.getName()).getPropertyDescriptor();
                    AliasedColumn mvColumn = new AliasedColumn(this, col.getName() + MvColumn.MV_INDICATOR_SUFFIX,
                                                            StorageProvisioner.get().getMvIndicatorColumn(getRealTable(), pd, "No MV column found for '" + col.getName() + "' in list '" + getName() + "'"));
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

        if (null != StudyService.get())
            configureSpecimensLookup(specimenIdCol, foundTargetStudyCol);

        var dataColumn = getMutableColumn("DataId");
        dataColumn.setLabel("Data");
        dataColumn.setFk(new ExpSchema(_userSchema.getUser(), _userSchema.getContainer()).getDataIdForeignKey(getContainerFilter()));
        dataColumn.setUserEditable(false);
        dataColumn.setShownInUpdateView(false);
        dataColumn.setShownInUpdateView(false);

        getMutableColumn("RowId").setShownInUpdateView(false);

        SQLFragment runIdSQL = new SQLFragment();
        runIdSQL.append(ExprColumn.STR_TABLE_ALIAS);
        runIdSQL.append(".");
        runIdSQL.append(RUN_ID_ALIAS);
        ExprColumn runColumn = new ExprColumn(this, RUN_ID_ALIAS, runIdSQL, JdbcType.INTEGER);
        // TODO ContainerFilter make sure this is still correct
        // Can't rely on normal container filter propagation since assay-backed datasets will have different
        // container filters on the dataset table compared with the assay result table from which they are
        // adopting columns
        runColumn.setFk(QueryForeignKey.from(_userSchema, getContainerFilter()).to(AssayProtocolSchema.RUNS_TABLE_NAME, null, null));
        runColumn.setUserEditable(false);
        runColumn.setShownInInsertView(false);
        runColumn.setShownInUpdateView(false);
        addColumn(runColumn);

        AssayWellExclusionService svc = AssayWellExclusionService.getProvider(_protocol);
        if (svc != null)
        {
            addColumn(svc.createExcludedColumn(this, getUserSchema().getProvider()));
            addColumn(svc.createExclusionCommentColumn(this, getUserSchema().getProvider()));
        }

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

        // add any QC filter conditions if applicable
        AssayQCService qcService = AssayQCService.getProvider();
        SQLFragment qcFragment = qcService.getDataTableCondition(_protocol, getContainer(), getUserSchema().getUser());
        addCondition(qcFragment);

        StudyPublishService studyPublishService = StudyPublishService.get();
        if (includeLinkedToStudyColumns && studyPublishService != null)
        {
            String rowIdName = _provider.getTableMetadata(_protocol).getResultRowIdFieldKey().getName();
            Set<String> studyColumnNames = studyPublishService.addLinkedToStudyColumns(this, Dataset.PublishSource.Assay, false, _protocol.getRowId(), rowIdName, _userSchema.getUser());

            for (String columnName : studyColumnNames)
            {
                visibleColumns.add(new FieldKey(null, columnName));
            }
        }

        var folderCol = getMutableColumn("Folder");
        if (folderCol == null)
        {
            // Insert a folder/container column so that we can build up the right URL for links to this row of data 
            SQLFragment folderSQL = new SQLFragment("(SELECT Container FROM exp.Data d WHERE d.RowId = " + ExprColumn.STR_TABLE_ALIAS + ".DataId)");
            folderCol = new ExprColumn(this, "Folder", folderSQL, JdbcType.VARCHAR);
            // This can usually be treated as a normal VARCHAR, but remember that it's actually a custom type
            // for places like multi valued columns
            folderCol.setSqlTypeName("entityid");
            folderCol.setConceptURI(BuiltInColumnTypes.CONTAINERID_CONCEPT_URI);
            addColumn(folderCol);
        }

        var lsidCol = createRowExpressionLsidColumn(this);
        addColumn(lsidCol);

        var propsCol = createPropertiesColumn();
        addColumn(propsCol);

        setDefaultVisibleColumns(visibleColumns);
    }

    public static BaseColumnInfo createRowExpressionLsidColumn(FilteredTable<? extends AssayProtocolSchema> table)
    {
        AssayProtocolSchema schema = table.getUserSchema();
        SqlDialect dialect = schema.getDbSchema().getSqlDialect();

        SQLFragment sql;
        String resultRowLsidExpression = schema.getProvider().getResultRowLSIDExpression();
        if (resultRowLsidExpression != null)
        {
            sql = new SQLFragment(dialect.concatenate(
                    "'" + resultRowLsidExpression +
                            ".Protocol-" + schema.getProtocol().getRowId() + ":'",
                    "CAST(" + ExprColumn.STR_TABLE_ALIAS + ".rowId AS VARCHAR)"));
        }
        else
        {
            sql = new SQLFragment("CAST(NULL AS VARCHAR)");
        }

        var lsidCol = new ExprColumn(table, "LSID", sql, JdbcType.VARCHAR);
        lsidCol.setHidden(true);
        lsidCol.setCalculated(true);
        lsidCol.setUserEditable(false);
        lsidCol.setReadOnly(true);
        lsidCol.setHidden(true);
        return lsidCol;
    }

    // Expensive render-time fetching of all ontology properties attached to the object row
    protected BaseColumnInfo createPropertiesColumn()
    {
        var lsidColumn = getColumn("LSID");

        var col = new AliasedColumn(this, "Properties", lsidColumn);
        col.setDescription("Includes all properties set for this row");
        col.setDisplayColumnFactory(colInfo -> new PropertiesDisplayColumn(getUserSchema(), colInfo));
        col.setConceptURI(PropertiesDisplayColumn.CONCEPT_URI);
        col.setHidden(true);
        col.setUserEditable(false);
        col.setReadOnly(true);
        col.setCalculated(true);
        return col;
    }


    private void configureSpecimensLookup(MutableColumnInfo specimenIdCol, boolean foundTargetStudyCol)
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
            FieldKey containerColumn = FieldKey.fromParts("Container");
            clearConditions(containerColumn);
            addCondition(filter.getSQLFragment(getSchema(), new SQLFragment("Container"), getContainer()), containerColumn);
        }
    }

    @NotNull
    @Override
    public SQLFragment getFromSQL(String alias)
    {
        checkReadBeforeExecute();
        SQLFragment result = new SQLFragment();
        result.append("(SELECT innerResults.*, innerData.RunId AS ").append(RUN_ID_ALIAS).append(", innerData.Container" );
        result.append(" FROM\n");
        result.append(getFromTable().getFromSQL("innerResults"));
        result.append("\nINNER JOIN ");
        result.append(ExperimentService.get().getTinfoData(), "innerData");
        result.append(" ON (innerData.RowId = innerResults.DataId) ");
        var filter = getFilter();
        var where = filter.getSQLFragment(_rootTable.getSqlDialect());
        if (!where.isEmpty())
        {
            Map<FieldKey, ColumnInfo> columnMap = Table.createColumnMap(getFromTable(), getFromTable().getColumns());
            SQLFragment filterFrag = filter.getSQLFragment(_rootTable.getSqlDialect(), "innerResults", columnMap);
        result.append("\n").append(filterFrag);
        }
        result.append(") ").append(alias);
        return result;
    }

    @Override
    public SQLFragment getFromSQL(String alias, boolean skipTransform)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Domain getDomain()
    {
        return _resultsDomain;
    }

    private BaseColumnInfo createRawValueColumn(ColumnInfo baseColumn, ColumnInfo col, String nameSuffix, String labelSuffix, String descriptionSuffix)
    {
        AliasedColumn rawValueCol = new AliasedColumn(baseColumn.getName() + nameSuffix, col);
        rawValueCol.setDisplayColumnFactory(BaseColumnInfo.DEFAULT_FACTORY);
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
            var wrapped = wrapColumn("Properties", getRealTable().getColumn("RowId"));
            wrapped.setIsUnselectable(true);
            LookupForeignKey fk = new LookupForeignKey(getContainerFilter(),"RowId", null)
            {
                @Override
                public TableInfo getLookupTableInfo()
                {
                    return new AssayResultTable(_userSchema, getLookupContainerFilter(), false);
                }
            };
            fk.setPrefixColumnCaption(false);
            wrapped.setFk(fk);
            result = wrapped;
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
        if (perm.equals(ReadPermission.class))
            return _userSchema.getContainer().hasPermission(user, perm);
        if (DeletePermission.class.isAssignableFrom(perm) || UpdatePermission.class.isAssignableFrom(perm))
                return _provider.isEditableResults(_protocol) && _userSchema.getContainer().hasPermission(user, perm);
        return false;
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        return new AssayResultUpdateService(_userSchema, this);
    }

    @Override
    public DataIteratorBuilder persistRows(DataIteratorBuilder data, DataIteratorContext context)
    {
        return new TableInsertDataIteratorBuilder(data, this);
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

    @Override
    public FieldKey getContainerFieldKey()
    {
        return new FieldKey(null, "Folder");
    }
}
