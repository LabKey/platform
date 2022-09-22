/*
 * Copyright (c) 2009-2018 LabKey Corporation
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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.old.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.Path;
import org.labkey.api.util.UniqueID;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
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
    public static final Path GENERATED_VIEWS_PATH = Path.parse("views/gen");

    private static final Logger LOG = LogManager.getLogger(ModuleHtmlView.class);
    private static final ModuleResourceCache<Map<Path, ModuleHtmlViewDefinition>> MODULE_HTML_VIEW_DEFINITION_CACHE = ModuleResourceCaches.create("HTML view definitions", new ModuleHtmlViewCacheHandler(), ResourceRootProvider.getStandard(VIEWS_PATH), ResourceRootProvider.getStandard(GENERATED_VIEWS_PATH), ResourceRootProvider.getAssayProviders(VIEWS_PATH));

    private final ModuleHtmlViewDefinition _viewdef;

    public static Path getStandardPath(String viewName)
    {
        return VIEWS_PATH.append(viewName + ModuleHtmlViewDefinition.HTML_VIEW_EXTENSION);
    }

    public static Path getGeneratedViewPath(String viewName)
    {
        return GENERATED_VIEWS_PATH.append(viewName + ModuleHtmlViewDefinition.HTML_VIEW_EXTENSION);
    }

    public static Path getViewPath(Module module, String viewName)
    {
        Path standardPath = getStandardPath(viewName);
        return exists(module, standardPath) ? standardPath : getGeneratedViewPath(viewName);
    }

    /**
     * Quick check for existence of an HTML view at this path
     */
    public static boolean exists(Module module, Path path)
    {
        return null != MODULE_HTML_VIEW_DEFINITION_CACHE.getResourceMap(module).get(path);
    }

    /**
     * Quick check for existence of an HTML view with this name in the standard location /views/*
     */
    public static boolean exists(Module module, String viewName)
    {
        return exists(module, getStandardPath(viewName)) || exists(module, getGeneratedViewPath(viewName));
    }

    public static @Nullable ModuleHtmlView get(@NotNull Module module, @NotNull String viewName)
    {
        return get(module, getViewPath(module, viewName));
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


    public HtmlString replaceTokensForView(HtmlString html, ViewContext context, @Nullable WebPart webpart)
    {
        if (HtmlString.isBlank(html))
            return html;

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

        String ret = replaceTokens(html.toString(), context);
        ret = ret.replaceAll("<%=\\s*webpartContext\\s*%>", Matcher.quoteReplacement(webpartContext));
        return HtmlString.unsafe("<div id=\"" + wrapperDivId + "\">" + ret + "</div>");
    }

    public static String replaceTokens(String html, ViewContext context)
    {
        if (null == html)
            return null;

        String contextPath = null != context.getContextPath() ? context.getContextPath() : "invalid context path";
        String containerPath = null != context.getContainer() ? context.getContainer().getPath() : "invalid container";
        String scriptNonce = HttpView.currentPageConfig().getScriptNonce().toString();
        String ret = html.replaceAll("<%=\\s*contextPath\\s*%>", Matcher.quoteReplacement(contextPath)); // 17751
        ret = ret.replaceAll("<%=\\s*containerPath\\s*%>", Matcher.quoteReplacement(containerPath));
        ret = ret.replaceAll("<%=\\s*scriptNonce\\s*%>", Matcher.quoteReplacement(scriptNonce));

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
                assertEquals("HTML view definitions from the simpletest module", 9, MODULE_HTML_VIEW_DEFINITION_CACHE.getResourceMap(simpleTest).size());
        }
    }
}
