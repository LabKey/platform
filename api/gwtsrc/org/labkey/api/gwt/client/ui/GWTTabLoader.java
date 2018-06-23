/*
 * Copyright (c) 2008-2015 LabKey Corporation
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
package org.labkey.api.gwt.client.ui;

import com.google.gwt.user.client.Window;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;

import java.util.*;

/**
 * User: jgarms
 * Date: Oct 27, 2008
 */
public class GWTTabLoader
{
    private final String tsv;

    public GWTTabLoader(String tsv)
    {
        this.tsv = tsv;
    }

    public boolean processTsv(PropertiesEditor propertiesEditor)
    {
        List<Map<String, String>> data = getData();

        if (data.size() == 0)
        {
            Window.alert("Unable to parse the tab-delimited text");
            return false;
        }

        List<GWTPropertyDescriptor> properties = new ArrayList<GWTPropertyDescriptor>();

        // Check for fields that are considered mandatory and don't let the user drop/redefine them
        Set<String> mandatoryFields = new HashSet<String>();
        for (int i = 0; i < propertiesEditor.getPropertyCount(); i++)
        {
            GWTPropertyDescriptor existingPD = propertiesEditor.getPropertyDescriptor(i);
            if (propertiesEditor.getDomain().isMandatoryField(existingPD))
            {
                // We don't have a case insensitive set for GWT, so manually lower case it
                mandatoryFields.add(existingPD.getName().toLowerCase());
                // Don't let the user drop a mandatory field
                properties.add(existingPD);
            }
        }

        // Insert the new properties
        int rowNumber = 1;
        for (Map<String, String> row : data)
        {
            // We can't have spaces in property names
            String name = row.get("property");
            if (name == null)
            {
                Window.alert("Required 'Property' value was not found for row " + rowNumber);
                return false;
            }
            // We don't have a case insensitive set for GWT, so manually lower case it
            if (mandatoryFields.contains(name.toLowerCase()))
            {
                Window.alert("Cannot redefine mandatory field '" + name + "'");
                return false;
            }
            String label = row.get("label");
            if (!PropertiesEditorUtil.isLegalName(name))
            {
                if (label == null)
                    label = name;
                name = PropertiesEditorUtil.sanitizeName(name);
            }

            GWTPropertyDescriptor prop = new GWTPropertyDescriptor();
            prop.setName(name);
            prop.setLabel(label);
            prop.setDescription(row.get("description"));
            prop.setRequired(isRequired(row));
            prop.setRangeURI(getRangeURI(row));
            prop.setFormat(row.get("format"));
            prop.setMvEnabled(isMvEnabled(row));
            prop.setHidden(isHidden(row));
            prop.setLookupSchema(row.get("lookupschema"));
            prop.setLookupQuery(row.get("lookupquery"));
            properties.add(prop);
            rowNumber++;
        }
        propertiesEditor.setPropertyDescriptors(properties);

        return true;
    }

    private boolean isRequired(Map<String, String> map)
    {
        String reqString = map.get("notnull");
        return reqString != null && reqString.equalsIgnoreCase("TRUE");
    }

    private boolean isMvEnabled(Map<String, String> map)
    {
        String mvString = map.get("mvenabled");
        return mvString != null && mvString.equalsIgnoreCase("TRUE");
    }

    private boolean isHidden(Map<String, String> map)
    {
        String hiddenString = map.get("hidden");
        return hiddenString != null && hiddenString.equalsIgnoreCase("TRUE");
    }

    private String getRangeURI(Map<String, String> map)
    {
        String rangeString = map.get("rangeuri");
        if (rangeString != null)
        {
            PropertyType t = PropertyType.fromName(rangeString);
            if (t != null)
                return t.toString();
        }

        // Default to string
        return PropertyType.xsdString.toString();
    }

    private List<Map<String,String>> getData()
    {
        List rows = getRows();

        if (rows.size() < 2)
        {
            return Collections.emptyList();
        }

        // First row is keys -- lowercase for easier access
        String[] keys = ((String)rows.get(0)).split("\t");
        for (int i=0;i<keys.length;i++)
        {
            keys[i] = keys[i].trim().toLowerCase();
        }

        List<Map<String,String>> data = new ArrayList<Map<String,String>>(rows.size() - 1);

        // Iterate starting at 1, since our keys were the first row
        for (int i=1;i<rows.size(); i++)
        {
            String row = (String)rows.get(i);
            String[] rowData = row.split("\t");
            Map<String,String> map = new HashMap<String,String>();
            for (int j=0;j<keys.length;j++)
            {
                String fieldData = "";
                if (j < rowData.length)
                    fieldData = rowData[j];
                map.put(keys[j], fieldData);
            }

            data.add(map);
        }
        return data;

    }

    private List<String> getRows()
    {
        List<String> rows = new ArrayList<String>();
        StringBuffer sb = new StringBuffer();

        for (int index = 0; index < tsv.length(); index++)
        {
            char c = tsv.charAt(index);
            if (c == '\n' || c == '\r')
            {
                if (sb.length() == 0)
                {
                    // Handle windows \r\n
                    continue;
                }
                rows.add(sb.toString());
                sb.setLength(0);
            }
            else
            {
                sb.append(c);
            }
        }
        // Handle last row which may not be properly terminated
        if (sb.length() > 0)
            rows.add(sb.toString());
        return rows;
    }
}
