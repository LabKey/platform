/*
 * Copyright (c) 2006-2017 LabKey Corporation
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
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MultiValuedForeignKey;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableInfo;
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
import org.labkey.api.query.PropertyForeignKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.RowIdForeignKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.StringExpression;
import org.labkey.api.view.ActionURL;
import org.labkey.experiment.controllers.exp.ExperimentController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ExpMaterialTableImpl extends ExpProtocolOutputTableImpl<ExpMaterialTable.Column> implements ExpMaterialTable
{
    ExpSampleSetImpl _ss;

    public ExpMaterialTableImpl(String name, UserSchema schema)
    {
        super(name, ExperimentServiceImpl.get().getTinfoMaterial(), schema, new ExpMaterialImpl(new Material()));
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

    public ColumnInfo createColumn(String alias, Column column)
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
                ColumnInfo columnInfo = wrapColumn(alias, _rootTable.getColumn("CpasType"));
                columnInfo.setFk(new LookupForeignKey(null, (String)null, "LSID", "Name")
                {
                    public TableInfo getLookupTableInfo()
                    {
                        ExpSampleSetTable sampleSetTable = ExperimentService.get().createSampleSetTable(ExpSchema.TableType.SampleSets.toString(), _userSchema);
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
                columnInfo.setFk(getExpSchema().getProtocolForeignKey("LSID"));
                columnInfo.setLabel("Source Protocol");
                columnInfo.setDescription("Contains a reference to the protocol for the protocol application that created this sample");
                columnInfo.setUserEditable(false);
                columnInfo.setReadOnly(true);
                columnInfo.setHidden(true);
                return columnInfo;
            }

            case SourceProtocolApplication:
            {
                ColumnInfo columnInfo = wrapColumn(alias, _rootTable.getColumn("SourceApplicationId"));
                columnInfo.setFk(getExpSchema().getProtocolApplicationForeignKey());
                columnInfo.setUserEditable(false);
                columnInfo.setReadOnly(true);
                columnInfo.setHidden(true);
                return columnInfo;
            }

            case SourceApplicationInput:
            {
                ColumnInfo col = createEdgeColumn(alias, Column.SourceProtocolApplication, ExpSchema.TableType.MaterialInputs);
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

                ColumnInfo col = new ExprColumn(this, alias, sql, JdbcType.INTEGER);
                col.setFk(getExpSchema().getProtocolApplicationForeignKey());
                col.setDescription("Contains a reference to the ExperimentRunOutput protocol application of the run that created this sample");
                col.setUserEditable(false);
                col.setReadOnly(true);
                col.setHidden(true);
                return col;
            }

            case RunApplicationOutput:
            {
                ColumnInfo col = createEdgeColumn(alias, Column.RunApplication, ExpSchema.TableType.MaterialInputs);
                col.setDescription("Contains a reference to the MaterialInput row between this ExpMaterial and it's RunOutputApplication");
                return col;
            }

            case Run:
            {
                ColumnInfo ret = wrapColumn(alias, _rootTable.getColumn("RunId"));
                ret.setReadOnly(true);
                return ret;
            }
            case RowId:
            {
                ColumnInfo ret = wrapColumn(alias, _rootTable.getColumn("RowId"));
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
                ColumnInfo aliasCol = wrapColumn("Alias", getRealTable().getColumn("LSID"));
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

            default:
                throw new IllegalArgumentException("Unknown column " + column);
        }
    }

    public ColumnInfo createPropertyColumn(String alias)
    {
        ColumnInfo ret = super.createPropertyColumn(alias);
        ExpSampleSet ss = _ss != null ? _ss :
            ExperimentService.get().lookupActiveSampleSet(getContainer());
        if (ss != null)
        {
            Domain domain = ss.getType();
            if (domain != null)
            {
                ret.setFk(new PropertyForeignKey(domain, _userSchema));
            }
        }
        ret.setIsUnselectable(true);
        ret.setDescription("A holder for any custom fields associated with this sample");
        ret.setHidden(true);
        return ret;
    }

    public void setSampleSet(ExpSampleSet ss, boolean filter)
    {
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

            ActionURL url = PageFlowUtil.urlProvider(ExperimentUrls.class).getShowUploadMaterialsURL(getContainer());
            url.addParameter("name", _ss.getName());
            url.addParameter("importMoreSamples", true);
            setImportURL(new DetailsURL(url));
        }
    }

    public void setMaterials(Set<ExpMaterial> materials)
    {
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

    public void populate()
    {
        populate(null, false);
    }

    public void populate(@Nullable ExpSampleSet ss, boolean filter)
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

        ColumnInfo rowIdCol = addColumn(ExpMaterialTable.Column.RowId);
        
        addColumn(Column.SourceProtocolApplication);

        addColumn(Column.SourceApplicationInput);

        addColumn(Column.RunApplication);

        addColumn(Column.RunApplicationOutput);

        addColumn(Column.SourceProtocolLSID);

        ColumnInfo nameCol = addColumn(ExpMaterialTable.Column.Name);
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

        addColumn(Column.Description);

        ColumnInfo typeColumnInfo = addColumn(Column.SampleSet);
        typeColumnInfo.setFk(new LookupForeignKey("lsid")
        {
            public TableInfo getLookupTableInfo()
            {
                ExpSchema expSchema = new ExpSchema(_userSchema.getUser(), _userSchema.getContainer());
                if (ss != null)
                {
                    // Be sure that we can resolve the sample set if it's defined in a separate container
                    expSchema.setContainerFilter(new ContainerFilter.CurrentPlusExtras(_userSchema.getUser(), ss.getContainer()));
                }
                return expSchema.getTable(ExpSchema.TableType.SampleSets);
            }

            @Override
            public StringExpression getURL(ColumnInfo parent)
            {
                return super.getURL(parent, true);
            }
        });
        typeColumnInfo.setReadOnly(true);
        typeColumnInfo.setShownInInsertView(false);

        addContainerColumn(ExpMaterialTable.Column.Folder, null);

        ColumnInfo runCol = addColumn(ExpMaterialTable.Column.Run);
        runCol.setFk(new ExpSchema(_userSchema.getUser(), getContainer()).getRunIdForeignKey());
        runCol.setShownInInsertView(false);
        runCol.setShownInUpdateView(false);

        ColumnInfo colLSID = addColumn(ExpMaterialTable.Column.LSID);
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
        if (ss != null)
        {
            defaultCols.add(FieldKey.fromParts(ExpMaterialTable.Column.Flag));
            setSampleSet(ss, filter);
            addSampleSetColumns(ss, defaultCols);
            setName(_ss.getName());

            ActionURL gridUrl = new ActionURL(ExperimentController.ShowMaterialSourceAction.class, getContainer());
            gridUrl.addParameter("rowId", ss.getRowId());
            setGridURL(new DetailsURL(gridUrl));
        }
        else
        {
            ExpSampleSet activeSource = ExperimentService.get().lookupActiveSampleSet(getContainer());
            if (activeSource != null)
            {
                addSampleSetColumns(activeSource, defaultCols);
            }
        }

        ColumnInfo colInputs = addColumn(Column.Inputs);
        addMethod("Inputs", new LineageMethod(getContainer(), colInputs, true));

        ColumnInfo colOutputs = addColumn(Column.Outputs);
        addMethod("Outputs", new LineageMethod(getContainer(), colOutputs, false));

        ActionURL detailsUrl = new ActionURL(ExperimentController.ShowMaterialAction.class, getContainer());
        DetailsURL url = new DetailsURL(detailsUrl, Collections.singletonMap("rowId", "RowId"));
        nameCol.setURL(url);
        rowIdCol.setURL(url);
        setDetailsURL(url);

        ActionURL deleteUrl = ExperimentController.ExperimentUrlsImpl.get().getDeleteMaterialsURL(getContainer(), null);
        setDeleteURL(new DetailsURL(deleteUrl));

        setTitleColumn(Column.Name.toString());

        setDefaultVisibleColumns(defaultCols);
    }

    public Domain getDomain()
    {
        return _ss == null ? null : _ss.getType();
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
        ColumnInfo lsidColumn = getColumn(Column.LSID);
        visibleColumns.remove(FieldKey.fromParts("Run"));
        for (DomainProperty dp : ss.getType().getProperties())
        {
            PropertyDescriptor pd = dp.getPropertyDescriptor();
            ColumnInfo propColumn = new PropertyColumn(pd, lsidColumn, _userSchema.getContainer(), _userSchema.getUser(), true);
            if (isIdCol(ss, pd))
            {
                propColumn.setNullable(false);
                propColumn.setDisplayColumnFactory(new IdColumnRendererFactory());
            }
            if (getColumn(propColumn.getName()) == null)
            {
                addColumn(propColumn);
                visibleColumns.add(FieldKey.fromParts(pd.getName()));
            }
        }
        setDefaultVisibleColumns(visibleColumns);
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
        return new SampleSetUpdateService(this, _ss);
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
}
