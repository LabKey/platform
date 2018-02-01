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

package org.labkey.study.importer;

import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.ImportException;
import org.labkey.api.data.Container;
import org.labkey.api.settings.WriteableFolderLookAndFeelProperties;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.util.XmlValidationException;
import org.labkey.api.writer.VirtualFile;
import org.labkey.data.xml.reportProps.PropertyList;
import org.labkey.study.model.DatasetReorderer;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.pipeline.DatasetInferSchemaReader;
import org.labkey.study.pipeline.StudyImportDatasetTask;
import org.labkey.study.writer.StudyArchiveDataTypes;
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

    public String getDataType() { return StudyArchiveDataTypes.DATASET_DEFINITIONS; }

    public void process(StudyImportContext ctx, VirtualFile vf, BindException errors) throws IOException, SQLException, XmlException, ImportException
    {
        if (!ctx.isDataTypeSelected(getDataType()))
            return;

        if (isValidForImportArchive(ctx, vf))
        {
            SchemaReader reader = null;
            StudyImpl study = ctx.getStudy();
            List<Integer> orderedIds = null;

            // dataset metadata provided or at least a definition file
            if (hasDatasetDefinitionFile(ctx))
            {
                StudyDocument.Study.Datasets datasetsXml = ctx.getXml().getDatasets();
                VirtualFile datasetDir = getDatasetDirectory(ctx, vf);

                Map<String, DatasetImportProperties> extraProps = null;
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
            }
            else
            {
                // infer column metadata
                List<String> readerErrors = new ArrayList<>();

                VirtualFile datasetsDirectory = StudyImportDatasetTask.getDatasetsDirectory(ctx, ctx.getRoot());
                String datasetsFileName = StudyImportDatasetTask.getDatasetsFileName(ctx);

                if (datasetsDirectory != null)
                {
                    reader = new DatasetInferSchemaReader(datasetsDirectory, datasetsFileName, study, ctx);
                    ((DatasetInferSchemaReader)reader).validate(readerErrors);

                    for (String error : readerErrors)
                        ctx.getLogger().error(error);
                }
            }

            if (null != reader)
            {
                if (!StudyManager.getInstance().importDatasetSchemas(study, ctx.getUser(), reader, errors, ctx.isCreateSharedDatasets(), ctx.getActivity()))
                    return;
            }

            if (null != orderedIds)
            {
                DatasetReorderer reorderer = new DatasetReorderer(study, ctx.getUser());
                reorderer.reorderDatasets(orderedIds);
            }
        }
    }

    /**
     * Determines whether there is a .dataset file in the import archive
     */
    private boolean hasDatasetDefinitionFile(StudyImportContext ctx) throws ImportException
    {
        VirtualFile datasetsDirectory = StudyImportDatasetTask.getDatasetsDirectory(ctx, ctx.getRoot());
        String datasetsFileName = StudyImportDatasetTask.getDatasetsFileName(ctx);

        if (datasetsDirectory != null && datasetsFileName != null)
        {
            return datasetsDirectory.list().contains(datasetsFileName);
        }
        return false;
    }

    @Override
    public boolean isValidForImportArchive(StudyImportContext ctx, VirtualFile root) throws ImportException
    {
        return ctx.getXml() != null;
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
    public static DatasetsDocument.Datasets getDatasetsManifest(StudyImportContext ctx, VirtualFile root, boolean log) throws IOException, ImportException
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
                    dataset.getShowByDefault(), dataset.getDemographicData(), dataset.getType(), dataset.getTags(), dataset.getTag(), dataset.getUseTimeKeyField());
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
        private final String _tag;
        private final boolean _useTimeKeyField;

        private DatasetImportProperties(int id, String category, String cohort, boolean showByDefault,
                                        boolean demographicData, String type, PropertyList tags, String tag, boolean useTimeKeyField)
        {
            _id = id;
            _category = category;
            _cohort = cohort;
            _showByDefault = showByDefault;
            _demographicData = demographicData;
            _type = type;
            _tags = tags;
            _tag = tag;
            _useTimeKeyField = useTimeKeyField;
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

        public String getTag()
        {
            return _tag;
        }

        public boolean getUseTimeKeyField()
        {
            return _useTimeKeyField;
        }
    }
}
