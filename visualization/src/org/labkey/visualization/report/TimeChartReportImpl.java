package org.labkey.visualization.report;

import org.apache.batik.transcoder.TranscoderException;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.SimpleAction;
import org.labkey.api.thumbnail.DynamicThumbnailProvider;
import org.labkey.api.thumbnail.Thumbnail;
import org.labkey.api.thumbnail.ThumbnailOutputStream;
import org.labkey.api.view.HBox;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.labkey.api.visualization.TimeChartReport;
import org.labkey.visualization.VisualizationController;
import org.labkey.visualization.VisualizationModule;
import org.labkey.visualization.VisualizationUtil;

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
        Module vizModule = ModuleLoader.getInstance().getModule(VisualizationModule.class);
        WebPartView timeChartWizard = SimpleAction.getModuleHtmlView(vizModule, VisualizationController.VisualizationUrlsImpl.TIME_CHART_VIEW_NAME);
        timeChartWizard.setFrame(WebPartView.FrameType.NONE);
        return new HBox(timeChartWizard);
    }

    @Override
    public Thumbnail getStaticThumbnail()
    {
        InputStream is = TimeChartReportImpl.class.getResourceAsStream("timechart_icon.gif");
        return new Thumbnail(is, "image/gif");
    }

    @Override
    public String getStaticThumbnailCacheKey()
    {
        return "Reports:TimeChartStatic";
    }

    @Override
    public Thumbnail generateDynamicThumbnail(ViewContext context)
    {
        // SVG is provided by the client code at save time and then stashed in the report by the save action. That's
        // the only way thumbnails can be generated from these reports.
        if (null != _svg)
        {
            try
            {
                ThumbnailOutputStream os = new ThumbnailOutputStream();
                VisualizationUtil.svgToPng(_svg, os, 256.0f);

                return os.getThumbnail("image/png");
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
