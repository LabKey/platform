/*
 * Copyright (c) 2011-2013 LabKey Corporation
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
import org.labkey.api.thumbnail.DynamicThumbnailProvider;
import org.labkey.api.thumbnail.StaticThumbnailProvider;
import org.labkey.api.thumbnail.Thumbnail;
import org.labkey.api.thumbnail.ThumbnailService.*;
import org.labkey.api.util.Pair;


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

    // Returns the static thumbnail, pulling it from the provider if it's not already in the cache.
    public static CacheableWriter getThumbnailWriter(StaticThumbnailProvider staticProvider, ImageType type)
    {
        return _cache.get(getCacheKey(staticProvider, type), staticProvider, _staticLoader);
    }

    // Get the thumbnail for a DynamicThumbnailProvider. Returns the dynamic thumbnail if it has already been created
    // and persisted in the attachments table.  If it hasn't, it falls back to the static thumbnail, but the loader will
    // kick off rendering of the dynamic thumbnail in the background to make it available soon.
    public static CacheableWriter getThumbnailWriter(DynamicThumbnailProvider dynamicProvider, ImageType type)
    {
        CacheableWriter dynamic = _cache.get(getCacheKey(dynamicProvider, type), new Pair<>(dynamicProvider, type), _dynamicLoader);

        if (CacheableWriter.noDocument == dynamic && ImageType.Large.equals(type))
            return getThumbnailWriter((StaticThumbnailProvider)dynamicProvider, type);
        else
            return dynamic;
    }

    public static void remove(DynamicThumbnailProvider dynamicProvider, ImageType type)
    {
        _cache.remove(getCacheKey(dynamicProvider, type));
    }

    private static String getCacheKey(DynamicThumbnailProvider dynamicProvider, ImageType type)
    {
        return dynamicProvider.getDynamicThumbnailCacheKey() + "|" + type.getFilename();
    }

    private static String getCacheKey(StaticThumbnailProvider staticProvider, ImageType type)
    {
        return staticProvider.getStaticThumbnailCacheKey() + "|" + type.getFilename();
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
            @SuppressWarnings("unchecked")
            Pair<DynamicThumbnailProvider, ImageType> pair = (Pair<DynamicThumbnailProvider, ImageType>) argument;
            DynamicThumbnailProvider provider = pair.getKey();
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
