/*
 * Copyright (c) 2011-2026 LabKey Corporation
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
import org.labkey.api.util.DOM;
import org.labkey.api.util.DOM.Renderable;
import org.labkey.api.util.GUID;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.InputBuilder;
import org.labkey.api.util.LinkBuilder;
import org.labkey.api.util.MimeMap;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.StringExpression;
import org.labkey.api.view.HttpView;
import org.labkey.api.writer.HtmlWriter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;

import static org.labkey.api.util.DOM.A;
import static org.labkey.api.util.DOM.Attribute.alt;
import static org.labkey.api.util.DOM.Attribute.href;
import static org.labkey.api.util.DOM.Attribute.src;
import static org.labkey.api.util.DOM.Attribute.style;
import static org.labkey.api.util.DOM.Attribute.target;
import static org.labkey.api.util.DOM.Attribute.title;
import static org.labkey.api.util.DOM.DIV;
import static org.labkey.api.util.DOM.IMG;
import static org.labkey.api.util.DOM.at;
import static org.labkey.api.util.DOM.id;
import static org.labkey.api.util.PageFlowUtil.jsString;

/**
 * Provides a consistent UI for both attachment (BLOB) and file link (file system) files
 */
public abstract class AbstractFileDisplayColumn extends DataColumn
{
    protected String _thumbnailWidth;
    protected String _popupWidth;

    public static final String UNAVAILABLE_FILE_SUFFIX = " (unavailable)";

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

    protected void renderIconAndFilename(RenderContext ctx, HtmlWriter out, String fileValue, boolean link, boolean thumbnail)
    {
        renderIconAndFilename(ctx, out, fileValue, null, null, link, thumbnail);
    }

    protected boolean isImage(String filename)
    {
        return filename.toLowerCase().endsWith(".png")
                || filename.toLowerCase().endsWith(".jpeg")
                || filename.toLowerCase().endsWith(".jpg")
                || filename.toLowerCase().endsWith(".gif");
    }

    protected void renderIconAndFilename(RenderContext ctx, HtmlWriter out, String fileValue, @Nullable String fileIconUrl, @Nullable String popupIconUrl, boolean link, boolean thumbnail)
    {
        if (null != fileValue && !StringUtils.isEmpty(fileValue))
        {
            // equivalent of DisplayColumn.renderURL.
            // Don't want to call renderUrl (DataColumn.renderUrl) to skip unnecessary displayValue check
            StringExpression s = compileExpression(ctx.getViewContext());
            String displayName = getFileName(ctx, fileValue, true);
            boolean unavailable = displayName.endsWith(UNAVAILABLE_FILE_SUFFIX);
            String url = null == s || unavailable ? null : s.eval(ctx);
            boolean isImage = isImage(fileValue);
            FileImageRenderHelper renderHelper = createRenderHelper(ctx, url, fileValue, displayName, fileIconUrl, popupIconUrl, thumbnail, isImage);


            if (link && null != url)
            {
                A(
                    at(title, "Download attached file")
                    .at(getLinkTarget() != null && MimeMap.DEFAULT.canInlineFor(fileValue), target, getLinkTarget())
                    .at(href, url),
                    (Renderable) ret -> {
                        renderPopup(renderHelper, url, fileIconUrl, displayName, fileValue, thumbnail, isImage, out);
                        return ret;
                    }
                ).appendTo(out);
            }
            else
            {
                renderPopup(renderHelper, url, fileIconUrl, displayName, fileValue, thumbnail, isImage, out);
            }
        }
        else
        {
            out.write(HtmlString.NBSP);
        }
    }

    private void renderPopup(FileImageRenderHelper renderHelper, String url, @Nullable String fileIconUrl, String displayName, String filename, boolean thumbnail, boolean isImage, HtmlWriter out)
    {
        if ((url != null || fileIconUrl != null) && thumbnail && isImage)
        {
            // controls whether to render a popup image on hover, otherwise just render an image with a click handler
            // to navigate to the url
            if (renderHelper.renderPopupImage())
                out.write(PageFlowUtil.popupHelp(renderHelper.createPopupImage(), displayName).link(renderHelper.createThumbnailImage()).width(310).script(renderHelper.createClickScript()));
            else
                out.write(PageFlowUtil.popupHelp(displayName).link(renderHelper.createThumbnailImage()).width(310).script(renderHelper.createClickScript()));
        }
        else
        {
            if (url != null && thumbnail && MimeMap.DEFAULT.isInlineImageFor(new File(filename)) )
            {
                if (renderHelper.renderPopupImage())
                    out.write(PageFlowUtil.popupHelp(renderHelper.createPopupImage(), displayName).link(renderHelper.createThumbnailImage()).width(310).script(renderHelper.createClickScript()));
                else
                    out.write(PageFlowUtil.popupHelp(displayName).link(renderHelper.createThumbnailImage()).width(310).script(renderHelper.createClickScript()));
            }
            else
            {
                renderHelper.createThumbnailImage().appendTo(out);
            }
        }
    }

    protected FileImageRenderHelper createRenderHelper(RenderContext ctx, String url, String filename, String displayName, @Nullable String fileIconUrl, @Nullable String popupIconUrl, boolean isThumbnail, boolean isImage)
    {
        return new FileImageRenderHelper(ctx, url, filename, displayName, fileIconUrl, popupIconUrl, isThumbnail, isImage);
    }

    /**
     * Helper class to generate the HTML for the various portions of a file or image grid cell content
     *
     * Tests to run if you touch this class: FileAttachmentColumnTest, InlineImagesAssayTest, InlineImagesListTest, SimpleModuleTest
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
        public Renderable createThumbnailImage()
        {
            if (_url != null && _isThumbnail && _isImage)
            {
                return IMG(
                    at(
                        style, "display:block; height:auto; vertical-align:middle; " + (_thumbnailWidth != null ? "width:" + _thumbnailWidth : "max-width:32px"),
                        src, _url, title, _displayName
                    )
                );
            }
            else
            {
                return DOM.createHtmlFragment(
                    IMG(
                        at(src, _ctx.getRequest().getContextPath() + (null != _fileIconUrl ? _fileIconUrl : Attachment.getFileIcon(_filename)), alt, "icon")
                    ),
                    HtmlString.NBSP,
                    _displayName
                );
            }
        }

        public boolean renderPopupImage()
        {
            return true;
        }

        // render the popup image to display on hover
        public Renderable createPopupImage()
        {
            return _url != null ? IMG(
                at(
                    style, "height:auto; " + (_popupWidth != null ? "width:" + _popupWidth : "max-width:300px"),
                    src, _url
                )
            ) : HtmlString.EMPTY_STRING;
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

            InputBuilder<?> input = InputBuilder.file()
                .name(formFieldName)
                .disabled(isDisabledInput(ctx))
                .needsWrapping(false);

            if (null != filename)
            {
                // Existing value, so tell the user the file name, allow the file to be removed, and a new file uploaded
                renderThumbnailAndRemoveLink(out, ctx, formFieldName, filename, input);
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

    private void renderThumbnailAndRemoveLink(HtmlWriter out, RenderContext ctx, String fieldName, String filename, InputBuilder<?> filePicker)
    {
        String divId = GUID.makeGUID();
        String linkId = "remove" + divId;

        DIV(
            id(divId)
                .data("fieldName", fieldName)
                .cl("lk-remove-file"),
            (Renderable) ret -> {
                renderIconAndFilename(ctx, out, filename, false, false);
                out.write(HtmlString.NBSP);
                out.write("[");
                out.write(LinkBuilder.simpleLink("remove", "#").id(linkId));
                out.write("]");
                return ret;
            }
        ).appendTo(out);
        String innerHtml = filePicker + "<input type=\"hidden\" name=\"deletedAttachments\" value=\"" + filename + "\"><span class=\"labkey-message\">" + getRemovalWarningText(filename) + "</span>";
        HttpView.currentPageConfig().addHandler(linkId, "click", "document.getElementById(" + jsString(divId) + ").innerHTML = " + jsString(innerHtml));
    }
}