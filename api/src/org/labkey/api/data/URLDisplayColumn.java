package org.labkey.api.data;

import org.apache.commons.collections4.MultiValuedMap;
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
    private static final String THUMBNAIL_MIME_TYPE = "thumbnailMimeType";
    private static final String THUMBNAIL_IMAGE_URL = "thumbnailImageUrl";
    private static final String THUMBNAIL_IMAGE_WIDTH = "thumbnailImageWidth";
    private static final String POPUP_IMAGE_URL = "popupImageUrl";
    private static final String POPUP_IMAGE_WIDTH = "popupImageWidth";

    private Container _container;
    private MultiValuedMap<String, String> _properties;

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
        String imageUrl = getCustomUrl(ctx, THUMBNAIL_IMAGE_URL);

        if (value != null && imageUrl != null)
        {
            // custom image URLs through the column metadata
            super.renderIconAndFilename(ctx, out, filename, imageUrl, getCustomUrl(ctx, POPUP_IMAGE_URL), link, thumbnail);
        }
        else
        {
            super.renderIconAndFilename(ctx, out, filename, link, thumbnail);
        }
    }

    private Container getContainer(RenderContext ctx)
    {
        if (_container == null)
        {
            if (getColumnInfo().getParentTable().getUserSchema() != null)
                _container = getColumnInfo().getParentTable().getUserSchema().getContainer();
            else
                _container = ctx.getContainer();
        }
        return _container;
    }

    private String getCustomUrl(RenderContext ctx, String propName)
    {
        if (_properties != null)
        {
            String value = _properties.get(propName).stream().findFirst().orElse(null);
            if (value != null)
            {
                StringExpression url = StringExpressionFactory.createURL(value);
                return url.eval(ctx);
            }
        }
        return null;
    }
}
