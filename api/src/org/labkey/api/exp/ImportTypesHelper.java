/*
 * Copyright (c) 2014-2015 LabKey Corporation
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
package org.labkey.api.exp;

import org.labkey.api.admin.ImportException;
import org.labkey.api.collections.RowMapFactory;
import org.labkey.api.data.ColumnRenderProperties;
import org.labkey.api.data.ConditionalFormat;
import org.labkey.api.exp.property.Type;
import org.labkey.data.xml.ColumnType;
import org.labkey.data.xml.DefaultScaleType;
import org.labkey.data.xml.FacetingBehaviorType;
import org.labkey.data.xml.TableType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by klum on 2/7/14.
 */
public class ImportTypesHelper
{
    protected TableType _tableXml;
    protected String _typeColumnName;
    protected Object _typeColumnValue;
    protected static List<String> _standardKeys = new ArrayList<>();

    static {
        _standardKeys.add("Property");
        _standardKeys.add("PropertyURI");
        _standardKeys.add("Label");
        _standardKeys.add("Description");

        _standardKeys.add("RangeURI");
        _standardKeys.add("NotNull");
        _standardKeys.add("ConceptURI");
        _standardKeys.add("Format");
        _standardKeys.add("InputType");
        _standardKeys.add("HiddenColumn");
        _standardKeys.add("MvEnabled");
        _standardKeys.add("LookupFolderPath");

        _standardKeys.add("LookupSchema");
        _standardKeys.add("LookupQuery");
        _standardKeys.add("URL");
        _standardKeys.add("ImportAliases");
        _standardKeys.add("ShownInInsertView");
        _standardKeys.add("ShownInUpdateView");

        _standardKeys.add("ShownInDetailsView");
        _standardKeys.add("Measure");
        _standardKeys.add("Dimension");
        _standardKeys.add("KeyVariable");
        _standardKeys.add("DefaultScale");
        _standardKeys.add("ConditionalFormats");
        _standardKeys.add("FacetingBehaviorType");
        _standardKeys.add("Protected");
        _standardKeys.add("ExcludeFromShifting");
        _standardKeys.add("Scale");

    }

    public ImportTypesHelper(TableType tableXml, String typeColumnName, Object typeColumnValue)
    {
        _tableXml = tableXml;
        _typeColumnName = typeColumnName;
        _typeColumnValue = typeColumnValue;
    }

    protected RowMapFactory<Object> getRowMapFactory()
    {
        List<String> keys = new ArrayList<>();

        keys.add(_typeColumnName);
        keys.addAll(_standardKeys);

        return new RowMapFactory<>(keys);
    }

    protected boolean acceptColumn(String columnName, ColumnType columnXml) throws Exception
    {
        return true;
    }

    /**
     * Create the row maps that OntologyManager.importTypes can consume
     * @return
     */
    public List<Map<String, Object>> createImportMaps() throws Exception
    {
        List<Map<String, Object>> importMaps = new LinkedList<>();
        RowMapFactory<Object> mapFactory = getRowMapFactory();

        for (ColumnType columnXml : _tableXml.getColumns().getColumnArray())
        {
            String columnName = columnXml.getColumnName();

            if (!acceptColumn(columnName, columnXml))
                continue;

            String dataType = columnXml.getDatatype();
            Type t = Type.getTypeBySqlTypeName(dataType);

            if (t == null)
                t = Type.getTypeByLabel(dataType);

            if ("entityid".equalsIgnoreCase(dataType))
            {
                // Special case handling for GUID keys
                t = Type.StringType;
            }

            if (t == null)
                throw new ImportException("Unknown property type \"" + dataType + "\" for property \"" + columnXml.getColumnName() + "\".");

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

            boolean keyVariable = columnXml.isSetKeyVariable() && columnXml.getKeyVariable();

            DefaultScaleType.Enum scaleType = columnXml.getDefaultScale();
            String defaultScale = scaleType != null ? scaleType.toString() : DefaultScaleType.LINEAR.toString();

            FacetingBehaviorType.Enum type = columnXml.getFacetingBehavior();
            String facetingBehaviorType = FacetingBehaviorType.AUTOMATIC.toString();
            if (type != null)
                facetingBehaviorType = type.toString();

            Set<String> importAliases = new LinkedHashSet<>();
            if (columnXml.isSetImportAliases())
            {
                importAliases.addAll(Arrays.asList(columnXml.getImportAliases().getImportAliasArray()));
            }

            boolean isProtected = columnXml.isSetProtected() && columnXml.getProtected();
            boolean isExcludeFromShifting = columnXml.isSetExcludeFromShifting() && columnXml.getExcludeFromShifting();

            ColumnType.Fk fk = columnXml.getFk();

            Map<String, Object> map = mapFactory.getRowMap(
                    _typeColumnValue,
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
                    keyVariable,
                    defaultScale,
                    ConditionalFormat.convertFromXML(columnXml.getConditionalFormats()),
                    facetingBehaviorType,
                    isProtected,
                    isExcludeFromShifting,
                    columnXml.isSetScale() ? columnXml.getScale() : null

            );

            importMaps.add(map);
        }

        return importMaps;
    }
}
