/*
 * Copyright (c) 2007 LabKey Software Foundation
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

import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.wiki.WikiRendererType;
import org.labkey.api.wiki.WikiService;
import org.labkey.api.data.Container;
import org.labkey.api.view.ActionURL;
import org.labkey.wiki.ServiceImpl;
import org.labkey.wiki.WikiManager;
import org.labkey.wiki.WikiController;

import java.util.List;

/**
 * Model bean for the wikiEdit.jsp view
 *
 * Created by IntelliJ IDEA.
 * User: Dave
 * Date: Mar 17, 2008
 * Time: 2:25:41 PM
 */
public class WikiEditModel
{
    private Wiki _wiki;
    private WikiVersion _wikiVersion;
    private String _redir;
    private Container _container;

    public WikiEditModel(Container container, Wiki wiki, WikiVersion wikiVersion, String redir)
    {
        _container = container;
        _wiki = wiki;
        _wikiVersion = wikiVersion;
        _redir = redir;
    }
    
    public Wiki getWiki()
    {
        return _wiki;
    }

    public void setWiki(Wiki wiki)
    {
        _wiki = wiki;
    }

    public WikiVersion getWikiVersion()
    {
        return _wikiVersion;
    }

    public void setWikiVersion(WikiVersion wikiVersion)
    {
        _wikiVersion = wikiVersion;
    }

    public String getRedir()
    {
        if(null == _redir || _redir.length() == 0)
            return PageFlowUtil.jsString(new ActionURL(WikiController.BeginAction.class, _container).getLocalURIString());
        else
            return PageFlowUtil.jsString(_redir);
    }

    public void setRedir(String redir)
    {
        _redir = redir;
    }

    public String getBody()
    {
        return null == _wikiVersion || null  == _wikiVersion.getBody() ? "null" : PageFlowUtil.jsString(_wikiVersion.getBody());
    }

    public String getName()
    {
        return null == _wiki ? "null" : PageFlowUtil.jsString(_wiki.getName());
    }

    public String getTitle()
    {
        return null == _wikiVersion || null == _wikiVersion.getTitle() ? "null" : PageFlowUtil.jsString(_wikiVersion.getTitle());
    }

    public String getEntityId()
    {
        return null == _wiki ? "null" : "'" + _wiki.getEntityId() + "'";
    }

    public String getRowId()
    {
        return null == _wiki ? "null" : String.valueOf(_wiki.getRowId());
    }

    public int getParent()
    {
        return null == _wiki ? -1 : _wiki.getParent();
    }

    public String getRendererType()
    {
        return null == _wikiVersion ? 
                PageFlowUtil.jsString(ServiceImpl.get().getDefaultWikiRendererType().name()) :
                PageFlowUtil.jsString(_wikiVersion.getRendererType());
    }

    public boolean isCurrentRendererType(WikiRendererType type)
    {
        if(null == _wikiVersion)
            return ServiceImpl.get().getDefaultWikiRendererType() == type;
        else
            return type.name().equalsIgnoreCase(_wikiVersion.getRendererType());
    }

    public List<Wiki> getPossibleParents()
    {
        List<Wiki> parents = WikiManager.getPageList(_container);

        //remove the current wiki from the list
        //so that it can't become its own parent
        if(null != _wiki)
        {
            for(Wiki wiki : parents)
            {
                if(wiki.getRowId() == _wiki.getRowId())
                {
                    parents.remove(wiki);
                    break;
                }
            }
        }
        
        return parents;
    }

    public boolean hasAttachments()
    {
        return null != _wiki && null != _wiki.getAttachments() && _wiki.getAttachments().size() > 0;
    }
}
