/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

package org.labkey.wiki.model;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.data.Container;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.HtmlStringBuilder;
import org.labkey.api.util.MemTracker;
import org.labkey.api.view.HttpView;
import org.labkey.api.wiki.FormattedHtml;
import org.labkey.api.wiki.WikiRenderer;
import org.labkey.api.wiki.WikiRendererType;
import org.labkey.api.wiki.WikiRenderingService;
import org.labkey.api.wiki.WikiRenderingService.SubstitutionMode;
import org.labkey.wiki.WikiContentCache;
import org.labkey.wiki.WikiManager;

import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.regex.Pattern;

public class WikiVersion
{
    private int _rowId;
    private String _pageEntityId;
    private int _version;
    private String _title;
    private String _body;
    private int _createdBy;
    private Date _created;
    private WikiRendererType _rendererType;
    private boolean _cache = true;

    private String _wikiName;

    public WikiVersion()
    {
        MemTracker.getInstance().put(this);
    }

    public WikiVersion(String wikiname)
    {
        _wikiName = wikiname;
        MemTracker.getInstance().put(this);
    }

    /**
     * Copy constructor used when creating a new version of a wiki
     *
     * @param copy The current latest version
     */
    public WikiVersion(WikiVersion copy)
    {
        if (null == copy)
            return;
        _pageEntityId = copy._pageEntityId;
        _title = copy._title;
        _body = copy._body;
        _rendererType = copy._rendererType;
        _wikiName = copy._wikiName;
        MemTracker.getInstance().put(this);
    }

    public String getName()
    {
        return _wikiName;
    }

    public void setName(String wikiname)
    {
        _wikiName = wikiname;
    }

    public int getCreatedBy()
    {
        return _createdBy;
    }

    public void setCreatedBy(int createdBy)
    {
        _createdBy = createdBy;
    }

    public Date getCreated()
    {
        return _created;
    }

    public void setCreated(Date created)
    {
        _created = created;
    }

    public String getPageEntityId()
    {
        return _pageEntityId;
    }

    public void setPageEntityId(String pageEntityId)
    {
        _pageEntityId = pageEntityId;
    }

    // TODO: WikiVersion should know its wiki & container
    // WARNING: This method drops all client dependencies declared by the wiki and embedded webparts! This HTML will
    // likely not render correctly, so it has limited uses.
    public HtmlString getHtml(Container c, Wiki wiki)
    {
        return render(null, c, wiki);
    }

    /** Adds all client dependencies to the view and returns the HTML to render */
    public HtmlString render(@Nullable HttpView<?> view, Container c, Wiki wiki)
    {
        FormattedHtml rendered = WikiContentCache.getHtml(c, wiki, this, _cache);
        if (null != view)
            view.addClientDependencies(rendered.getClientDependencies());

        return HtmlStringBuilder.of(WikiRenderingService.WIKI_PREFIX)
            .append(rendered.getHtml())
            .append(WikiRenderingService.WIKI_SUFFIX).getHtmlString();
    }

    public String getTitle()
    {
        if (null == _title)
            _title = (null == _wikiName || "default".equals(_wikiName)) ? "Wiki" : _wikiName;
        return _title;
    }

    public void setTitle(String title)
    {
        _title = title;
    }

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    public int getVersion()
    {
        return _version;
    }

    public void setVersion(int version)
    {
        _version = version;
    }

    public String getBody()
    {
        return _body;
    }

    public void setBody(String body)
    {
        _body = body;
    }

    @Deprecated
    // Only used for bean binding -- use getRenderTypeEnum() instead
    public String getRendererType()
    {
        return getRendererTypeEnum().name();
    }

    public WikiRendererType getRendererTypeEnum()
    {
        if (_rendererType == null)
            _rendererType = WikiManager.DEFAULT_WIKI_RENDERER_TYPE;

        return _rendererType;
    }

    public void setRendererType(String rendererTypeName)
    {
        _rendererType = WikiRendererType.valueOf(rendererTypeName);
    }

    public void setRendererTypeEnum(WikiRendererType rendererType)
    {
        _rendererType = rendererType;
    }

    public WikiRenderer getRenderer(SubstitutionMode substitutionMode,
                                    String hrefPrefix,
                                    String attachPrefix,
                                    Map<String, String> nameTitleMap,
                                    Collection<? extends Attachment> attachments,
                                    String sourceDescription)
    {
        if (_rendererType == null)
            _rendererType = WikiManager.DEFAULT_WIKI_RENDERER_TYPE;

        return WikiRenderingService.get().getRenderer(_rendererType, substitutionMode, hrefPrefix, attachPrefix, nameTitleMap, attachments, sourceDescription);
    }

    // Cache the rendered wiki content by default; set to false to avoid caching
    public void setCacheContent(boolean cache)
    {
        _cache = cache;
    }

    private static final Pattern NON_VISUAL_RE = Pattern.compile("(<(script|form)[\\s>]|\\$\\{labkey\\.)");

    public boolean hasNonVisualElements()
    {
        // look for form, script tag and ${labkey...} expressions
        return _body != null && NON_VISUAL_RE.matcher(_body.toLowerCase()).find();
    }
}
