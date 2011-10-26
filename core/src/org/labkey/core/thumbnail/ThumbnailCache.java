/*
 * Copyright (c) 2011 LabKey Corporation
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
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.thumbnail.DynamicThumbnailProvider;
import org.labkey.api.thumbnail.StaticThumbnailProvider;
import org.labkey.api.thumbnail.Thumbnail;
import org.labkey.api.thumbnail.ThumbnailService;

import java.io.IOException;

/**
 * User: adam
 * Date: 10/8/11
 * Time: 9:22 AM
 */
public class ThumbnailCache
{
    private static final BlockingStringKeyCache<CacheableWriter> _cache = CacheManager.getBlockingStringKeyCache(10000, CacheManager.YEAR, "Thumbnails", null);
    private static final StaticThumbnailLoader _staticLoader = new StaticThumbnailLoader();
    private static final DynamicThumbnailLoader _dynamicLoader = new DynamicThumbnailLoader();

    // Returns the static thumbnail, pulling to from the provider if it's not already in the cache.
    public static CacheableWriter getThumbnailWriter(StaticThumbnailProvider staticProvider)
    {
        return _cache.get(staticProvider.getStaticThumbnailCacheKey(), staticProvider, _staticLoader);
    }

    // Get the thumbnail for a DynamicThumbnailProvider. Returns the dynamic thumbnail if it has already been created
    // and persisted in the attachments table.  If it hasn't, it falls back to the static thumbnail, but the loader will
    // kick off rendering of the dynamic thumbnail in the background to make it available soon.
    public static CacheableWriter getThumbnailWriter(DynamicThumbnailProvider dynamicProvider)
    {
        CacheableWriter dynamic = _cache.get(dynamicProvider.getDynamicThumbnailCacheKey(), dynamicProvider, _dynamicLoader);

        if (CacheableWriter.noDocument == dynamic)
            return getThumbnailWriter((StaticThumbnailProvider)dynamicProvider);
        else
            return dynamic;
    }

    public static void remove(DynamicThumbnailProvider dynamicProvider)
    {
        _cache.remove(dynamicProvider.getDynamicThumbnailCacheKey());
    }

    private static class StaticThumbnailLoader implements CacheLoader<String, CacheableWriter>
    {
        @Override
        public CacheableWriter load(String key, Object argument)
        {
            try
            {
                StaticThumbnailProvider provider = (StaticThumbnailProvider) argument;
                Thumbnail thumbnail = provider.getStaticThumbnail();

                return new CacheableWriter(thumbnail.getContentType(), thumbnail.getInputStream());
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    private static class DynamicThumbnailLoader implements CacheLoader<String, CacheableWriter>
    {
        @Override
        public CacheableWriter load(String key, Object argument)
        {
            DynamicThumbnailProvider provider = (DynamicThumbnailProvider) argument;
            Attachment thumbnailAttachment = AttachmentService.get().getAttachment(provider, ThumbnailService.THUMBNAIL_FILENAME);

            if (null == thumbnailAttachment)
            {
                ThumbnailService svc = ServiceRegistry.get().getService(ThumbnailService.class);

                if (null != svc)
                    svc.queueThumbnailRendering(provider);

                return CacheableWriter.noDocument;
            }

            try
            {
                CacheableWriter writer = new CacheableWriter();
                AttachmentService.get().writeDocument(writer, provider, ThumbnailService.THUMBNAIL_FILENAME, false);
                return writer;
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }
    }
}
