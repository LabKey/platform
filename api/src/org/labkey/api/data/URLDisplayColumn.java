package org.labkey.api.data;

import org.apache.commons.collections4.MultiValuedMap;
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
    private static final String POPUP_IMAGE_URL = "popupImageUrl";
    private static final String POPUP_IMAGE_WIDTH = "popupImageWidth";
    private static final String POPUP_IMAGE_HEIGHT = "popupImageHeight";

    private MultiValuedMap<String, String> _properties;
    private String _thumbnailHeight;
    private String _popupHeight;

    public URLDisplayColumn(ColumnInfo col, MultiValuedMap properties)
    {
        super(col);
        _properties = properties;
        if (_properties != null)
        {
            String value = _properties.get(THUMBNAIL_IMAGE_WIDTH).stream().findFirst().orElse(null);
            if (value != null)
                _thumbnailWidth = value;

            value = _properties.get(POPUP_IMAGE_WIDTH).stream().findFirst().orElse(null);
            if (value != null)
                _popupWidth = value;

            _thumbnailHeight = _properties.get(THUMBNAIL_IMAGE_HEIGHT).stream().findFirst().orElse(null);
            _popupHeight = _properties.get(POPUP_IMAGE_HEIGHT).stream().findFirst().orElse(null);
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

                String thumbnailUrl = _fileIconUrl != null ? ensureAbsoluteUrl(_ctx, _fileIconUrl) : ensureAbsoluteUrl(_ctx, _url);
                sb.append("<img style=\"display:block;").
                        append(_thumbnailWidth != null ? " width:" + _thumbnailWidth : " max-width:32px").append(";").
                        append(_thumbnailHeight != null ? " height:" + _thumbnailHeight : " height:auto").append(";").
                        append(" vertical-align:middle\"").
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
        public String createPopupImage()
        {
            if (_url != null || _fileIconUrl != null)
            {
                StringBuilder sb = new StringBuilder();

                String popupUrl = _popupIconUrl != null ? ensureAbsoluteUrl(_ctx, _popupIconUrl) : (_url != null ? ensureAbsoluteUrl(_ctx, _url) : null);
                if (popupUrl != null)
                {
                    sb.append("<img style=\"").
                            append(_popupWidth != null ? "width:" + _popupWidth : "max-width:300px").append(";").
                            append(_popupHeight != null ? " height:" + _popupHeight : " height:auto").append("\"").
                            append(" src=\"").append(PageFlowUtil.filter(popupUrl)).
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
            String url = _fileIconUrl != null ? ensureAbsoluteUrl(_ctx, _fileIconUrl) : ensureAbsoluteUrl(_ctx, _url);
            return url == null ? null : "window.location = '" + url + "'";
        }
    }
}
