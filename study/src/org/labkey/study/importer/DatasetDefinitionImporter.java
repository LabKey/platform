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

package org.labkey.study.importer;

import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.ImportException;
import org.labkey.api.annotations.RefactorIn15_1;
import org.labkey.api.data.Container;
import org.labkey.api.settings.WriteableFolderLookAndFeelProperties;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.util.XmlValidationException;
import org.labkey.api.writer.VirtualFile;
import org.labkey.data.xml.reportProps.PropertyList;
import org.labkey.study.model.DatasetReorderer;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.xml.DatasetsDocument;
import org.labkey.study.xml.StudyDocument;
import org.springframework.validation.BindException;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: adam
 * Date: May 16, 2009
 * Time: 9:42:02 PM
 */
public class DatasetDefinitionImporter implements InternalStudyImporter
{
    @Override
    public String getDescription()
    {
        return "Dataset Definition Importer";
    }

    @RefactorIn15_1 // Remove SchemaXmlReader in 15.1
    public void process(StudyImportContext ctx, VirtualFile vf, BindException errors) throws IOException, SQLException, DatasetImportUtils.DatasetLockExistsException, XmlException, ImportException
    {
        StudyImpl study = ctx.getStudy();
        StudyDocument.Study.Datasets datasetsXml = ctx.getXml().getDatasets();

        if (null != datasetsXml)
        {
            VirtualFile datasetDir = getDatasetDirectory(ctx, vf);

            List<Integer> orderedIds = null;
            Map<String, DatasetImportProperties> extraProps = null;
            SchemaReader reader = null;
            DatasetsDocument.Datasets manifestDatasetsXml = getDatasetsManifest(ctx, vf, true);  // Log the first manifest load

            if (null != manifestDatasetsXml)
            {
                Container c = ctx.getContainer();

                // This is only for backwards compatibility; we now export default formats to folder.xml
                if (manifestDatasetsXml.isSetDefaultDateFormat())
                {
                    try
                    {
                        WriteableFolderLookAndFeelProperties.saveDefaultDateFormat(c, manifestDatasetsXml.getDefaultDateFormat());
                    }
                    catch (IllegalArgumentException e)
                    {
                        ctx.getLogger().warn("Illegal default date format specified: " + e.getMessage());
                    }
                }

                // This is only for backwards compatibility; we now export default formats to folder.xml
                if (manifestDatasetsXml.isSetDefaultNumberFormat())
                {
                    try
                    {
                        WriteableFolderLookAndFeelProperties.saveDefaultNumberFormat(c, manifestDatasetsXml.getDefaultNumberFormat());
                    }
                    catch (IllegalArgumentException e)
                    {
                        ctx.getLogger().warn("Illegal default number format specified: " + e.getMessage());
                    }
                }

                DatasetsDocument.Datasets.Datasets2.Dataset[] datasets = manifestDatasetsXml.getDatasets().getDatasetArray();

                extraProps = getDatasetImportProperties(manifestDatasetsXml);

                orderedIds = new ArrayList<>(datasets.length);

                for (DatasetsDocument.Datasets.Datasets2.Dataset dataset : datasets)
                    orderedIds.add(dataset.getId());

                String metaDataFilename = manifestDatasetsXml.getMetaDataFile();

                if (null != metaDataFilename)
                {
                    ctx.getLogger().info("Loading dataset schema from " + metaDataFilename);
                    reader = new SchemaXmlReader(study, datasetDir, metaDataFilename, extraProps);
                }
            }

            if (null == reader)
            {
                StudyDocument.Study.Datasets.Schema schema = datasetsXml.getSchema();

                if (null != schema)
                {
                    String schemaTsvSource = schema.getFile();
                    String labelColumn = schema.getLabelColumn();
                    String typeNameColumn = schema.getTypeNameColumn();
                    String typeIdColumn = schema.getTypeIdColumn();

                    ctx.getLogger().info("Loading dataset schema from " + schemaTsvSource);
                    ctx.getLogger().warn("DataFax schema definition format is deprecated and scheduled for removal in LabKey release 15.1. Contact LabKey immediately if your organization requires this support.");
                    reader = new SchemaTsvReader(study, datasetDir, schemaTsvSource, labelColumn, typeNameColumn, typeIdColumn, extraProps, errors);
                }
            }

            if (null != reader)
            {
                if (!StudyManager.getInstance().importDatasetSchemas(study, ctx.getUser(), reader, errors, ctx.isCreateSharedDatasets()))
                    return;
            }

            if (null != orderedIds)
            {
                DatasetReorderer reorderer = new DatasetReorderer(study, ctx.getUser());
                reorderer.reorderDatasets(orderedIds);
            }
        }
    }

    public static VirtualFile getDatasetDirectory(StudyImportContext ctx, VirtualFile root) throws ImportException
    {
        StudyDocument.Study.Datasets datasetsXml = ctx.getXml().getDatasets();

        if (null != datasetsXml)
        {
            if (null == datasetsXml.getDir())
                return root;

            return root.getDir(datasetsXml.getDir());
        }

        return null;
    }

    @Nullable
    public static DatasetsDocument.Datasets getDatasetsManifest(StudyImportContext ctx, VirtualFile root, boolean log) throws XmlException, IOException, ImportException
    {
        VirtualFile datasetDir = getDatasetDirectory(ctx, root);

        if (null != datasetDir)
        {
            String datasetsXmlFilename = ctx.getXml().getDatasets().getFile();

            if (null != datasetsXmlFilename)
            {
                try
                {
                    if (log)
                        ctx.getLogger().info("Loading datasets manifest from " + datasetsXmlFilename);

                    XmlObject doc = datasetDir.getXmlBean(datasetsXmlFilename);
                    if (doc instanceof DatasetsDocument)
                    {
                        XmlBeansUtil.validateXmlDocument(doc);
                        return ((DatasetsDocument)doc).getDatasets();
                    }
                    return null;
                }
                catch (XmlValidationException e)
                {
                    throw new ImportException("Invalid DatasetsDocument ", e);
                }
            }
        }

        return null;
    }

    public static Map<String, DatasetImportProperties> getDatasetImportProperties(@NotNull DatasetsDocument.Datasets datasetsXml)
    {
        DatasetsDocument.Datasets.Datasets2.Dataset[] datasets = datasetsXml.getDatasets().getDatasetArray();
        Map<String, DatasetImportProperties> extraProps = new HashMap<>(datasets.length);

        for (DatasetsDocument.Datasets.Datasets2.Dataset dataset : datasets)
        {
            DatasetImportProperties props = new DatasetImportProperties(dataset.getId(), dataset.getCategory(), dataset.getCohort(),
                    dataset.getShowByDefault(), dataset.getDemographicData(), dataset.getType(), dataset.getTags());
            extraProps.put(dataset.getName(), props);
        }

        return extraProps;
    }


    // These are the study-specific dataset properties that are defined in datasets.xml
    public static class DatasetImportProperties
    {
        private final int _id;
        private final String _category;
        private final String _cohort;
        private final boolean _showByDefault;
        private final boolean _demographicData;
        private final String _type;
        private final PropertyList _tags;

        private DatasetImportProperties(int id, String category, String cohort, boolean showByDefault,
                                        boolean demographicData, String type, PropertyList tags)
        {
            _id = id;
            _category = category;
            _cohort = cohort;
            _showByDefault = showByDefault;
            _demographicData = demographicData;
            _type = type;
            _tags = tags;
        }

        public int getId()
        {
            return _id;
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

        public boolean isDemographicData()
        {
            return _demographicData;
        }

        public String getType()
        {
            return _type;
        }

        public PropertyList getTags()
        {
            return _tags;
        }
    }
}
