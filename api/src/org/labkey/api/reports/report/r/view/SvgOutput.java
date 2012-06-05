package org.labkey.api.reports.report.r.view;

import org.apache.batik.transcoder.TranscoderException;
import org.apache.log4j.Logger;
import org.labkey.api.attachments.DocumentConversionService;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.thumbnail.Thumbnail;
import org.labkey.api.thumbnail.ThumbnailOutputStream;
import org.labkey.api.util.PageFlowUtil;

import java.io.IOException;

/**
 * User: adam
 * Date: 6/4/12
 * Time: 3:15 PM
 */
public class SvgOutput extends HtmlOutput
{
    public static final String ID = "svgout:";

    public SvgOutput()
    {
        super(ID);
    }

    @Override
    protected String getLabel()
    {
        return "SVG output";
    }

    @Override
    public Thumbnail renderThumbnail() throws IOException
    {
        DocumentConversionService svc = ServiceRegistry.get().getService(DocumentConversionService.class);

        if (null != svc)
        {
            ThumbnailOutputStream os = new ThumbnailOutputStream();

            try
            {
                svc.svgToPng(PageFlowUtil.getFileContentsAsString(getFile()), os, 256.0f);

                return os.getThumbnail("image/png");
            }
            catch (TranscoderException e)
            {
                Logger.getLogger(SvgOutput.class).error("Couldn't generate thumbnail", e);
            }
        }

        return null;
    }
}
