/*
 * Copyright (c) 2016-2017 LabKey Corporation
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
package org.labkey.api.attachments;

import org.labkey.api.cache.BlockingStringKeyCache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.CacheableWriter;
import org.labkey.api.util.Pair;

public class PortalBackgroundImageCache
{
    private static final BlockingStringKeyCache<CacheableWriter> _cache = CacheManager.getBlockingStringKeyCache(10000, CacheManager.YEAR, "Background Images", null);
    private static final ImageLoader _dynamicLoader = new ImageLoader();

    public static CacheableWriter getImageWriter(AttachmentParent parent, String imageName)
    {
        return _cache.get(getCacheKey(parent.getEntityId(), imageName), new Pair<>(parent, imageName), _dynamicLoader);
    }

    public static void remove(String id, String imageName)
    {
        _cache.remove(getCacheKey(id, imageName));
    }

    private static String getCacheKey(String id, String imageName)
    {
        return "Portal: " + id + "|" + imageName;
    }

    private static class ImageLoader implements CacheLoader<String, CacheableWriter>
    {
        @Override
        public CacheableWriter load(String key, Object argument)
        {
            @SuppressWarnings("unchecked")
            Pair<AttachmentParent, String> pair = (Pair<AttachmentParent, String>) argument;
            AttachmentParent parent = pair.getKey();
            String imageName = pair.getValue();
            Attachment image = AttachmentService.get().getAttachment(parent, imageName);

            if (null == image)
                return CacheableWriter.noDocument;

            try
            {
                CacheableWriter writer = new CacheableWriter();
                AttachmentService.get().writeDocument(writer, parent, imageName, false);
                return writer;
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }
    }
}
