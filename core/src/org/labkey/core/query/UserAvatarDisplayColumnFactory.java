package org.labkey.core.query;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.AbstractFileDisplayColumn;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.RenderContext;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;

public class UserAvatarDisplayColumnFactory implements DisplayColumnFactory
{
    public static final String FIELD_KEY = "Avatar";

    @Override
    public DisplayColumn createRenderer(ColumnInfo colInfo)
    {
        return new AbstractFileDisplayColumn(colInfo)
        {
            @Override
            public boolean isEditable()
            {
                return true;
            }

            @Override
            protected boolean renderRequiredIndicators()
            {
                return false;
            }

            @Override
            public String renderURL(RenderContext ctx)
            {
                User user = getUserFromCtx(ctx);
                return (user != null && user.getAvatarUrl() != null) ? user.getAvatarThumbnailPath() : null;
            }

            @Override
            public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
            {
                String renderUrl = renderURL(ctx);
                if (renderUrl != null)
                {
                    out.write(getImageTagStr(renderUrl, 32));
                }
            }

            @Override
            public void renderDetailsCellContents(RenderContext ctx, Writer out) throws IOException
            {
                String renderUrl = renderURL(ctx);
                if (renderUrl != null)
                {
                    out.write(getImageTagStr(renderUrl, null));
                }
            }

            @Override
            protected void renderIconAndFilename(RenderContext ctx, Writer out, String filename, @Nullable String fileIconUrl, boolean link, boolean thumbnail) throws IOException
            {
                renderDetailsCellContents(ctx, out);
            }

            @Override
            protected String getFileName(Object value)
            {
                User user = getUserFromValue(value);
                return user != null && user.getAvatarUrl() != null ? "avatar" : null;
            }

            @Override
            protected InputStream getFileContents(RenderContext ctx, Object value) throws FileNotFoundException
            {
                return null;
            }

            private String getImageTagStr(String renderUrl, Integer size)
            {
                return "<img src=\"" + renderUrl + "\"" + (size != null ? " height=\"" + size + "\" width=\"" + size + "\"" : "") + "/>";
            }

            private User getUserFromCtx(RenderContext ctx)
            {
                return getUserFromValue(getValue(ctx));
            }

            private User getUserFromValue(Object value)
            {
                try
                {
                    Integer userId = Integer.parseInt(value.toString());
                    return UserManager.getUser(userId);
                }
                catch (NumberFormatException e)
                {
                    return null;
                }
            }
        };
    }
}
