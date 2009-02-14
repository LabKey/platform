/*
 * Copyright (c) 2008 LabKey Corporation
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

package org.labkey.xarassay;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.api.ExpMaterialTable;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.query.*;
import org.labkey.api.study.assay.AbstractAssayProvider;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;

import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Jan 21, 2008
 */
public class MsFractionRunDataTable extends FilteredTable
{
    public static final String RUN_ID_COLUMN_NAME = "RunId";

//    public abstract PropertyDescriptor[] getExistingDataProperties(ExpProtocol protocol) throws SQLException;
//    public abstract String getInputMaterialPropertyName();
//    public abstract String getDataRowLsidPrefix();
//
    public MsFractionRunDataTable(final QuerySchema schema, String alias, final ExpProtocol protocol)
    {
        super(OntologyManager.getTinfoObject(), schema.getContainer());

        final AssayProvider provider = AssayService.get().getProvider(protocol);
        setAlias(alias);
        List<FieldKey> visibleColumns = new ArrayList<FieldKey>();

        try
        {
            // add material lookup columns to the view first, so they appear at the left:
            String sampleDomainURI = AbstractAssayProvider.getDomainURIForPrefix(protocol, MsFractionAssayProvider.FRACTION_DOMAIN_PREFIX);
            final ExpSampleSet sampleSet = ExperimentService.get().getSampleSet(sampleDomainURI);
            if (sampleSet != null)
            {
                for (PropertyDescriptor pd : sampleSet.getPropertiesForType())
                {
                    visibleColumns.add(FieldKey.fromParts("Properties", getInputMaterialPropertyName(),
                            ExpMaterialTable.Column.Property.toString(), pd.getName()));
                }
            }
            PropertyDescriptor[] pds = getExistingDataProperties(protocol);

            // add object ID to this tableinfo and set it as a key field:
            ColumnInfo objectIdColumn = addWrapColumn(_rootTable.getColumn("ObjectId"));
            objectIdColumn.setKeyField(true);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }

        SQLFragment filterClause = new SQLFragment("OwnerObjectId IN (\n" +
                "SELECT ObjectId FROM exp.Object o, exp.Data d, exp.ExperimentRun r WHERE o.ObjectURI = d.lsid AND \n" +
                "d.RunId = r.RowId and r.ProtocolLSID = ?)");
        filterClause.add(protocol.getLSID());
        addCondition(filterClause, "OwnerObjectId");

        // TODO - we should have a more reliable (and speedier) way of identifying just the data rows here
        SQLFragment dataRowClause = new SQLFragment("ObjectURI LIKE '%" + getDataRowLsidPrefix() + "%'");
        addCondition(dataRowClause, "ObjectURI");

        String sqlRunLSID = "(SELECT RunObjects.objecturi FROM exp.Object AS DataRowParents, " +
                "    exp.Object AS RunObjects, exp.Data d, exp.ExperimentRun r WHERE \n" +
                "    DataRowParents.ObjectUri = d.lsid AND\n" +
                "    r.RowId = d.RunId AND\n" +
                "    RunObjects.ObjectURI = r.lsid AND\n" +
                "    DataRowParents.ObjectID IN (SELECT OwnerObjectId FROM exp.Object AS DataRowObjects\n" +
                "    WHERE DataRowObjects.ObjectId = " + ExprColumn.STR_TABLE_ALIAS + ".ObjectId))";

        ExprColumn runColumn = new ExprColumn(this, "Run", new SQLFragment(sqlRunLSID), Types.VARCHAR);
        runColumn.setFk(new LookupForeignKey("LSID")
        {
            public TableInfo getLookupTableInfo()
            {
                return AssayService.get().createRunTable(null, protocol, provider, schema.getUser(), schema.getContainer());
            }
        });
        addColumn(runColumn);

        String sqlRunRowId = "(SELECT r.RowId FROM exp.Object AS DataRowParents, " +
                "    exp.Object AS RunObjects, exp.Data d, exp.ExperimentRun r WHERE \n" +
                "    DataRowParents.ObjectUri = d.lsid AND\n" +
                "    r.RowId = d.RunId AND\n" +
                "    RunObjects.ObjectURI = r.lsid AND\n" +
                "    DataRowParents.ObjectID IN (SELECT OwnerObjectId FROM exp.Object AS DataRowObjects\n" +
                "    WHERE DataRowObjects.ObjectId = " + ExprColumn.STR_TABLE_ALIAS + ".ObjectId))";

        ExprColumn runIdColumn = new ExprColumn(this, RUN_ID_COLUMN_NAME, new SQLFragment(sqlRunRowId), Types.INTEGER);
        ColumnInfo addedRunIdColumn = addColumn(runIdColumn);
        addedRunIdColumn.setIsHidden(true);
        addedRunIdColumn.setKeyField(true);

        Set<String> hiddenProperties = new HashSet<String>();
        hiddenProperties.add(AbstractAssayProvider.PARTICIPANTID_PROPERTY_NAME);
        for (PropertyDescriptor prop : provider.getRunPropertyColumns(protocol))
        {
            if (!hiddenProperties.contains(prop.getName()))
                visibleColumns.add(FieldKey.fromParts("Run", AssayService.RUN_PROPERTIES_COLUMN_NAME, prop.getName()));
        }
        for (PropertyDescriptor prop : provider.getUploadSetColumns(protocol))
        {
            if (!hiddenProperties.contains(prop.getName()))
                visibleColumns.add(FieldKey.fromParts("Run", AssayService.RUN_PROPERTIES_COLUMN_NAME, prop.getName()));
        }
        setDefaultVisibleColumns(visibleColumns);
    }

    public PropertyDescriptor[] getExistingDataProperties(ExpProtocol protocol) throws SQLException
    {
        return MsAssaySchema.getExistingDataProperties(protocol);
    }

    public String getInputMaterialPropertyName()
    {
        return MsFractionDataHandler.FRACTION_INPUT_MATERIAL_DATA_PROPERTY;
    }

    public String getDataRowLsidPrefix()
    {
        return MsFractionDataHandler.FRACTION_DATA_ROW_LSID_PREFIX;
    }
}

