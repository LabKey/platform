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

package org.labkey.experiment.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MultiValuedForeignKey;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Sort;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.UnionContainerFilter;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.dataiterator.LoggingDataIterator;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyColumn;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.query.ExpDataTable;
import org.labkey.api.exp.query.ExpMaterialTable;
import org.labkey.api.exp.query.ExpSampleSetTable;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.RowIdForeignKey;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.StringExpression;
import org.labkey.api.view.ActionURL;
import org.labkey.experiment.ExpDataIterators;
import org.labkey.experiment.ExpDataIterators.AliasDataIteratorBuilder;
import org.labkey.experiment.controllers.exp.ExperimentController;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Objects.requireNonNull;

public class ExpMaterialTableImpl extends ExpRunItemTableImpl<ExpMaterialTable.Column> implements ExpMaterialTable
{
    ExpSampleSetImpl _ss;

    public ExpMaterialTableImpl(String name, UserSchema schema, ContainerFilter cf)
    {
        super(name, ExperimentServiceImpl.get().getTinfoMaterial(), schema, new ExpMaterialImpl(new Material()), cf);
        setDetailsURL(new DetailsURL(new ActionURL(ExperimentController.ShowMaterialAction.class, schema.getContainer()), Collections.singletonMap("rowId", "rowId")));
        setName(ExpSchema.TableType.Materials.name());
        setPublicSchemaName(ExpSchema.SCHEMA_NAME);
    }

    @Override
    protected ColumnInfo resolveColumn(String name)
    {
        ColumnInfo result = super.resolveColumn(name);
        if (result == null)
        {
            if ("CpasType".equalsIgnoreCase(name))
                return createColumn("SampleSet", Column.SampleSet);

            if ("Property".equalsIgnoreCase(name))
                return createPropertyColumn("Property");
        }
        return result;
    }

    public BaseColumnInfo createColumn(String alias, Column column)
    {
        switch (column)
        {
            case Folder:
                return wrapColumn(alias, _rootTable.getColumn("Container"));
            case LSID:
                return wrapColumn(alias, _rootTable.getColumn("LSID"));
            case Name:
                return wrapColumn(alias, _rootTable.getColumn("Name"));
            case Description:
                return wrapColumn(alias, _rootTable.getColumn("Description"));
            case SampleSet:
            {
                var columnInfo = wrapColumn(alias, _rootTable.getColumn("CpasType"));
                columnInfo.setFk(new LookupForeignKey(getContainerFilter(), null, null, null, (String)null, "LSID", "Name")
                {
                    public TableInfo getLookupTableInfo()
                    {
                        ExpSampleSetTable sampleSetTable = ExperimentService.get().createSampleSetTable(ExpSchema.TableType.SampleSets.toString(), _userSchema, getLookupContainerFilter());
                        sampleSetTable.populate();
                        return sampleSetTable;
                    }

                    @Override
                    public StringExpression getURL(ColumnInfo parent)
                    {
                        return super.getURL(parent, true);
                    }
                });
                return columnInfo;
            }
            case SourceProtocolLSID:
            {
                // NOTE: This column is incorrectly named "Protocol", but we are keeping it for backwards compatibility to avoid breaking queries in hvtnFlow module
                ExprColumn columnInfo = new ExprColumn(this, ExpDataTable.Column.Protocol.toString(), new SQLFragment(
                        "(SELECT ProtocolLSID FROM " + ExperimentServiceImpl.get().getTinfoProtocolApplication() + " pa " +
                        " WHERE pa.RowId = " + ExprColumn.STR_TABLE_ALIAS + ".SourceApplicationId)"), JdbcType.VARCHAR);
                columnInfo.setSqlTypeName("lsidtype");
                columnInfo.setFk(getExpSchema().getProtocolForeignKey(getContainerFilter(),"LSID"));
                columnInfo.setLabel("Source Protocol");
                columnInfo.setDescription("Contains a reference to the protocol for the protocol application that created this sample");
                columnInfo.setUserEditable(false);
                columnInfo.setReadOnly(true);
                columnInfo.setHidden(true);
                return columnInfo;
            }

            case SourceProtocolApplication:
            {
                var columnInfo = wrapColumn(alias, _rootTable.getColumn("SourceApplicationId"));
                columnInfo.setFk(getExpSchema().getProtocolApplicationForeignKey(getContainerFilter()));
                columnInfo.setUserEditable(false);
                columnInfo.setReadOnly(true);
                columnInfo.setHidden(true);
                columnInfo.setAutoIncrement(false);
                return columnInfo;
            }

            case SourceApplicationInput:
            {
                var col = createEdgeColumn(alias, Column.SourceProtocolApplication, ExpSchema.TableType.MaterialInputs);
                col.setDescription("Contains a reference to the MaterialInput row between this ExpMaterial and it's SourceProtocolApplication");
                col.setHidden(true);
                return col;
            }

            case RunApplication:
            {
                SQLFragment sql = new SQLFragment("(SELECT pa.rowId FROM ")
                        .append(ExperimentService.get().getTinfoProtocolApplication(), "pa")
                        .append(" WHERE pa.runId = ").append(ExprColumn.STR_TABLE_ALIAS).append(".runId")
                        .append(" AND pa.cpasType = '").append(ExpProtocol.ApplicationType.ExperimentRunOutput.name()).append("'")
                        .append(")");

                var col = new ExprColumn(this, alias, sql, JdbcType.INTEGER);
                col.setFk(getExpSchema().getProtocolApplicationForeignKey(getContainerFilter()));
                col.setDescription("Contains a reference to the ExperimentRunOutput protocol application of the run that created this sample");
                col.setUserEditable(false);
                col.setReadOnly(true);
                col.setHidden(true);
                return col;
            }

            case RunApplicationOutput:
            {
                var col = createEdgeColumn(alias, Column.RunApplication, ExpSchema.TableType.MaterialInputs);
                col.setDescription("Contains a reference to the MaterialInput row between this ExpMaterial and it's RunOutputApplication");
                return col;
            }

            case Run:
            {
                var ret = wrapColumn(alias, _rootTable.getColumn("RunId"));
                ret.setReadOnly(true);
                return ret;
            }
            case RowId:
            {
                var ret = wrapColumn(alias, _rootTable.getColumn("RowId"));
                // When no sorts are added by views, QueryServiceImpl.createDefaultSort() adds the primary key's default sort direction
                ret.setSortDirection(Sort.SortDirection.DESC);
                ret.setFk(new RowIdForeignKey(ret));
                ret.setHidden(true);
                ret.setShownInInsertView(false);
                return ret;
            }
            case Property:
                return createPropertyColumn(alias);
            case Flag:
                return createFlagColumn(alias);
            case Created:
                return wrapColumn(alias, _rootTable.getColumn("Created"));
            case CreatedBy:
                return createUserColumn(alias, _rootTable.getColumn("CreatedBy"));
            case Modified:
                return wrapColumn(alias, _rootTable.getColumn("Modified"));
            case ModifiedBy:
                return createUserColumn(alias, _rootTable.getColumn("ModifiedBy"));
            case Alias:
                var aliasCol = wrapColumn("Alias", getRealTable().getColumn("LSID"));
                aliasCol.setDescription("Contains the list of aliases for this data object");
                aliasCol.setFk(new MultiValuedForeignKey(new LookupForeignKey("LSID") {
                    @Override
                    public TableInfo getLookupTableInfo()
                    {
                        return ExperimentService.get().getTinfoMaterialAliasMap();
                    }
                    }, "Alias")
                {
                    @Override
                    public boolean isMultiSelectInput()
                    {
                        return false;
                    }
                });
                aliasCol.setCalculated(false);
                aliasCol.setNullable(true);
                aliasCol.setRequired(false);
                aliasCol.setDisplayColumnFactory(new ExpDataClassDataTableImpl.AliasDisplayColumnFactory());

                return aliasCol;

            case Inputs:
                return createLineageColumn(this, alias, true);

            case Outputs:
                return createLineageColumn(this, alias, false);

            case Properties:
                return (BaseColumnInfo) createPropertiesColumn(alias);

            default:
                throw new IllegalArgumentException("Unknown column " + column);
        }
    }

    public BaseColumnInfo createPropertyColumn(String alias)
    {
        var ret = super.createPropertyColumn(alias);
        if (_ss != null)
        {
            final TableInfo t = _ss.getTinfo();
            if (t != null)
            {
                ret.setFk(new LookupForeignKey()
                {
                    @Override
                    public TableInfo getLookupTableInfo()
                    {
                        return t;
                    }

                    @Override
                    protected ColumnInfo getPkColumn(TableInfo table)
                    {
                        return t.getColumn("lsid");
                    }
                });
//                ret.setFk(new PropertyForeignKey(domain, _userSchema));
            }
        }
        ret.setIsUnselectable(true);
        ret.setDescription("A holder for any custom fields associated with this sample");
        ret.setHidden(true);
        return ret;
    }

    public void setSampleSet(ExpSampleSet ss, boolean filter)
    {
        checkLocked();
        if (_ss != null)
        {
            throw new IllegalStateException("Cannot unset sample set");
        }
        if (ss != null && !(ss instanceof ExpSampleSetImpl))
        {
            throw new IllegalArgumentException("Expected sample set to be an instance of " + ExpSampleSetImpl.class.getName() + " but was a " + ss.getClass().getName());
        }
        _ss = (ExpSampleSetImpl)ss;
        if (_ss != null)
        {
            setPublicSchemaName(SamplesSchema.SCHEMA_NAME);
            setName(ss.getName());
            if (filter)
                addCondition(getRealTable().getColumn("CpasType"), _ss.getLSID());

            ActionURL url = PageFlowUtil.urlProvider(ExperimentUrls.class).getImportSamplesURL(getContainer(), _ss.getName());
            setImportURL(new DetailsURL(url));
        }
    }

    public void setMaterials(Set<ExpMaterial> materials)
    {
        checkLocked();
        if (materials.isEmpty())
        {
            addCondition(new SQLFragment("1 = 2"));
        }
        else
        {
            SQLFragment sql = new SQLFragment();
            sql.append("RowID IN (");
            String separator = "";
            for (ExpMaterial material : materials)
            {
                sql.append(separator);
                separator = ", ";
                sql.append(material.getRowId());
            }
            sql.append(")");
            addCondition(sql);
        }
    }

    @Override
    protected void populateColumns()
    {
        populate(null, false);
    }

    @Override
    public final void populate(@Nullable ExpSampleSet ss, boolean filter)
    {
        populateColumns(ss, filter);
        _populated = true;
    }

    protected void populateColumns(@Nullable ExpSampleSet ss, boolean filter)
    {
        if (ss != null)
        {
            if (ss.getDescription() != null)
            {
                setDescription(ss.getDescription());
            }
            else
            {
                setDescription("Contains one row per sample in the " + ss.getName() + " sample set");
            }
        }

        var rowIdCol = addColumn(ExpMaterialTable.Column.RowId);
        
        addColumn(Column.SourceProtocolApplication);

        addColumn(Column.SourceApplicationInput);

        addColumn(Column.RunApplication);

        addColumn(Column.RunApplicationOutput);

        addColumn(Column.SourceProtocolLSID);

        var nameCol = addColumn(ExpMaterialTable.Column.Name);
        if (ss != null && ss.hasNameAsIdCol())
        {
            // Show the Name field but don't mark is as required when using name expressions
            if (ss.hasNameExpression())
            {
                nameCol.setNullable(true);
                String desc = appendNameExpressionDescription(nameCol.getDescription(), ss.getNameExpression());
                nameCol.setDescription(desc);
            }
            else
            {
                nameCol.setNullable(false);
            }
            nameCol.setDisplayColumnFactory(new IdColumnRendererFactory());
        }
        else
        {
            nameCol.setReadOnly(true);
            nameCol.setShownInInsertView(false);
        }

        addColumn(Column.Alias);

        addColumn(Column.Description);

        var typeColumnInfo = addColumn(Column.SampleSet);
        typeColumnInfo.setFk(new QueryForeignKey(_userSchema, getContainerFilter(), ExpSchema.SCHEMA_NAME, getContainer(), null, getUserSchema().getUser(), ExpSchema.TableType.SampleSets.name(), "lsid", null)
        {
            @Override
            protected ContainerFilter getLookupContainerFilter()
            {
                // Be sure that we can resolve the sample set if it's defined in a separate container.
                // Same as CurrentPlusProjectAndShared but includes SampleSet's container as well.
                // Issue 37982: Sample Set: Link to precursor sample set does not resolve correctly if sample has parents in current sample set and a sample set in the parent container
                Set<Container> containers = new HashSet<>();
                if (null != ss)
                    containers.add(ss.getContainer());
                containers.add(getContainer());
                if (getContainer().getProject() != null)
                    containers.add(getContainer().getProject());
                containers.add(ContainerManager.getSharedContainer());
                ContainerFilter cf = new ContainerFilter.CurrentPlusExtras(_userSchema.getUser(), containers);

                if (null != _containerFilter && _containerFilter != ContainerFilter.CURRENT)
                    cf = new UnionContainerFilter(_containerFilter, cf);
                return cf;
            }
        });

        typeColumnInfo.setReadOnly(true);
        typeColumnInfo.setShownInInsertView(false);

        addContainerColumn(ExpMaterialTable.Column.Folder, null);

        var runCol = addColumn(ExpMaterialTable.Column.Run);
        runCol.setFk(new ExpSchema(_userSchema.getUser(), getContainer()).getRunIdForeignKey(getContainerFilter()));
        runCol.setShownInInsertView(false);
        runCol.setShownInUpdateView(false);

        var colLSID = addColumn(ExpMaterialTable.Column.LSID);
        colLSID.setHidden(true);
        colLSID.setReadOnly(true);
        colLSID.setUserEditable(false);
        colLSID.setShownInInsertView(false);
        colLSID.setShownInDetailsView(false);
        colLSID.setShownInUpdateView(false);

        addColumn(ExpMaterialTable.Column.Created);
        addColumn(ExpMaterialTable.Column.CreatedBy);
        addColumn(ExpMaterialTable.Column.Modified);
        addColumn(ExpMaterialTable.Column.ModifiedBy);

        List<FieldKey> defaultCols = new ArrayList<>();
        defaultCols.add(FieldKey.fromParts(ExpMaterialTable.Column.Name));
        defaultCols.add(FieldKey.fromParts(ExpMaterialTable.Column.Run));

        if (ss == null)
            defaultCols.add(FieldKey.fromParts(ExpMaterialTable.Column.SampleSet));

        addColumn(ExpMaterialTable.Column.Flag);
        // TODO is this a real Domain???
        if (ss != null && !"urn:lsid:labkey.com:SampleSource:Default".equals(ss.getDomain().getTypeURI()))
        {
            defaultCols.add(FieldKey.fromParts(ExpMaterialTable.Column.Flag));
            setSampleSet(ss, filter);
            addSampleSetColumns(ss, defaultCols);
            setName(_ss.getName());

            ActionURL gridUrl = new ActionURL(ExperimentController.ShowMaterialSourceAction.class, getContainer());
            gridUrl.addParameter("rowId", ss.getRowId());
            setGridURL(new DetailsURL(gridUrl));
        }

        addVocabularyDomains();
        addColumn(Column.Properties);

        var colInputs = addColumn(Column.Inputs);
        addMethod("Inputs", new LineageMethod(getContainer(), colInputs, true));

        var colOutputs = addColumn(Column.Outputs);
        addMethod("Outputs", new LineageMethod(getContainer(), colOutputs, false));


        ActionURL detailsUrl = new ActionURL(ExperimentController.ShowMaterialAction.class, getContainer());
        DetailsURL url = new DetailsURL(detailsUrl, Collections.singletonMap("rowId", "RowId"));
        nameCol.setURL(url);
        rowIdCol.setURL(url);
        setDetailsURL(url);

        setTitleColumn(Column.Name.toString());

        setDefaultVisibleColumns(defaultCols);
    }

    public Domain getDomain()
    {
        return _ss == null ? null : _ss.getDomain();
    }

    public static String appendNameExpressionDescription(String currentDescription, String nameExpression)
    {
        if (nameExpression == null)
            return currentDescription;

        StringBuilder sb = new StringBuilder();
        if (currentDescription != null && !currentDescription.isEmpty())
            sb.append(currentDescription).append("\n");

        sb.append("If not provided, a unique name will be generated from the expression:\n");
        sb.append(nameExpression);
        return sb.toString();
    }

    private void addSampleSetColumns(ExpSampleSet ss, List<FieldKey> visibleColumns)
    {
        TableInfo dbTable = ((ExpSampleSetImpl)ss).getTinfo();
        if (null == dbTable)
            return;

        UserSchema schema = getUserSchema();
        Domain domain = ss.getDomain();
        ColumnInfo lsidColumn = getColumn(Column.LSID);

        visibleColumns.remove(FieldKey.fromParts("Run"));

        for (ColumnInfo dbColumn : dbTable.getColumns())
        {
            if (lsidColumn.getFieldKey().equals(dbColumn.getFieldKey()))
                continue;

            // TODO this seems bad to me, why isn't this done in ss.getTinfo()
            if (dbColumn.getName().equalsIgnoreCase("genid"))
            {
                ((BaseColumnInfo)dbColumn).setHidden(true);
                ((BaseColumnInfo)dbColumn).setUserEditable(false);
                ((BaseColumnInfo)dbColumn).setShownInDetailsView(false);
                ((BaseColumnInfo)dbColumn).setShownInInsertView(false);
                ((BaseColumnInfo)dbColumn).setShownInUpdateView(false);
            }

            // TODO missing values? comments? flags?
            DomainProperty dp = domain.getPropertyByURI(dbColumn.getPropertyURI());
            var propColumn = wrapColumnFromJoinedTable(null==dp?dbColumn.getName():dp.getName(), dbColumn, ExprColumn.STR_TABLE_ALIAS);
            if (null != dp)
            {
                PropertyColumn.copyAttributes(schema.getUser(), propColumn, dp.getPropertyDescriptor(), schema.getContainer(),
                    SchemaKey.fromParts("samples"), ss.getName(), FieldKey.fromParts("RowId"));
                if (isIdCol(ss, dp.getPropertyDescriptor()))
                {
                    propColumn.setNullable(false);
                    propColumn.setDisplayColumnFactory(new IdColumnRendererFactory());
                }

                //fix for Issue 38341: domain designer advanced settings 'show in default view' setting is not respected
                if (!propColumn.isHidden())
                {
                    visibleColumns.add(propColumn.getFieldKey());
                }
            }
            addColumn(propColumn);
        }
        setDefaultVisibleColumns(visibleColumns);
    }

    @NotNull
    @Override
    public SQLFragment getFromSQL(String alias)
    {
        TableInfo provisioned = null == _ss ? null : _ss.getTinfo();

        // all columns from exp.material except lsid
        Set<String> dataCols = new CaseInsensitiveHashSet(_rootTable.getColumnNameSet());

        // don't select lsid twice
        if (null != provisioned)
            dataCols.remove("lsid");

        SQLFragment sql = new SQLFragment();
        sql.append("(SELECT ");
        String comma = "";
        for (String dataCol : dataCols)
        {
            sql.append(comma).append("m.").append(dataCol);
            comma = ", ";
        }
        if (null != provisioned)
            sql.append(comma).append("ss.*");
        sql.append(" FROM ");
        sql.append(_rootTable, "m");
        if (null != provisioned)
            sql.append(" INNER JOIN ").append(provisioned, "ss").append(" ON m.lsid = ss.lsid");

        // WHERE
        Map<FieldKey, ColumnInfo> columnMap = Table.createColumnMap(getFromTable(), getFromTable().getColumns());
        SQLFragment filterFrag = getFilter().getSQLFragment(_rootTable.getSqlDialect(), columnMap);
        sql.append("\n").append(filterFrag).append(") ").append(alias);

        return sql;
    }


    private boolean isIdCol(ExpSampleSet ss, PropertyDescriptor pd)
    {
        for (DomainProperty dp : ss.getIdCols())
            if (dp.getPropertyDescriptor() == pd)
                return true;
        return false;
    }

    private class IdColumnRendererFactory implements DisplayColumnFactory
    {
        @Override
        public DisplayColumn createRenderer(ColumnInfo colInfo)
        {
            return new IdColumnRenderer(colInfo);
        }
    }

    private class IdColumnRenderer extends DataColumn
    {
        public IdColumnRenderer(ColumnInfo col)
        {
            super(col);
        }

        @Override
        protected boolean isDisabledInput(RenderContext ctx)
        {
            return !super.isDisabledInput() && ctx.getMode() != DataRegion.MODE_INSERT;
        }
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        return new SampleSetUpdateServiceDI(this, _ss);
    }



    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        if (_ss != null || perm.isAssignableFrom(DeletePermission.class) || perm.isAssignableFrom(ReadPermission.class))
            return _userSchema.getContainer().hasPermission(user, perm);

        // don't allow insert/update on exp.Materials without a sample set
        return false;
    }

    @NotNull
    @Override
    public Map<String, Pair<IndexType, List<ColumnInfo>>> getUniqueIndices()
    {
        // Rewrite the "idx_material_ak" unique index over "Folder", "SampleSet", "Name" to just "Name"
        // Issue 25397: Don't include the "idx_material_ak" index if the "Name" column hasn't been added to the table.  Some FKs to ExpMaterialTable don't include the "Name" column (e.g. NabBaseTable.Specimen)
        Map<String, Pair<IndexType, List<ColumnInfo>>> ret = new HashMap<>(super.getUniqueIndices());
        if (getColumn("Name") != null)
            ret.put("idx_material_ak", Pair.of(IndexType.Unique, Arrays.asList(getColumn("Name"))));
        else
            ret.remove("idx_material_ak");
        return Collections.unmodifiableMap(ret);
    }


    //
    // UpdatableTableInfo
    //


    @Override
    public @Nullable Integer getOwnerObjectId()
    {
        return OntologyManager.ensureObject(_ss.getContainer(), _ss.getLSID(), (Integer) null);
    }

    @Nullable
    @Override
    public CaseInsensitiveHashMap<String> remapSchemaColumns()
    {
        if (null != getRealTable().getColumn("container") && null != getColumn("folder"))
        {
            CaseInsensitiveHashMap<String> m = new CaseInsensitiveHashMap<>();
            m.put("container", "folder");
            return m;
        }
        return null;
    }

    @Override
    public DataIteratorBuilder persistRows(DataIteratorBuilder data, DataIteratorContext context)
    {
        TableInfo propertiesTable = _ss.getTinfo();

        int sampleSetObjectId = requireNonNull(getOwnerObjectId());

        // TODO: subclass PersistDataIteratorBuilder to index Materials! not DataClass!
        try
        {
            DataIteratorBuilder persist = LoggingDataIterator.wrap(new ExpDataIterators.PersistDataIteratorBuilder(data, this, propertiesTable, getUserSchema().getContainer(), getUserSchema().getUser(), _ss.getImportAliasMap(), sampleSetObjectId)
                    .setFileLinkDirectory("sampleset")
                    .setIndexFunction(lsids -> () ->
                    {
                        for (String lsid : lsids)
                        {
                            ExpMaterialImpl expMaterial = ExperimentServiceImpl.get().getExpMaterial(lsid);
                            if (null != expMaterial)
                                expMaterial.index(null);
                        }
                    }));

            return LoggingDataIterator.wrap(new AliasDataIteratorBuilder(persist, getUserSchema().getContainer(), getUserSchema().getUser(), ExperimentService.get().getTinfoMaterialAliasMap()));
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }
}
