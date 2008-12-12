/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.api.study.assay;

import org.labkey.api.data.*;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRunTable;
import org.labkey.api.query.*;
import org.labkey.api.security.ACL;
import org.labkey.api.study.StudyService;
import org.labkey.api.view.ActionURL;

import java.io.IOException;
import java.io.Writer;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * User: brittp
 * Date: Jul 6, 2007
 * Time: 5:35:15 PM
 */
public class RunDataTable extends FilteredTable
{
    public RunDataTable(final QuerySchema schema, String alias, final ExpProtocol protocol)
    {
        super(OntologyManager.getTinfoObject(), schema.getContainer());
        setAlias(alias);
        List<FieldKey> visibleColumns = new ArrayList<FieldKey>();
        ColumnInfo objectIdColumn = addWrapColumn(_rootTable.getColumn("ObjectId"));
        objectIdColumn.setKeyField(true);
        ColumnInfo column = wrapColumn("Properties", _rootTable.getColumn("ObjectId"));
        column.setKeyField(false);
        column.setIsUnselectable(true);
        final AssayProvider provider = AssayService.get().getProvider(protocol);
        PropertyDescriptor[] pds = provider.getRunDataColumns(protocol);
        OORAwarePropertyForeignKey fk = new OORAwarePropertyForeignKey(pds, this, schema);

        Set<String> hiddenCols = new HashSet<String>();
        for (PropertyDescriptor pd : fk.getDefaultHiddenProperties())
            hiddenCols.add(pd.getName());

        FieldKey dataKeyProp = new FieldKey(null, column.getName());
        for (PropertyDescriptor lookupCol : pds)
        {
            if (!hiddenCols.contains(lookupCol.getName()))
                visibleColumns.add(new FieldKey(dataKeyProp, lookupCol.getName()));
        }
        column.setFk(fk);
        addColumn(column);
        
        SQLFragment filterClause = new SQLFragment("OwnerObjectId IN (\n" +
                "SELECT ObjectId FROM exp.Object o, exp.Data d, exp.ExperimentRun r WHERE o.ObjectURI = d.lsid AND \n" +
                "d.RunId = r.RowId and r.ProtocolLSID = ?)");
        filterClause.add(protocol.getLSID());
        addCondition(filterClause, "OwnerObjectId");

        // TODO - we should have a more reliable (and speedier) way of identifying just the data rows here
        SQLFragment dataRowClause = new SQLFragment("ObjectURI LIKE '%.DataRow-%'");
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
                ExpRunTable expRunTable = AssayService.get().createRunTable(null, protocol, provider, schema.getUser(), schema.getContainer());
                expRunTable.setContainerFilter(getContainerFilter(), schema.getUser());
                return expRunTable;
            }
        });
        addColumn(runColumn);

        for (PropertyDescriptor prop : provider.getRunPropertyColumns(protocol))
            visibleColumns.add(FieldKey.fromParts("Run", "Run Properties", prop.getName()));
        for (PropertyDescriptor prop : provider.getUploadSetColumns(protocol))
            visibleColumns.add(FieldKey.fromParts("Run", "Run Properties", prop.getName()));

        // Add columns for each study that have data that we may have copied into
        int datasetIndex = 0;
        Set<String> usedColumnNames = new HashSet<String>();
        for (final Container studyContainer : provider.getAllAssociatedStudyContainers(protocol))
        {
            if (!studyContainer.hasPermission(schema.getUser(), ACL.PERM_READ))
                continue;

            // We need the dataset ID as a separate column in order to display the URL
            String datasetIdSQL = "(SELECT sd.datasetid FROM study.StudyData sd " +
                "WHERE sd.container = '" + studyContainer.getId() + "' AND " +
                "sd._key = CAST(" + ExprColumn.STR_TABLE_ALIAS + ".objectid AS " +
                getSqlDialect().sqlTypeNameFromSqlType(Types.VARCHAR) +
                "(200)))";

            final ExprColumn datasetColumn = new ExprColumn(this,
                "dataset" + datasetIndex++,
                new SQLFragment(datasetIdSQL),
                Types.INTEGER);
            datasetColumn.setIsHidden(true);
            addColumn(datasetColumn);

            String studyCopiedSql = "(SELECT CASE WHEN " + datasetIdSQL +
                " IS NOT NULL THEN 'copied' ELSE NULL END)";

            String studyName = StudyService.get().getStudyName(studyContainer);
            if (studyName == null)
                continue; // No study in that folder
            String studyColumnName = "Copied to " + studyName;

            // column names must be unique. Prevent collisions
            while (usedColumnNames.contains(studyColumnName))
                studyColumnName = studyColumnName + datasetIndex;
            usedColumnNames.add(studyColumnName);
            final String finalStudyColumnName = studyColumnName;

            ExprColumn studyCopiedColumn = new ExprColumn(this,
                studyColumnName,
                new SQLFragment(studyCopiedSql),
                Types.VARCHAR);

            studyCopiedColumn.setDisplayColumnFactory(new DisplayColumnFactory()
            {
                public DisplayColumn createRenderer(ColumnInfo colInfo)
                {
                    return new StudyDisplayColumn(finalStudyColumnName, studyContainer, datasetColumn);
                }
            });

            addColumn(studyCopiedColumn);

            visibleColumns.add(new FieldKey(null, studyCopiedColumn.getName()));
        }

        setDefaultVisibleColumns(visibleColumns);
    }

    private static class StudyDisplayColumn extends DataColumn
    {
        private final String title;
        private final Container container;

        public StudyDisplayColumn(String title, Container container, ColumnInfo datasetIdColumn)
        {
            super(datasetIdColumn);
            this.title = title;
            this.container = container;
        }

        @Override
        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            Integer datasetId = (Integer)getBoundColumn().getValue(ctx);
            if (datasetId != null)
            {
                ActionURL url = StudyService.get().getDatasetURL(container, datasetId.intValue());

                out.write("<a href=\"");
                out.write(url.getLocalURIString());
                out.write("\">copied</a>");
            }
        }

        @Override
        public void renderTitle(RenderContext ctx, Writer out) throws IOException
        {
            out.write(title);
        }
    }
}
 