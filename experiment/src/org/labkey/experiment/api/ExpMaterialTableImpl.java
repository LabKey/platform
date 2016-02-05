/*
 * Copyright (c) 2006-2016 LabKey Corporation
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
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.PropertyColumn;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.api.ExpMaterial;
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

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ExpMaterialTableImpl extends ExpTableImpl<ExpMaterialTable.Column> implements ExpMaterialTable
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
                ExprColumn columnInfo = new ExprColumn(this, ExpDataTable.Column.Protocol.toString(), new SQLFragment(
                        "(SELECT ProtocolLSID FROM " + ExperimentServiceImpl.get().getTinfoProtocolApplication() + " pa " +
                        " WHERE pa.RowId = " + ExprColumn.STR_TABLE_ALIAS + ".SourceApplicationId)"), JdbcType.VARCHAR);//, getColumn("SourceProtocolApplication"));
                columnInfo.setSqlTypeName("lsidtype");
                columnInfo.setFk(getExpSchema().getProtocolForeignKey("LSID"));
                columnInfo.setDescription("Contains a reference to the protocol for the protocol application that created this sample");
                columnInfo.setUserEditable(false);
                columnInfo.setReadOnly(true);
                return columnInfo;
            }
            case SourceProtocolApplication:
            {
                ColumnInfo columnInfo = wrapColumn(alias, _rootTable.getColumn("SourceApplicationId"));
                columnInfo.setFk(getExpSchema().getProtocolApplicationForeignKey());
                columnInfo.setUserEditable(false);
                columnInfo.setReadOnly(true);
                return columnInfo;
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
                ColumnInfo aliasCol = wrapColumn("Alias", _rootTable.getColumn("LSID"));
                aliasCol.setDescription("Contains the list of aliases for this sample");
                aliasCol.setFk(new MultiValuedForeignKey(new LookupForeignKey("LSID")
                {
                    @Override
                    public TableInfo getLookupTableInfo()
                    {
                        return ExperimentService.get().getTinfoMaterialAliasMap();
                    }
                }, "Alias"));
                return aliasCol;
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
        if (filter)
            addCondition(getRealTable().getColumn("CpasType"), ss.getLSID());
        if (ss != null && !(ss instanceof ExpSampleSetImpl))
        {
            throw new IllegalArgumentException("Expected sample set to be an instance of " + ExpSampleSetImpl.class.getName() + " but was a " + ss.getClass().getName());
        }
        _ss = (ExpSampleSetImpl)ss;
        if (_ss != null)
        {
            setPublicSchemaName(SamplesSchema.SCHEMA_NAME);
            setName(ss.getName());

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

        addColumn(ExpMaterialTable.Column.RowId);
        
        ColumnInfo appCol = addColumn(Column.SourceProtocolApplication);
        appCol.setHidden(true);
        ColumnInfo sourceProtocolCol = addColumn(Column.SourceProtocolLSID);
        sourceProtocolCol.setHidden(true);
        sourceProtocolCol.setLabel("Source Protocol");

        ColumnInfo nameCol = addColumn(ExpMaterialTable.Column.Name);
        if (ss != null && ss.hasNameAsIdCol())
        {
            nameCol.setNullable(false);
            nameCol.setDisplayColumnFactory(new IdColumnRendererFactory());
        }
        else
        {
            nameCol.setReadOnly(true);
            nameCol.setShownInInsertView(false);
        }
        addColumn(Column.Alias).setHidden(true);

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

        ActionURL detailsUrl = new ActionURL(ExperimentController.ShowMaterialAction.class, getContainer());
        DetailsURL url = new DetailsURL(detailsUrl, Collections.singletonMap("rowId", "RowId"));
        nameCol.setURL(url);
        setDetailsURL(url);
        setTitleColumn(Column.Name.toString());

        setDefaultVisibleColumns(defaultCols);

    }

    public Domain getDomain()
    {
        return _ss == null ? null : _ss.getType();
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
        public void renderInputCell(RenderContext ctx, Writer out, int span) throws IOException
        {
            if (ctx.getMode() == DataRegion.MODE_INSERT)
            {
                super.renderInputCell(ctx, out, span);
            }
            else
            {
                super.renderHiddenFormInput(ctx, out);
                super.renderDetailsData(ctx, out, span);
            }
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
    public Map<String, Pair<IndexType, List<ColumnInfo>>> getIndices()
    {
        // rewrite the "idx_material_ak" unique index over "Folder", "SampleSet", "Name" to just "Name"
        Map<String, Pair<IndexType, List<ColumnInfo>>> ret = new HashMap<>(super.getIndices());
        ret.put("idx_material_ak", Pair.of(IndexType.Unique, Arrays.asList(getColumn("Name"))));
        return Collections.unmodifiableMap(ret);
    }
}
