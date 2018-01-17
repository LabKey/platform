/*
 * Copyright (c) 2008-2017 LabKey Corporation
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
package org.labkey.api.data;

import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.gwt.client.DefaultScaleType;
import org.labkey.api.gwt.client.FacetingBehaviorType;
import org.labkey.api.gwt.client.PHIType;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.RowIdForeignKey;
import org.labkey.api.util.ExtUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * User: brittp
 * Created: Feb 11, 2008 12:57:07 PM
 */
public class JsonWriter
{
    public static Map<FieldKey, Map<String,Object>> getNativeColProps(TableInfo tableInfo, Collection<FieldKey> fields, FieldKey fieldKeyPrefix, boolean includeDomainFormat)
    {
        List<DisplayColumn> displayColumns = QueryService.get().getColumns(tableInfo, fields, tableInfo.getColumns())
                .values()
                .stream()
                .map(cinfo -> cinfo.getDisplayColumnFactory().createRenderer(cinfo))
                .collect(Collectors.toList());

        return getNativeColProps(displayColumns, fieldKeyPrefix, includeDomainFormat);
    }

    public static Map<FieldKey, Map<String,Object>> getNativeColProps(Collection<DisplayColumn> columns, FieldKey fieldKeyPrefix, boolean includeDomainFormat)
    {
        Map<FieldKey, Map<String,Object>> colProps = new LinkedHashMap<>();
        for (DisplayColumn displayColumn : columns)
        {
            FieldKey fieldKey;
            ColumnInfo cinfo = displayColumn.getColumnInfo();
            if (cinfo != null && null != cinfo.getFieldKey())
                fieldKey = fieldKeyPrefix == null ? cinfo.getFieldKey() : FieldKey.fromParts(fieldKeyPrefix, cinfo.getFieldKey());
            else
                fieldKey = new FieldKey(fieldKeyPrefix, displayColumn.getName());

            colProps.put(fieldKey, JsonWriter.getMetaData(displayColumn, fieldKeyPrefix, true, true, includeDomainFormat));
        }
        return colProps;
    }

    public static Map<String, Object> getMetaData(DisplayColumn dc, FieldKey fieldKeyPrefix, boolean useFriendlyAsType, boolean includeLookup, boolean includeDomainFormat)
    {
        @Nullable ColumnInfo cinfo = dc.getColumnInfo();
        Map<String, Object> props = new LinkedHashMap<>();
        JSONObject ext = new JSONObject();
        props.put("ext",ext);

        // Some DisplayColumns aren't backed by a ColumnInfo, so handle null when determining metadata
        String name = cinfo == null ? dc.getName() : cinfo.getName();
        props.put("name", (null != fieldKeyPrefix ? FieldKey.fromString(fieldKeyPrefix, name) : name));
        if (cinfo != null && null != cinfo.getDescription())
            props.put("description", cinfo.getDescription());

        String friendlyTypeName = cinfo == null ? ColumnInfo.getFriendlyTypeName(dc.getDisplayValueClass()) : cinfo.getFriendlyTypeName();
        props.put("align", dc.getTextAlign());
        props.put("friendlyType", friendlyTypeName);
        props.put("type", useFriendlyAsType ? friendlyTypeName : dc.getJsonTypeName());
        props.put("jsonType", dc.getJsonTypeName());
        props.put("sqlType", cinfo == null ? null : cinfo.getSqlTypeName());
        props.put("defaultValue", cinfo == null ? null : cinfo.getDefaultValue());

        if (includeDomainFormat)
        {
            PropertyType propertyType = cinfo != null ? cinfo.getPropertyType() : PropertyType.getFromClass(dc.getValueClass());
            if (propertyType != null)
            {
                props.put("typeName", propertyType.getXmlName());
                props.put("typeURI", propertyType.getTypeUri());
            }
        }

        FieldKey fieldKey;
        if (cinfo != null && null != cinfo.getFieldKey())
            fieldKey = cinfo.getFieldKey();
        else
            fieldKey = new FieldKey(null, name);

        props.put("fieldKey", fieldKey.toString());
        props.put("fieldKeyArray", new JSONArray(fieldKey.getParts()));
        props.put("fieldKeyPath", (fieldKeyPrefix == null ? fieldKey : FieldKey.fromParts(fieldKeyPrefix, fieldKey)).toString());

        // Duplicate booleans with alternate property name for backwards compatibility
        boolean autoIncrement = cinfo != null && cinfo.isAutoIncrement();
        props.put("isAutoIncrement", autoIncrement);
        props.put("autoIncrement", autoIncrement);
        boolean hidden = cinfo != null && cinfo.isHidden();
        props.put("isHidden", hidden);
        props.put("hidden", hidden);
        boolean keyField = cinfo != null && cinfo.isKeyField();
        props.put("isKeyField", keyField);
        props.put("keyField", keyField);
        boolean mvEnabled = cinfo != null && cinfo.isMvEnabled();
        props.put("isMvEnabled", mvEnabled);
        props.put("mvEnabled", mvEnabled);
        boolean nullable = cinfo != null && cinfo.isNullable();
        props.put("isNullable", nullable);
        props.put("nullable", nullable);
        props.put("required", cinfo != null && cinfo.isRequired());
        boolean readOnly = cinfo != null && cinfo.isReadOnly();
        props.put("isReadOnly", readOnly);
        props.put("readOnly", readOnly);
        boolean userEditable = cinfo != null && cinfo.isUserEditable();
        props.put("isUserEditable", userEditable);
        props.put("userEditable", userEditable);
        props.put("calculated", cinfo == null || cinfo.isCalculated());
        boolean versionColumn = cinfo != null && cinfo.isVersionColumn();
        props.put("isVersionField", versionColumn);
        props.put("versionField", versionColumn);
        boolean selectable = cinfo != null && !cinfo.isUnselectable();
        props.put("isSelectable", selectable);  //avoid double-negative boolean name
        props.put("selectable", selectable);    //avoid double-negative boolean name

        // These fields are new and don't need to have the "is" prefix for backwards compatibility
        props.put("shownInInsertView", cinfo != null && cinfo.isShownInInsertView());
        props.put("shownInUpdateView", cinfo != null && cinfo.isShownInUpdateView());
        props.put("shownInDetailsView", cinfo == null || cinfo.isShownInDetailsView());
        props.put("dimension", cinfo != null && cinfo.isDimension());
        props.put("measure", cinfo != null && cinfo.isMeasure());
        props.put("recommendedVariable", cinfo != null && cinfo.isRecommendedVariable());
        props.put("defaultScale", cinfo != null ? cinfo.getDefaultScale().name() : DefaultScaleType.LINEAR.name());
        props.put("phi", cinfo != null ? cinfo.getPHI().name() : PHIType.NotPHI.name());
        props.put("excludeFromShifting", cinfo != null && cinfo.isExcludeFromShifting());
        props.put("sortable", dc.isSortable());

        props.put("conceptURI", cinfo == null ? null : cinfo.getConceptURI());
        props.put("rangeURI", cinfo == null ? null : cinfo.getRangeURI());
        props.put("defaultValue", cinfo == null ? null : cinfo.getDefaultValue());

        ColumnInfo displayField = dc.getDisplayColumnInfo();
        if (displayField != null && displayField != cinfo)
        {
            props.put("displayField", displayField.getFieldKey().toString());
            props.put("displayFieldSqlType", displayField.getSqlTypeName());
            props.put("displayFieldJsonType", displayField.getRenderer().getJsonTypeName());
        }

        if (cinfo != null)
        {
            if (!cinfo.getImportAliasSet().isEmpty())
            {
                props.put("importAliases", new ArrayList<>(cinfo.getImportAliasSet()));
            }

            if (cinfo.getTsvFormatString() != null)
            {
                props.put("tsvFormat", cinfo.getTsvFormatString());
            }
            // DisplayColumn should have the correct format (respecting folder default and translating "DateTime", etc.)
            if (dc.getFormatString() != null)
            {
                String format = dc.getFormatString();
                props.put("format", format);
                if (includeDomainFormat)
                    props.put("formatString", format);
                String extFormat = null;
                String extFormatFn = null;
                if (cinfo.getJdbcType().isDateOrTime())
                {
                    extFormat = ExtUtil.toExtDateFormat(format);
                    extFormatFn = ExtUtil.toExtDateFormatFn(format);
                }
                else if (cinfo.getJdbcType().isNumeric())
                {
                    extFormat = ExtUtil.toExtNumberFormat(format);
                    extFormatFn = ExtUtil.toExtNumberFormatFn(format);
                }
                if (null != extFormatFn)
                    props.put("extFormatFn", extFormatFn);
                if (null != extFormat)
                    props.put("extFormat", extFormat);
            }
            if (cinfo.getExcelFormatString() != null)
            {
                props.put("excelFormat", cinfo.getExcelFormatString());
            }

            props.put("inputType", cinfo.getInputType());
            // UNDONE ext info for other field typesxtype: checkbox, combo, datefield, field, hidden, htmleditor, numberfield, radio, textarea, textfield, timefield
            //fmdata.put("xtype","");
            if ("textarea".equals(cinfo.getInputType()))
            {
                if (dc instanceof DataColumn)
                {
                    int cols = ((DataColumn)dc).getInputLength();
                    if (cols > 0)
                        props.put("cols", Math.min(1000,cols));
                    int rows = ((DataColumn)dc).getInputRows();
                    if (rows > 0)
                        props.put("rows", Math.min(1000,rows));
                }
                ext.put("xtype","textarea");
            }

            props.put("shortCaption", cinfo.getShortLabel());

            if (dc instanceof IMultiValuedDisplayColumn || (cinfo.getParentTable() != null && cinfo.getParentTable().getSqlDialect() != null && !cinfo.getParentTable().getSqlDialect().isSortableDataType(cinfo.getSqlTypeName())))
            {
                // Disallow faceted filtering when the column is multi-valued, as the value that comes out of the
                // database likely has a different delimiter compared to what the user wants to see and therefore
                // doesn't work very well.

                // Similarly, SQLServer doesn't allow doing a SELECT DISTINCT on TEXT columns, so check the data type (they also can't be sorted)
                props.put("facetingBehaviorType", FacetingBehaviorType.ALWAYS_OFF);
            }
            else if (cinfo.getFacetingBehaviorType() != null)
            {
                props.put("facetingBehaviorType", cinfo.getFacetingBehaviorType().name());
            }

            if (cinfo.getCrosstabColumnMember() != null)
            {
                CrosstabMember member = cinfo.getCrosstabColumnMember();
                Map<String, Object> jsonMember = new HashMap<>();
                jsonMember.put("value", String.valueOf(member.getValue()));
                jsonMember.put("caption", member.getCaption());
                jsonMember.put("dimensionFieldKey", new JSONArray(member.getDimensionFieldKey().getParts()));
                props.put("crosstabColumnMember", jsonMember);

                props.put("crosstabColumnDimension", cinfo.getCrosstabColumnDimension());
            }
        }
        else
        {
            props.put("shortCaption", dc.getCaption());
        }

        props.put("caption", dc.getCaption());
        if (includeDomainFormat)
            props.put("label", dc.getCaption());

        if (includeLookup && cinfo != null)
        {
            Map<String, Object> lookupJSON = getLookupInfo(cinfo, includeDomainFormat);
            if (lookupJSON != null)
            {
                props.put("lookup", lookupJSON);
            }
        }

        if (dc instanceof IMultiValuedDisplayColumn)
            props.put("multiValue", true);

        return props;
    }

    @Nullable
    public static JSONObject getLookupInfo(ColumnInfo columnInfo, boolean includeDomainFormat)
    {
        ForeignKey fk = columnInfo.getFk();

        //lookup info
        TableInfo lookupTable = columnInfo.getFkTableInfo();
        if (null != fk
                && null != lookupTable
                && (!(fk instanceof RowIdForeignKey) || !(((RowIdForeignKey)fk).getOriginalColumn().equals(columnInfo))))
        {
            JSONObject lookupInfo = new JSONObject();
            if (null != fk.getLookupContainer())
            {
                Container fkContainer = fk.getLookupContainer();
                if (null != fkContainer)
                {
                    lookupInfo.put("containerPath", fkContainer.getPath());
                    if (includeDomainFormat)
                        lookupInfo.put("container", fkContainer.getPath());
                }
            }

            boolean isPublic = lookupTable.isPublic() && null != lookupTable.getPublicName() && null != lookupTable.getPublicSchemaName();
            // Duplicate with alternate property name for backwards compatibility
            lookupInfo.put("isPublic", isPublic);
            lookupInfo.put("public", isPublic);
            String queryName;
            String schemaName;
            if (isPublic)
            {
                queryName = lookupTable.getPublicName();
                schemaName = lookupTable.getPublicSchemaName();
            }
            else
            {
                queryName = lookupTable.getName();
                schemaName = lookupTable.getSchema().getName();
            }

            // Duplicate info with different property names for backwards compatibility
            lookupInfo.put("queryName", queryName);
            lookupInfo.put("table", queryName);
            lookupInfo.put("schemaName", schemaName);
            lookupInfo.put("schema", schemaName);

            ColumnInfo displayColumn = fk.createLookupColumn(columnInfo, null);
            if (displayColumn != null && displayColumn.getFieldKey() != null && displayColumn.getFieldKey().getParts().size() > columnInfo.getFieldKey().getParts().size())
            {
                lookupInfo.put("displayColumn", displayColumn.getFieldKey().getName());
            }
            else
            {
                // In this case, we likely won't be able to resolve the column when executing the query, but
                // it's the best guess that we have
                lookupInfo.put("displayColumn", lookupTable.getTitleColumn());
            }
            String key = null;
            List<String> pks = lookupTable.getPkColumnNames();

            //Issue 20092: the target column specified by the FK does not necessarily need to be a true PK
            if (fk.getLookupColumnName() != null)
            {
                //NOTE: the XML could specify a column with different casing than the canonical name.  this could be problematic for client side JS.  \
                ColumnInfo targetCol = lookupTable.getColumn(fk.getLookupColumnName());
                if (targetCol != null)
                    key = targetCol.getName();
            }

            if (key == null)
            {
                if (null != pks && pks.size() > 0)
                    key = pks.get(0);
                if (null != pks && pks.size() == 2 && ("container".equalsIgnoreCase(key) || "containerid".equalsIgnoreCase(key)))
                    key = pks.get(1);
            }
            lookupInfo.put("keyColumn", key);

            if (fk instanceof MultiValuedForeignKey)
            {
                MultiValuedForeignKey mvfk = (MultiValuedForeignKey)fk;
                String junctionLookup = mvfk.getJunctionLookup();
                lookupInfo.put("multiValued", junctionLookup != null ? "junction" : "value");
                if (junctionLookup != null)
                    lookupInfo.put("junctionLookup", junctionLookup);
            }

            return lookupInfo;
        }

        return null;
    }

    public static Map<String,Object> getColModel(DisplayColumn dc) throws Exception
    {
        Map<String,Object> extGridColumn = new HashMap<>();
        ColumnInfo colInfo = dc.getColumnInfo();

        // see  Ext.grid.ColumnModel Ext.grid.Column
        extGridColumn.put("dataIndex", dc.getName());
        extGridColumn.put("sortable", dc.isSortable());
        extGridColumn.put("editable", dc.isEditable());
        extGridColumn.put("hidden", colInfo != null && (colInfo.isHidden() || colInfo.isAutoIncrement())); //auto-incr list key columns return false for isHidden(), so check isAutoIncrement as well
        if (dc.getTextAlign() != null)
            extGridColumn.put("align", dc.getTextAlign());
        if (dc.getDescription() != null)
            extGridColumn.put("tooltip", dc.getDescription());
        if (dc.getCaption() != null)
            extGridColumn.put("header", dc.getCaption());
        if (dc.getWidth() != null)
        {
            try
            {
                //try to parse as integer (which is what Ext wants)
                extGridColumn.put("width", Integer.parseInt(dc.getWidth()));
            }
            catch(NumberFormatException e)
            {
                //include it as a string
                extGridColumn.put("width", dc.getWidth());
            }
        }

        // TODO ext grids doesn't understand missing values, so treat required as !nullable
        extGridColumn.put("required", colInfo != null && (!colInfo.isNullable() || colInfo.isRequired()));
        if (colInfo != null && dc.isEditable() && null != colInfo.getDefaultValue())
            extGridColumn.put("defaultValue", colInfo.getDefaultValue());
        if (colInfo != null)
        {
            extGridColumn.put("scale", colInfo.getScale());
        }
        return extGridColumn;
    }
}
