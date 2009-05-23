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
import org.labkey.api.util.PageFlowUtil;
import org.springframework.validation.BindException;
import org.apache.xmlbeans.XmlException;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

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
            File datasetDir = getDatasetDirectory(ctx, root);

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
                Map<Integer, DatasetImportProperties> extraProps = null;

                DatasetsDocument.Datasets manifestDatasetsXml = getDatasetsManifest(ctx, root);

                if (null != manifestDatasetsXml)
                {
                    Container c = ctx.getContainer();

                    if (!PageFlowUtil.nullSafeEquals(manifestDatasetsXml.getDefaultDateFormat(), StudyManager.getInstance().getDefaultDateFormatString(c)))
                        StudyManager.getInstance().setDefaultDateFormatString(c, manifestDatasetsXml.getDefaultDateFormat());

                    if (!PageFlowUtil.nullSafeEquals(manifestDatasetsXml.getDefaultNumberFormat(), StudyManager.getInstance().getDefaultNumberFormatString(c)))
                        StudyManager.getInstance().setDefaultNumberFormatString(c, manifestDatasetsXml.getDefaultNumberFormat());

                    DatasetsDocument.Datasets.Datasets2.Dataset[] datasets = manifestDatasetsXml.getDatasets().getDatasetArray();

                    extraProps = getDatasetImportProperties(manifestDatasetsXml);

                    orderedIds = new ArrayList<Integer>(datasets.length);

                    for (DatasetsDocument.Datasets.Datasets2.Dataset dataset : datasets)
                        orderedIds.add(dataset.getId());
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
                    submitStudyBatch(study, datasetFile, ctx.getContainer(), ctx.getUser(), ctx.getUrl());
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


    private static File getDatasetDirectory(ImportContext ctx, File root)
    {
        StudyDocument.Study.Datasets datasetsXml = ctx.getStudyXml().getDatasets();

        if (null != datasetsXml)
        {
            return (null == datasetsXml.getDir() ? root : new File(root, datasetsXml.getDir()));
        }

        return null;
    }


    @Nullable
    public static DatasetsDocument.Datasets getDatasetsManifest(ImportContext ctx, File root) throws XmlException, IOException
    {
        File datasetDir = getDatasetDirectory(ctx, root);

        if (null != datasetDir)
        {
            String datasetsXmlFilename = ctx.getStudyXml().getDatasets().getFile();

            if (null != datasetsXmlFilename)
            {
                File datasetsXmlFile = new File(datasetDir, datasetsXmlFilename);

                if (datasetsXmlFile.exists())
                {
                    return DatasetsDocument.Factory.parse(datasetsXmlFile).getDatasets();
                }
            }
        }

        return null;
    }


    public static Map<Integer, DatasetImportProperties> getDatasetImportProperties(@NotNull DatasetsDocument.Datasets datasetsXml)
    {
        DatasetsDocument.Datasets.Datasets2.Dataset[] datasets = datasetsXml.getDatasets().getDatasetArray();
        Map<Integer, DatasetImportProperties> extraProps = new HashMap<Integer, DatasetImportProperties>(datasets.length);

        for (DatasetsDocument.Datasets.Datasets2.Dataset dataset : datasets)
        {
            DatasetImportProperties props = new DatasetImportProperties(dataset.getCategory(), dataset.getCohort(), dataset.getShowByDefault());
            extraProps.put(dataset.getId(), props);
        }

        return extraProps;
    }


    // These dataset properties are defined in datasets.xml; the rest are specified in schema.tsv.
    // TODO: Get rid of schema.tsv and put all dataset-level properties here 
    public static class DatasetImportProperties
    {
        private String _category;
        private String _cohort;
        private boolean _showByDefault;

        private DatasetImportProperties(String category, String cohort, boolean showByDefault)
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
