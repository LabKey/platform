/*
 * Copyright (c) 2011-2018 LabKey Corporation
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

import org.apache.commons.beanutils.BeanUtils;
import org.json.JSONObject;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reports.report.ChartQueryReport;
import org.labkey.api.reports.report.ChartReport;
import org.labkey.api.reports.report.ChartReportDescriptor;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.view.JspView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.template.ClientDependency;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * User: brittp
 * Date: Feb 3, 2011 11:15:10 AM
 */
public class VisualizationReportDescriptor extends ReportDescriptor
{
    public static final String VIEW_CLASS = "/org/labkey/visualization/views/chartWizard.jsp";

    public String getJSON()
    {
        return getProperty(Prop.json);
    }

    public void setJSON(String json)
    {
        setProperty(Prop.json, json);
    }

    public Map<String, Object> getReportProps()
    {
        return null;
    }

    @Override
    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> d = super.getClientDependencies();
        JspView v = new JspView(getViewClass());
        d.addAll(v.getClientDependencies());
        return d;
    }

    public static VisualizationReportDescriptor getConvertedChartViewDescriptor(ChartReport report, String newDescriptorType, String newReportType) throws IOException
    {
        String xml = report.getDescriptor().serialize(ContainerManager.getForId(report.getContainerId()));
        xml = xml.replace("descriptorType=\"chartDescriptor\"", "descriptorType=\"" + newDescriptorType + "\"");
        xml = xml.replace("<Prop name=\"descriptorType\">chartDescriptor</Prop>", "<Prop name=\"descriptorType\">" + newDescriptorType + "</Prop>");
        xml = xml.replace("<Prop name=\"reportType\">" + ChartQueryReport.TYPE + "</Prop>", "<Prop name=\"reportType\">" + newReportType + "</Prop>");
        xml = xml.replace("<Prop name=\"reportType\">Study.chartQueryReport</Prop>", "<Prop name=\"reportType\">" + newReportType + "</Prop>");
        xml = xml.replace("<Prop name=\"reportType\">Study.datasetChart</Prop>", "<Prop name=\"reportType\">" + newReportType + "</Prop>");
        xml = xml.replace("<Prop name=\"reportType\">Study.chartReport</Prop>", "<Prop name=\"reportType\">" + newReportType + "</Prop>");
        ReportDescriptor descriptor = ReportDescriptor.createFromXML(xml);

        if (descriptor != null)
        {
            try
            {
                BeanUtils.copyProperties(descriptor, report.getDescriptor());
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }

            descriptor.setDescriptorType(newDescriptorType);
            descriptor.setReportType(newReportType);
            descriptor.initProperties();
        }
        if (!(descriptor instanceof VisualizationReportDescriptor))
            return null;
        return (VisualizationReportDescriptor) descriptor;
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

    public String[] getColumnYName()
    {
        final Object colY = _props.get(ChartReportDescriptor.Prop.columnYName.toString());
        if (colY instanceof List)
            return ((List<String>)colY).toArray(new String[0]);
        else if (colY instanceof String)
            return new String[]{(String)colY};

        return new String[0];
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

}
