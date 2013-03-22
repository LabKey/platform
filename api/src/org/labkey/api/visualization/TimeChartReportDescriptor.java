/*
 * Copyright (c) 2011-2013 LabKey Corporation
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

package org.labkey.api.visualization;

import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.reports.model.ReportPropsManager;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.Pair;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
* User: brittp
* Date: Jan 28, 2011
* Time: 4:49 PM
*/
public class TimeChartReportDescriptor extends VisualizationReportDescriptor
{
    public static final String TYPE = "timeChartReportDescriptor";

    public TimeChartReportDescriptor()
    {
        setDescriptorType(TYPE);
    }

    public Map<String, Object> getReportProps() throws Exception
    {
        Map<String, Object> props = new HashMap<String, Object>();
        List<Pair<DomainProperty, Object>> propsList = ReportPropsManager.get().getProperties(getEntityId(), getResourceContainer());
        if (propsList.size() > 0)
        {
            for (Pair<DomainProperty, Object> pair : propsList)
                props.put(pair.getKey().getName(), pair.getValue());

            return props;
        }
        else
            return null;
    }

    @Override
    protected String adjustPropertyValue(@Nullable ImportContext context, String key, Object value)
    {
        if (null != context && context.isAlternateIds() && "json".equals(key))
        {
            Map<String, String> alternateIdMap = StudyService.get().getAlternateIdMap(context.getContainer()); // Gotta call into study land to translate the IDs
            JSONObject jsonObj = new JSONObject((String) value);
            convertSubjectPTIDs(jsonObj, alternateIdMap);
            convertPTIDSInDimensions(jsonObj, alternateIdMap);
            value  = jsonObj.toString();
        }

        return super.adjustPropertyValue(context, key, value);
    }

    private void convertSubjectPTIDs(JSONObject json, Map<String, String> alternateIdMap)
    {
        JSONArray participantsFromJson = json.getJSONObject("subject").getJSONArray("values");
        JSONArray transformedPTIDs = new JSONArray();

            for (int i = 0; i < participantsFromJson.length(); i++)
            {
                String altId = alternateIdMap.get(participantsFromJson.get(i));
                if(altId != null)
                    transformedPTIDs.put(altId);
            }

            json.getJSONObject("subject").put("values", transformedPTIDs);
    }

    private void convertPTIDSInDimensions(JSONObject json, Map<String, String> alternateIdMap)
    {
        JSONArray measures = json.getJSONArray("measures");

        for(int i = 0; i < measures.length(); i++)
        {
            try
            {
                JSONObject dimension = measures.getJSONObject(i).getJSONObject("dimension");
                JSONArray values = dimension.getJSONArray("values");
                JSONArray transformedPTIDs = new JSONArray();

                if(values != null)
                {
                    for(int j = 0; j < values.length(); j++)
                    {
                        if(alternateIdMap.containsKey(values.get(j)))
                        {
                            transformedPTIDs.put(alternateIdMap.get(values.get(j)));
                        }
                    }

                    dimension.put("values", transformedPTIDs);
                }
            }
            catch(JSONException e)
            {
                if(e.getMessage().contains("is not a JSONArray"))
                {
                    // no-op, values is not always defined so this is an acceptable failure.
                }
                else
                {
                    throw e;
                }
            }
        }
    }

    public String getViewClass()
    {
        return "/org/labkey/visualization/views/timeChartWizard.jsp";
    }
}
