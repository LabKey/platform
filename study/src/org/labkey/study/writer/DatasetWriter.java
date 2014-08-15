/*
 * Copyright (c) 2009-2014 LabKey Corporation
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
package org.labkey.study.writer;

import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.admin.ImportException;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.Results;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TSVGridWriter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.reports.model.ReportPropsManager;
import org.labkey.api.reports.model.ViewCategory;
import org.labkey.api.reports.model.ViewCategoryManager;
import org.labkey.api.study.Cohort;
import org.labkey.api.study.DataSet;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.writer.VirtualFile;
import org.labkey.data.xml.reportProps.PropertyList;
import org.labkey.study.StudySchema;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.query.DataSetTableImpl;
import org.labkey.study.query.StudyQuerySchema;
import org.labkey.study.xml.DatasetsDocument;
import org.labkey.study.xml.StudyDocument;
import org.labkey.study.xml.StudyDocument.Study.Datasets;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * User: adam
 * Date: Apr 16, 2009
 * Time: 3:10:37 PM
 */
public class DatasetWriter implements InternalStudyWriter
{
    private static final Logger LOG = Logger.getLogger(DatasetWriter.class);
    private static final String DEFAULT_DIRECTORY = "datasets";
    private static final String MANIFEST_FILENAME = "datasets_manifest.xml";

    public static final String SELECTION_TEXT = "CRF Datasets";

    public String getSelectionText()
    {
        return SELECTION_TEXT;
    }

    public void write(StudyImpl study, StudyExportContext ctx, VirtualFile root) throws SQLException, IOException, ServletException, ImportException
    {
        StudyDocument.Study studyXml = ctx.getXml();
        Datasets datasetsXml = studyXml.addNewDatasets();
        datasetsXml.setDir(DEFAULT_DIRECTORY);
        datasetsXml.setFile(MANIFEST_FILENAME);

        VirtualFile vf = root.getDir(DEFAULT_DIRECTORY);
        List<DataSetDefinition> datasets = ctx.getDatasets();

        DatasetsDocument manifestXml = DatasetsDocument.Factory.newInstance();
        DatasetsDocument.Datasets dsXml = manifestXml.addNewDatasets();
        DatasetsDocument.Datasets.Datasets2 datasets2Xml = dsXml.addNewDatasets();

        for (DataSetDefinition def : datasets)
        {
            DatasetsDocument.Datasets.Datasets2.Dataset datasetXml = datasets2Xml.addNewDataset();
            datasetXml.setName(def.getName());
            datasetXml.setId(def.getDatasetId());

            Cohort cohort = def.getCohort();

            if (null != cohort)
                datasetXml.setCohort(cohort.getLabel());

            // Default value is "true"
            if (!def.isShowByDefault())
                datasetXml.setShowByDefault(false);

            ViewCategory category = null;
            if (def.getCategoryId() != null)
            {
                category = ViewCategoryManager.getInstance().getCategory(def.getCategoryId());
            }

            if (null != category)
            {
                datasetXml.setCategory(ViewCategoryManager.getInstance().encode(category));
            }

            if (def.isDemographicData())
                datasetXml.setDemographicData(true);

            datasetXml.setType(def.getType());

            // serialize any dataset properties (reportPropsManager)
            PropertyList propList = datasetXml.addNewTags();
            ReportPropsManager.get().exportProperties(def.getEntityId(), ctx.getContainer(), propList);
        }

        if (ctx.useOldFormats())
        {
            // Write out the schema.tsv file and add reference & attributes to study.xml
            SchemaTsvWriter schemaTsvWriter = new SchemaTsvWriter();
            schemaTsvWriter.write(datasets, ctx, vf);
        }
        else
        {
            SchemaXmlWriter schemaXmlWriter = new SchemaXmlWriter();
            schemaXmlWriter.write(datasets, ctx, vf);
            dsXml.setMetaDataFile(SchemaXmlWriter.SCHEMA_FILENAME);
        }

        vf.saveXmlBean(MANIFEST_FILENAME, manifestXml);

        // Write out the .dataset file and add reference to study.xml
        Datasets.Definition definitionXml = datasetsXml.addNewDefinition();
        String datasetFilename = vf.makeLegalName(study.getShortName() + ".dataset");
        definitionXml.setFile(datasetFilename);

        try (PrintWriter writer = vf.getPrintWriter(datasetFilename))
        {
            writer.println("# default group can be used to avoid repeating definitions for each dataset\n" +
                "#\n" +
                "# action=[REPLACE,APPEND,DELETE] (default:REPLACE)\n" +
                "# deleteAfterImport=[TRUE|FALSE] (default:FALSE)\n" +
                "\n" +
                "default.action=REPLACE\n" +
                "default.deleteAfterImport=FALSE\n" +
                "\n" +
                "# map a source tsv column (right side) to a property name or full propertyURI (left)\n" +
                "# predefined properties: ParticipantId, SiteId, VisitId, Created\n" +
                "default.property.ParticipantId=ptid\n" +
                "default.property.Created=dfcreate\n" +
                "\n" +
                "# use to map from filename->datasetid\n" +
                "# NOTE: if there are NO explicit import definitions, we will try to import all files matching pattern\n" +
                "# NOTE: if there are ANY explicit mapping, we will only import listed datasets\n" +
                "\n" +
                "default.filePattern=dataset(\\\\d*).tsv\n" +
                "default.importAllMatches=TRUE");
        }

        StudyQuerySchema schema = StudyQuerySchema.createSchema(StudyManager.getInstance().getStudy(ctx.getContainer()), ctx.getUser(), true);

        // Write out all the dataset .tsv files
        for (DataSetDefinition def : datasets)
        {
            // no data to export for placeholder datasets
            if (def.getType().equals(DataSet.TYPE_PLACEHOLDER))
                continue;

            TableInfo ti = schema.getTable(def.getName());
            Collection<ColumnInfo> columns = getColumnsToExport(ti, def, false, ctx.isRemoveProtected());
            // need to make sure the SequenceNum column is included for visit based demographic datasets, issue #16146
            if (def.isDemographicData())
            {
                ColumnInfo seqNumCol = ti.getColumn("SequenceNum");
                if (seqNumCol != null && !columns.contains(seqNumCol))
                    columns.add(seqNumCol);
            }
            // Sort the data rows by PTID & sequence, #11261
            Sort sort = new Sort(StudyService.get().getSubjectColumnName(ctx.getContainer()) + ", SequenceNum");

            SimpleFilter filter = new SimpleFilter();
            if (def.isAssayData())
            {
                // Try to find the protocol and provider
                ExpProtocol protocol = def.getAssayProtocol();
                if (protocol != null)
                {
                    AssayProvider provider = AssayService.get().getProvider(protocol);
                    if (provider != null)
                    {
                        // Assuming they're still around, filter out rows where the source assay run has been deleted,
                        // thus orphaning the dataset row and pulling out all of its real data
                        filter.addCondition(provider.getTableMetadata(protocol).getRunFieldKeyFromResults().toString(), null, CompareType.NONBLANK);
                    }
                }
            }

            if (ctx.isShiftDates())
            {
                createDateShiftColumns(ti, columns, ctx.getContainer());
            }
            if (ctx.isAlternateIds())
            {
                createAlternateIdColumns(ti, columns, ctx.getContainer());
            }
            if (ctx.getVisitIds() != null && !ctx.getVisitIds().isEmpty())
            {
                filter.addInClause(FieldKey.fromParts("VisitRowId"), ctx.getVisitIds());
            }
            if (!ctx.getParticipants().isEmpty())
            {
                filter.addInClause(FieldKey.fromParts(StudyService.get().getSubjectColumnName(ctx.getContainer())), ctx.getParticipants());
            }

            if (ctx.isDataspaceProject())
                DefaultStudyDesignWriter.createExtraForeignKeyColumns(ti, columns);
            Results rs = QueryService.get().select(ti, columns, filter, sort, null, false);
            writeResultsToTSV(rs, vf, def.getFileName());
        }
    }

    private void writeResultsToTSV(Results rs, VirtualFile vf, String fileName) throws SQLException, IOException
    {
        TSVGridWriter tsvWriter = new TSVGridWriter(rs);
        tsvWriter.setApplyFormats(false);
        tsvWriter.setColumnHeaderType(TSVGridWriter.ColumnHeaderType.queryColumnName);
        PrintWriter out = vf.getPrintWriter(fileName);
        tsvWriter.write(out);     // NOTE: TSVGridWriter closes PrintWriter and ResultSet
    }

    public static void createDateShiftColumns(TableInfo ti, Collection<ColumnInfo> columns, Container c)
    {
        Map<ColumnInfo, ExprColumn> exprColumnMap = new HashMap<>();
        for (ColumnInfo column : columns)
        {
            if (column.isDateTimeType() && !column.isExcludeFromShifting())
            {
                SQLFragment sql = generateSqlForShiftDateCol(c, column);
                exprColumnMap.put(column, new ExprColumn(ti, column.getName(), sql, column.getJdbcType(), column));
            }
        }

        // replace the original date/timestamp column with the expression column
        for (Map.Entry<ColumnInfo, ExprColumn> entry : exprColumnMap.entrySet())
        {
            columns.remove(entry.getKey());
            columns.add(entry.getValue());
        }
    }

    public static void createAlternateIdColumns(TableInfo ti, Collection<ColumnInfo> columns, Container c)
    {
        String participantIdColumnName = StudyService.get().getSubjectColumnName(c);
        for (ColumnInfo column : columns)
        {
            if (column.getColumnName().equalsIgnoreCase(participantIdColumnName))
            {
                ColumnInfo newColumn = StudyService.get().createAlternateIdColumn(ti, column, c);
                columns.remove(column);
                columns.add(newColumn);
                break;
            }
        }
    }

    private static SQLFragment generateSqlForShiftDateCol(Container c, ColumnInfo col)
    {
        // join to the study.participant table to get the participant's date offset number
        SQLFragment dateOffsetJoin = new SQLFragment();
        dateOffsetJoin.append("(SELECT p.DateOffset FROM ");
        dateOffsetJoin.append(StudySchema.getInstance().getTableInfoParticipant());
        dateOffsetJoin.append(" p  WHERE p.participantid = ");
        dateOffsetJoin.append(ExprColumn.STR_TABLE_ALIAS);
        dateOffsetJoin.append(".participantid  AND p.container = ?)");
        dateOffsetJoin.add(c);

        // use the timestampadd function to apply the date offset (subtracting the offset)
        SQLFragment sql = new SQLFragment ();
        sql.append("{fn timestampadd(SQL_TSI_DAY, -");
        sql.append(dateOffsetJoin);
        sql.append(", ");
        sql.append(col.getValueSql(ExprColumn.STR_TABLE_ALIAS));
        sql.append(")}");

        return sql;
    }

    private static boolean shouldExport(ColumnInfo column, boolean metaData, boolean removeProtected, boolean isKeyProperty)
    {
        return (column.isUserEditable() || (!metaData && column.getPropertyURI().equals(DataSetDefinition.getQCStateURI()))) &&
                !(column.getFk() instanceof ContainerForeignKey) && (!removeProtected || !column.isProtected() || isKeyProperty);
    }

    public static Collection<ColumnInfo> getColumnsToExport(TableInfo tinfo, DataSetDefinition def, boolean metaData, boolean removeProtected)
    {
        // tinfo can be null if the dataset is a Placeholder
        if (tinfo == null)
            return Collections.emptyList();

        List<ColumnInfo> inColumns = tinfo.getColumns();
        Collection<ColumnInfo> outColumns = new LinkedHashSet<>(inColumns.size());

        ColumnInfo ptidColumn = null; String ptidURI = DataSetDefinition.getParticipantIdURI();
        ColumnInfo sequenceColumn = null; String sequenceURI = DataSetDefinition.getSequenceNumURI();
        ColumnInfo qcStateColumn = null; String qcStateURI = DataSetDefinition.getQCStateURI();

        if (def.isAssayData())
        {
            inColumns = new ArrayList<>(QueryService.get().getColumns(tinfo, tinfo.getDefaultVisibleColumns(), inColumns).values());
        }

        for (ColumnInfo in : inColumns)
        {
            // Find the PTID column but ignore the PTID wrapped 'DataSets' column.
            if (in.getPropertyURI().equals(ptidURI) && !in.getName().equals("DataSets"))
            {
                if (null == ptidColumn)
                    ptidColumn = in;
                else
                    LOG.error("More than one ptid column found: " + ptidColumn.getName() + " and " + in.getName());
            }

            if (in.getPropertyURI().equals(sequenceURI))
            {
                if (null == sequenceColumn)
                    sequenceColumn = in;
                else
                    LOG.error("More than one sequence number column found: " + sequenceColumn.getName() + " and " + in.getName());
            }

            if (in.getPropertyURI().equals(qcStateURI))
            {
                if (null == qcStateColumn)
                    qcStateColumn = in;
                else
                    LOG.error("More than one qc state column found: " + qcStateColumn.getName() + " and " + in.getName());
            }
        }

        // Check if we already have a column named "QCStateLabel"
        ColumnInfo qcStateLabelColumn = null;
        for (ColumnInfo inColumn : inColumns)
        {
            if (DataSetTableImpl.QCSTATE_LABEL_COLNAME.equalsIgnoreCase(inColumn.getName()))
            {
                qcStateLabelColumn = inColumn;
            }
        }

        for (ColumnInfo in : inColumns)
        {
            boolean isKeyProperty = in.getName().equals(def.getKeyPropertyName()) || (in.isUserEditable() && (in.equals(ptidColumn) || in.equals(sequenceColumn) || in.getName().toLowerCase().equals("date")));
            if (shouldExport(in, metaData, removeProtected, isKeyProperty) || (metaData && isKeyProperty))
            {
                if ("visit".equalsIgnoreCase(in.getName()) && !in.equals(sequenceColumn))
                    continue;

                if ("ptid".equalsIgnoreCase(in.getName()) && !in.equals(ptidColumn))
                    continue;

                if (null != qcStateColumn && in.equals(qcStateColumn))
                {
                    // Need to replace QCState column (containing rowId) with QCStateLabel (containing the label), but
                    // only if the dataset don't already have a property named "QCStateLabel"
                    if (qcStateLabelColumn == null)
                    {
                        FieldKey qcFieldKey = FieldKey.fromParts(DataSetTableImpl.QCSTATE_ID_COLNAME, "Label");
                        Map<FieldKey, ColumnInfo> select = QueryService.get().getColumns(tinfo, Collections.singletonList(qcFieldKey));
                        ColumnInfo qcAlias = new AliasedColumn(tinfo, DataSetTableImpl.QCSTATE_LABEL_COLNAME, select.get(qcFieldKey));   // Change the caption to QCStateLabel
                        outColumns.add(qcAlias);
                        qcStateLabelColumn = qcAlias;
                    }
                }
                else
                {
                    outColumns.add(in);
                    ColumnInfo displayField = in.getDisplayField();
                    // For assay datasets only, include both the display value and raw value for FKs if they differ
                    // Don't do this for the Participant and SequenceNum columns, since we know that their lookup targets
                    // will be available. See issue 15141
                    if (def.isAssayData() && displayField != null && displayField != in && !ptidURI.equals(in.getPropertyURI()) && !sequenceURI.equals(in.getPropertyURI()))
                    {
                        boolean foundMatch = false;
                        for (ColumnInfo existingColumns : inColumns)
                        {
                            if (existingColumns.getFieldKey().equals(displayField.getFieldKey()))
                            {
                                foundMatch = true;
                                break;
                            }
                        }
                        if (!foundMatch)
                        {
                            outColumns.add(displayField);
                        }
                    }

                    // If the column is MV enabled, export the data in the indicator column as well
                    if (!metaData && in.isMvEnabled())
                        outColumns.add(tinfo.getColumn(in.getMvColumnName()));
                }
            }
        }

        // Handle lookup columns which have "/" in their names by mapping them to "."
        for (ColumnInfo outColumn : outColumns)
        {
            if (outColumn.getName().indexOf("/") != -1)
            {
                outColumn.setName(outColumn.getName().replace('/', '.'));
            }
        }

        return outColumns;
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testShouldExportColumn()
        {
            // true cases
            ColumnInfo ci = new ColumnInfo("test");
            assertTrue(shouldExport(ci, true, true, true));
            assertTrue(shouldExport(ci, true, true, false));
            assertTrue(shouldExport(ci, true, false, true));
            assertTrue(shouldExport(ci, false, true, true));
            assertTrue(shouldExport(ci, true, false, false));
            assertTrue(shouldExport(ci, false, true, false));
            assertTrue(shouldExport(ci, false, false, true));
            assertTrue(shouldExport(ci, false, false, false));

            ci.setProtected(true);
            assertTrue(shouldExport(ci, true, true, true));

            // false cases
            ci = new ColumnInfo("test");
            ci.setUserEditable(false);
            assertFalse(shouldExport(ci, true, false, false));

            ci = new ColumnInfo("test");
            ci.setFk(new ContainerForeignKey(null, null));
            assertFalse(shouldExport(ci, true, false, false));

            ci = new ColumnInfo("test");
            ci.setProtected(true);
            assertFalse(shouldExport(ci, true, true, false));
        }
    }
}
