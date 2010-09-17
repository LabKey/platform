/**
 * Copyright (c) 2008-2010 LabKey Corporation
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * <p/>
 * User: brittp
 * Created: Feb 11, 2008 12:57:07 PM
 */

package org.labkey.api.data;

import org.json.JSONObject;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.RowIdForeignKey;

import java.util.*;

public class JsonWriter
{
    public static List<Map<String,Object>> getNativeColProps(TableInfo tinfo, FieldKey fieldKeyPrefix)
    {
        List<Map<String,Object>> colProps = new ArrayList<Map<String,Object>>();
        for (ColumnInfo cinfo : tinfo.getColumns())
        {
            colProps.add(JsonWriter.getMetaData(cinfo.getDisplayColumnFactory().createRenderer(cinfo), fieldKeyPrefix, true, true));
        }

        return colProps;
    }

    public static Map<String, Object> getMetaData(DisplayColumn dc, FieldKey fieldKeyPrefix, boolean useFriendlyAsType, boolean includeLookup)
    {
        ColumnInfo cinfo = dc.getColumnInfo();
        Map<String, Object> props = new LinkedHashMap<String, Object>();
        JSONObject ext = new JSONObject();
        props.put("ext",ext);

        // Some DisplayColumns aren't backed by a ColumnInfo, so handle null when determining metadata
        String name = cinfo == null ? dc.getName() : cinfo.getName();
        props.put("name", (null != fieldKeyPrefix ? FieldKey.fromString(fieldKeyPrefix, name) : name));
        if (cinfo != null && null != cinfo.getDescription())
            props.put("description", cinfo.getDescription());

        String friendlyTypeName = cinfo == null ? ColumnInfo.getFriendlyTypeName(dc.getDisplayValueClass()) : cinfo.getFriendlyTypeName();
        props.put("friendlyType", friendlyTypeName);
        props.put("type", useFriendlyAsType ? friendlyTypeName : dc.getJsonTypeName());
        props.put("jsonType", dc.getJsonTypeName());

        FieldKey fieldKey;
        if (cinfo != null && null != cinfo.getFieldKey())
            fieldKey = cinfo.getFieldKey();
        else
            fieldKey = new FieldKey(null, FieldKey.encodePart(name));

        props.put("fieldKey", fieldKey.toString());
        props.put("fieldKeyPath", FieldKey.fromParts(fieldKeyPrefix, fieldKey).toString());

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
        boolean readOnly = cinfo != null && cinfo.isReadOnly();
        props.put("isReadOnly", readOnly);
        props.put("readOnly", readOnly);
        boolean userEditable = cinfo != null && cinfo.isUserEditable();
        props.put("isUserEditable", userEditable);
        props.put("userEditable", userEditable);
        boolean versionColumn = cinfo != null && cinfo.isVersionColumn();
        props.put("isVersionField", versionColumn);
        props.put("versionField", versionColumn);
        boolean selectable = cinfo != null && !cinfo.isUnselectable();
        props.put("isSelectable", selectable); //avoid double-negative boolean name
        props.put("selectable", selectable); //avoid double-negative boolean name

        // These fields are new and don't need to have the "is" prefix for backwards compatibility
        props.put("shownInInsertView", cinfo != null && cinfo.isShownInInsertView());
        props.put("shownInUpdateView", cinfo != null && cinfo.isShownInUpdateView());
        props.put("shownInDetailsView", cinfo == null || cinfo.isShownInDetailsView());
        props.put("dimension", cinfo != null && cinfo.isDimension());
        props.put("measure", cinfo != null && cinfo.isMeasure());

        if (cinfo != null)
        {

            if (!cinfo.getImportAliasSet().isEmpty())
            {
                props.put("importAliases", new ArrayList<String>(cinfo.getImportAliasSet()));
            }

            if (cinfo.getTsvFormatString() != null)
            {
                props.put("tsvFormat", cinfo.getTsvFormatString());
            }
            if (cinfo.getFormat() != null)
            {
                props.put("format", cinfo.getTsvFormatString());
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
        }

        props.put("caption", dc.getCaption());

        if (includeLookup && cinfo != null)
        {
            Map<String, Object> lookupJSON = getLookupInfo(cinfo);
            if (lookupJSON != null)
            {
                props.put("lookup", lookupJSON);
            }
        }

        return props;
    }
    
    private static JSONObject getLookupInfo(ColumnInfo columnInfo)
    {
        ForeignKey fk = columnInfo.getFk();

        //lookup info
        if (null != fk
                && null != columnInfo.getFkTableInfo()
                && (!(fk instanceof RowIdForeignKey) || !(((RowIdForeignKey)fk).getOriginalColumn().equals(columnInfo))))
        {
            TableInfo lookupTable = columnInfo.getFkTableInfo();
            if(lookupTable != null)
            {
                JSONObject lookupInfo = new JSONObject();
                if (null != fk.getLookupContainerId())
                {
                    Container fkContainer = ContainerManager.getForId(fk.getLookupContainerId());
                    if (null != fkContainer)
                        lookupInfo.put("containerPath", fkContainer.getPath());
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

                lookupInfo.put("displayColumn", lookupTable.getTitleColumn());
                if (lookupTable.getPkColumns().size() > 0)
                    lookupInfo.put("keyColumn", lookupTable.getPkColumns().get(0).getName());

                return lookupInfo;
            }
        }

        return null;
    }
}
