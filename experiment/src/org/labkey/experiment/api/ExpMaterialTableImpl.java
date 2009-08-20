/*
 * Copyright (c) 2006-2009 LabKey Corporation
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

import org.labkey.api.exp.api.*;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.query.*;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.query.*;
import org.labkey.api.view.ActionURL;
import org.labkey.experiment.controllers.exp.ExperimentController;

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.sql.Types;

public class ExpMaterialTableImpl extends ExpTableImpl<ExpMaterialTable.Column> implements ExpMaterialTable
{
    ExpSampleSet _ss;

    public ExpMaterialTableImpl(String name, UserSchema schema)
    {
        super(name, ExperimentServiceImpl.get().getTinfoMaterial(), schema);
        setName(ExpSchema.TableType.Materials.name());
    }

    @Override
    protected ColumnInfo resolveColumn(String name)
    {
        ColumnInfo result = super.resolveColumn(name);
        if (result == null && "CpasType".equalsIgnoreCase(name))
        {
            return createColumn("SampleSet", Column.SampleSet);
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
                columnInfo.setFk(new LookupForeignKey((ActionURL)null, (String)null, "LSID", "Name")
                {
                    public TableInfo getLookupTableInfo()
                    {
                        ExpSampleSetTable sampleSetTable = ExperimentService.get().createSampleSetTable(ExpSchema.TableType.SampleSets.toString(), _schema);
                        sampleSetTable.populate();
                        return sampleSetTable;
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
                return columnInfo;
            }
            case SourceProtocolApplication:
            {
                ColumnInfo columnInfo = wrapColumn(alias, _rootTable.getColumn("SourceApplicationId"));
                columnInfo.setFk(getExpSchema().getProtocolApplicationForeignKey());
                return columnInfo;
            }
            case Run:
                return wrapColumn(alias, _rootTable.getColumn("RunId"));
            case RowId:
            {
                ColumnInfo ret = wrapColumn(alias, _rootTable.getColumn("RowId"));
                ret.setFk(new RowIdForeignKey(ret));
                ret.setHidden(true);
                return ret;
            }
            case Property:
                ColumnInfo ret = createPropertyColumn(alias);
                if (_ss != null)
                {
                    Domain domain = _ss.getType();
                    if (domain != null)
                    {
                        ret.setFk(new DomainForeignKey(domain, _schema));
                    }
                }
                return ret;
            case Flag:
                return createFlagColumn(alias);
            case Created:
                return wrapColumn(alias, _rootTable.getColumn("Created"));
            default:
                throw new IllegalArgumentException("Unknown column " + column);
        }
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
        if (ss != null && !ss.getContainer().equals(getContainer()))
        {
            setContainerFilter(new ContainerFilter.CurrentPlusExtras(_schema.getUser(), ss.getContainer()));
        }

        addColumn(ExpMaterialTable.Column.RowId).setHidden(true);
        ColumnInfo appCol = addColumn(Column.SourceProtocolApplication);
        appCol.setHidden(true);
        ColumnInfo sourceProtocolCol = addColumn(Column.SourceProtocolLSID);
        sourceProtocolCol.setHidden(true);
        sourceProtocolCol.setLabel("Source Protocol");
        addColumn(ExpMaterialTable.Column.Name);
        ColumnInfo typeColumnInfo = addColumn(Column.SampleSet);
        typeColumnInfo.setFk(new LookupForeignKey("lsid")
        {
            public TableInfo getLookupTableInfo()
            {
                return new ExpSchema(_schema.getUser(), _schema.getContainer()).createSampleSetTable();
            }
        });
        addContainerColumn(ExpMaterialTable.Column.Folder, null);
        addColumn(ExpMaterialTable.Column.Run).setFk(new ExpSchema(_schema.getUser(), getContainer()).getRunIdForeignKey());
        ColumnInfo colLSID = addColumn(ExpMaterialTable.Column.LSID);
        colLSID.setHidden(true);
        addColumn(ExpMaterialTable.Column.Created);

        List<FieldKey> defaultCols = new ArrayList<FieldKey>();
        defaultCols.add(FieldKey.fromParts(ExpMaterialTable.Column.Name));
        defaultCols.add(FieldKey.fromParts(ExpMaterialTable.Column.Run));
        defaultCols.add(FieldKey.fromParts(ExpMaterialTable.Column.SampleSet));

        if (ss != null)
        {
            addColumn(ExpMaterialTable.Column.Flag);
            defaultCols.add(FieldKey.fromParts(ExpMaterialTable.Column.Flag));
            setSampleSet(ss, filter);
            addSampleSetColumns(ss, defaultCols);
        }
        else
        {
            ExpSampleSet activeSource = ExperimentService.get().lookupActiveSampleSet(getContainer());
            if (activeSource != null)
            {
                setSampleSet(activeSource, false);
                addSampleSetColumns(_ss, defaultCols);
            }
        }
        if (_ss != null)
        {
            setName(_ss.getName());
        }

        ActionURL url = new ActionURL(ExperimentController.ShowMaterialAction.class, getContainer());
        setDetailsURL(new DetailsURL(url, Collections.singletonMap("rowId", "RowId")));
        setTitleColumn(Column.Name.toString());

        setDefaultVisibleColumns(defaultCols);

    }

    private void addSampleSetColumns(ExpSampleSet ss, List<FieldKey> visibleColumns)
    {
        addColumn(Column.Property);
        visibleColumns.remove(FieldKey.fromParts("Run"));
        FieldKey keyProp = new FieldKey(null, "Property");
        for (DomainProperty pd : ss.getPropertiesForType())
        {
            visibleColumns.add(new FieldKey(keyProp, pd.getName()));
        }
        setDefaultVisibleColumns(visibleColumns);
    }

    public String getPublicSchemaName()
    {
        if (_ss != null)
        {
            return SamplesSchema.SCHEMA_NAME;
        }
        return super.getPublicSchemaName();
    }
}
