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
import org.labkey.api.data.CacheableWriter;
import org.labkey.api.data.views.DataViewProvider.EditInfo.ThumbnailType;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ViewContext;

import java.io.IOException;
import java.util.Set;

/**
 * Works with {@link ThumbnailProvider} implementations to cache thumbnails.
 * User: adam
 * Date: 10/8/11
 */
public interface ThumbnailService
{
    Set<String> ImageFilenames = PageFlowUtil.set(ImageType.Large.getFilename(), ImageType.Small.getFilename());

    enum ImageType
    {
        Large("Thumbnail", "thumbnail", 256.0f),
        Small("SmallThumbnail", "icon", 16.0f);

        private final String _filename;
        private final String _propertyNamePrefix;
        private final float _height;

        ImageType(String filename, String propertyNamePrefix, float height)
        {
            _filename = filename;
            _propertyNamePrefix = propertyNamePrefix;
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

        public String getPropertyNamePrefix()
        {
            return _propertyNamePrefix;
        }
    }

    CacheableWriter getThumbnailWriter(ThumbnailProvider provider, ImageType imageType);
    void deleteThumbnail(ThumbnailProvider provider, ImageType imageType);
    void queueThumbnailRendering(ThumbnailProvider provider, ImageType imageType, ThumbnailType thumbnailType);
    void replaceThumbnail(ThumbnailProvider provider, ImageType imageType, ThumbnailType thumbnailType, @Nullable ViewContext context) throws IOException;
}
