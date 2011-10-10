package org.labkey.api.thumbnail;

import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.view.ViewContext;

/**
 * User: adam
 * Date: 10/8/11
 * Time: 6:44 AM
 */
public interface DynamicThumbnailProvider extends StaticThumbnailProvider, AttachmentParent
{
    Thumbnail generateDynamicThumbnail(ViewContext context);
    String getDynamicThumbnailCacheKey();
}
