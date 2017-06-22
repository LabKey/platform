/*
 * Copyright (c) 2009-2017 LabKey Corporation
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
import org.labkey.api.admin.ImportException;
import org.labkey.api.reports.model.ReportPropsManager;
import org.labkey.api.reports.model.ViewCategory;
import org.labkey.api.reports.model.ViewCategoryManager;
import org.labkey.api.study.Cohort;
import org.labkey.api.writer.VirtualFile;
import org.labkey.data.xml.reportProps.PropertyList;
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.xml.DatasetsDocument;
import org.labkey.study.xml.StudyDocument;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.List;

/**
 * User: adam
 * Date: Apr 16, 2009
 * Time: 3:10:37 PM
 */
public class DatasetWriter implements InternalStudyWriter
{
    private static final Logger LOG = Logger.getLogger(DatasetWriter.class);
    protected static final String DEFAULT_DIRECTORY = "datasets";
    protected static final String MANIFEST_FILENAME = "datasets_manifest.xml";

    public String getDataType()
    {
        return StudyArchiveDataTypes.CRF_DATASETS;
    }

    public void write(StudyImpl study, StudyExportContext ctx, VirtualFile root) throws SQLException, IOException, ServletException, ImportException
    {
        StudyDocument.Study studyXml = ctx.getXml();
        StudyDocument.Study.Datasets datasetsXml = studyXml.addNewDatasets();
        datasetsXml.setDir(DEFAULT_DIRECTORY);
        datasetsXml.setFile(DatasetWriter.MANIFEST_FILENAME);

        VirtualFile vf = root.getDir(DEFAULT_DIRECTORY);
        List<DatasetDefinition> datasets = ctx.getDatasets();

        DatasetsDocument manifestXml = DatasetsDocument.Factory.newInstance();
        DatasetsDocument.Datasets dsXml = manifestXml.addNewDatasets();
        DatasetsDocument.Datasets.Datasets2 datasets2Xml = dsXml.addNewDatasets();

        for (DatasetDefinition def : datasets)
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
                category = ViewCategoryManager.getInstance().getCategory(ctx.getContainer(), def.getCategoryId());
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

            if(def.getTag() != null)
            {
                datasetXml.setTag(def.getTag());
            }

            if (def.getUseTimeKeyField())
                datasetXml.setUseTimeKeyField(true);
        }

        SchemaXmlWriter schemaXmlWriter = new SchemaXmlWriter();
        schemaXmlWriter.write(datasets, ctx, vf);
        dsXml.setMetaDataFile(SchemaXmlWriter.SCHEMA_FILENAME);

        vf.saveXmlBean(MANIFEST_FILENAME, manifestXml);

        StudyDocument.Study.Datasets.Definition definitionXml = datasetsXml.addNewDefinition();
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
    }

}
