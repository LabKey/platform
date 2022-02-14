/*
 * Copyright (c) 2018-2019 LabKey Corporation
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
package org.labkey.api.data;

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.StringExpressionFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;

public class URLDisplayColumn extends AbstractFileDisplayColumn
{
    public static class Factory implements DisplayColumnFactory
    {
        private MultiValuedMap _properties;             // metadata XML column properties

        // factory for metadata XML loading
        public Factory(MultiValuedMap properties)
        {
            if (properties != null)
                _properties = properties;
        }

        @Override
        public DisplayColumn createRenderer(ColumnInfo col)
        {
            if (_properties != null)
                return new URLDisplayColumn(col, _properties);
            else
                throw new IllegalArgumentException("Cannot create a renderer from the specified configuration properties");
        }
    }

    // XML defined properties
    private static final String THUMBNAIL_IMAGE_URL = "thumbnailImageUrl";
    private static final String THUMBNAIL_IMAGE_WIDTH = "thumbnailImageWidth";
    private static final String THUMBNAIL_IMAGE_HEIGHT = "thumbnailImageHeight";
    private static final String USE_THUMBNAIL_IMAGE_URL_ONCLICK = "useThumbnailImageUrlOnClick";
    private static final String RENDER_POPUP_IMAGE = "renderPopupImage";
    private static final String POPUP_IMAGE_URL = "popupImageUrl";
    private static final String POPUP_IMAGE_WIDTH = "popupImageWidth";
    private static final String POPUP_IMAGE_HEIGHT = "popupImageHeight";
    private static final String URL_TARGET = "urlTarget";

    private static final String UNSCALED = "auto";

    private MultiValuedMap<String, String> _properties;
    private String _thumbnailHeight;
    private boolean _useThumbnailImageUrlOnClick = true;
    private boolean _renderPopupImage = true;
    private String _popupHeight;
    private String _urlTarget;

    public URLDisplayColumn(ColumnInfo col, MultiValuedMap properties)
    {
        super(col);
        _properties = properties;
        if (_properties != null)
        {
            _thumbnailHeight = _properties.get(THUMBNAIL_IMAGE_HEIGHT).stream().findFirst().orElse(null);
            _thumbnailWidth = _properties.get(THUMBNAIL_IMAGE_WIDTH).stream().findFirst().orElse(null);
            _useThumbnailImageUrlOnClick = Boolean.parseBoolean(_properties.get(USE_THUMBNAIL_IMAGE_URL_ONCLICK).stream().findFirst().orElse("true"));
            _renderPopupImage = Boolean.parseBoolean(_properties.get(RENDER_POPUP_IMAGE).stream().findFirst().orElse("true"));
            _popupHeight = _properties.get(POPUP_IMAGE_HEIGHT).stream().findFirst().orElse(null);
            _popupWidth = _properties.get(POPUP_IMAGE_WIDTH).stream().findFirst().orElse(null);
            _urlTarget = _properties.get(URL_TARGET).stream().findFirst().orElse(null);

            // if any of the sizes are blank, default to auto
            if (_thumbnailHeight != null && StringUtils.isBlank(_thumbnailHeight)) _thumbnailHeight = UNSCALED;
            if (_thumbnailWidth != null && StringUtils.isBlank(_thumbnailWidth)) _thumbnailWidth = UNSCALED;
            if (_popupHeight != null && StringUtils.isBlank(_popupHeight)) _popupHeight = UNSCALED;
            if (_popupWidth != null && StringUtils.isBlank(_popupWidth)) _popupWidth = UNSCALED;
        }
    }

    @Override
    protected String getFileName(RenderContext ctx, Object value)
    {
        return String.valueOf(value);
    }

    @Override
    protected InputStream getFileContents(RenderContext ctx, Object value)
    {
        return null;
    }

    @Override
    protected void renderIconAndFilename(RenderContext ctx, Writer out, String filename, boolean link, boolean thumbnail) throws IOException
    {
        Object value = getValue(ctx);
        String url = renderURL(ctx);
        String imageUrl = getCustomUrl(ctx, THUMBNAIL_IMAGE_URL);
        String popupImageUrl = getCustomUrl(ctx, POPUP_IMAGE_URL);

        if (value != null && (url != null || imageUrl != null || popupImageUrl != null))
        {
            // custom image URLs through the column metadata
            super.renderIconAndFilename(ctx, out, filename, imageUrl, popupImageUrl, false, thumbnail);
        }
        else
        {
            super.renderIconAndFilename(ctx, out, filename, link, thumbnail);
        }
    }

    private String getCustomUrl(RenderContext ctx, String propName)
    {
        if (_properties != null)
        {
            String value = _properties.get(propName).stream().findFirst().orElse(null);
            if (value != null)
            {
                StringExpression url = StringExpressionFactory.createURL(value, StringExpressionFactory.AbstractStringExpression.NullValueBehavior.NullResult);
                return url.eval(ctx);
            }
        }
        return null;
    }

    @Override
    protected boolean isImage(String filename)
    {
        return true;
    }

    @Override
    protected boolean hasFileInputHtml()
    {
        return false;
    }

    @Override
    protected FileImageRenderHelper createRenderHelper(RenderContext ctx, String url, String filename, String displayName, @Nullable String fileIconUrl, @Nullable String popupIconUrl, boolean isThumbnail, boolean isImage)
    {
        return new URLImageRenderHelper(ctx, url, filename, displayName, fileIconUrl, popupIconUrl, isThumbnail, isImage);
    }

    private class URLImageRenderHelper extends FileImageRenderHelper
    {
        public URLImageRenderHelper(RenderContext ctx, String url, String filename, String displayName, String fileIconUrl, String popupIconUrl, boolean isThumbnail, boolean isImage)
        {
            super(ctx, url, filename, displayName, fileIconUrl, popupIconUrl, isThumbnail, isImage);
        }

        @Override
        public String createThumbnailImage()
        {
            if (_url != null || _fileIconUrl != null)
            {
                StringBuilder sb = new StringBuilder();
                boolean renderSize = (!UNSCALED.equals(_thumbnailHeight)) && (!UNSCALED.equals(_thumbnailWidth));

                String thumbnailUrl = _fileIconUrl != null ? ensureAbsoluteUrl(_ctx, _fileIconUrl) : ensureAbsoluteUrl(_ctx, _url);
                sb.append("<img style=\"display:block; ");

                if (renderSize)
                {
                    sb.append(_thumbnailWidth != null ? " width:" + _thumbnailWidth : " max-width:32px").append(";").
                            append(_thumbnailHeight != null ? " height:" + _thumbnailHeight : " height:auto").append(";");
                }
                sb.append(" vertical-align:middle\"").
                        append(" src=\"").append(PageFlowUtil.filter(thumbnailUrl)).append("\"").
                        append(" title=\"").append(PageFlowUtil.filter(_displayName)).append("\"").
                        append("/>");

                return sb.toString();
            }
            else
            {
                return super.createThumbnailImage();
            }
        }

        @Override
        public boolean renderPopupImage()
        {
            if (_popupIconUrl != null)
                return _popupIconUrl.trim().length() > 0;
            else
                return _renderPopupImage;
        }

        @Override
        public String createPopupImage()
        {
            if (_url != null || _fileIconUrl != null)
            {
                StringBuilder sb = new StringBuilder();
                boolean renderSize = (!UNSCALED.equals(_popupHeight)) && (!UNSCALED.equals(_popupWidth));

                String popupUrl = _popupIconUrl != null ? ensureAbsoluteUrl(_ctx, _popupIconUrl) : (_url != null ? ensureAbsoluteUrl(_ctx, _url) : null);
                if (popupUrl != null)
                {
                    sb.append("<img ");
                    if (renderSize)
                    {
                        sb.append("style=\"").
                                append(_popupWidth != null ? "width:" + _popupWidth : "max-width:300px").append(";").
                                append(_popupHeight != null ? " height:" + _popupHeight : " height:auto").append("\"");
                    }
                    sb.append(" src=\"").append(PageFlowUtil.filter(popupUrl)).
                            append("\" />");
                }
                return sb.toString();
            }
            else
            {
                return super.createPopupImage();
            }
        }

        @Override
        public String createClickScript()
        {
            String url = _useThumbnailImageUrlOnClick && _fileIconUrl != null
                    ? ensureAbsoluteUrl(_ctx, _fileIconUrl)
                    : _url != null ? ensureAbsoluteUrl(_ctx, _url) : null;
            if (url != null)
            {
                String open = "window.location = '" + url + "'";
                if (_urlTarget != null)
                    open = "window.open('" + url + "', '" + _urlTarget + "') || window.location.replace('" + url + "');";
                return open;
            }
            return null;
        }
    }
}
