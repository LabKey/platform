/*
 * Copyright (c) 2011-2017 LabKey Corporation
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
package org.labkey.api.thumbnail;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.data.views.DataViewProvider.EditInfo.ThumbnailType;
import org.labkey.api.thumbnail.ThumbnailService.ImageType;
import org.labkey.api.view.ViewContext;

/**
 * Provides a simple path to a static thumbnail image (usually the same link for all objects of the same type) plus an
 * optional ability to generate a "dynamic" thumbnail image, representing the specific content of this report, etc.
 * Dynamic thumbnails are generated from content at save time and stored as attachments. At display time, thumbnails
 * are read from the attachments service and cached in memory (using the cache key).
 * User: adam
 * Date: 10/7/11
 */
public interface ThumbnailProvider extends AttachmentParent
{
    /**
     * Simple path to browser accessible thumbnail image in the webapp, e.g., "visualization/images/timechart.png". Callers will prepend
     * context path and append look-and-feel version parameter to turn this into a legal relative URL.
     *
     * @return Path to the thumbnail image
     */
    String getStaticThumbnailPath();

    /**
     * String used to cache this provider's dynamic or custom thumbnail image. Also used to cache custom mini icons.
     *
     * @return The cache key
     */
    String getThumbnailCacheKey();

    /**
     * Can this provider generate a thumbnail from the content?
     *
     * @return True if this provider can generate a thumbnail from the content.
     */
    boolean supportsDynamicThumbnail();

    /**
     * Generate a dynamic thumbnail from the contents of this specific report or document. Return null if the thumbnail
     * can't be rendered for some reason (e.g., image type not supported, encrypted PDF). If null, callers will fall
     * back on the static thumbnail.
     *
     * @param context ViewContext of the current request
     * @return The thumbnail image or null if the image couldn't be generated
     */
    @Nullable
    Thumbnail generateThumbnail(@Nullable ViewContext context);

    /**
     * Called by the thumbnail-generating background thread when a thumbnail has been successfully saved, enabling
     * provider bookkeeping.
     *
     * @param imageType The image type (Large or Small) that was saved
     * @param thumbnailType The thumbnail type (AUTO, CUSTOM, NONE)
     */
    void afterThumbnailSave(ImageType imageType, ThumbnailType thumbnailType);

    /**
     * Called after a thumbnail has been deleted, enabling provider bookkeeping.
     *
     * @param imageType The image (Large or Small) type that was deleted
     */
    void afterThumbnailDelete(ImageType imageType);
}
