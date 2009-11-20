/*
 * Copyright (c) 2006-2009 LabKey Corporation
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

import org.labkey.api.attachments.Attachment;
import org.labkey.api.data.Container;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.HString;
import org.labkey.api.wiki.FormattedHtml;
import org.labkey.api.wiki.WikiRenderer;
import org.labkey.api.wiki.WikiRendererType;
import org.labkey.api.wiki.WikiService;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.wiki.ServiceImpl;
import org.labkey.wiki.WikiManager;

import java.sql.SQLException;
import java.util.Date;
import java.util.Map;


/**
 * User: Tamra Myers
 * Date: Feb 14, 2006
 * Time: 1:31:25 PM
 */
public class WikiVersion implements WikiRenderer.WikiLinkable
{
    private int _rowId;
    private String _pageEntityId;
    private int _version;
    private HString _title;
    private String _body;
    private int _createdBy;
    private Date _created;
    private WikiRendererType _rendererType;

    private String _html = null;
    private HString _wikiName;

    public WikiVersion()
    {
        assert MemTracker.put(this);
    }

    public WikiVersion(HString wikiname)
    {
        _wikiName = wikiname;
        assert MemTracker.put(this);
    }

    public HString getName()
    {
        return _wikiName;
    }

    public void setName(HString wikiname)
    {
        _wikiName = wikiname;
    }

    public int getCreatedBy()
    {
        return _createdBy;
    }

    public void setCreatedBy(int createdBy)
    {
        this._createdBy = createdBy;
    }

    public Date getCreated()
    {
        return _created;
    }

    public void setCreated(Date created)
    {
        this._created = created;
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
    public String getHtml(Container c, Wiki wiki) throws SQLException
    {
        if (null != _html)
            return _html;

        FormattedHtml formattedHtml = WikiManager.formatWiki(c, wiki, this);

        if (!formattedHtml.isVolatile())
            _html = formattedHtml.getHtml();

        return formattedHtml.getHtml();
    }

    public HString getTitle()
    {
        if (null == _title)
            _title = (null == _wikiName || HString.eq(_wikiName,"default")) ? new HString("Wiki",false) : _wikiName;
        return _title;
    }

    public void setTitle(HString title)
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

    //returns string corresponding to name of enum entry
    public String getRendererType()
    {
        if (_rendererType == null)
            _rendererType = ServiceImpl.DEFAULT_WIKI_RENDERER_TYPE;

        return _rendererType.name();
    }

    public void setRendererType(String rendererType)
    {
        _rendererType = WikiRendererType.valueOf(rendererType);
    }

    //we're not currently calling this one....
//    public WikiRenderer getRenderer()
//    {
//        return getRenderer(null, null, null, null);
//    }

    public WikiRenderer getRenderer(String hrefPrefix,
                                    String attachPrefix,
                                    Map<HString, WikiRenderer.WikiLinkable> pages,
                                    Attachment[] attachments)
    {
        if (_rendererType == null)
            _rendererType = ServiceImpl.DEFAULT_WIKI_RENDERER_TYPE;

        //TODO: why are we calling a version of getRenderer that doesn't exist on the service interface?
        WikiService svc = ServiceRegistry.get().getService(WikiService.class);
        return null == svc ? null : ((ServiceImpl)svc).getRenderer(_rendererType, hrefPrefix, attachPrefix, pages, attachments);
    }
}
