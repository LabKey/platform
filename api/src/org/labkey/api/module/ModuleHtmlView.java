/*
 * Copyright (c) 2009-2010 LabKey Corporation
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
package org.labkey.api.module;

import org.labkey.api.cache.CacheI;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.resource.Resource;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.template.PageConfig;

/*
* User: Dave
* Date: Jan 23, 2009
* Time: 4:48:17 PM
*/

/**
 * Html view based on HTML source stored in a module
 */
public class ModuleHtmlView extends HtmlView
{
    private static final CacheI<String, ModuleHtmlViewDefinition> VIEW_DEF_CACHE = CacheManager.getCache(1024, CacheManager.HOUR, "Module HTML view definition cache");

    private ModuleHtmlViewDefinition _viewdef = null;

    public ModuleHtmlView(Resource r)
    {
        super(null);
        _viewdef = getViewDef(r);
        setTitle(_viewdef.getTitle());
        setHtml(replaceTokens(_viewdef.getHtml()));
        if(null != _viewdef.getFrameType())
            setFrame(_viewdef.getFrameType());
    }

    public String replaceTokens(String html)
    {
        if (null == html)
            return null;
        
        ViewContext context = getViewContext();
        String contextPath = null != context.getContextPath() ? context.getContextPath() : "invalid context path";
        String containerPath = null != context.getContainer() ? context.getContainer().getPath() : "invalid container";

        String ret = html.replaceAll("<%=\\s*contextPath\\s*%>", contextPath);
        ret = ret.replaceAll("<%=\\s*containerPath\\s*%>", containerPath);
        return ret;
    }

    public static ModuleHtmlViewDefinition getViewDef(Resource r)
    {
        String cacheKey = r.toString();
        ModuleHtmlViewDefinition viewdef = VIEW_DEF_CACHE.get(cacheKey);
        if (null == viewdef || viewdef.isStale())
        {
            viewdef = new ModuleHtmlViewDefinition(r);
            VIEW_DEF_CACHE.put(cacheKey, viewdef);
        }
        return viewdef;
    }

    public PageConfig.Template getPageTemplate()
    {
        return _viewdef.getPageTemplate();
    }

    public boolean isRequiresLogin()
    {
        return _viewdef.isRequiresLogin();
    }

    public int getRequiredPerms()
    {
        return _viewdef.getRequiredPerms();
    }
}
