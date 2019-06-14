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
package org.labkey.visualization.report;

import org.apache.commons.beanutils.BeanUtils;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.ChartQueryReport;
import org.labkey.api.reports.report.ChartReport;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.thumbnail.Thumbnail;
import org.labkey.api.util.ThumbnailUtil;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.labkey.api.visualization.GenericChartReport;
import org.labkey.api.visualization.GenericChartReportDescriptor;
import org.labkey.api.visualization.SvgThumbnailGenerator;
import org.labkey.api.writer.ContainerUser;
import org.labkey.visualization.VisualizationController;

import java.io.IOException;

/**
 * User: klum
 * Date: May 31, 2012
 */
public class GenericChartReportImpl extends GenericChartReport implements SvgThumbnailGenerator
{
    private String _svg = null;

    @Override
    public HttpView renderReport(ViewContext context)
    {
        VisualizationController.ChartWizardReportForm form = new VisualizationController.ChartWizardReportForm();
        form.setAllowToggleMode(true);
        form.setReportId(getReportId());
        form.setComponentId("chart-wizard-report-" + getReportId().toString());

        JspView view = new JspView<>(getDescriptor().getViewClass(), form);
        view.setFrame(WebPartView.FrameType.NONE);
        return view;
    }

    @Override
    public void setSvg(String svg)
    {
        _svg = svg;
    }

    @Override
    public Thumbnail generateThumbnail(@Nullable ViewContext context)
    {
        // SVG is provided by the client code at save time and then stashed in the report by the save action. That's
        // the only way thumbnails can be generated from these reports.
        try
        {
            _svg = VisualizationController.filterSVGSource(_svg);
            return ThumbnailUtil.getThumbnailFromSvg(_svg);
        }
        catch (NotFoundException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getStaticThumbnailPath()
    {
        RenderType type = getRenderType();
        String name = null != type ? type.getThumbnailName() : RenderType.BOX_PLOT.getThumbnailName();

        return "/visualization/images/" + name;
    }

    @Override
    public boolean hasContentModified(ContainerUser context)
    {
        // Content modified if change to the "chartConfig" part of the JSON property
        String newJson = getDescriptor().getProperty(ReportDescriptor.Prop.json);

        if (getReportId() != null)
        {
            Report origReport = ReportService.get().getReport(context.getContainer(), getReportId().getRowId());
            String origJson = origReport != null  ? origReport.getDescriptor().getProperty(ReportDescriptor.Prop.json) : null;

            if (origJson != null)
            {
                JSONObject origChartConfig = new JSONObject(origJson).getJSONObject("chartConfig");
                JSONObject newChartConfig = new JSONObject(newJson).getJSONObject("chartConfig");
                return newChartConfig != null && !newChartConfig.equals(origChartConfig);
            }
        }
        return false;
    }

    @Override
    public boolean isSandboxed()
    {
        return true;
    }

    @Override
    public void setChartViewDescriptor(ChartReport report, ContainerUser context) throws IOException, ValidationException
    {
        GenericChartReportDescriptor descriptor = getConvertedChartViewDescriptor(report, context);
        setDescriptor(descriptor);
    }

    private GenericChartReportDescriptor getConvertedChartViewDescriptor(ChartReport report, ContainerUser context) throws IOException, ValidationException
    {
        String newDescriptorType = GenericChartReportDescriptor.TYPE;
        String newReportType = GenericChartReport.TYPE;
        String xml = report.getDescriptor().serialize(ContainerManager.getForId(report.getContainerId()));
        xml = xml.replace("descriptorType=\"chartDescriptor\"", "descriptorType=\"" + newDescriptorType + "\"");
        xml = xml.replace("<Prop name=\"descriptorType\">chartDescriptor</Prop>", "<Prop name=\"descriptorType\">" + newDescriptorType + "</Prop>");
        xml = xml.replace("<Prop name=\"reportType\">" + ChartQueryReport.TYPE + "</Prop>", "<Prop name=\"reportType\">" + newReportType + "</Prop>");
        xml = xml.replace("<Prop name=\"reportType\">Study.chartQueryReport</Prop>", "<Prop name=\"reportType\">" + newReportType + "</Prop>");

        xml = xml.replace("<Prop name=\"reportType\">Study.datasetChart</Prop>", "<Prop name=\"reportType\">" + newReportType + "</Prop>");
        xml = xml.replace("<Prop name=\"reportType\">Study.chartReport</Prop>", "<Prop name=\"reportType\">" + newReportType + "</Prop>");
        xml = xml.replace("<Prop name=\"reportType\">Study.chartReportView</Prop>", "<Prop name=\"reportType\">" + newReportType + "</Prop>");

        xml = xml.replace("<Prop name=\"filterParam\">participantId</Prop>", "<Prop name=\"showInParticipantView\">true</Prop>");

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
        if (!(descriptor instanceof GenericChartReportDescriptor))
            return null;
        ((GenericChartReportDescriptor) descriptor).updateChartViewJsonConfig(context);
        return (GenericChartReportDescriptor) descriptor;
    }

}
