/*
 * Copyright (c) 2011-2012 LabKey Corporation
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

import org.apache.batik.transcoder.TranscoderException;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.DocumentConversionService;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.thumbnail.DynamicThumbnailProvider;
import org.labkey.api.thumbnail.Thumbnail;
import org.labkey.api.thumbnail.ThumbnailOutputStream;
import org.labkey.api.view.HBox;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.labkey.api.visualization.TimeChartReport;
import org.labkey.visualization.VisualizationController;

import java.io.InputStream;

/*
 * User: brittp
 * Date: Feb 7, 2011 11:23:05 AM
 */
public class TimeChartReportImpl extends TimeChartReport implements DynamicThumbnailProvider
{
    private String _svg = null;

    @Override
    public HttpView renderReport(ViewContext context) throws Exception
    {
        VisualizationController.GetVisualizationForm form = new VisualizationController.GetVisualizationForm();

        form.setReportId(getReportId());
        JspView timeChartWizard = new JspView<VisualizationController.GetVisualizationForm>("/org/labkey/visualization/views/timeChartWizard.jsp", form);

        timeChartWizard.setFrame(WebPartView.FrameType.NONE);
        return new HBox(timeChartWizard);
    }

    @Override
    public Thumbnail getStaticThumbnail()
    {
        InputStream is = TimeChartReportImpl.class.getResourceAsStream("timechart.png");
        return new Thumbnail(is, "image/png");
    }

    @Override
    public String getStaticThumbnailCacheKey()
    {
        return "Reports:TimeChartStatic";
    }

    @Override
    public Thumbnail generateDynamicThumbnail(@Nullable ViewContext context)
    {
        // SVG is provided by the client code at save time and then stashed in the report by the save action. That's
        // the only way thumbnails can be generated from these reports.
        if (null != _svg)
        {
            try
            {
                DocumentConversionService svc = ServiceRegistry.get().getService(DocumentConversionService.class);

                if (null != svc)
                {
                    ThumbnailOutputStream os = new ThumbnailOutputStream();
                    svc.svgToPng(_svg, os, 256.0f);

                    return os.getThumbnail("image/png");
                }
            }
            catch (TranscoderException e)
            {
                throw new RuntimeException(e);
            }
        }

        return null;
    }

    @Override
    public String getDynamicThumbnailCacheKey()
    {
        return "Reports:" + getReportId();
    }

    public void setSvg(String svg)
    {
        _svg = svg;
    }
}
