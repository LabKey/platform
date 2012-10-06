package org.labkey.api.util;

import org.apache.batik.transcoder.TranscoderException;
import org.labkey.api.attachments.DocumentConversionService;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.thumbnail.Thumbnail;
import org.labkey.api.thumbnail.ThumbnailOutputStream;

/**
 * User: adam
 * Date: 10/4/12
 * Time: 5:34 AM
 */
public class ThumbnailUtil
{
    public static Thumbnail getThumbnailFromSvg(String svg)
    {
        if (null != svg)
        {
            try
            {
                DocumentConversionService svc = ServiceRegistry.get().getService(DocumentConversionService.class);

                if (null != svc)
                {
                    ThumbnailOutputStream os = new ThumbnailOutputStream();
                    svc.svgToPng(svg, os, ImageUtil.THUMBNAIL_HEIGHT);

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
}
