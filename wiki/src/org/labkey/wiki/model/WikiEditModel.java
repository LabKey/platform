/*
 * Copyright (c) 2008-2016 LabKey Corporation
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

import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.wiki.BaseWikiPermissions;
import org.labkey.wiki.WikiManager;
import org.labkey.wiki.WikiSelectManager;

import java.util.Set;

/**
 * Model bean for the wikiEdit.jsp view
 *
 * User: Dave
 * Date: Mar 17, 2008
 * Time: 2:25:41 PM
 */
public class WikiEditModel
{
    private final Wiki _wiki;
    private final WikiVersion _wikiVersion;
    private final String _redir;
    private final String _cancelRedir;
    private final Container _container;
    private final String _format;
    private final String _defName;
    private final boolean _useVisualEditor;
    private final int _webPartId;
    private final User _user;

    public WikiEditModel(Container container, Wiki wiki, WikiVersion wikiVersion, String redir,
                         String cancelRedir, String format, String defName, boolean useVisualEditor,
                         int webPartId, User user)
    {
        _container = container;
        _wiki = wiki;
        _wikiVersion = wikiVersion;
        _redir = redir;
        _cancelRedir = cancelRedir;
        _format = format;
        _defName = defName;
        _useVisualEditor = useVisualEditor;
        _webPartId = webPartId;
        _user = user;
    }
    
    public Wiki getWiki()
    {
        return _wiki;
    }

    public String getRedir()
    {
        return PageFlowUtil.jsString(_redir);
    }

    public String getCancelRedir()
    {
        return null == _cancelRedir ? "null" : PageFlowUtil.jsString(_cancelRedir);
    }

    public String getBody()
    {
        return null == _wikiVersion || null  == _wikiVersion.getBody() ? "null" : PageFlowUtil.jsString(_wikiVersion.getBody());
    }

    public String getName()
    {
        return null == _wiki ? getDefName() : PageFlowUtil.jsString(_wiki.getName());
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

    public int getPageVersionId()
    {
        return null == _wiki ? -1 : _wiki.getPageVersionId();
    }

    public String getRendererType()
    {
        if (null == _wikiVersion)
            return _format != null ? PageFlowUtil.jsString(_format)
                    : PageFlowUtil.jsString(WikiManager.DEFAULT_WIKI_RENDERER_TYPE.name());
        else
            return PageFlowUtil.jsString(_wikiVersion.getRendererTypeEnum().name());
    }

    public Set<WikiTree> getPossibleParents()
    {
        return WikiSelectManager.getPossibleParents(_container, _wiki);
    }

    public boolean hasAttachments()
    {
        return null != _wiki && null != _wiki.getAttachments() && _wiki.getAttachments().size() > 0;
    }

    public String getDefName()
    {
        return null == _defName ? "null" : PageFlowUtil.jsString(_defName);
    }

    public boolean useVisualEditor()
    {
        return _useVisualEditor;
    }

    public int getWebPartId()
    {
        return _webPartId;
    }

    public boolean canUserDelete()
    {
        if (null == _wiki)
        {
            return _container.hasPermission(_user, DeletePermission.class);
        }
        else
        {
            BaseWikiPermissions perms = new BaseWikiPermissions(_user, _container);
            return perms.allowDelete(_wiki);
        }
    }

    public boolean isShowAttachments()
    {
        return null == _wiki || _wiki.isShowAttachments();
    }

    public boolean isShouldIndex()
    {
        return null == _wiki || _wiki.isShouldIndex();
    }
}
