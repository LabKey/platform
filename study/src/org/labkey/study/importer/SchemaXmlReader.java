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
import org.labkey.api.admin.ImportException;
import org.labkey.api.data.Container;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.exp.DomainURIFactory;
import org.labkey.api.exp.ImportTypesHelper;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.study.Dataset;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.util.XmlValidationException;
import org.labkey.api.writer.VirtualFile;
import org.labkey.data.xml.ColumnType;
import org.labkey.data.xml.IndexType;
import org.labkey.data.xml.IndicesType;
import org.labkey.data.xml.SharedConfigType;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.TablesDocument;
import org.labkey.data.xml.TablesType;
import org.labkey.study.importer.DatasetDefinitionImporter.DatasetImportProperties;
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.model.StudyImpl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: adam
 * Date: May 26, 2009
 * Time: 10:31:36 AM
 */
public class SchemaXmlReader implements SchemaReader
{
    private static final String NAME_KEY = "PlateName";

    private final Map<Integer, DatasetImportInfo> _datasetInfoMap;
    List<ImportTypesHelper.Builder> _builders = new ArrayList<>();

    public SchemaXmlReader(final StudyImpl study, VirtualFile root, String metaDataFile, Map<String, DatasetImportProperties> extraImportProps) throws IOException, XmlException, ImportException
    {
        TablesDocument tablesDoc;

        try
        {
            XmlObject doc = root.getXmlBean(metaDataFile);
            if (doc instanceof TablesDocument)
            {
                XmlBeansUtil.validateXmlDocument(doc, metaDataFile);
                tablesDoc =  (TablesDocument)doc;
            }
            else
                throw new IllegalArgumentException("Could not get an instance of: " + metaDataFile);
        }
        catch (XmlValidationException xve)
        {
            // Note: different constructor than the one below
            throw new ImportException("Invalid TablesDocument ", xve);
        }

        TablesType tablesXml = tablesDoc.getTables();

        _datasetInfoMap = new HashMap<>(tablesXml.getTableArray().length);

        for (TableType tableXml : tablesXml.getTableArray())
        {
            final String datasetName = tableXml.getTableName();

            final DatasetImportInfo info = new DatasetImportInfo(datasetName);
            DatasetImportProperties tableProps = extraImportProps.get(datasetName);

            if (null == tableProps)
                throw new ImportException("Dataset \"" + datasetName + "\" was specified in " + metaDataFile + " but not in the dataset manifest file.");

            info.category = tableProps.getCategory();
            info.name = datasetName;
            info.isHidden = !tableProps.isShowByDefault();
            info.label = tableXml.getTableTitle();
            info.description = tableXml.getDescription();
            info.demographicData = tableProps.isDemographicData();
            info.visitDatePropertyName = null;
            info.tags = tableProps.getTags();
            info.tag = tableProps.getTag();
            info.useTimeKeyField = tableProps.getUseTimeKeyField();

            if (tableProps.getType() != null)
            {
                info.type = tableProps.getType();
            }

            // TODO: fill this in
            info.startDatePropertyName = null;

            addIndicesToDatasetImportInfo(tableXml, info, tablesDoc);

            _datasetInfoMap.put(tableProps.getId(), info);

            applySharedColumns(tableXml, tablesDoc);

            ImportTypesHelper helper = new ImportTypesHelper(tableXml, NAME_KEY, datasetName)
            {
                @Override
                protected boolean acceptColumn(String columnName, ColumnType columnXml) throws Exception
                {
                    // Proper ConceptURI support is not implemented, but we use the 'VisitDate' concept in this isolated spot
                    // as a marker to indicate which dataset column should be tagged as the visit date column during import:
                    if (DatasetDefinition.getVisitDateURI().equalsIgnoreCase(columnXml.getConceptURI()))
                    {
                        if (info.visitDatePropertyName == null)
                            info.visitDatePropertyName = columnName;
                        else
                            throw new IllegalStateException("Dataset " + datasetName + " has multiple visitdate fields specified: '" + info.visitDatePropertyName + "' and '" + columnName + "'");
                    }

                    // filter out the built-in types
                    if (DatasetDefinition.isDefaultFieldName(columnXml.getColumnName(), study))
                        return false;

                    if (columnXml.getIsKeyField())
                    {
                        if (null != info.keyPropertyName)
                            throw new IllegalStateException("Dataset " + datasetName + " has more than one key specified: '" + info.keyPropertyName + "' and '" + columnName + "'");

                        info.keyPropertyName = columnName;

                        if (columnXml.getIsAutoInc())
                            info.keyManagementType = Dataset.KeyManagementType.RowId;
                        if ("entityid".equalsIgnoreCase(columnXml.getDatatype()))
                            info.keyManagementType = Dataset.KeyManagementType.GUID;
                    }

                    return true;
                }
            };

            try
            {
                _builders.addAll(helper.createPropertyDescriptorBuilders(study.getContainer()));
            }
            catch (Exception e)
            {
                throw new IllegalStateException(e);
            }
        }
    }

    private void addIndicesToDatasetImportInfo(TableType tableXml, DatasetImportInfo info, TablesDocument tablesDocument)
    {
        IndicesType tableXmlIndices = tableXml.getIndices();
        List<List<String>> indicesColumns = new ArrayList<>();
        if(tableXmlIndices != null && tableXmlIndices.getIndexArray().length > 0)
        {
            for (IndexType indexType : tableXmlIndices.getIndexArray())
            {
                List<String> columns = Arrays.asList(indexType.getColumnArray());

                indicesColumns.add(columns);
                PropertyStorageSpec.Index index = getIndexFromIndexType(indexType);
                info.indices.add(index);
            }
        }

        applySharedIndices(info, tablesDocument, indicesColumns);
    }

    private void applySharedIndices(DatasetImportInfo info, TablesDocument tablesDocument, List<List<String>> indicesColumnsAsSortedLists)
    {
        Map<List<String>, PropertyStorageSpec.Index> sharedIndicesMap = getSharedConfigIndicesAsMap(tablesDocument);
        for (Map.Entry<List<String>, PropertyStorageSpec.Index> listIndexEntry : sharedIndicesMap.entrySet())
        {
            if(!indicesColumnsAsSortedLists.contains(listIndexEntry.getKey())){
                info.indices.add(listIndexEntry.getValue());
            }
        }
    }

    @NotNull
    private PropertyStorageSpec.Index getIndexFromIndexType(IndexType indexType)
    {
        return new PropertyStorageSpec.Index(
                (IndexType.Type.UNIQUE.equals(indexType.getType())),
                indexType.getColumnArray());
    }

    private Map<List<String>, PropertyStorageSpec.Index> getSharedConfigIndicesAsMap(TablesDocument tablesDocument){
        Map<List<String>, PropertyStorageSpec.Index> indexMap = new HashMap<>();
        for (PropertyStorageSpec.Index index : getSharedConfigIndices(tablesDocument))
        {
            List<String> columnNameList = Arrays.asList(index.columnNames);
            columnNameList.sort(String::compareTo);
            indexMap.put(columnNameList, index);
        }
        return indexMap;
    }

    private List<PropertyStorageSpec.Index> getSharedConfigIndices(TablesDocument tablesDocument)
    {
        List<PropertyStorageSpec.Index> indices = new ArrayList<>();

        SharedConfigType[] sharedConfigArray = tablesDocument.getTables().getSharedConfigArray();

        for (SharedConfigType sharedConfigType : sharedConfigArray)
        {
            IndicesType tableXmlIndices = sharedConfigType.getIndices();
            if (tableXmlIndices != null && tableXmlIndices.getIndexArray().length > 0)
            {
                for (IndexType indexType : tableXmlIndices.getIndexArray())
                {
                    PropertyStorageSpec.Index index = getIndexFromIndexType(indexType);
                    indices.add(index);
                }
            }
        }

        return indices;
    }

    private void applySharedColumns(TableType tableXml, TablesDocument tablesDoc)
    {
        SharedConfigType[] sharedConfigArray = tablesDoc.getTables().getSharedConfigArray();

        if (sharedConfigArray == null || sharedConfigArray.length == 0)
        {
            return;
        }

        SharedConfigType sharedConfigType = sharedConfigArray[0];
        SharedConfigType.Columns columns = sharedConfigType.getColumns();

        if(columns == null || columns.sizeOfColumnArray()==0)
        {
            return;
        }

        ColumnType[] sharedColumns = columns.getColumnArray();

        if (sharedColumns.length > 0)
        {
            ColumnType[] columnArray = tableXml.getColumns().getColumnArray();

            List<String> columnNames = new ArrayList<>();

            for (ColumnType aColumnArray : columnArray)
            {
                columnNames.add(aColumnArray.getColumnName().toLowerCase());
            }

            List<ColumnType> columnTypeArrayList = null;
            for (ColumnType sharedColumn : sharedColumns)
            {
                if (!columnNames.contains(sharedColumn.getColumnName().toLowerCase()))
                {
                    if (columnTypeArrayList == null)
                    {
                        columnTypeArrayList = new ArrayList<>(Arrays.asList(columnArray));
                    }
                    columnTypeArrayList.add(sharedColumn);
                }
            }
            if (columnTypeArrayList != null)
            {
                tableXml.getColumns().setColumnArray(columnTypeArrayList.toArray(new ColumnType[columnTypeArrayList.size()]));
            }
        }
    }

    @Override
    public OntologyManager.ImportPropertyDescriptorsList getImportPropertyDescriptors(DomainURIFactory factory, Collection<String> errors, Container defaultContainer)
    {
        return ImportTypesHelper.getImportPropertyDescriptors(_builders, factory, errors, defaultContainer);
    }

    public Map<Integer, DatasetImportInfo> getDatasetInfo()
    {
        return _datasetInfoMap;
    }

    public String getTypeNameColumn()
    {
        return NAME_KEY;
    }
}
