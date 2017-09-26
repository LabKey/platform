/*
 * Copyright (c) 2009-2017 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.util.Path;
import org.labkey.api.util.UniqueID;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.ModuleHtmlViewCacheHandler;
import org.labkey.api.view.Portal.WebPart;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.api.view.template.PageConfig;

import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

/**
 * HTML web part based on .html file stored in a module's ./resources/views directory.
 * User: Dave
 * Date: Jan 23, 2009
 */
public class ModuleHtmlView extends HtmlView
{
    public static final Path VIEWS_PATH = Path.parse("views");

    private static final Logger LOG = Logger.getLogger(ModuleHtmlView.class);
    private static final ModuleResourceCache<Map<Path, ModuleHtmlViewDefinition>> MODULE_HTML_VIEW_DEFINITION_CACHE = ModuleResourceCaches.create("HTML view definitions", new ModuleHtmlViewCacheHandler(), ResourceRootProvider.getStandard(VIEWS_PATH), ResourceRootProvider.getAssayProviders(VIEWS_PATH));

    private final ModuleHtmlViewDefinition _viewdef;

    public static Path getStandardPath(String viewName)
    {
        return VIEWS_PATH.append(viewName + ModuleHtmlViewDefinition.HTML_VIEW_EXTENSION);
    }

    /**
     * Quick check for existence of an HTML view at this path
     * @param module
     * @param path
     * @return
     */
    public static boolean exists(Module module, Path path)
    {
        return null != MODULE_HTML_VIEW_DEFINITION_CACHE.getResourceMap(module).get(path);
    }

    /**
     * Quick check for existence of an HTML view with this name in the standard location /views/*
     * @param module
     * @param viewName
     * @return
     */
    public static boolean exists(Module module, String viewName)
    {
        return exists(module, getStandardPath(viewName));
    }

    public static @Nullable ModuleHtmlView get(@NotNull Module module, @NotNull String viewName)
    {
        return get(module, getStandardPath(viewName));
    }

    public static @Nullable ModuleHtmlView get(@NotNull Module module, @NotNull Path path)
    {
        return get(module, path, null);
    }

    public static @Nullable ModuleHtmlView get(@NotNull Module module, @NotNull Path path, @Nullable WebPart webpart)
    {
        ModuleHtmlViewDefinition viewDefinition = MODULE_HTML_VIEW_DEFINITION_CACHE.getResourceMap(module).get(path);

        if (null == viewDefinition)
            return null;

        return new ModuleHtmlView(viewDefinition, module, webpart);
    }

    private ModuleHtmlView(ModuleHtmlViewDefinition viewdef, @NotNull Module module, @Nullable WebPart webpart)
    {
        super(null);
        _debugViewDescription = getClass().getSimpleName() + ": " + module.getName() + "/" + viewdef.getName();

        _viewdef = viewdef;

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


    public String replaceTokensForView(String html, ViewContext context, @Nullable WebPart webpart)
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

    public String getHtml()
    {
        return _viewdef.getHtml();
    }


    public static class TestCase extends Assert
    {
        @Test
        public void testModuleResourceCache()
        {
            // Load all the HTML view definitions to ensure no exceptions
            int viewCount = MODULE_HTML_VIEW_DEFINITION_CACHE.streamAllResourceMaps()
                .mapToInt(Map::size)
                .sum();

            LOG.info(viewCount + " HTML view definitions defined in all modules");

            // Make sure the cache retrieves the expected number of HTML view definitions from the simpletest module, if present

            Module simpleTest = ModuleLoader.getInstance().getModule("simpletest");

            if (null != simpleTest)
                assertEquals("HTML view definitions from the simpletest module", 6, MODULE_HTML_VIEW_DEFINITION_CACHE.getResourceMap(simpleTest).size());
        }
    }
}
