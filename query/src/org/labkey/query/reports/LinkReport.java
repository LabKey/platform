package org.labkey.query.reports;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.thumbnail.DynamicThumbnailProvider;
import org.labkey.api.thumbnail.Thumbnail;
import org.labkey.api.util.ImageUtil;
import org.labkey.api.view.ViewContext;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/**
 * User: kevink
 * Date: 6/21/12
 */
public class LinkReport extends BaseRedirectReport implements DynamicThumbnailProvider
{
    public static final String TYPE = "ReportService.linkReport";

    public String getType()
    {
        return TYPE;
    }

    public String getTypeDescription()
    {
        return "Link Report";
    }

    @Override
    public Thumbnail getStaticThumbnail()
    {
        InputStream is = LinkReport.class.getResourceAsStream("web.png");
        return new Thumbnail(is, "image/png");
    }

    @Override
    public String getStaticThumbnailCacheKey()
    {
        return "Reports:LinkReportStatic";
    }

    @Override
    public Thumbnail generateDynamicThumbnail(@Nullable ViewContext context)
    {
        URL url = getURL();
        try
        {
            return ImageUtil.webThumbnail(url);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getDynamicThumbnailCacheKey()
    {
        return "Reports:" + getReportId();
    }

}
