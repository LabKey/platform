/*
 * Copyright (c) 2011-2017 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryChangeListener;
import org.labkey.api.reports.model.ReportPropsManager;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;

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
        Map<String, Object> props = new HashMap<>();
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
            convertPTIDSinGroups(jsonObj, alternateIdMap);
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
                        else
                        {
                            transformedPTIDs.put(values.get(j));
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

    private void convertPTIDSinGroups(JSONObject json, Map<String, String> alternateIdMap)
    {
        JSONObject subjectJSON = json.getJSONObject("subject");
        if (subjectJSON.has("groups"))
        {
            JSONArray groupsJSON = subjectJSON.getJSONArray("groups");
            for (int i = 0; i < groupsJSON.length(); i++)
            {
                JSONObject groupJSON = groupsJSON.getJSONObject(i);
                if (groupJSON.has("participantIds"))
                {
                    JSONArray ptidsFromGroupJSON = groupJSON.getJSONArray("participantIds");
                    JSONArray transformedPTIDs = new JSONArray();

                    for (int j = 0; j < ptidsFromGroupJSON.length(); j++)
                    {
                        if (alternateIdMap.containsKey(ptidsFromGroupJSON.getString(j)))
                            transformedPTIDs.put(alternateIdMap.get(ptidsFromGroupJSON.getString(j)));
                    }

                    groupJSON.put("participantIds", transformedPTIDs);
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
            // filterQuery (as schema.query key, i.e. study.DEM-1)
            // filterUrl (as filter parameter fieldKeys)

            // most property updates only care about the query name old value string and new value string
            Map<String, String> queryNameChangeMap = new HashMap<>();
            for (QueryChangeListener.QueryPropertyChange qpc : changes)
            {
                queryNameChangeMap.put((String)qpc.getOldValue(), (String)qpc.getNewValue());
            }

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
                }

                // update dimension queryName
                if (measures.getJSONObject(i).has("dimension") && !measures.getJSONObject(i).isNull("dimension"))
                {
                    dimensionUpdates = updateJSONObjectQueryNameReference(measures.getJSONObject(i).getJSONObject("dimension"), "queryName", changes);
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

            // update filterQuery (should be schema.query)
            if (null != json.getString("filterQuery"))
            {
                String[] keyParts = json.getString("filterQuery").split("\\.");
                if (keyParts.length == 2 && queryNameChangeMap.containsKey(keyParts[1]))
                {
                    String queryKey = keyParts[0] + "." + queryNameChangeMap.get(keyParts[1]);
                    json.put("filterQuery", queryKey);
                    hasUpdates = true;
                }
            }

            // update filterUrl (parameter fieldKeys)
            if (null != json.getString("filterUrl"))
            {
                boolean filterUrlChanges = false;
                ActionURL filterUrl = new ActionURL(json.getString("filterUrl"));
                for (String filterKey : filterUrl.getParameterMap().keySet())
                {
                    String value = filterUrl.getParameter(filterKey);
                    String[] parts = StringUtils.splitPreserveAllTokens(filterKey, '~');
                    if (parts.length == 2)
                    {
                        String dataRegionName = parts[0].substring(0, filterKey.indexOf("."));
                        FieldKey fieldKey = FieldKey.fromString(parts[0].substring(dataRegionName.length()+1));
                        String op = parts[1];

                        FieldKey filterTable = fieldKey.getTable();
                        if (filterTable != null && filterTable.getName() != null && queryNameChangeMap.containsKey(filterTable.getName()))
                        {
                            fieldKey = new FieldKey(new FieldKey(filterTable.getParent(), queryNameChangeMap.get(filterTable.getName())), fieldKey.getName());
                            filterUrl.deleteParameter(filterKey);
                            filterUrl.addParameter(dataRegionName + "." + fieldKey.toString() + "~" + op, value);
                            filterUrlChanges = true;
                        }
                    }
                }

                if (filterUrlChanges)
                {
                    json.put("filterUrl", filterUrl.toString());
                    hasUpdates = true;
                }
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
        return "/org/labkey/visualization/views/chartWizard.jsp";
    }
}
