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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.util.GUID;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.MimeMap;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.element.Input;
import org.labkey.api.util.element.Input.InputBuilder;
import org.labkey.api.view.HttpView;
import org.labkey.api.writer.HtmlWriter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;

import static org.labkey.api.util.PageFlowUtil.jsString;

/**
 * Provides a consistent UI for both attachment (BLOB) and file link (file system) files
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
    public void renderDetailsCellContents(RenderContext ctx, HtmlWriter out)
    {
        renderIconAndFilename(ctx, out, (String)getValue(ctx), true, true);
    }

    @Override
    public void renderGridCellContents(RenderContext ctx, HtmlWriter out)
    {
        renderIconAndFilename(ctx, out, (String)getValue(ctx), true, true);
    }

    /** @return the short name of the file (not including full path) */
    protected abstract String getFileName(RenderContext ctx, Object value);

    protected String getFileName(RenderContext ctx, Object value, boolean isDisplay)
    {
        return getFileName(ctx, value);
    }

    protected abstract InputStream getFileContents(RenderContext ctx, Object value) throws FileNotFoundException;

    protected void renderIconAndFilename(RenderContext ctx, HtmlWriter out, String filename, boolean link, boolean thumbnail)
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

    protected void renderIconAndFilename(RenderContext ctx, HtmlWriter out, String filename, @Nullable String fileIconUrl, @Nullable String popupIconUrl, boolean link, boolean thumbnail)
    {
        Writer oldWriter = out.unwrap();
        try
        {
            if (null != filename && !StringUtils.isEmpty(filename))
            {
                // equivalent of DisplayColumn.renderURL.
                // Don't want to call renderUrl (DataColumn.renderUrl) to skip unnecessary displayValue check
                StringExpression s = compileExpression(ctx.getViewContext());
                String url = null == s ? null : s.eval(ctx);

                if (link)
                {
                    if (null != url)
                    {
                        oldWriter.write("<a title=\"Download attached file\"");
                        if (getLinkTarget() != null && MimeMap.DEFAULT.canInlineFor(filename))
                        {
                            oldWriter.write(" target=\"");
                            oldWriter.write(PageFlowUtil.filter(getLinkTarget()));
                            oldWriter.write("\"");
                        }
                        oldWriter.write(" href=\"");
                        oldWriter.write(PageFlowUtil.filter(url));
                        oldWriter.write("\">");
                    }
                }

                String displayName = getFileName(ctx, filename, true);
                boolean isImage = isImage(filename);

                FileImageRenderHelper renderHelper = createRenderHelper(ctx, url, filename, displayName, fileIconUrl, popupIconUrl, thumbnail, isImage);

                if ((url != null || fileIconUrl != null) && thumbnail && isImage)
                {
                    // controls whether to render a popup image on hover, otherwise just render an image with a click handler
                    // to navigate to the url
                    if (renderHelper.renderPopupImage())
                        PageFlowUtil.popupHelp(HtmlString.unsafe(renderHelper.createPopupImage()), displayName).link(HtmlString.unsafe(renderHelper.createThumbnailImage())).width(310).script(renderHelper.createClickScript()).appendTo(oldWriter);
                    else
                        PageFlowUtil.popupHelp(displayName).link(HtmlString.unsafe(renderHelper.createThumbnailImage())).width(310).script(renderHelper.createClickScript()).appendTo(oldWriter);
                }
                else
                {
                    if (url != null && thumbnail && MimeMap.DEFAULT.isInlineImageFor(new File(filename)) )
                    {
                        if (renderHelper.renderPopupImage())
                            PageFlowUtil.popupHelp(HtmlString.unsafe(renderHelper.createPopupImage()), displayName).link(HtmlString.unsafe(renderHelper.createThumbnailImage())).width(310).script(renderHelper.createClickScript()).appendTo(oldWriter);
                        else
                            PageFlowUtil.popupHelp(displayName).link(HtmlString.unsafe(renderHelper.createThumbnailImage())).width(310).script(renderHelper.createClickScript()).appendTo(oldWriter);
                    }
                    else
                        oldWriter.write(renderHelper.createThumbnailImage());
                }

                if (link && null != url)
                {
                    oldWriter.write("</a>");
                }
            }
            else
            {
                oldWriter.write("&nbsp;");
            }
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
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
                return "window.open(" + jsString(_url) + "," + jsString(getLinkTarget()) + ", 'noopener,noreferrer')";
            }
            return "window.location = " + jsString(_url);
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
    public void renderInputHtml(RenderContext ctx, HtmlWriter out, Object value)
    {
        if (hasFileInputHtml())
        {
            String filename = getFileName(ctx, value);
            String formFieldName = ctx.getForm().getFormFieldName(getBoundColumn());

            InputBuilder<?> input = new InputBuilder<>()
                    .type("file")
                    .name(formFieldName)
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
                input.appendTo(out);
            }
        }
        else
            super.renderInputHtml(ctx, out, value);
    }

    /**
     * Enable subclasses to override the warning text
     * @param filename being displayed
     */
    protected String getRemovalWarningText(String filename)
    {
        return "Previous file " + filename + " will be removed.";
    }

    // TODO: filePicker should be a builder or HtmlString or something sensible like that
    private void renderThumbnailAndRemoveLink(HtmlWriter out, RenderContext ctx, String filename, String filePicker)
    {
        String divId = GUID.makeGUID();
        String linkId = "remove" + divId;

        Writer oldWriter = out.unwrap();
        try
        {
            oldWriter.write("<div id=\"" + divId + "\">");
            renderIconAndFilename(ctx, out, filename, false, false);
            oldWriter.write("&nbsp;[<a id=\"" + linkId + "\" href=\"#\">remove</a>]");
            oldWriter.write("</div>\n");
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
        String innerHtml = filePicker + "<input type=\"hidden\" name=\"deletedAttachments\" value=\"" + filename + "\"><span class=\"labkey-message\">" + getRemovalWarningText(filename) + "</span>";
        HttpView.currentPageConfig().addHandler(linkId, "click", "document.getElementById(" + jsString(divId) + ").innerHTML = " + jsString(innerHtml));
    }
}