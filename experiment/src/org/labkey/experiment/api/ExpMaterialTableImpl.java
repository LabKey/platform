/*
 * Copyright (c) 2006-2010 LabKey Corporation
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

import org.labkey.api.data.*;
import org.labkey.api.exp.PropertyColumn;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.api.*;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.query.*;
import org.labkey.api.query.*;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.view.ActionURL;
import org.labkey.api.util.StringExpression;
import org.labkey.experiment.controllers.exp.ExperimentController;

import java.io.IOException;
import java.io.Writer;
import java.util.*;
import java.sql.Types;

public class ExpMaterialTableImpl extends ExpTableImpl<ExpMaterialTable.Column> implements ExpMaterialTable
{
    ExpSampleSet _ss;

    public ExpMaterialTableImpl(String name, UserSchema schema)
    {
        super(name, ExperimentServiceImpl.get().getTinfoMaterial(), schema, new ExpMaterialImpl(new Material()));
        setName(ExpSchema.TableType.Materials.name());
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
            {
                ColumnInfo columnInfo = wrapColumn(alias, _rootTable.getColumn("Name"));
                return columnInfo;
            }
            case SampleSet:
            {
                ColumnInfo columnInfo = wrapColumn(alias, _rootTable.getColumn("CpasType"));
                columnInfo.setFk(new LookupForeignKey((ActionURL)null, (String)null, "LSID", "Name")
                {
                    public TableInfo getLookupTableInfo()
                    {
                        ExpSampleSetTable sampleSetTable = ExperimentService.get().createSampleSetTable(ExpSchema.TableType.SampleSets.toString(), _schema);
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
                        " WHERE pa.RowId = " + ExprColumn.STR_TABLE_ALIAS + ".SourceApplicationId)"), Types.VARCHAR);//, getColumn("SourceProtocolApplication"));
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
                ret.setFk(new PropertyForeignKey(domain, _schema));
            }
        }
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
        _ss = ss;
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

    public void populate(ExpSampleSet ss, boolean filter)
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
            if (!ss.getContainer().equals(getContainer()))
            {
                setContainerFilter(new ContainerFilter.CurrentPlusExtras(_schema.getUser(), ss.getContainer()));
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

        ColumnInfo typeColumnInfo = addColumn(Column.SampleSet);
        typeColumnInfo.setFk(new LookupForeignKey("lsid")
        {
            public TableInfo getLookupTableInfo()
            {
                return new ExpSchema(_schema.getUser(), _schema.getContainer()).getTable(ExpSchema.TableType.SampleSets);
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
        runCol.setFk(new ExpSchema(_schema.getUser(), getContainer()).getRunIdForeignKey());
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

        List<FieldKey> defaultCols = new ArrayList<FieldKey>();
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
        setDetailsURL(new DetailsURL(detailsUrl, Collections.singletonMap("rowId", "RowId")));
        setTitleColumn(Column.Name.toString());

        setDefaultVisibleColumns(defaultCols);

    }

    private void addSampleSetColumns(ExpSampleSet ss, List<FieldKey> visibleColumns)
    {
        ColumnInfo lsidColumn = getColumn(Column.LSID);
        visibleColumns.remove(FieldKey.fromParts("Run"));
        for (DomainProperty dp : ss.getPropertiesForType())
        {
            PropertyDescriptor pd = dp.getPropertyDescriptor();
            ColumnInfo propColumn = new PropertyColumn(pd, lsidColumn, _schema.getContainer().getId(), _schema.getUser());
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

    public String getPublicSchemaName()
    {
        if (_ss != null)
        {
            return SamplesSchema.SCHEMA_NAME;
        }
        return super.getPublicSchemaName();
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        if (_ss != null)
            return new ExpMaterialTableUpdateService(this, _ss);
        return null;
    }

    public boolean hasPermission(User user, Class<? extends Permission> perm)
    {
        return _schema.getContainer().hasPermission(user, perm);
    }
}
