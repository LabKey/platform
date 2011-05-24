/*
 * Copyright (c) 2009-2011 LabKey Corporation
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
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Results;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TSVGridWriter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.study.StudyImportException;
import org.labkey.api.study.StudyService;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.query.DataSetTable;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: adam
 * Date: Apr 16, 2009
 * Time: 3:10:37 PM
 */
class DatasetWriter implements InternalStudyWriter
{
    private static final Logger LOG = Logger.getLogger(DatasetWriter.class);
    private static final String DEFAULT_DIRECTORY = "datasets";
    private static final String MANIFEST_FILENAME = "datasets_manifest.xml";

    public static final String SELECTION_TEXT = "CRF Datasets";

    public String getSelectionText()
    {
        return SELECTION_TEXT;
    }

    public void write(StudyImpl study, StudyExportContext ctx, VirtualFile root) throws SQLException, IOException, ServletException, StudyImportException
    {
        StudyDocument.Study studyXml = ctx.getStudyXml();
        Datasets datasetsXml = studyXml.addNewDatasets();
        datasetsXml.setDir(DEFAULT_DIRECTORY);
        datasetsXml.setFile(MANIFEST_FILENAME);

        VirtualFile vf = root.getDir(DEFAULT_DIRECTORY);
        List<DataSetDefinition> datasets = ctx.getDatasets();

        DatasetsDocument manifestXml = DatasetsDocument.Factory.newInstance();
        DatasetsDocument.Datasets dsXml = manifestXml.addNewDatasets();
        String defaultDateFormat = StudyManager.getInstance().getDefaultDateFormatString(ctx.getContainer());
        String defaultNumberFormat = StudyManager.getInstance().getDefaultNumberFormatString(ctx.getContainer());
        if (null != defaultDateFormat)
            dsXml.setDefaultDateFormat(defaultDateFormat);
        if (null != defaultNumberFormat)
            dsXml.setDefaultNumberFormat(defaultNumberFormat);

        // Create <categories> element now so it appears first in the file
        DatasetsDocument.Datasets.Categories categoriesXml = dsXml.addNewCategories();
        DatasetsDocument.Datasets.Datasets2 datasets2Xml = dsXml.addNewDatasets();

        Set<String> categories = new LinkedHashSet<String>();

        for (DataSetDefinition def : datasets)
        {
            DatasetsDocument.Datasets.Datasets2.Dataset datasetXml = datasets2Xml.addNewDataset();
            datasetXml.setName(def.getName());
            datasetXml.setId(def.getDataSetId());

            org.labkey.api.study.Cohort cohort = def.getCohort();

            if (null != cohort)
                datasetXml.setCohort(cohort.getLabel());

            // Default value is "true"
            if (!def.isShowByDefault())
                datasetXml.setShowByDefault(false);

            String category = def.getCategory();

            if (null != category)
            {
                categories.add(category);
                datasetXml.setCategory(category);
            }

            if (def.isDemographicData())
                datasetXml.setDemographicData(true);
        }

        if (categories.isEmpty())
            dsXml.unsetCategories();     // Don't need the <categories> element after all
        else
            categoriesXml.setCategoryArray(categories.toArray(new String[categories.size()]));

        if (ctx.useOldFormats())
        {
            // Write out the schema.tsv file and add reference & attributes to study.xml
            SchemaTsvWriter schemaTsvWriter = new SchemaTsvWriter();
            schemaTsvWriter.write(datasets, ctx, vf);
        }
        else
        {
            SchemaXmlWriter schemaXmlWriter = new SchemaXmlWriter(defaultDateFormat);
            schemaXmlWriter.write(datasets, ctx, vf);
            dsXml.setMetaDataFile(SchemaXmlWriter.SCHEMA_FILENAME);
        }

        vf.saveXmlBean(MANIFEST_FILENAME, manifestXml);

        // Write out the .dataset file and add reference to study.xml
        Datasets.Definition definitionXml = datasetsXml.addNewDefinition();
        String datasetFilename = vf.makeLegalName(study.getLabel().replaceAll("\\s", "") + ".dataset");
        definitionXml.setFile(datasetFilename);

        PrintWriter writer = vf.getPrintWriter(datasetFilename);
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
        writer.close();

        StudyQuerySchema schema = new StudyQuerySchema(StudyManager.getInstance().getStudy(ctx.getContainer()), ctx.getUser(), true);

        // Write out all the dataset .tsv files
        for (DataSetDefinition def : datasets)
        {
            TableInfo ti = new DataSetTable(schema, def);
            Collection<ColumnInfo> columns = getColumnsToExport(ti, def, false);
            // Sort the data rows by PTID & sequence, #11261
            Sort sort = new Sort(StudyService.get().getSubjectColumnName(ctx.getContainer()) + ", SequenceNum");
            Results rs = QueryService.get().select(ti, columns, null, sort);
            TSVGridWriter tsvWriter = new TSVGridWriter(rs);
            tsvWriter.setApplyFormats(false);
            tsvWriter.setColumnHeaderType(TSVGridWriter.ColumnHeaderType.queryColumnName);
            PrintWriter out = vf.getPrintWriter(def.getFileName());
            tsvWriter.write(out);     // NOTE: TSVGridWriter closes PrintWriter and ResultSet
        }
    }

    private static boolean shouldExport(ColumnInfo column, boolean metaData)
    {
        return column.isUserEditable() || (!metaData && column.getPropertyURI().equals(DataSetDefinition.getQCStateURI()));
    }

    public static Collection<ColumnInfo> getColumnsToExport(TableInfo tinfo, DataSetDefinition def, boolean metaData)
    {
        List<ColumnInfo> inColumns = tinfo.getColumns();
        Collection<ColumnInfo> outColumns = new LinkedHashSet<ColumnInfo>(inColumns.size());

        ColumnInfo ptidColumn = null; String ptidURI = DataSetDefinition.getParticipantIdURI();
        ColumnInfo sequenceColumn = null; String sequenceURI = DataSetDefinition.getSequenceNumURI();
        ColumnInfo qcStateColumn = null; String qcStateURI = DataSetDefinition.getQCStateURI();

        if (def.isAssayData())
        {
            inColumns = new ArrayList<ColumnInfo>(QueryService.get().getColumns(tinfo, tinfo.getDefaultVisibleColumns(), inColumns).values());
        }

        for (ColumnInfo in : inColumns)
        {
            if (in.getPropertyURI().equals(ptidURI))
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

        for (ColumnInfo in : inColumns)
        {
            if (shouldExport(in, metaData) || (metaData && in.getName().equals(def.getKeyPropertyName())))
            {
                if ("visit".equalsIgnoreCase(in.getName()) && !in.equals(sequenceColumn))
                    continue;

                if ("ptid".equalsIgnoreCase(in.getName()) && !in.equals(ptidColumn))
                    continue;

                if (null != qcStateColumn && in.equals(qcStateColumn))
                {
                    // Need to replace QCState column (containing rowId) with QCStateLabel (containing the label)
                    FieldKey qcFieldKey = FieldKey.fromParts("QCState", "Label");
                    Map<FieldKey, ColumnInfo> select = QueryService.get().getColumns(tinfo, Collections.singletonList(qcFieldKey));
                    ColumnInfo qcAlias = new AliasedColumn(tinfo, "QCStateLabel", select.get(qcFieldKey));   // Change the caption to QCStateLabel
                    outColumns.add(qcAlias);
                }
                else
                {
                    outColumns.add(in);
                    ColumnInfo displayField = in.getDisplayField();
                    // For assay datasets only, include both the display value and raw value for FKs if they differ
                    if (def.isAssayData() && displayField != null && displayField != in)
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
}
