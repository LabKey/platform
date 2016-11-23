/*
 * Copyright (c) 2011-2016 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.thumbnail.Thumbnail;
import org.labkey.api.util.ThumbnailUtil;
import org.labkey.api.view.HBox;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.labkey.api.visualization.SvgThumbnailGenerator;
import org.labkey.api.visualization.TimeChartReport;
import org.labkey.api.visualization.TimeChartReportDescriptor;
import org.labkey.api.writer.ContainerUser;
import org.labkey.visualization.VisualizationController;

/*
 * User: brittp
 * Date: Feb 7, 2011 11:23:05 AM
 */
public class TimeChartReportImpl extends TimeChartReport implements SvgThumbnailGenerator
{
    private String _svg = null;

    @Override
    public HttpView renderReport(ViewContext context) throws Exception
    {
        VisualizationController.ChartWizardReportForm form = new VisualizationController.ChartWizardReportForm();
        form.setAllowToggleMode(true);
        form.setReportId(getReportId());
        form.setComponentId("chart-wizard-report-" + getReportId().toString());

        Report report = form.getReportId().getReport(context);
        if (null != report)
        {
            TimeChartReportDescriptor descriptor = (TimeChartReportDescriptor) report.getDescriptor();

            JspView timeChartWizard = new JspView<>(descriptor.getViewClass(), form);

            timeChartWizard.setFrame(WebPartView.FrameType.NONE);
            return new HBox(timeChartWizard);
        }

        return new HBox(new HtmlView("Failed to find report."));
    }

    @Override
    public String getStaticThumbnailPath()
    {
        return "visualization/images/timechart.png";
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

    public void setSvg(String svg)
    {
        _svg = svg;
    }

    @Override
    public boolean hasContentModified(ContainerUser context)
    {
        // TODO: need to trim the JSON content to just relevant properties (i.e. don't need group IDs or categoryIDs which are currently also include in export/import)

        // Content modified if change to the JSON config property
        return hasDescriptorPropertyChanged(ReportDescriptor.Prop.json.name());
    }
}
