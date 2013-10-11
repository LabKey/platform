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
package org.labkey.api.thumbnail;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.CacheableWriter;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ViewContext;

import java.io.IOException;
import java.util.Set;

/**
 * User: adam
 * Date: 10/8/11
 * Time: 7:17 AM
 */
public interface ThumbnailService
{
    public static final Set<String> ImageFilenames = PageFlowUtil.set(ImageType.Large.getFilename(), ImageType.Small.getFilename());

    public enum ImageType
    {
        Large("Thumbnail", 256.0f),
        Small("SmallThumbnail", 16.0f);

        private final String _filename;
        private final float _height;

        ImageType(String filename, float height)
        {
            _filename = filename;
            _height = height;
        }

        public String getFilename()
        {
            return _filename;
        }

        public float getHeight()
        {
            return _height;
        }
    }

    CacheableWriter getThumbnailWriter(StaticThumbnailProvider provider, ImageType type);
    void queueThumbnailRendering(DynamicThumbnailProvider provider, ImageType type);
    void deleteThumbnail(DynamicThumbnailProvider provider, ImageType type);
    void replaceThumbnail(DynamicThumbnailProvider provider, ImageType type, @Nullable ViewContext context) throws IOException;
}
