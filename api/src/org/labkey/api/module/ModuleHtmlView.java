/*
 * Copyright (c) 2009-2014 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.resource.Resolver;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.util.UniqueID;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.ModuleHtmlViewCacheHandler;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.api.view.template.PageConfig;

import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

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
    public static final PathBasedModuleResourceCache<ModuleHtmlViewDefinition> MODULE_HTML_VIEW_DEFINITION_CACHE = ModuleResourceCaches.create("HTML view definitions", new ModuleHtmlViewCacheHandler());

    private final ModuleHtmlViewDefinition _viewdef;

    public ModuleHtmlView(@NotNull Resource r)
    {
        this(r, null);
    }


    public ModuleHtmlView(@NotNull Resource r, @Nullable Portal.WebPart webpart)
    {
        super(null);
        _debugViewDescription = this.getClass().getSimpleName() + ": " + r.getPath().toString();

        // This is hackery, but at least we're now explicit about it
        Resolver resolver = r.getResolver();
        assert resolver instanceof ModuleResourceResolver;
        Module module = ((ModuleResourceResolver) resolver).getModule();

        _viewdef = MODULE_HTML_VIEW_DEFINITION_CACHE.getResource(module, r.getPath());
        assert null != _viewdef;

        setTitle(_viewdef.getTitle());
        setClientDependencies(_viewdef.getClientDependencies());
        setHtml(replaceTokensForView(_viewdef.getHtml(), getViewContext(), webpart));
        if (null != _viewdef.getFrameType())
            setFrame(_viewdef.getFrameType());

        _clientDependencies.add(ClientDependency.fromModule(module));

        //if this HTML view uses a portal frame, we automatically hide the redundant page title
        if (FrameType.PORTAL.equals(getFrame()))
            setHidePageTitle(true);
    }


    public String replaceTokensForView(String html, ViewContext context, @Nullable Portal.WebPart webpart)
    {
        if (null == html)
            return null;

        String wrapperDivId = "ModuleHtmlView_" + UniqueID.getServerSessionScopedUID();
        int id = null == webpart ? DEFAULT_WEB_PART_ID : webpart.getRowId();

        JSONObject config = new JSONObject();
        config.put("wrapperDivId", wrapperDivId);
        config.put("id", id);
        JSONObject properties = new JSONObject();
        config.put("properties", properties);
        if (null != webpart)
        {
            for (Map.Entry<String,String> e : webpart.getPropertyMap().entrySet())
                config.put(e.getKey(), e.getValue());
        }

        String webpartContext = config.toString();

        String ret = replaceTokens(html, context);
        ret = ret.replaceAll("<%=\\s*webpartContext\\s*%>", Matcher.quoteReplacement(webpartContext));
        return "<div id=\"" + wrapperDivId + "\">" + ret + "</div>";
    }

    public static String replaceTokens(String html, ViewContext context)
    {
        if (null == html)
            return null;

        String contextPath = null != context.getContextPath() ? context.getContextPath() : "invalid context path";
        String containerPath = null != context.getContainer() ? context.getContainer().getPath() : "invalid container";
        String ret = html.replaceAll("<%=\\s*contextPath\\s*%>", Matcher.quoteReplacement(contextPath)); // 17751
        ret = ret.replaceAll("<%=\\s*containerPath\\s*%>", Matcher.quoteReplacement(containerPath));

        return ret;
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

    public Set<Class<? extends Permission>> getRequiredPermissionClasses()
    {
        return _viewdef.getRequiredPermissionClasses();
    }
}
