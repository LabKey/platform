/*
 * Copyright (c) 2004-2017 Fred Hutchinson Cancer Research Center
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
package org.labkey.api.view;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.action.ApiJsonWriter;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.data.Container;
import org.labkey.api.miniprofiler.MiniProfiler;
import org.labkey.api.miniprofiler.Timing;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.ErrorRenderer;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.WebPartFrame.FrameConfig;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.api.view.template.PageConfig;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 *
 * @param <ModelBean> the class to use as the data model for configuration
 */
public abstract class WebPartView<ModelBean> extends HttpView<ModelBean>
{
    /**
     * Warning: Any changes to this enum require corresponding changes in ViewServiceImpl
     */
    public enum FrameType
    {
        /** with portal widgets */
        PORTAL,
        /** title w/ hr */
        TITLE,
        DIALOG,
        /** just <div class=""> */
        DIV,
        LEFT_NAVIGATION,
        /** clean */
        NONE,
        NOT_HTML;    // same as NONE, just a marker

        public WebPartFrame getFrame(ViewContext context, FrameConfig config)
        {
            return ViewService.get().getFrame(this, context, config);
        }
    }


    public static final int DEFAULT_WEB_PART_ID = -1;

    protected final FrameConfig _frameConfig = new FrameConfig();

    private Throwable _prepareException = null;
    private boolean _isPrepared = false;
    private final boolean _devMode = AppProps.getInstance().isDevMode();
    protected String _debugViewDescription = null;

    private static final Logger LOG = Logger.getLogger(WebPartView.class);


    public boolean isEmpty()
    {
        return _frameConfig._isEmpty;
    }

    public void setEmpty(boolean empty)
    {
        _frameConfig._isEmpty = empty;
    }

    public ApiResponse renderToApiResponse()
    {
        Container container = getViewContext().getContainer();
        final HttpServletRequest request = getViewContext().getRequest();

        final LinkedHashSet<ClientDependency> dependencies = getClientDependencies();
        final LinkedHashSet<String> includes = new LinkedHashSet<>();
        final LinkedHashSet<String> implicitIncludes = new LinkedHashSet<>();
        PageFlowUtil.getJavaScriptFiles(container, dependencies, includes, implicitIncludes);

        final LinkedHashSet<String> cssScripts = new LinkedHashSet<>();
        final LinkedHashSet<String> implicitCssScripts = new LinkedHashSet<>();
        for (ClientDependency d : dependencies)
        {
            cssScripts.addAll(d.getCssPaths(container));
            implicitCssScripts.addAll(d.getCssPaths(container));
        }

        final Set<String> extCSSIncludes = PageFlowUtil.getExtJSStylesheets(container, dependencies);
        if (!extCSSIncludes.isEmpty())
        {
            cssScripts.addAll(extCSSIncludes);
            implicitCssScripts.addAll(extCSSIncludes);
        }

        getViewContext().getResponse().setContentType(ApiJsonWriter.CONTENT_TYPE_JSON);

        return writer ->
        {
            MockHttpServletResponse mr = new MockHttpResponseWithRealPassthrough(getViewContext().getResponse());
            mr.setCharacterEncoding("UTF-8");
            try
            {
                WebPartView.this.render(request, mr);
            }
            catch (MockHttpResponseWithRealPassthrough.SizeLimitExceededException e)
            {
                LOG.warn("Failed in renderToApiResponse() - " + e.getMessage() + " for URL " + getViewContext().getActionURL());
                throw e;
            }

            if (mr.getStatus() != HttpServletResponse.SC_OK)
            {
                WebPartView.this.render(request, getViewContext().getResponse());
            }
            else // 21477: Query editor fails to display simple errors
            {
                writer.startResponse();
                writer.writeProperty("html", mr.getContentAsString());
                writer.writeProperty("requiredJsScripts", includes);
                writer.writeProperty("implicitJsIncludes", implicitIncludes);
                writer.writeProperty("requiredCssScripts", cssScripts);
                writer.writeProperty("implicitCssIncludes", implicitCssScripts);
                writer.writeProperty("moduleContext", PageFlowUtil.getModuleClientContext(getViewContext(), dependencies));
                writer.endResponse();
            }
        };
    }

    public WebPartView(FrameType ft)
    {
        setFrame(ft);
    }

    public WebPartView(String title)
    {
        if (StringUtils.isEmpty(title))
            setFrame(FrameType.DIV);
        else
            setFrame(FrameType.PORTAL);
        setTitle(title);
    }

    public WebPartView(ModelBean model)
    {
        super(model);
        setFrame(FrameType.PORTAL);
    }

    public void setFrame(FrameType type)
    {
        _frameConfig._frame = type;
    }

    public FrameType getFrame()
    {
        return _frameConfig._frame;
    }

    public void setBodyClass(String className)
    {
        _frameConfig._className = className;
    }

    public void setTitle(CharSequence title)
    {
        _frameConfig._title = null==title ? null : title.toString();
    }

    public String getTitle()
    {
        return _frameConfig._title;
    }

    /** Use ActionURL version instead */
    @Deprecated
    public void setTitleHref(String href)
    {
        _frameConfig._titleHref = href;
    }

    public void setTitlePopupHelp(String title, String body)
    {
        _frameConfig._helpPopup = PageFlowUtil.helpPopup(title, body);
    }

    public void setTitleHref(ActionURL href)
    {
        _frameConfig._titleHref =  href.getLocalURIString();
    }


    public void setIsOnlyWebPartOnPage(boolean b)
    {
        /* by default we don't care */
    }

    public int getWebPartRowId()
    {
        return _frameConfig._webpart == null ? -1 : _frameConfig._webpart.rowId;
    }

    public void setWebPart(Portal.WebPart wp)
    {
        _frameConfig._webpart = wp;
    }

    public NavTree getPortalLinks()
    {
        return _frameConfig._portalLinks;
    }

    public void setPortalLinks(NavTree navTree)
    {
        _frameConfig._portalLinks = navTree;
    }

    public void setCustomize(NavTree tree)
    {
        _frameConfig._customize = tree;
    }

    public final NavTree getCustomize()
    {
        return _frameConfig._customize;
    }

    public void enableExpandCollapse(String rootId, boolean collapsed)
    {
        _frameConfig._isCollapsible = true;
        _frameConfig._collapsed = collapsed;
        _frameConfig._rootId = rootId;
    }

    public void setNavMenu(NavTree navMenu)
    {
        _frameConfig._navMenu = navMenu;
    }

    public NavTree getNavMenu()
    {
        return _frameConfig._navMenu;
    }

    public void addCustomMenu(NavTree tree)
    {
        if (_frameConfig._customMenus == null)
            _frameConfig._customMenus = new ArrayList<>();
        _frameConfig._customMenus.add(tree);
    }

    public void addFloatingButton(NavTree tree)
    {
        if (_frameConfig._floatingBtns == null)
            _frameConfig._floatingBtns = new ArrayList<>();
        _frameConfig._floatingBtns.add(tree);
    }

    public void setShowTitle(boolean showTitle)
    {
        _frameConfig._showTitle = showTitle;
    }

    public boolean showTitle()
    {
        return _frameConfig._showTitle;
    }

    public void setCollapsible(boolean b)
    {
        _frameConfig._isCollapsible = b;
    }

    public boolean isCollapsible()
    {
        return _frameConfig._isCollapsible;
    }

    public void setCloseURL(URLHelper url)
    {
        _frameConfig._closeURL = url;
    }

    public void setLocation(String location)
    {
        _frameConfig._location = location;
    }

    public String getLocation()
    {
        return _frameConfig._location;
    }

    public boolean isWebPart()
    {
        return _frameConfig._isWebpart;
    }

    public void setIsWebPart(boolean isWebPart)
    {
        _frameConfig._isWebpart = isWebPart;
    }

    public boolean isShowFloatingCustomBtn()
    {
        return _frameConfig._showfloatingCustomBtn;
    }

    public void setShowFloatingCustomBtn(boolean floatCustomMenu)
    {
        _frameConfig._showfloatingCustomBtn = floatCustomMenu;
    }

    protected WebPartFrame getWebPartFrame()
    {
        return _frameConfig._frame.getFrame(getViewContext(), _frameConfig);
    }
    
    @Override
    protected final void renderInternal(ModelBean model, HttpServletRequest request, HttpServletResponse response) throws Exception
    {
        prepare(model);
        if (!isVisible())
            return;

        Throwable exceptionToRender = _prepareException;
        String errorMessage = null;

        String name = StringUtils.defaultString(_debugViewDescription, this.getClass().getSimpleName());
        try (Timing ignored = MiniProfiler.step(name))
        {
            boolean isDebugHtml = _devMode && _frameConfig._frame != FrameType.NOT_HTML && StringUtils.startsWith(response.getContentType(), "text/html");
            if (isDebugHtml)
                response.getWriter().print("<!--" + name + "-->");

            WebPartFrame frame = getWebPartFrame();

            frame.doStartTag(response.getWriter());

            if (exceptionToRender == null)
            {
                try
                {
                    renderView(getModelBean(), request, response);
                }
                catch (RedirectException x)
                {
                    Logger.getLogger(WebPartView.class).warn("Shouldn't throw redirect during renderView()", x);
                    throw x;
                }
                catch (UnauthorizedException x)
                {
                    Logger.getLogger(WebPartView.class).warn("Shouldn't throw unauthorized during renderView()", x);
                    errorMessage = ExceptionUtil.getUnauthorizedMessage(getViewContext());
                }
                catch (Throwable t)
                {
                    exceptionToRender = ExceptionUtil.unwrapException(t);

                    //
                    // issue:21209 - don't hide exceptions that we should be showing to the user (configuration, incorrect JSON, etc)
                    // Render these now so that the user can at least know what is wrong instead of just getting a blank webpart. The one
                    // difference is that we won't log the exception if it is deemed ignorable.
                    //

                    if (!ExceptionUtil.isIgnorable(exceptionToRender))
                    {
                        Logger log = Logger.getLogger(WebPartView.class);
                        ActionURL url = getViewContext().getActionURL();
                        log.error("renderView() exception in " + getClass().getName() + (null != url ? " while responding to " + getViewContext().getActionURL().getLocalURIString() : ""), exceptionToRender);
                        log.error("View creation stacktrace:" + ExceptionUtil.renderStackTrace(_creationStackTrace));
                    }
                }
            }

            //if we received an exception during prepare or render, we'll display it here
            if (exceptionToRender != null || errorMessage != null)
            {
                if (errorMessage != null)
                {
                    response.getWriter().write(errorMessage);
                }
                if (exceptionToRender != null)
                {
                    renderException(exceptionToRender, request, response);
                }
            }

            frame.doEndTag(response.getWriter());
            if (isDebugHtml)
                response.getWriter().print("<!--/" + name + "-->");
        }
    }

    /**
     * Subclasses can override this method to handle how they respond
     * to different types of exceptions
     */
    protected void renderException(Throwable t, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
    {
        int status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        String message = "An unexpected error occurred";
        ErrorRenderer renderer = ExceptionUtil.getErrorRenderer(status, message, t, request, true, false);
        renderer.renderStart(response.getWriter());
        renderer.renderContent(response.getWriter(), request, null);
        renderer.renderEnd(response.getWriter());
    }


    @Override
    public boolean isVisible()
    {
        prepare(getModelBean());
        return super.isVisible();
    }


    protected final void prepare(ModelBean model)
    {
        if (_isPrepared)
            return;

        try
        {
            // HttpView.render() sets ActionURL(), but render may not have been called yet
            // UNDONE: MAB: clean up the propagation/initialization of viewURLHelper
            HttpView.initViewContext(getViewContext());
            this.prepareWebPart(model);
        }
        catch (Throwable t)
        {
            _prepareException = t;
        }
        finally
        {
            _isPrepared = true;
        }
    }


    protected void prepareWebPart(ModelBean model)
            throws ServletException
    {
    }


    protected void renderView(ModelBean model, HttpServletRequest request, HttpServletResponse response) throws Exception
    {
        renderView(model, response.getWriter());
    }


    protected void renderView(ModelBean model, PrintWriter out) throws Exception
    {
        throw new IllegalStateException("override renderView");
    }

    public boolean isEmbedded()
    {
        return _frameConfig._isEmbedded;
    }

    public void setEmbedded(boolean embedded)
    {
        _frameConfig._isEmbedded = embedded;
    }

    protected static class WebPartCollapsible implements Collapsible
    {
        private boolean _collapsed;
        private String _id;

        public WebPartCollapsible(String id)
        {
            _id = id;
        }

        public void setCollapsed(boolean collapsed)
        {
            _collapsed = collapsed;
        }

        public boolean isCollapsed()
        {
            return _collapsed;
        }

        @NotNull
        @Override
        public List<? extends Collapsible> getChildren()
        {
            return Collections.emptyList();
        }

        public Collapsible findSubtree(String path)
        {
            return null;
        }

        public String getId()
        {
            return _id;
        }
    }


    public boolean isHidePageTitle()
    {
        return _frameConfig._hidePageTitle;
    }

    public void setHidePageTitle(boolean hidePageTitle)
    {
        _frameConfig._hidePageTitle = hidePageTitle;
    }
}
