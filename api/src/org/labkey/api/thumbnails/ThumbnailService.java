package org.labkey.api.thumbnails;

import org.labkey.api.data.CacheableWriter;

import java.io.IOException;

/**
 * User: adam
 * Date: 10/8/11
 * Time: 7:17 AM
 */
public interface ThumbnailService
{
    public static final String THUMBNAIL_FILENAME = "Thumbnail";

    CacheableWriter getThumbnailWriter(StaticThumbnailProvider provider);
    void queueThumbnailRendering(DynamicThumbnailProvider provider);
}
