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
import org.labkey.api.admin.ImportException;
import org.labkey.api.collections.RowMapFactory;
import org.labkey.api.data.ColumnRenderProperties;
import org.labkey.api.data.ConditionalFormat;
import org.labkey.api.exp.property.Type;
import org.labkey.api.study.DataSet;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.util.XmlValidationException;
import org.labkey.api.writer.VirtualFile;
import org.labkey.data.xml.ColumnType;
import org.labkey.data.xml.FacetingBehaviorType;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.TablesDocument;
import org.labkey.data.xml.TablesType;
import org.labkey.study.importer.DatasetDefinitionImporter.DatasetImportProperties;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.StudyImpl;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: adam
 * Date: May 26, 2009
 * Time: 10:31:36 AM
 */
public class SchemaXmlReader implements SchemaReader
{
    private static final String NAME_KEY = "PlateName";

    private final List<Map<String, Object>> _importMaps = new LinkedList<>();
    private final Map<Integer, DatasetImportInfo> _datasetInfoMap;

    public SchemaXmlReader(StudyImpl study, VirtualFile root, String metaDataFile, Map<String, DatasetImportProperties> extraImportProps) throws IOException, XmlException, ImportException
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
            String datasetName = tableXml.getTableName();

            DatasetImportInfo info = new DatasetImportInfo(datasetName);
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

            if (tableProps.getType() != null)
                info.type = tableProps.getType();

            // TODO: fill this in
            info.startDatePropertyName = null;

            _datasetInfoMap.put(tableProps.getId(), info);

            // Set up RowMap with all the keys that OntologyManager.importTypes() handles
            RowMapFactory<Object> mapFactory = new RowMapFactory<>(NAME_KEY, "Property", "PropertyURI", "Label", "Description",
                    "RangeURI", "NotNull", "ConceptURI", "Format", "InputType", "HiddenColumn", "MvEnabled", "LookupFolderPath",
                    "LookupSchema", "LookupQuery", "URL", "ImportAliases", "ShownInInsertView", "ShownInUpdateView",
                    "ShownInDetailsView", "Measure", "Dimension", "ConditionalFormats", "FacetingBehaviorType", "Protected", "ExcludeFromShifting");

            if (tableXml.getColumns() != null)
            {
                for (ColumnType columnXml : tableXml.getColumns().getColumnArray())
                {
                    String columnName = columnXml.getColumnName();

                    // filter out the built-in types
                    if (DataSetDefinition.isDefaultFieldName(columnName, study))
                        continue;

                    String dataType = columnXml.getDatatype();
                    Type t = Type.getTypeBySqlTypeName(dataType);
                    if ("entityid".equalsIgnoreCase(dataType))
                    {
                        // Special case handling for GUID keys
                        t = Type.StringType;
                    }

                    if (t == null)
                        throw new IllegalStateException("Unknown property type '" + dataType + "' for property '" + columnXml.getColumnName() + "' in dataset '" + datasetName + "'.");

                    // Assume nullable if not specified
                    boolean notNull = columnXml.isSetNullable() && !columnXml.getNullable();

                    boolean mvEnabled = columnXml.isSetIsMvEnabled() ? columnXml.getIsMvEnabled() : null != columnXml.getMvColumnName();

                    // These default to being visible if nothing's specified in the XML
                    boolean shownInInsertView = !columnXml.isSetShownInInsertView() || columnXml.getShownInInsertView();
                    boolean shownInUpdateView = !columnXml.isSetShownInUpdateView() || columnXml.getShownInUpdateView();
                    boolean shownInDetailsView = !columnXml.isSetShownInDetailsView() || columnXml.getShownInDetailsView();

                    boolean measure;
                    if (columnXml.isSetMeasure())
                        measure = columnXml.getMeasure();
                    else
                        measure = ColumnRenderProperties.inferIsMeasure(columnXml.getColumnName(), columnXml.getColumnTitle(), t.isNumeric(), columnXml.getIsAutoInc(), columnXml.getFk() != null, columnXml.getIsHidden());

                    boolean dimension;
                    if (columnXml.isSetDimension())
                        dimension = columnXml.getDimension();
                    else
                        dimension = ColumnRenderProperties.inferIsDimension(columnXml.getColumnName(), columnXml.getFk() != null, columnXml.getIsHidden());

                    Set<String> importAliases = new LinkedHashSet<>();
                    if (columnXml.isSetImportAliases())
                    {
                        importAliases.addAll(Arrays.asList(columnXml.getImportAliases().getImportAliasArray()));
                    }

                    FacetingBehaviorType.Enum type = columnXml.getFacetingBehavior();
                    String facetingBehaviorType = FacetingBehaviorType.AUTOMATIC.toString();
                    if (type != null)
                        facetingBehaviorType = type.toString();

                    boolean isProtected = columnXml.isSetProtected() && columnXml.getProtected();
                    boolean isExcludeFromShifting = columnXml.isSetExcludeFromShifting() && columnXml.getExcludeFromShifting();

                    ColumnType.Fk fk = columnXml.getFk();
                    List<ConditionalFormat> conditionalFormats = ConditionalFormat.convertFromXML(columnXml.getConditionalFormats());
                    Map<String, Object> map = mapFactory.getRowMap(new Object[]{
                        datasetName,
                        columnName,
                        columnXml.getPropertyURI(),
                        columnXml.getColumnTitle(),
                        columnXml.getDescription(),
                        t.getXsdType(),
                        notNull,
                        columnXml.getConceptURI(),
                        columnXml.getFormatString(),
                        columnXml.isSetInputType() ? columnXml.getInputType() : null,
                        columnXml.getIsHidden(),
                        mvEnabled,
                        null != fk ? fk.getFkFolderPath() : null,
                        null != fk ? fk.getFkDbSchema() : null,
                        null != fk ? fk.getFkTable() : null,
                        columnXml.getUrl(),
                        ColumnRenderProperties.convertToString(importAliases),
                        shownInInsertView,
                        shownInUpdateView,
                        shownInDetailsView,
                        measure,
                        dimension,
                        conditionalFormats,
                        facetingBehaviorType,
                        isProtected,
                        isExcludeFromShifting
                    });

                    _importMaps.add(map);

                    if (columnXml.getIsKeyField())
                    {
                        if (null != info.keyPropertyName)
                            throw new IllegalStateException("Dataset " + datasetName + " has more than one key specified: '" + info.keyPropertyName + "' and '" + columnName + "'");

                        info.keyPropertyName = columnName;

                        if (columnXml.getIsAutoInc())
                            info.keyManagementType = DataSet.KeyManagementType.RowId;
                        if ("entityid".equalsIgnoreCase(columnXml.getDatatype()))
                            info.keyManagementType = DataSet.KeyManagementType.GUID;
                    }

                    // Proper ConceptURI support is not implemented, but we use the 'VisitDate' concept in this isolated spot
                    // as a marker to indicate which dataset column should be tagged as the visit date column during import:
                    if (DataSetDefinition.getVisitDateURI().equalsIgnoreCase(columnXml.getConceptURI()))
                    {
                        if (info.visitDatePropertyName == null)
                            info.visitDatePropertyName = columnName;
                        else
                            throw new IllegalStateException("Dataset " + datasetName + " has multiple visitdate fields specified: '" + info.visitDatePropertyName + "' and '" + columnName + "'");
                    }
                }
            }
        }
    }

    public List<Map<String, Object>> getImportMaps()
    {
        return _importMaps;
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
