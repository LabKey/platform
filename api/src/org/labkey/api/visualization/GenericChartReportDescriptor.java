/*
 * Copyright (c) 2012-2019 LabKey Corporation
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

import org.json.old.JSONObject;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.QueryChangeListener;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reports.model.ReportPropsManager;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ViewContext;
import org.labkey.api.writer.ContainerUser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: klum
 * Date: May 11, 2012
 */
public class GenericChartReportDescriptor extends VisualizationReportDescriptor
{
    public static final String TYPE = "GenericChartReportDescriptor";

    public enum Prop implements ReportProperty
    {
        renderType,
    }
    
    public GenericChartReportDescriptor()
    {
        super(TYPE);
    }

    @Override
    public String getViewClass()
    {
        return VIEW_CLASS;
    }

    @Override
    public Map<String, Object> getReportProps()
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
    public boolean updateQueryNameReferences(Collection<QueryChangeListener.QueryPropertyChange> changes)
    {
        if (getJSON() != null)
        {
            // GenericChart JSON config usages of queryName in 13.1:
            // jsonData.queryConfig.queryName
            JSONObject json = new JSONObject(getJSON());
            JSONObject queryJson = json.getJSONObject("queryConfig");
            String queryName = queryJson.getString("queryName");
            if (queryName != null)
            {
                for (QueryChangeListener.QueryPropertyChange qpc : changes)
                {
                    if (queryName.equals(qpc.getOldValue()))
                    {
                        queryJson.put("queryName", qpc.getNewValue());
                        queryJson.put("queryLabel", ReportUtil.getQueryLabelByName(qpc.getSource().getSchema(), qpc.getSource().getName()));
                        setJSON(json.toString());
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public void updateSaveConfig()
    {
        if (getJSON() != null)
        {
            setJSON(updateJSON(getJSON()));
        }
    }

    private String updateJSON(String json)
    {
        JSONObject oldJson = new JSONObject(json);
        JSONObject newJson = new JSONObject();
        JSONObject oldChartConfig = oldJson.getJSONObject("chartConfig");

        if(oldChartConfig.get("geomOptions") != null)
        {
            return json;
        }

        JSONObject oldChartOptions = oldChartConfig.getJSONObject("chartOptions");
        JSONObject newChartConfig = new JSONObject();
        JSONObject measures = new JSONObject();
        JSONObject labels = new JSONObject();
        JSONObject scales = new JSONObject();
        JSONObject geomOptions = new JSONObject();

        // Copy over queryConfig, that does not need to change.
        newJson.put("queryConfig", oldJson.getJSONObject("queryConfig"));

        measures.put("x", oldChartConfig.get("xAxisMeasure"));
        measures.put("y", oldChartConfig.get("yAxisMeasure"));

        if (oldChartOptions.get("grouping") != null) {
            JSONObject grouping = oldChartOptions.getJSONObject("grouping");
            if (grouping.get("colorType") != null && grouping.get("colorType").equals("measure")) {
                measures.put("color", grouping.getJSONObject("colorMeasure"));
            }

            // Change naming of point to shape since that's how it's represented in the vis API.
            if (grouping.get("pointType") != null && grouping.get("pointType").equals("measure")) {
                measures.put("shape", grouping.getJSONObject("pointMeasure"));
            }
        }

        if (oldChartOptions.get("developer") != null && oldChartOptions.getJSONObject("developer").get("pointClickFn") != null) {
            measures.put("pointClickFn", oldChartOptions.getJSONObject("developer").get("pointClickFn"));
        }

        newChartConfig.put("measures", measures);

        labels.put("x", oldChartOptions.getJSONObject("xAxis").get("label"));
        labels.put("y", oldChartOptions.getJSONObject("yAxis").get("label"));
        labels.put("main", oldChartOptions.get("mainTitle"));
        newChartConfig.put("labels", labels);

        // Convert scaleType to trans since that's how it is represented in the vis API.
        scales.put("x", new JSONObject().put("trans", oldChartOptions.getJSONObject("xAxis").get("scaleType")));
        scales.put("y", new JSONObject().put("trans", oldChartOptions.getJSONObject("yAxis").get("scaleType")));
        newChartConfig.put("scales", scales);

        if (oldChartConfig.get("curveFit") != null) {
            newChartConfig.put("curveFit", oldChartConfig.get("curveFit"));
        }

        geomOptions.put("boxFillColor", oldChartOptions.get("fillColor"));
        geomOptions.put("lineColor", oldChartOptions.get("lineColor"));
        geomOptions.put("lineWidth", oldChartOptions.get("lineWidth"));
        geomOptions.put("opacity", oldChartOptions.get("opacity"));
        geomOptions.put("pointFillColor", oldChartOptions.get("pointColor"));
        geomOptions.put("pointSize", oldChartOptions.get("pointSize"));
        newChartConfig.put("geomOptions", geomOptions);

        newChartConfig.put("renderType", oldChartOptions.get("renderType"));
        newChartConfig.put("width", oldChartOptions.get("width"));
        newChartConfig.put("height", oldChartOptions.get("height"));

        newJson.put("chartConfig", newChartConfig);

        return newJson.toString();
    }

    public QueryView getSourceQueryView(ViewContext viewContext, String schemaName, String queryName, String viewName, String dataRegionName) throws ValidationException
    {
        UserSchema schema = QueryService.get().getUserSchema(viewContext.getUser(), viewContext.getContainer(), schemaName);
        if (schema == null)
        {
            throw new ValidationException("Invalid schema name: " + schemaName + ". ");
        }
        QuerySettings settings = schema.getSettings(viewContext, dataRegionName == null ? "query" : dataRegionName, queryName, viewName);
        QueryView queryView = schema.createView(viewContext, settings, null);
        if (queryView == null)
        {
            throw new ValidationException("Invalid query/view.");
        }

        return queryView;
    }

    private List<DisplayColumn> getDisplayColumns(ContainerUser context, String schemaName, String queryName, String viewName) throws ValidationException
    {
        UserSchema schema = QueryService.get().getUserSchema(context.getUser(), context.getContainer(), schemaName);
        if (schema == null)
        {
            throw new ValidationException("Invalid schema name: " + schemaName + ". ");
        }
        QueryDefinition queryDefinition = schema.getQueryDef(queryName);
        if (queryDefinition == null)
            queryDefinition = QueryService.get().createQueryDefForTable(schema, queryName);

        if (queryDefinition != null)
        {
            List<QueryException> errors = new ArrayList<>();
            TableInfo tableInfo = queryDefinition.getTable(schema, errors, true);
            if (tableInfo != null)
            {
                if (errors.isEmpty())
                {
                    CustomView customView = QueryService.get().getCustomView(context.getUser(), context.getContainer(), null, schemaName, queryName, viewName);
                    return queryDefinition.getDisplayColumns(customView, tableInfo);
                }
                else
                {
                    StringBuilder sb = new StringBuilder();
                    String delim = "";

                    for (QueryException error : errors)
                    {
                        sb.append(delim).append(error.getMessage());
                        delim = "\n";
                    }
                    throw new ValidationException("Unable to get table or query: " + sb.toString());
                }
            }
            else
                throw new ValidationException("Unable to create the source table: " + queryName + " it may no longer exist, or you don't have access to it.");
        }
        else
            throw new ValidationException("Unable to get a query definition for table : " + queryName);
    }
}
