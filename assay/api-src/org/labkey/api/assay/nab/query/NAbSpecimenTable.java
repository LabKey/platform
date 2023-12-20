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
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.assay.AssayProtocolSchema;
import org.labkey.api.query.QueryService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.labkey.api.assay.plate.PlateBasedRunCreator.SAMPLE_TYPE_NAME_PREFIX;


public class NAbSpecimenTable extends FilteredTable<AssayProtocolSchema>
{
    private static final FieldKey CONTAINER_FIELD_KEY = FieldKey.fromParts("Container");
    public static final String PERCENT_NEUT_MAX_PROP = "PercentNeutralizationMax";
    public static final String PERCENT_NEUT_INIT_DILUTION_PROP = "PercentNeutralizationInitialDilution";

    private final ExpProtocol _protocol;

    public NAbSpecimenTable(AssayProtocolSchema schema, ContainerFilter cf, final ExpProtocol protocol)
    {
        super(DilutionManager.getTableInfoNAbSpecimen(), schema, cf);
        _protocol = protocol;

        wrapAllColumns(true);
        setTitle(DilutionManager.NAB_SPECIMEN_TABLE_NAME);
        setName("Data");

        // TODO - add columns for all of the different cutoff values
        ExprColumn selectedAUC = new ExprColumn(this, "AUC", getSelectedCurveFitAUC(false), JdbcType.DECIMAL);
        ExprColumn selectedPositiveAUC = new ExprColumn(this, "PositiveAUC", getSelectedCurveFitAUC(true), JdbcType.DECIMAL);
        addColumn(selectedAUC);
        addColumn(selectedPositiveAUC);

        ExprColumn percNeutMax = new ExprColumn(this, PERCENT_NEUT_MAX_PROP, getPercentNeutralizationMax(), JdbcType.DECIMAL);
        percNeutMax.setDescription("The max percent neutralization value for the dilution data for this sample.");
        percNeutMax.setHidden(true);
        addColumn(percNeutMax);
        ExprColumn percNeutInitDilution = new ExprColumn(this, PERCENT_NEUT_INIT_DILUTION_PROP, getPercentNeutralizationInitialDilution(), JdbcType.DECIMAL);
        percNeutInitDilution.setDescription("The percent neutralization value from the dilution data row for this sample where the dilution value equals the sample's InitialDilution property.");
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
        // Issue 48437: Use AVG() since we can't guarantee that there will be just a single DilutionData row for the min dilution
        SQLFragment sql = new SQLFragment("(SELECT AVG(PercentNeutralization) FROM ")
            .append(DilutionManager.getTableInfoDilutionData(), "dd");

        // Issue 49036: InitialDilution does not always equal MinDilution (i.e. method Dilution vs Concentration)
        // so we need to join to the sample type table to get the InitialDilution value
        SamplesSchema schema = new SamplesSchema(_userSchema);
        TableInfo samplesTable = schema.getTable(SAMPLE_TYPE_NAME_PREFIX + _protocol.getName(), getContainerFilter());
        if (samplesTable != null && samplesTable.getColumn("InitialDilution") != null)
        {
            List<ColumnInfo> columns = Arrays.asList(samplesTable.getColumn("LSID"), samplesTable.getColumn("InitialDilution"));
            SQLFragment samplesSql = QueryService.get().getSelectSQL(samplesTable, columns, null, null, Table.ALL_ROWS, 0, false);
            return sql.append(" LEFT JOIN (").append(samplesSql).append(" ) x ON x.LSID = ").append(ExprColumn.STR_TABLE_ALIAS + ".SpecimenLsid")
                    .append(" WHERE dd.RunDataId = ").append(ExprColumn.STR_TABLE_ALIAS + ".RowId")
                    .append(" AND dd.Dilution = x.InitialDilution)");
        }
        else
        {
            return sql.append(" WHERE dd.RunDataId = ").append(ExprColumn.STR_TABLE_ALIAS + ".RowId")
                    .append(" AND dd.Dilution = dd.MinDilution)");
        }
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
