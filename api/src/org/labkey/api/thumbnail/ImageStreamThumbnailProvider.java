package org.labkey.api.thumbnail;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.ImageUtil;
import org.labkey.api.view.ViewContext;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.InputStream;

/**
 * User: adam
 * Date: 6/13/12
 * Time: 4:14 PM
 */
public class ImageStreamThumbnailProvider implements DynamicThumbnailProvider
{
    private final DynamicThumbnailProvider _provider;
    private final InputStream _is;

    // Generates a thumbnail from the image in the inputstream and associates it with the provider
    public ImageStreamThumbnailProvider(DynamicThumbnailProvider provider, InputStream is)
    {
        _provider = provider;
        _is = is;
    }

    @Override
    @Nullable
    public Thumbnail generateDynamicThumbnail(ViewContext context)
    {
        try
        {
            return ImageUtil.renderThumbnail(ImageIO.read(_is));
        }
        catch (IOException e)
        {
            ExceptionUtil.logExceptionToMothership(null, e);
            return null;
        }
    }

    @Override
    public String getDynamicThumbnailCacheKey()
    {
        return _provider.getDynamicThumbnailCacheKey();
    }

    @Override
    public Thumbnail getStaticThumbnail()
    {
        return _provider.getStaticThumbnail();
    }

    @Override
    public String getStaticThumbnailCacheKey()
    {
        return _provider.getStaticThumbnailCacheKey();
    }

    @Override
    public String getEntityId()
    {
        return _provider.getEntityId();
    }

    @Override
    public String getContainerId()
    {
        return _provider.getContainerId();
    }

    @Override
    public String getDownloadURL(ViewContext context, String name)
    {
        return _provider.getDownloadURL(context, name);
    }
}
