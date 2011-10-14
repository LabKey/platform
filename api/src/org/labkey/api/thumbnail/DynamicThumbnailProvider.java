package org.labkey.api.thumbnail;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.view.ViewContext;

/**
 * User: adam
 * Date: 10/8/11
 * Time: 6:44 AM
 */
public interface DynamicThumbnailProvider extends StaticThumbnailProvider, AttachmentParent
{
    // If null then service will fall back on the static thumbnail. Returning null is reasonable in a couple cases:
    // - Provider is not able to render a thumbnail for the content (e.g., image type not supported, encrypted PDF)
    // - Server simply can't render this content (e.g., timechart). Other mechanisms can be used to generate and
    //   save a thumbnail to the attachments service.
    @Nullable Thumbnail generateDynamicThumbnail(ViewContext context);
    String getDynamicThumbnailCacheKey();
}
