/*
 * Copyright (c) 2009 LabKey Corporation
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
package org.labkey.study.importer;

import org.labkey.study.xml.StudyDocument;
import org.labkey.study.xml.DatasetsDocument;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.Study;
import org.labkey.study.model.DatasetReorderer;
import org.labkey.study.pipeline.StudyPipeline;
import org.labkey.study.pipeline.DatasetBatch;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewBackgroundInfo;
import org.springframework.validation.BindException;
import org.apache.xmlbeans.XmlException;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

/**
 * User: adam
 * Date: May 16, 2009
 * Time: 9:42:02 PM
 */
public class DatasetImporter
{
    boolean process(Study study, ImportContext ctx, File root, BindException errors) throws IOException, SQLException, StudyImporter.DatasetLockExistsException, XmlException
    {
        StudyDocument.Study.Datasets datasetsXml = ctx.getStudyXml().getDatasets();

        if (null != datasetsXml)
        {
            File datasetDir = (null == datasetsXml.getDir() ? root : new File(root, datasetsXml.getDir()));

            StudyDocument.Study.Datasets.Schema schema = datasetsXml.getSchema();
            String schemaSource = schema.getFile();
            String labelColumn = schema.getLabelColumn();
            String typeNameColumn = schema.getTypeNameColumn();
            String typeIdColumn = schema.getTypeIdColumn();

            String datasetFilename = ctx.getStudyXml().getDatasets().getDefinition().getFile();

            File schemaFile = new File(datasetDir, schemaSource);

            if (schemaFile.exists())
            {
                List<Integer> orderedIds = null;
                Map<Integer, ExtraImportProperties> extraProps = null;

                String datasetsXmlFilename = ctx.getStudyXml().getDatasets().getFile();

                if (null != datasetsXmlFilename)
                {
                    File datasetsXmlFile = new File(datasetDir, datasetsXmlFilename);

                    if (datasetsXmlFile.exists())
                    {
                        Container c = ctx.getContainer();
                        DatasetsDocument datasetsDoc = DatasetsDocument.Factory.parse(datasetsXmlFile);
                        DatasetsDocument.Datasets manifestDatasetsXml = datasetsDoc.getDatasets();

                        if (manifestDatasetsXml.isSetDefaultDateFormat())
                            if (manifestDatasetsXml.getDefaultDateFormat().equals(StudyManager.getInstance().getDefaultDateFormatString(c)))
                                StudyManager.getInstance().setDefaultDateFormatString(c, manifestDatasetsXml.getDefaultDateFormat());

                        if (manifestDatasetsXml.isSetDefaultNumberFormat())
                            if (manifestDatasetsXml.getDefaultNumberFormat().equals(StudyManager.getInstance().getDefaultNumberFormatString(c)))
                                StudyManager.getInstance().setDefaultNumberFormatString(c, manifestDatasetsXml.getDefaultNumberFormat());

                        DatasetsDocument.Datasets.Datasets2.Dataset[] datasets = manifestDatasetsXml.getDatasets().getDatasetArray();

                        orderedIds = new ArrayList<Integer>(datasets.length);
                        extraProps = new HashMap<Integer, ExtraImportProperties>(datasets.length);

                        for (DatasetsDocument.Datasets.Datasets2.Dataset dataset : datasets)
                        {
                            orderedIds.add(dataset.getId());
                            ExtraImportProperties props = new ExtraImportProperties(dataset.getCategory(), dataset.getCohort(), dataset.getShowByDefault());
                            extraProps.put(dataset.getId(), props);
                        }
                    }
                }

                if (!StudyManager.getInstance().bulkImportTypes(study, schemaFile, ctx.getUser(), labelColumn, typeNameColumn, typeIdColumn, extraProps, errors))
                    return false;

                if (null != orderedIds)
                {
                    DatasetReorderer reorderer = new DatasetReorderer(study, ctx.getUser());
                    reorderer.reorderDatasets(orderedIds);
                }

                File datasetFile = new File(datasetDir, datasetFilename);

                if (datasetFile.exists())
                {
                    submitStudyBatch(study, datasetFile, ctx.getContainer(), ctx.getUser(), ctx.getUrl());  // TODO: remove last param
                }
            }
        }

        return true;
    }

    public static void submitStudyBatch(Study study, File datasetFile, Container c, User user, ActionURL url) throws IOException, StudyImporter.DatasetLockExistsException, SQLException
    {
        if (null == datasetFile || !datasetFile.exists() || !datasetFile.isFile())
        {
            HttpView.throwNotFound();
            return;
        }

        File lockFile = StudyPipeline.lockForDataset(study, datasetFile);
        if (!datasetFile.canRead() || lockFile.exists())
        {
            throw new StudyImporter.DatasetLockExistsException();
        }

        DatasetBatch batch = new DatasetBatch(new ViewBackgroundInfo(c, user, url), datasetFile);
        batch.submit();
    }

    // These dataset properties are defined in datasets.xml; the rest are specified in schema.tsv
    public static class ExtraImportProperties
    {
        private String _category;
        private String _cohort;
        private boolean _showByDefault;

        private ExtraImportProperties(String category, String cohort, boolean showByDefault)
        {
            _category = category;
            _cohort = cohort;
            _showByDefault = showByDefault;
        }

        public String getCategory()
        {
            return _category;
        }

        public String getCohort()
        {
            return _cohort;
        }

        public boolean isShowByDefault()
        {
            return _showByDefault;
        }
    }
}
