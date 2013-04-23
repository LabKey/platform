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
import org.labkey.api.query.QueryChangeListener;
import org.labkey.api.reports.model.ReportPropsManager;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.Pair;

import java.util.Collection;
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

    @Override
    public boolean updateQueryNameReferences(Collection<QueryChangeListener.QueryPropertyChange> changes)
    {
        if (getJSON() != null)
        {
            // TimeChart JSON config usages of queryName in 13.1:
            // subject.queryName
            // measures[].dateOptions.dateCol.queryName
            // measures[].dateOptions.zeroDateCol.queryName
            // measures[].dimension.queryName
            // measures[].measure.queryName
            JSONObject json = new JSONObject(getJSON());

            boolean hasUpdates = updateJSONObjectQueryNameReference(json.getJSONObject("subject"), "queryName", changes);

            JSONArray measures = json.getJSONArray("measures");
            for(int i = 0; i < measures.length(); i++)
            {
                // update dateOptions queryNames for dateCol and zeroDateCol
                boolean dateColUpdates = false;
                boolean zeroDateColUpdates = false;
                boolean dimensionUpdates = false;
                if (measures.getJSONObject(i).has("dateOptions"))
                {
                    JSONObject dateOptions = measures.getJSONObject(i).getJSONObject("dateOptions");
                    if (dateOptions.has("dateCol"))
                    {
                        dateColUpdates = updateJSONObjectQueryNameReference(dateOptions.getJSONObject("dateCol"), "queryName", changes);
                    }
                    if (dateOptions.has("zeroDateCol"))
                    {
                        zeroDateColUpdates = updateJSONObjectQueryNameReference(dateOptions.getJSONObject("zeroDateCol"), "queryName", changes);
                    }
                    // update dimension queryName
                    if (measures.getJSONObject(i).has("dimension"))
                    {
                        dimensionUpdates = updateJSONObjectQueryNameReference(measures.getJSONObject(i).getJSONObject("dimension"), "queryName", changes);
                    }
                }

                // update measure queryName
                JSONObject measureJson = measures.getJSONObject(i).getJSONObject("measure");
                String origQueryName = measureJson.getString("queryName");
                boolean measureUpdates = updateJSONObjectQueryNameReference(measureJson, "queryName", changes);
                // special case for measure queryname:
                // reset the measure alias based on the schemaName_queryName_measureName and add a queryLabel
                if (measureUpdates)
                {
                    String schema = measureJson.getString("schemaName");
                    String query = measureJson.getString("queryName");
                    String name = measureJson.getString("name");
                    if (schema != null && query != null && name != null)
                        measureJson.put("alias", schema + "_" + query + "_" + name);

                    for (QueryChangeListener.QueryPropertyChange qpc : changes)
                    {
                        if (query.equals(qpc.getNewValue()))
                        {
                            measureJson.put("queryLabel", ReportUtil.getQueryLabelByName(qpc.getSource().getSchema(), query));
                            break;
                        }
                    }
                }

                hasUpdates = hasUpdates || dateColUpdates || zeroDateColUpdates || dimensionUpdates || measureUpdates;
            }

            if (hasUpdates)
            {
                setJSON(json.toString());
                return true;
            }
        }

        return false;
    }

    private boolean updateJSONObjectQueryNameReference(JSONObject json, String propName, Collection<QueryChangeListener.QueryPropertyChange> changes)
    {
        String queryName = json.getString(propName);
        if (queryName != null)
        {
            for (QueryChangeListener.QueryPropertyChange qpc : changes)
            {
                if (queryName.equals(qpc.getOldValue()))
                {
                    json.put(propName, qpc.getNewValue());
                    return true;
                }
            }
        }

        return false;
    }

    public String getViewClass()
    {
        return "/org/labkey/visualization/views/timeChartWizard.jsp";
    }
}
