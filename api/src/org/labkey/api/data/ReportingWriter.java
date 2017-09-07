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

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.gwt.client.DefaultScaleType;
import org.labkey.api.gwt.client.FacetingBehaviorType;
import org.labkey.api.gwt.client.PHIType;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.RowIdForeignKey;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.util.ExtUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * User: tgaluhn
 * Created: May 22, 2013
 *
 * Modeled on JsonWriter
 */
public class ReportingWriter
{
    private static final Logger LOG = Logger.getLogger(ReportingWriter.class);

    public static Map<String, Object> getMetaData(DisplayColumn dc, boolean useFriendlyAsType, boolean includeLookup, boolean includeDomainFormat)
    {
        // TODO: Consider breaking this method up into logically grouped submethods. It's too darn long!

        @Nullable ColumnInfo cinfo = dc.getColumnInfo();
        Map<String, Object> props = new LinkedHashMap<>();

        // Some DisplayColumns aren't backed by a ColumnInfo, so handle null when determining metadata
        FieldKey fieldKey;
        if (cinfo != null && null != cinfo.getFieldKey())
        {
            fieldKey = cinfo.getFieldKey();
        }
        else
        {
            String name = cinfo == null ? dc.getName() : cinfo.getName();
            fieldKey = new FieldKey(null, name);
        }
        props.put("fieldKey", fieldKey);

        if (cinfo != null && null != cinfo.getDescription())
            props.put("description", cinfo.getDescription());

        String friendlyTypeName = cinfo == null ? ColumnInfo.getFriendlyTypeName(dc.getDisplayValueClass()) : cinfo.getFriendlyTypeName();
        props.put("friendlyType", friendlyTypeName);
        props.put("type", useFriendlyAsType ? friendlyTypeName : dc.getJsonTypeName());
        props.put("jsonType", dc.getJsonTypeName());
        props.put("sqlType", cinfo == null ? null : cinfo.getSqlTypeName());
        props.put("defaultValue", cinfo == null ? null : cinfo.getDefaultValue());

        /////////////////////////////////////////////////////////////////////////////////////////////////////
        // Begin properties moved from columnModel section

        // see  Ext.grid.ColumnModel Ext.grid.Column
        if (cinfo != null)
        {
            props.put("scale", cinfo.getScale());
        }
        if (dc.getTextAlign() != null)
            props.put("align", dc.getTextAlign());
        props.put("sortable", dc.isSortable());
        if (dc.getWidth() != null)
        {
            try
            {
                //try to parse as integer (which is what Ext wants)
               props.put("width", Integer.parseInt(dc.getWidth()));
            }
            catch(NumberFormatException e)
            {
                //include it as a string
                props.put("width", dc.getWidth());
            }
        }

        /* TODO: The remainder of these former columnModel properties seem to duplicate keys in the field object,
            but the logic defining their values is slightly different. Which is correct?
         */
//        extGridColumn.put("editable", isEditable(dc));
//        extGridColumn.put("hidden", colInfo != null && (colInfo.isHidden() || colInfo.isAutoIncrement())); //auto-incr list key columns return false for isHidden(), so check isAutoIncrement as well
//        /** These are not part of Ext.Grid.Column, don't know why they are hear (MAB) */
//        extGridColumn.put("required", colInfo != null && !colInfo.isNullable());
//        if (colInfo != null && isEditable(dc) && null != colInfo.getDefaultValue())
//            extGridColumn.put("defaultValue", colInfo.getDefaultValue());

        // End propertiess moved from columnModel section
        /////////////////////////////////////////////////////////////////////////////////////////////////////

        if (includeDomainFormat)
        {
            PropertyType propertyType = PropertyType.getFromClass(dc.getValueClass());
            if (propertyType != null)
            {
                props.put("typeName", propertyType.getXmlName());
                props.put("typeURI", propertyType.getTypeUri());
            }
        }

        props.put("autoIncrement", cinfo != null && cinfo.isAutoIncrement());
        props.put("hidden", cinfo != null && cinfo.isHidden());
        props.put("keyField", cinfo != null && cinfo.isKeyField());
        props.put("mvEnabled", cinfo != null && cinfo.isMvEnabled());
        boolean nullable = cinfo != null && cinfo.isNullable();
        props.put("nullable", nullable);
        if (includeDomainFormat)
            props.put("required", !nullable);
        props.put("readOnly", cinfo != null && cinfo.isReadOnly());
        props.put("userEditable", cinfo != null && cinfo.isUserEditable());
        props.put("calculated", cinfo == null || cinfo.isCalculated());
        props.put("versionField", cinfo != null && cinfo.isVersionColumn());
        props.put("selectable", cinfo != null && !cinfo.isUnselectable());    //avoid double-negative boolean name

        props.put("shownInInsertView", cinfo != null && cinfo.isShownInInsertView());
        props.put("shownInUpdateView", cinfo != null && cinfo.isShownInUpdateView());
        props.put("shownInDetailsView", cinfo == null || cinfo.isShownInDetailsView());
        props.put("dimension", cinfo != null && cinfo.isDimension());
        props.put("measure", cinfo != null && cinfo.isMeasure());
        props.put("recommendedVariable", cinfo != null && cinfo.isRecommendedVariable());
        props.put("defaultScale", cinfo != null ? cinfo.getDefaultScale().name() : DefaultScaleType.LINEAR.name());

        props.put("phi", cinfo != null ? cinfo.getPHI().name() : PHIType.NotPHI.name());
        props.put("excludeFromShifting", cinfo != null && cinfo.isExcludeFromShifting());

        props.put("conceptURI", cinfo == null ? null : cinfo.getConceptURI());

        ColumnInfo displayField = dc.getDisplayColumnInfo();
        if (displayField != null && displayField != cinfo)
        {
            props.put("displayField", displayField.getFieldKey());
            props.put("displayFieldJsonType", displayField.getRenderer().getJsonTypeName());
        }

        if (cinfo != null)
        {

            if (!cinfo.getImportAliasSet().isEmpty())
            {
                props.put("importAliases", new ArrayList<>(cinfo.getImportAliasSet()));
            }

            if (dc.getTsvFormatString() != null)
            {
                props.put("tsvFormat", dc.getTsvFormatString());
            }
            if (dc.getFormatString() != null)
            {
                String format = dc.getFormatString();
                props.put("format", format);
                if (includeDomainFormat)
                    props.put("formatString", format);
                String extFormat = null;                // TODO: Are these extFormat properties needed?
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
            if (dc.getExcelFormatString() != null)
            {
                props.put("excelFormat", dc.getExcelFormatString());
            }

            String inputType = cinfo.getInputType();
            props.put("inputType", inputType);
            if ( ("textarea".equals(inputType) || "text".equals(inputType) ) && dc instanceof DataColumn)
            {
                int cols = ((DataColumn)dc).getInputLength();
                if (cols > 0)
                    props.put("cols", Math.min(1000,cols));
                if ("textarea".equals(inputType))
                {
                    int rows = ((DataColumn)dc).getInputRows();
                    if (rows > 0)
                        props.put("rows", Math.min(1000,rows));
                }
            }
            props.put("shortCaption", cinfo.getShortLabel());

            if (dc instanceof IMultiValuedDisplayColumn)
            {
                // Disallow faceted filtering when the column is multi-valued, as the value that comes out of the
                // database likely has a different delimiter compared to what the user wants to see and therefore
                // doesn't work very well. See issue
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
                jsonMember.put("dimensionFieldKey", member.getDimensionFieldKey());
                props.put("crosstabColumnMember", jsonMember);

                props.put("crosstabColumnDimension", cinfo.getCrosstabColumnDimension());
            }

            props.put("phi", cinfo.getPHI());
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
            Map<String, Object> lookup = getLookupInfoMap(cinfo, includeDomainFormat);
            if (lookup != null)
            {
                props.put("lookup", lookup);
            }
        }

        return props;
    }
    
    public static Map<String, Object> getLookupInfoMap(ColumnInfo columnInfo, boolean includeDomainFormat)
    {
        ForeignKey fk = columnInfo.getFk();

        //lookup info
        TableInfo lookupTable = columnInfo.getFkTableInfo();
        if (null != fk
                && null != lookupTable
                && (!(fk instanceof RowIdForeignKey) || !(((RowIdForeignKey)fk).getOriginalColumn().equals(columnInfo))))
        {
            Map<String, Object> lookupInfo = new HashMap<>();
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
            lookupInfo.put("public", isPublic);
            String queryName;
            SchemaKey schemaName;
            if (isPublic)
            {
                queryName = lookupTable.getPublicName();
                schemaName = SchemaKey.decode(lookupTable.getPublicSchemaName());
            }
            else
            {
                queryName = lookupTable.getName();
                UserSchema userSchema = lookupTable.getUserSchema();
                if (userSchema != null)
                {
                    schemaName = userSchema.getSchemaPath();
                }
                else
                {
                    schemaName = SchemaKey.decode(lookupTable.getSchema().getName());
                    TableInfo parentTable = columnInfo.getParentTable();
                    LOG.debug("userSchema for non-public lookup table " + queryName + " was null on column " + columnInfo.getName() + " in table " + parentTable.getPublicSchemaName() + "." + parentTable.getPublicName() + ". Using " + lookupTable.getSchema().getName());
                }
            }
            lookupInfo.put("queryName", queryName);
            lookupInfo.put("schemaName", schemaName);

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
            if (null != pks && pks.size() > 0)
                key = pks.get(0);
            if (null != pks && pks.size() == 2 && ("container".equalsIgnoreCase(key) || "containerid".equalsIgnoreCase(key)))
                key = pks.get(1);
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


}
