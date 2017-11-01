/*
 * Copyright (c) 2012-2017 LabKey Corporation
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

import org.apache.batik.transcoder.TranscoderException;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.AttachmentType;
import org.labkey.api.attachments.DocumentConversionService;
import org.labkey.api.data.views.DataViewProvider.EditInfo.ThumbnailType;
import org.labkey.api.reader.Readers;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.thumbnail.ThumbnailService.ImageType;
import org.labkey.api.util.CheckedInputStream;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.ImageUtil;
import org.labkey.api.view.ViewContext;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.InputStream;

/**
 * User: adam
 * Date: 6/13/12
 * Time: 4:14 PM
 */
public class ImageStreamThumbnailProvider implements ThumbnailProvider
{
    private static final Logger LOG = Logger.getLogger(ImageStreamThumbnailProvider.class);

    private final ThumbnailProvider _provider;
    private final @Nullable String _contentType;
    private final ImageType _type;
    private final InputStream _is;
    private final boolean _makeSquare;

    // Generates a thumbnail from the image in the inputstream and associates it with the provider
    public ImageStreamThumbnailProvider(ThumbnailProvider provider, InputStream is, @Nullable String contentType, ImageType type, boolean makeSquare)
    {
        _provider = provider;
        _contentType = contentType;
        _type = type;
        _is = new CheckedInputStream(is);
        _makeSquare = makeSquare;
    }

    @Override
    public String getStaticThumbnailPath()
    {
        return _provider.getStaticThumbnailPath();
    }

    @Override
    public String getThumbnailCacheKey()
    {
        return _provider.getThumbnailCacheKey();
    }

    @Override
    public boolean supportsDynamicThumbnail()
    {
        return _provider.supportsDynamicThumbnail();
    }

    @Override
    @Nullable
    public Thumbnail generateThumbnail(@Nullable ViewContext context)
    {
        try
        {
            if ("image/svg+xml".equals(_contentType))
            {
                DocumentConversionService svc = ServiceRegistry.get().getService(DocumentConversionService.class);

                if (null != svc)
                {
                    ThumbnailOutputStream os = new ThumbnailOutputStream();

                    // Try-with-resources will close stream
                    try (InputStream is = _is)
                    {
                        svc.svgToPng(Readers.getXmlReader(is), os, _type.getHeight());

                        return os.getThumbnail("image/png");
                    }
                    catch (TranscoderException e)
                    {
                        LOG.error("Couldn't generate thumbnail", e);
                    }
                }
            }
            else if (null == _contentType || _contentType.startsWith("image/"))
            {
                // Try-with-resources will close stream
                try (InputStream is = _is)
                {
                    return ImageUtil.renderThumbnail(ImageIO.read(is), _type, _makeSquare);
                }
            }
        }
        catch (IOException e)
        {
            ExceptionUtil.logExceptionToMothership(null, e);
        }

        return null;
    }

    @Override
    public void afterThumbnailSave(ImageType type, ThumbnailType thumbnailType)
    {
        _provider.afterThumbnailSave(type, thumbnailType);
    }

    @Override
    public void afterThumbnailDelete(ImageType type)
    {
        _provider.afterThumbnailDelete(type);
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
    public @NotNull AttachmentType getAttachmentType()
    {
        return _provider.getAttachmentType();
    }
}
