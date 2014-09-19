/*
 * Copyright (c) 2013-2014 LabKey Corporation
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

package org.labkey.api.assay.nab.query;

import org.labkey.api.assay.dilution.DilutionAssayProvider;
import org.labkey.api.assay.dilution.DilutionManager;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.OORDisplayColumnFactory;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.study.assay.AssayProtocolSchema;

public class CutoffValueTable extends FilteredTable<AssayProtocolSchema>
{
    private static final FieldKey CONTAINER_FIELD_KEY = FieldKey.fromParts("Container");
    private static final FieldKey PROTOCOL_FIELD_KEY = FieldKey.fromParts("ProtocolId");

    public CutoffValueTable(AssayProtocolSchema schema)
    {
        super(DilutionManager.getTableInfoCutoffValue(), schema);

        addWrapColumn(getRealTable().getColumn("RowId")).setHidden(true);
        addWrapColumn(getRealTable().getColumn("NAbSpecimenID")).setFk(new LookupForeignKey()
        {
            @Override
            public TableInfo getLookupTableInfo()
            {
                return _userSchema.getTable(DilutionManager.NAB_SPECIMEN_TABLE_NAME);
            }
        });
        addWrapColumn(getRealTable().getColumn("Cutoff"));

        OORDisplayColumnFactory.addOORColumns(this, getRealTable().getColumn("Point"), getRealTable().getColumn("PointOORIndicator"));
        OORDisplayColumnFactory.addOORColumns(this, getRealTable().getColumn("IC_4pl"), getRealTable().getColumn("IC_4plOORIndicator"));
        OORDisplayColumnFactory.addOORColumns(this, getRealTable().getColumn("IC_5pl"), getRealTable().getColumn("IC_5plOORIndicator"));
        OORDisplayColumnFactory.addOORColumns(this, getRealTable().getColumn("IC_Poly"), getRealTable().getColumn("IC_PolyOORIndicator"));

        ColumnInfo selectedIC = new ExprColumn(this, "IC", getSelectedCurveFitIC(false), JdbcType.DECIMAL);
        ColumnInfo selectedICOOR = new ExprColumn(this, "ICOORIndicator", getSelectedCurveFitIC(true), JdbcType.VARCHAR);
        OORDisplayColumnFactory.addOORColumns(this, selectedIC, selectedICOOR, selectedIC.getLabel(), false);

        SQLFragment protocolSQL = new SQLFragment("NAbSpecimenID IN (SELECT RowId FROM ");
        protocolSQL.append(DilutionManager.getTableInfoNAbSpecimen(), "s");
        protocolSQL.append(" WHERE ProtocolId = ?)");
        protocolSQL.add(_userSchema.getProtocol().getRowId());
        addCondition(protocolSQL, FieldKey.fromParts(PROTOCOL_FIELD_KEY));
    }

    private SQLFragment getSelectedCurveFitIC(boolean oorIndicator)
    {
        String suffix = oorIndicator ? "OORIndicator" : "";
        SQLFragment defaultICSQL = new SQLFragment("CASE (SELECT op.StringValue FROM ");
        defaultICSQL.append(OntologyManager.getTinfoObject(), "o");
        defaultICSQL.append(", ");
        defaultICSQL.append(OntologyManager.getTinfoObjectProperty(), "op");
        defaultICSQL.append(", ");
        defaultICSQL.append(OntologyManager.getTinfoPropertyDescriptor(), "pd");
        defaultICSQL.append(", ");
        defaultICSQL.append(DilutionManager.getTableInfoNAbSpecimen(), "ns");
        defaultICSQL.append(", ");
        defaultICSQL.append(ExperimentService.get().getTinfoExperimentRun(), "er");
        defaultICSQL.append(" WHERE op.PropertyId = pd.PropertyId AND pd.PropertyURI LIKE '%#" + DilutionAssayProvider.CURVE_FIT_METHOD_PROPERTY_NAME + "' AND ns.RowId = ");
        defaultICSQL.append(ExprColumn.STR_TABLE_ALIAS);
        defaultICSQL.append(".NAbSpecimenID AND er.LSID = o.ObjectURI AND o.ObjectId = op.ObjectId AND er.RowId = ns.RunId)");
        defaultICSQL.append("\nWHEN 'Polynomial' THEN ");
        defaultICSQL.append(ExprColumn.STR_TABLE_ALIAS + ".");
        defaultICSQL.append("IC_Poly");
        defaultICSQL.append(suffix);
        defaultICSQL.append("\nWHEN 'Five Parameter' THEN ");
        defaultICSQL.append(ExprColumn.STR_TABLE_ALIAS + ".");
        defaultICSQL.append("IC_5pl");
        defaultICSQL.append(suffix);
        defaultICSQL.append("\nWHEN 'Four Parameter' THEN ");
        defaultICSQL.append(ExprColumn.STR_TABLE_ALIAS + ".");
        defaultICSQL.append("IC_4pl");
        defaultICSQL.append(suffix);
        defaultICSQL.append("\nEND\n");
        return defaultICSQL;
    }

    private boolean _dontNeedFilterContainer = false;

    @Override
    protected void applyContainerFilter(ContainerFilter filter)
    {
        if (!_dontNeedFilterContainer)
        {
        // We need to do our filtering based on the run since we don't have a container column of our own
            clearConditions(CONTAINER_FIELD_KEY);
            SQLFragment sql = new SQLFragment("NabSpecimenID IN (SELECT ns.RowId FROM ");
            sql.append(ExperimentService.get().getTinfoExperimentRun(), "r");
            sql.append(", ");
            sql.append(DilutionManager.getTableInfoNAbSpecimen(), "ns");
            sql.append(" WHERE r.RowId = ns.RunId AND ");
            sql.append(filter.getSQLFragment(getSchema(), new SQLFragment("Container"), _userSchema.getContainer()));
            sql.append(")");
            addCondition(sql, CONTAINER_FIELD_KEY);
        }
    }

    public void removeContainerAndProtocolFilters()
    {
        // When CutoffValueTable is used with NabSpecimenTable already we don't want the extra filter;
        // Need to clear explicitly because FilteredTable constructor calls applyContainerFilter
        clearConditions(CONTAINER_FIELD_KEY);
        clearConditions(PROTOCOL_FIELD_KEY);
        _dontNeedFilterContainer = true;
    }

    @Override
    public boolean supportsContainerFilter()
    {
        if (_dontNeedFilterContainer)
            return false;
        return super.supportsContainerFilter();
    }

}
