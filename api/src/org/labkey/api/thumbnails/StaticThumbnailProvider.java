package org.labkey.api.thumbnails;

/**
 * User: adam
 * Date: 10/7/11
 * Time: 10:55 PM
 */

// A static thumbnail provider
public interface StaticThumbnailProvider
{
    Thumbnail getStaticThumbnail();
    String getStaticThumbnailCacheKey();
}
