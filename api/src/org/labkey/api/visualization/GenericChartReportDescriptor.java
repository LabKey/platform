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

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.data.ColumnInfo;
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
import org.labkey.api.reports.report.ChartReportDescriptor;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ViewContext;
import org.labkey.api.writer.ContainerUser;

import java.util.ArrayList;
import java.util.Arrays;
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
        setDescriptorType(TYPE);
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

    public void updateChartViewJsonConfig(ContainerUser context) throws ValidationException
    {
        setJSON(getChartViewJSON(context));
    }

    private String getChartViewJSON(ContainerUser context) throws ValidationException
    {
        String schemaName = getProperty(ReportDescriptor.Prop.schemaName);
        String queryName = getProperty(ReportDescriptor.Prop.queryName);
        String viewName = getProperty(ReportDescriptor.Prop.viewName);
        String plotName = getReportName();
        boolean isLogX = BooleanUtils.toBoolean(getProperty(ChartReportDescriptor.Prop.isLogX));
        boolean isLogY = BooleanUtils.toBoolean(getProperty(ChartReportDescriptor.Prop.isLogY));
        String xColName = getProperty(ChartReportDescriptor.Prop.columnXName);
        String[] ys = getColumnYName();
        List<String> yColNames = Arrays.asList(ys);
        int width = NumberUtils.toInt(getProperty(ChartReportDescriptor.Prop.width), -1);
        int height = NumberUtils.toInt(getProperty(ChartReportDescriptor.Prop.height), -1);

        boolean isMultiYAxis = BooleanUtils.toBoolean(getProperty(ChartReportDescriptor.Prop.showMultipleYAxis));
        boolean showMultiPlot = BooleanUtils.toBoolean(getProperty(ChartReportDescriptor.Prop.showMultipleCharts));
        boolean showLines = BooleanUtils.toBoolean(getProperty(ChartReportDescriptor.Prop.showLines));
        //isVerticalOrientation not supported in new chart

        JSONObject measures = new JSONObject();

        // get the display columns for the table representing this report
        List<DisplayColumn> displayColumns = getDisplayColumns(context, schemaName, queryName, viewName);

        DisplayColumn xCol = null;
        List<DisplayColumn> yCols = new ArrayList<>();
        String caseInsensitiveXColName = xColName.toLowerCase();
        List<String> caseInsensitiveYColNames = new ArrayList<>();
        yColNames.forEach(y -> caseInsensitiveYColNames.add(y.toLowerCase()));
        Map<String, String> yColCaptionsMap = new HashMap<>();
        for (DisplayColumn col : displayColumns)
        {
            if (col.getColumnInfo() == null)
                continue;
            String colFieldKey = col.getColumnInfo().getJdbcRsName().toLowerCase();
            if (colFieldKey.equals(caseInsensitiveXColName))
                xCol = col;
            else if (caseInsensitiveYColNames.contains(colFieldKey))
            {
                yCols.add(col);
                yColCaptionsMap.put(colFieldKey, col.getCaption());
            }
        }

        if (xCol == null || yCols.size() == 0)
            throw new ValidationException("Unable to convert chart view");

        List<String> yColCaptions = new ArrayList<>();
        yColNames.forEach(y -> yColCaptions.add(yColCaptionsMap.getOrDefault(y.toLowerCase(), y)));

        JSONObject xColJson = getColumnInfoJson(xCol, schemaName, queryName);
        measures.put("x", xColJson);

        JSONArray yArray = new JSONArray();
        for (int i = 0; i < yCols.size(); i++)
        {
            DisplayColumn yCol = yCols.get(i);
            JSONObject yColJson = getColumnInfoJson(yCol, schemaName, queryName);
            String position = "left";
            if (i > 0 && (showMultiPlot || isMultiYAxis))
                position = "right";
            yColJson.put("yAxis", position);
            yArray.put(yColJson);
        }
        measures.put("y", yArray);

        JSONObject newChartConfig = new JSONObject();
        newChartConfig.put("measures", measures);

        JSONObject scales = new JSONObject();
        scales.put("x", new JSONObject().put("trans", isLogX ? "log" : "linear"));
        scales.put("y", new JSONObject().put("trans", isLogY ? "log" : "linear"));
        if (yCols.size() > 1 && (showMultiPlot || isMultiYAxis))
            scales.put("yRight", new JSONObject().put("trans", isLogY ? "log" : "linear"));
        newChartConfig.put("scales", scales);

        JSONObject geomOptions = new JSONObject();
        geomOptions.put("boxFillColor", "3366FF");
        geomOptions.put("lineColor", "000000");
        geomOptions.put("lineWidth", 1);
        geomOptions.put("opacity", 0.5);
        geomOptions.put("pointFillColor", "3366FF");
        geomOptions.put("pointSize", 5);
        geomOptions.put("chartLayout", showMultiPlot ? "per_measure" : "single");
        newChartConfig.put("geomOptions", geomOptions);

        JSONObject labels = new JSONObject();
        labels.put("x", xCol.getCaption());
        if (yColCaptions.size() > 1 && (showMultiPlot || isMultiYAxis))
        {
            labels.put("y", yColCaptions.get(0));
            labels.put("yRight", StringUtils.join(yColCaptions.subList(1, yColCaptions.size()), ", "));
        }
        else
            labels.put("y", StringUtils.join(yColCaptions, ", "));

        labels.put("main", plotName);
        newChartConfig.put("labels", labels);

        // need to set the renderType both on the JSON and property
        String renderType = showLines ? "line_plot" : "scatter_plot";
        newChartConfig.put("renderType", renderType);
        setProperty(Prop.renderType, renderType);

        // don't set an explicit width or height if one has either not been specified or if
        // it were the legacy default (640 x 200)
        if (width != -1 && width != 640)
            newChartConfig.put("width", width);
        if (height != -1 && height != 200)
            newChartConfig.put("height", height);

        JSONObject newJson = new JSONObject();
        newJson.put("chartConfig", newChartConfig);

        JSONObject newQueryConfig = new JSONObject();
        newQueryConfig.put("maxRows", -1);
        newQueryConfig.put("filterArray", new JSONArray());
        newQueryConfig.put("viewName", viewName);
        newQueryConfig.put("method", "POST");
        newQueryConfig.put("requiredVersion", 13.2);
        newQueryConfig.put("queryName", queryName);
        newQueryConfig.put("queryLabel", queryName);
        newQueryConfig.put("schemaName", schemaName);
        JSONArray colArray = new JSONArray();
        displayColumns.forEach(col -> {
            if (col.getColumnInfo() != null)
                colArray.put(col.getColumnInfo().getFieldKey());
        });
        newQueryConfig.put("columns", colArray);

        newJson.put("queryConfig", newQueryConfig);

        return newJson.toString();
    }

    public String[] getColumnYName()
    {
        final Object colY = _props.get(ChartReportDescriptor.Prop.columnYName.toString());
        if (colY instanceof List)
            return ((List<String>)colY).toArray(new String[0]);
        else if (colY instanceof String)
            return new String[]{(String)colY};

        return new String[0];
    }

    public JSONObject getColumnInfoJson(DisplayColumn displayColumn, String schemaName, String queryName)
    {
        JSONObject column = new JSONObject();
        column.put("schemaName", schemaName);
        column.put("queryName", queryName);
        column.put("queryLabel", queryName);

        ColumnInfo columnInfo = displayColumn.getColumnInfo();

        column.put("alias", columnInfo.getAlias());
        column.put("name", columnInfo.getName());
        column.put("fieldKey", columnInfo.getFieldKey());
        column.put("shortCaption", columnInfo.getShortLabel());
        column.put("label", columnInfo.getLabel());
        column.put("hidden", columnInfo.isHidden());
        column.put("measure", columnInfo.isMeasure());
        column.put("dimension", columnInfo.isDimension());
        column.put("type", displayColumn.getJsonTypeName());
        column.put("displayFieldJsonType", displayColumn.getDisplayJsonTypeName());
        column.put("normalizedType", displayColumn.getDisplayJsonTypeName());

        return column;
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
