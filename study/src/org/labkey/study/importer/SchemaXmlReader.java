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

import org.apache.xmlbeans.XmlException;
import org.labkey.api.collections.RowMapFactory;
import org.labkey.api.exp.property.Type;
import org.labkey.data.xml.ColumnType;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.TablesDocument;
import org.labkey.study.importer.DatasetImporter.DatasetImportProperties;
import org.labkey.study.importer.StudyImporter.InvalidFileException;
import org.labkey.study.importer.StudyImporter.StudyImportException;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.StudyImpl;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
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

    private final List<Map<String, Object>> _importMaps;
    private final Map<Integer, DataSetImportInfo> _datasetInfoMap;


    public SchemaXmlReader(StudyImpl study, File root, File metaDataFile, Map<String, DatasetImportProperties> extraImportProps) throws IOException, XmlException, StudyImportException
    {
        TablesDocument tablesDoc;

        try
        {
            tablesDoc = TablesDocument.Factory.parse(metaDataFile);
        }
        catch (XmlException e)
        {
            throw new InvalidFileException(root, metaDataFile, e);
        }

        TablesDocument.Tables tablesXml = tablesDoc.getTables();

        _datasetInfoMap = new HashMap<Integer, DataSetImportInfo>(tablesXml.getTableArray().length);
        _importMaps = new ArrayList<Map<String, Object>>();
        String visitDateURI = DataSetDefinition.getVisitDateURI();

        for (TableType tableXml : tablesXml.getTableArray())
        {
            String datasetName = tableXml.getTableName();

            DataSetImportInfo info = new DataSetImportInfo(datasetName);
            DatasetImportProperties tableProps = extraImportProps.get(datasetName);

            if (null == tableProps)
                throw new StudyImportException("Dataset \"" + datasetName + "\" was specified in " + metaDataFile.getName() + " but not in the dataset manifest file.");

            info.category = tableProps.getCategory();
            info.name = datasetName;
            info.isHidden = !tableProps.isShowByDefault();
            info.label = tableXml.getTableTitle();
            info.description = tableXml.getDescription();
            info.demographicData = tableProps.isDemographicData();
            info.visitDatePropertyName = null;

            // TODO: fill these in
            info.startDatePropertyName = null;

            _datasetInfoMap.put(tableProps.getId(), info);

            // Set up RowMap with all the keys that OntologyManager.importTypes() handles
            RowMapFactory<Object> mapFactory = new RowMapFactory<Object>(NAME_KEY, "Property", "Label", "Description", "RangeURI", "NotNull", "ConceptURI", "Format", "HiddenColumn", "MvEnabled", "LookupFolderPath", "LookupSchema", "LookupQuery");

            for (ColumnType columnXml : tableXml.getColumns().getColumnArray())
            {
                String columnName = columnXml.getColumnName();

                // filter out the built-in types
                if (DataSetDefinition.isDefaultFieldName(columnName, study))
                    continue;

                String dataType = columnXml.getDatatype();
                Type t = Type.getTypeBySqlTypeName(dataType);

                if (t == null)
                    throw new IllegalStateException("Unknown property type '" + dataType + "' for property '" + columnXml.getColumnName() + "' in dataset '" + datasetName + "'.");

                // Assume nullable if not specified
                boolean notNull = columnXml.isSetNullable() && !columnXml.getNullable();

                ColumnType.Fk fk = columnXml.getFk();

                Map<String, Object> map = mapFactory.getRowMap(new Object[]{
                    datasetName,
                    columnName,
                    columnXml.getColumnTitle(),
                    columnXml.getDescription(),
                    t.getXsdType(),
                    notNull,
                    null,  // TODO: conceptURI
                    columnXml.getFormatString(),
                    columnXml.getIsHidden(),
                    null != columnXml.getMvColumnName(),
                    null != fk ? fk.getFkFolderPath() : null,
                    null != fk ? fk.getFkDbSchema() : null,
                    null != fk ? fk.getFkTable() : null
                });

                _importMaps.add(map);

                if (columnXml.getIsKeyField())
                {
                    if (null != info.keyPropertyName)
                        throw new IllegalStateException("Dataset " + datasetName + " has more than one key specified: '" + info.keyPropertyName + "' and '" + columnName + "'");

                    info.keyPropertyName = columnName;

                    if (columnXml.getIsAutoInc())
                        info.keyManaged = true;
                }

                if (DataSetDefinition.getVisitDateURI().equalsIgnoreCase(columnXml.getPropertyURI()))
                {
                    if (info.visitDatePropertyName == null)
                        info.visitDatePropertyName = columnName;
                    else
                        throw new IllegalStateException("Dataset " + datasetName + " has multiple visitdate fields specified: '" + info.visitDatePropertyName + "' and '" + columnName + "'");
                }
            }
        }
    }

    public List<Map<String, Object>> getImportMaps()
    {
        return _importMaps;
    }

    public Map<Integer, DataSetImportInfo> getDatasetInfo()
    {
        return _datasetInfoMap;
    }

    public String getTypeNameColumn()
    {
        return NAME_KEY;
    }
}