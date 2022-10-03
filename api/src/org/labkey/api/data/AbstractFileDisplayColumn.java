/*
 * Copyright (c) 2011-2019 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.util.GUID;
import org.labkey.api.util.MimeMap;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.element.Input;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;

/**
 * Provides a consistent UI for both attachment (BLOB) and file link (file system) files
 * User: jeckels
 * Date: Nov 7, 2011
 */
public abstract class AbstractFileDisplayColumn extends DataColumn
{
    protected String _thumbnailWidth;
    protected String _popupWidth;

    public AbstractFileDisplayColumn(ColumnInfo col)
    {
        super(col);
    }

    @Override
    public void renderDetailsCellContents(RenderContext ctx, Writer out) throws IOException
    {
        renderIconAndFilename(ctx, out, (String)getValue(ctx), true, true);
    }

    @Override
    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        renderIconAndFilename(ctx, out, (String)getValue(ctx), true, true);
    }

    /** @return the short name of the file (not including full path) */
    protected abstract String getFileName(RenderContext ctx, Object value);

    protected abstract InputStream getFileContents(RenderContext ctx, Object value) throws FileNotFoundException;

    protected void renderIconAndFilename(RenderContext ctx, Writer out, String filename, boolean link, boolean thumbnail) throws IOException
    {
        renderIconAndFilename(ctx, out, filename, null, null, link, thumbnail);
    }

    protected boolean isImage(String filename)
    {
        return filename.toLowerCase().endsWith(".png")
                || filename.toLowerCase().endsWith(".jpeg")
                || filename.toLowerCase().endsWith(".jpg")
                || filename.toLowerCase().endsWith(".gif");
    }

    protected void renderIconAndFilename(RenderContext ctx, Writer out, String filename, @Nullable String fileIconUrl, @Nullable String popupIconUrl, boolean link, boolean thumbnail) throws IOException
    {
        if (null != filename)
        {
            String url = renderURL(ctx);

            if (link)
            {
                if (null != url)
                {
                    out.write("<a title=\"Download attached file\"");
                    if (getLinkTarget() != null && MimeMap.DEFAULT.canInlineFor(filename))
                    {
                        out.write(" target=\"");
                        out.write(PageFlowUtil.filter(getLinkTarget()));
                        out.write("\"");
                    }
                    out.write(" href=\"");
                    out.write(PageFlowUtil.filter(url));
                    out.write("\">");
                }
            }

            String displayName = getFileName(ctx, filename);
            boolean isImage = isImage(filename);

            FileImageRenderHelper renderHelper = createRenderHelper(ctx, url, filename, displayName, fileIconUrl, popupIconUrl, thumbnail, isImage);

            if ((url != null || fileIconUrl != null) && thumbnail && isImage)
            {
                // controls whether to render a popup image on hover, otherwise just render an image with a click handler
                // to navigate to the url
                if (renderHelper.renderPopupImage())
                    out.write(PageFlowUtil.helpPopup(displayName, renderHelper.createPopupImage(), true, renderHelper.createThumbnailImage(), 310, renderHelper.createClickScript()));
                else
                    out.write(PageFlowUtil.helpPopup(null, displayName, false, renderHelper.createThumbnailImage(), 310, renderHelper.createClickScript()));
            }
            else
            {
                if (url != null && thumbnail && MimeMap.DEFAULT.isInlineImageFor(new File(filename)) )
                {
                    if (renderHelper.renderPopupImage())
                        out.write(PageFlowUtil.helpPopup(displayName, renderHelper.createPopupImage(), true, renderHelper.createThumbnailImage(), 310, renderHelper.createClickScript()));
                    else
                        out.write(PageFlowUtil.helpPopup(null, displayName, false, renderHelper.createThumbnailImage(), 310, renderHelper.createClickScript()));
                }
                else
                    out.write(renderHelper.createThumbnailImage());
            }

            if (link && null != url)
            {
                out.write("</a>");
            }
        }
        else
        {
            out.write("&nbsp;");
        }
    }

    protected FileImageRenderHelper createRenderHelper(RenderContext ctx, String url, String filename, String displayName, @Nullable String fileIconUrl, @Nullable String popupIconUrl, boolean isThumbnail, boolean isImage)
    {
        return new FileImageRenderHelper(ctx, url, filename, displayName, fileIconUrl, popupIconUrl, isThumbnail, isImage);
    }

    /**
     * Helper class to generate the HTML for the various portions of a file or image grid cell content
     *
     * Tests to run if you touch this class : FileAttachmentColumnTest, InlineImagesAssayTest, InlineImagesListTest, SimpleModuleTest
     */
    public class FileImageRenderHelper
    {
        protected RenderContext _ctx;
        protected String _displayName;
        protected String _url;
        protected String _filename;
        protected String _fileIconUrl;
        protected String _popupIconUrl;
        protected boolean _isThumbnail;
        protected boolean _isImage;

        public FileImageRenderHelper(RenderContext ctx, String url, String filename, String displayName, String fileIconUrl, String popupIconUrl, boolean isThumbnail, boolean isImage)
        {
            _ctx = ctx;
            _url = url;
            _filename = filename;
            _displayName = displayName;
            _fileIconUrl = fileIconUrl;
            _popupIconUrl = popupIconUrl;
            _isThumbnail = isThumbnail;
            _isImage = isImage;
        }

        // render the grid cell content
        public String createThumbnailImage()
        {
            StringBuilder sb = new StringBuilder();
            if (_url != null && _isThumbnail && _isImage)
            {
                sb.append("<img style=\"display:block; height:auto;").
                        append(_thumbnailWidth != null ? "width:" + _thumbnailWidth : "max-width:32px").append("; vertical-align:middle\"").
                        append(" src=\"").append(PageFlowUtil.filter(_url)).append("\"").
                        append(" title=\"").append(PageFlowUtil.filter(_displayName)).append("\"").
                        append("\" />");
            }
            else
            {
                sb.append("<img src=\"").append(_ctx.getRequest().getContextPath()).
                        append((null != _fileIconUrl) ? _fileIconUrl : Attachment.getFileIcon(_filename)).
                        append("\" alt=\"icon\"").
                        append("/>&nbsp;").append(PageFlowUtil.filter(_displayName));
            }
            return sb.toString();
        }

        public boolean renderPopupImage()
        {
            return true;
        }

        // render the popup image to display on hover
        public String createPopupImage()
        {
            StringBuilder sb = new StringBuilder();
            if (_url != null)
            {
                sb.append("<img style=\"").
                        append(_popupWidth != null ? "width:" + _popupWidth : "max-width:300px").append("; height:auto;\" src=\"").
                        append(PageFlowUtil.filter(_url)).
                        append("\" />");
            }
            return sb.toString();
        }

        // render the click script when a user clicks on the grid cell
        public String createClickScript()
        {
            if (_url == null)
            {
                return null;
            }
            if (getLinkTarget() != null)
            {
                return "window.open(" + PageFlowUtil.jsString(_url) + "," + PageFlowUtil.jsString(getLinkTarget()) + ", 'noopener,noreferrer')";
            }
            return "window.location = " + PageFlowUtil.jsString(_url);
        }
    }

    protected String ensureAbsoluteUrl(RenderContext ctx, String url)
    {
        if (!url.startsWith(ctx.getRequest().getContextPath()))
        {
            String lcUrl = url.toLowerCase();
            if (!lcUrl.startsWith("http:") && !lcUrl.startsWith("https:"))
            {
                if (url.startsWith("/"))
                    return ctx.getRequest().getContextPath() + url;
                else
                    return ctx.getRequest().getContextPath() + "/" + url;
            }
        }
        return url;
    }

    protected boolean hasFileInputHtml()
    {
        return true;
    }

    @Override
    public void renderInputHtml(RenderContext ctx, Writer out, Object value) throws IOException
    {
        if (hasFileInputHtml())
        {
            String filename = getFileName(ctx, value);
            String formFieldName = ctx.getForm().getFormFieldName(getBoundColumn());

            Input.InputBuilder input = new Input.InputBuilder()
                    .type("file")
                    .name(getInputPrefix() + formFieldName)
                    .disabled(isDisabledInput(ctx))
                    .needsWrapping(false);

            if (null != filename)
            {
                // Existing value, so tell the user the file name, allow the file to be removed, and a new file uploaded
                renderThumbnailAndRemoveLink(out, ctx, filename, input.build().toString());
            }
            else
            {
                // No existing value, so render just the regular <input type=file> element
                out.write(input.build().toString());
            }
        }
        else
            super.renderInputHtml(ctx, out, value);
    }

    /**
     * Enable subclasses to override the warning text
     * @param filename being displayed
     * @return
     */
    protected String getRemovalWarningText(String filename)
    {
        return "Previous file " + filename + " will be removed.";
    }

    private void renderThumbnailAndRemoveLink(Writer out, RenderContext ctx, String filename, String filePicker) throws IOException
    {
        String divId = GUID.makeGUID();

        out.write("<div id=\"" + divId + "\">");
        renderIconAndFilename(ctx, out, filename, false, false);
        out.write("&nbsp;[<a href=\"javascript:{}\" onClick=\"");
        out.write("document.getElementById('" + divId + "').innerHTML = " + PageFlowUtil.filter(PageFlowUtil.jsString(filePicker + "<input type=\"hidden\" name=\"deletedAttachments\" value=\"" + filename + "\"><span class=\"labkey-message\">" + getRemovalWarningText(filename) + "</span>")) + "\"");
        out.write(">remove");
        out.write("</a>]");
        out.write("</div>\n");
    }
}
