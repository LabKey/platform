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

package org.labkey.api.assay.nab.query;

import org.labkey.api.assay.AssayService;
import org.labkey.api.assay.dilution.DilutionAssayProvider;
import org.labkey.api.assay.dilution.DilutionDataHandler;
import org.labkey.api.assay.dilution.DilutionManager;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.assay.AssayProtocolSchema;

import java.util.ArrayList;
import java.util.List;


public class NAbSpecimenTable extends FilteredTable<AssayProtocolSchema>
{
    private static final FieldKey CONTAINER_FIELD_KEY = FieldKey.fromParts("Container");

    public static final String PERCENT_NEUT_MAX_PROP = "PercentNeutralizationMax";
    public static final String PERCENT_NEUT_INIT_DILUTION_PROP = "PercentNeutralizationInitialDilution";

    public NAbSpecimenTable(AssayProtocolSchema schema, ContainerFilter cf)
    {
        super(DilutionManager.getTableInfoNAbSpecimen(), schema, cf);

        wrapAllColumns(true);

        // TODO - add columns for all of the different cutoff values
        ExprColumn selectedAUC = new ExprColumn(this, "AUC", getSelectedCurveFitAUC(false), JdbcType.DECIMAL);
        ExprColumn selectedPositiveAUC = new ExprColumn(this, "PositiveAUC", getSelectedCurveFitAUC(true), JdbcType.DECIMAL);
        addColumn(selectedAUC);
        addColumn(selectedPositiveAUC);

        ExprColumn percNeutMax = new ExprColumn(this, PERCENT_NEUT_MAX_PROP, getPercentNeutralizationMax(), JdbcType.DECIMAL);
        percNeutMax.setHidden(true);
        addColumn(percNeutMax);
        ExprColumn percNeutInitDilution = new ExprColumn(this, PERCENT_NEUT_INIT_DILUTION_PROP, getPercentNeutralizationInitialDilution(), JdbcType.DECIMAL);
        percNeutInitDilution.setHidden(true);
        addColumn(percNeutInitDilution);

        addCondition(getRealTable().getColumn("ProtocolID"), _userSchema.getProtocol().getRowId());
    }

    @Override
    protected void applyContainerFilter(ContainerFilter filter)
    {
        // We need to do our filtering based on the run since we don't have a container column of our own
        clearConditions(CONTAINER_FIELD_KEY);
        SQLFragment sql = new SQLFragment("RunId IN (SELECT RowId FROM ");
        sql.append(ExperimentService.get().getTinfoExperimentRun(), "r");
        sql.append(" WHERE ");
        sql.append(filter.getSQLFragment(getSchema(), new SQLFragment("r.Container"), _userSchema.getContainer()));
        sql.append(")");
        addCondition(sql, CONTAINER_FIELD_KEY);
    }

    private SQLFragment getSelectedCurveFitAUC(boolean positive)
    {
        String prefix = positive ? "Positive" : "";
        SQLFragment sql = new SQLFragment("CASE (SELECT op.StringValue FROM ");
        sql.append(OntologyManager.getTinfoObject(), "o");
        sql.append(", ");
        sql.append(OntologyManager.getTinfoObjectProperty(), "op");
        sql.append(", ");
        sql.append(OntologyManager.getTinfoPropertyDescriptor(), "pd");
        sql.append(", ");
        sql.append(ExperimentService.get().getTinfoExperimentRun(), "er");
        sql.append(" WHERE op.PropertyId = pd.PropertyId AND pd.PropertyURI LIKE '%#" + DilutionAssayProvider.CURVE_FIT_METHOD_PROPERTY_NAME + "'");
        sql.append(" AND er.LSID = o.ObjectURI AND o.ObjectId = op.ObjectId AND er.RowId = " + ExprColumn.STR_TABLE_ALIAS + ".RunId)");
        sql.append("\nWHEN 'Polynomial' THEN ");
        sql.append(ExprColumn.STR_TABLE_ALIAS + ".");
        sql.append(prefix);
        sql.append("AUC_Poly");
        sql.append("\nWHEN 'Five Parameter' THEN ");
        sql.append(ExprColumn.STR_TABLE_ALIAS + ".");
        sql.append(prefix);
        sql.append("AUC_5pl");
        sql.append("\nWHEN 'Four Parameter' THEN ");
        sql.append(ExprColumn.STR_TABLE_ALIAS + ".");
        sql.append(prefix);
        sql.append("AUC_4pl");
        sql.append("\nEND\n");
        return sql;
    }

    private SQLFragment getPercentNeutralizationMax()
    {
        return new SQLFragment("(SELECT MAX(PercentNeutralization) FROM ")
            .append(DilutionManager.getTableInfoDilutionData(), "dd")
            .append(" WHERE dd.RunDataId = ").append(ExprColumn.STR_TABLE_ALIAS + ".RowId)");
    }

    private SQLFragment getPercentNeutralizationInitialDilution()
    {
        return new SQLFragment("(SELECT PercentNeutralization FROM ")
            .append(DilutionManager.getTableInfoDilutionData(), "dd")
            .append(" WHERE dd.RunDataId = ").append(ExprColumn.STR_TABLE_ALIAS + ".RowId")
            .append(" AND dd.DilutionOrder = 1)");
    }

    @Override
    protected ColumnInfo resolveColumn(String name)
    {
        ColumnInfo columnInfo;
        columnInfo = super.resolveColumn(name);
        return columnInfo;
    }

    public List<PropertyDescriptor> getAdditionalDataProperties(ExpProtocol protocol)
    {
        DilutionAssayProvider provider = (DilutionAssayProvider) AssayService.get().getProvider(protocol);
        DilutionDataHandler dataHandler = provider.getDataHandler();
        List<PropertyDescriptor> propertyDescriptors = new ArrayList<>();
        propertyDescriptors.add(dataHandler.getTypedPropertyDescriptor(getContainer(), protocol, PERCENT_NEUT_MAX_PROP, PropertyType.DECIMAL));
        propertyDescriptors.add(dataHandler.getTypedPropertyDescriptor(getContainer(), protocol, PERCENT_NEUT_INIT_DILUTION_PROP, PropertyType.DECIMAL));
        return propertyDescriptors;
    }
}
