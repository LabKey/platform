/*
 * Copyright (c) 2012 LabKey Corporation
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
import org.labkey.api.util.CheckedInputStream;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.ImageUtil;
import org.labkey.api.view.ViewContext;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

/**
 * User: adam
 * Date: 6/13/12
 * Time: 4:14 PM
 */
public class ImageStreamThumbnailProvider implements DynamicThumbnailProvider
{
    private final DynamicThumbnailProvider _provider;
    private final InputStream _is;

    // Generates a thumbnail from the image in the inputstream and associates it with the provider
    public ImageStreamThumbnailProvider(DynamicThumbnailProvider provider, InputStream is)
    {
        _provider = provider;
        _is = new CheckedInputStream(is);
    }

    @Override
    @Nullable
    public Thumbnail generateDynamicThumbnail(@Nullable ViewContext context)
    {
        try
        {
            BufferedImage image;

            try
            {
                image = ImageIO.read(_is);
            }
            finally
            {
                _is.close();
            }

            return ImageUtil.renderThumbnail(image);
        }
        catch (IOException e)
        {
            ExceptionUtil.logExceptionToMothership(null, e);
            return null;
        }
    }

    @Override
    public String getDynamicThumbnailCacheKey()
    {
        return _provider.getDynamicThumbnailCacheKey();
    }

    @Override
    public Thumbnail getStaticThumbnail()
    {
        return _provider.getStaticThumbnail();
    }

    @Override
    public String getStaticThumbnailCacheKey()
    {
        return _provider.getStaticThumbnailCacheKey();
    }

    @Override
    public String getEntityId()
    {
        return _provider.getEntityId();
    }

    @Override
    public String getContainerId()
    {
        return _provider.getContainerId();
    }

    @Override
    public String getDownloadURL(ViewContext context, String name)
    {
        return _provider.getDownloadURL(context, name);
    }
}
