/*
 * Copyright (c) 2011-2014 LabKey Corporation
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
package org.labkey.core.thumbnail;

import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.cache.BlockingStringKeyCache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.CacheableWriter;
import org.labkey.api.thumbnail.ThumbnailProvider;
import org.labkey.api.thumbnail.ThumbnailService.ImageType;
import org.labkey.api.util.Pair;
import org.labkey.api.util.ThumbnailUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.RedirectException;

/**
 * User: adam
 * Date: 10/8/11
 * Time: 9:22 AM
 */
public class ThumbnailCache
{
    private static final BlockingStringKeyCache<CacheableWriter> _cache = CacheManager.getBlockingStringKeyCache(10000, CacheManager.YEAR, "Thumbnails", null);
    private static final DynamicThumbnailLoader _dynamicLoader = new DynamicThumbnailLoader();

    // Get the thumbnail for a DynamicThumbnailProvider. Returns the dynamic thumbnail if it has already been created
    // and persisted in the attachments table. If it hasn't, it falls back to the static thumbnail, though this likely
    // indicates a report in a bad state.
    public static CacheableWriter getThumbnailWriter(ThumbnailProvider provider, ImageType imageType)
    {
        CacheableWriter dynamic = _cache.get(getCacheKey(provider, imageType), new Pair<>(provider, imageType), _dynamicLoader);

        if (CacheableWriter.noDocument == dynamic)
        {
            // Something went wrong... provider claimed we'd have a saved thumbnail, but it was missing. Just redirect
            // to the static thumbnail link.
            // TODO: Probably an old report that's gotten out-of-sync; consider deleting the thumbnail to clean things up
            URLHelper redirectURL = ThumbnailUtil.getStaticThumbnailURL(provider, imageType);

            if (null != redirectURL)
                throw new RedirectException(redirectURL);
        }

        return dynamic;
    }

    public static void remove(ThumbnailProvider provider, ImageType type)
    {
        _cache.remove(getCacheKey(provider, type));
    }

    private static String getCacheKey(ThumbnailProvider provider, ImageType type)
    {
        return provider.getThumbnailCacheKey() + "|" + type.getFilename();
    }

    private static class DynamicThumbnailLoader implements CacheLoader<String, CacheableWriter>
    {
        @Override
        public CacheableWriter load(String key, Object argument)
        {
            @SuppressWarnings("unchecked")
            Pair<ThumbnailProvider, ImageType> pair = (Pair<ThumbnailProvider, ImageType>) argument;
            ThumbnailProvider provider = pair.getKey();
            ImageType type = pair.getValue();
            Attachment thumbnailAttachment = AttachmentService.get().getAttachment(provider, type.getFilename());

            if (null == thumbnailAttachment)
                return CacheableWriter.noDocument;

            try
            {
                CacheableWriter writer = new CacheableWriter();
                AttachmentService.get().writeDocument(writer, provider, type.getFilename(), false);
                return writer;
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }
    }
}
