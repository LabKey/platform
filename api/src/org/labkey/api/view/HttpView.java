/*
 * Copyright (c) 2004-2018 Fred Hutchinson Cancer Research Center
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

import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.HasViewContext;
import org.labkey.api.data.Container;
import org.labkey.api.miniprofiler.MiniProfiler;
import org.labkey.api.util.Debug;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.api.view.template.PageConfig;
import org.springframework.beans.PropertyValues;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.View;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.function.Supplier;


/// Base class for LabKey's server-side HTML rendering components (views).
///
/// `HttpView` serves a dual role: it is both a Spring [View] (capable of rendering a response)
/// and a [ModelAndView][org.springframework.web.servlet.ModelAndView] (carrying its own model bean
/// of type `ModelBean`). This dual nature means an `HttpView` can be rendered directly by Spring's
/// `DispatcherServlet` and can also be composed inside other views via [#include(ModelAndView)].
///
/// ## Subclassing
///
/// Subclasses typically override one of the `renderInternal` methods:
///
/// - [#renderInternal(Object, PrintWriter)] — for simple HTML output via a `PrintWriter`.
/// - [#renderInternal(Object, HttpServletRequest, HttpServletResponse)] — when full access to the
///   servlet request/response is needed (e.g., [JspView]).
///
/// ## View stack and thread-local context
///
/// During rendering, `HttpView` maintains a thread-local stack of active views
/// ([ViewStackEntry] records). This allows any code running on the render thread to access
/// the current request, response, [ViewContext], and [PageConfig] via static helpers:
///
/// - [#currentRequest()] / [#currentResponse()]
/// - [#currentContext()] — the [ViewContext] of the innermost `HttpView` on the stack.
/// - [#currentPageConfig()] — the active page configuration.
/// - [#currentView()] / [#currentModel()]
///
/// The stack is pushed automatically in [#render] and can be initialized for non-view
/// contexts (e.g., filters, servlets) via [#initForRequest].
///
/// ## Composition
///
/// Views can be nested using named child views stored in [#_views]:
///
/// - [#setBody] / [#getBody] — convenience accessors for the well-known `BODY` slot.
/// - [#setView(String, ModelAndView)] / [#getView(String)] — arbitrary named slots.
/// - [#include(ModelAndView)] — renders a child view inline within the current response.
///
/// ## Client dependencies
///
/// Each view can declare CSS/JS dependencies via [#addClientDependency]. These are
/// aggregated recursively over the view tree by [#getClientDependencies()] so that page
/// templates can emit the full set of required resources.
///
/// @param <ModelBean> the type of the model object this view renders
/// @see WebPartView
/// @see JspView
/// @see HtmlView
/// @see org.labkey.api.query.QueryView
public abstract class HttpView<ModelBean> extends DefaultModelAndView<ModelBean> implements View, HasViewContext
{
    private static final int _debug = Debug.getLevel(HttpView.class);
    protected LinkedHashSet<ClientDependency> _clientDependencies = new LinkedHashSet<>();

    private static final ThreadLocal<ViewStack> _viewContexts = ThreadLocal.withInitial(ViewStack::new);


    private static class ViewStack extends Stack<ViewStackEntry>
    {
        @Override
        public ViewStackEntry push(ViewStackEntry item)
        {
            return super.push(item);
        }
    }

    public static String BODY = WebPartFactory.LOCATION_BODY; // specify in one place


    //
    // instance variables
    //
    
    protected Map<String, ModelAndView> _views;
    protected ViewContext _viewContext;
    protected Map<?, ?> _renderMap = null;
    @Nullable
    protected final StackTraceElement[] _creationStackTrace;



    /**
     * Most view writers should not need a request or response, just a Writer.  However,
     * JspView needs request, response as does the View interface.  Since I don't want
     * to require any particular implementation of Model, I track request/response in the
     * _viewContexts stack.
     */
    protected record ViewStackEntry(ModelAndView mv, HttpServletRequest request, HttpServletResponse response, PageConfig pageConfig) {}


    /**
     * convert org.springframework.web.servlet.View.render(Map) to View.render(ModelBean)
     */
    @Override
    public final void render(Map map, @NotNull HttpServletRequest request, @NotNull HttpServletResponse response) throws Exception
    {
        // HttpView acts like map is not important, however, stash it so we can get it back
        // it is not always the same as getModel()
        _renderMap = map;
        render(getModelBean(), request, response);
    }

    /**
     * HttpView.render() does not check isVisible(); renderInternal() should do that.
     */
    public final void render(ModelBean model, HttpServletRequest request, HttpServletResponse response) throws Exception
    {
        try
        {
            if (response.getContentType() != null && !response.getContentType().contains("charset"))
                response.setContentType(response.getContentType()+"; charset=UTF-8");
            try (var _ = HttpView.pushView(this, request, response, null))
            {
                initViewContext(getViewContext(), request, response);
                renderInternal(model, request, response);
            }
        }
        catch (Exception e)
        {
            if (response.isCommitted())
            {
                try {response.getWriter().flush();} catch (Exception x) {/* */}
                response.flushBuffer();
            }
            if (!ExceptionUtil.isIgnorable(e))
                LogManager.getLogger(HttpView.class).error("Exception while rendering view; creation stacktrace:" + ExceptionUtil.renderStackTrace(_creationStackTrace));
            throw e;
        }
    }


    @Override
    public String getContentType()
    {
        return "text/html";
    }

    static void initViewContextNoWriter(ViewContext context)
    {
        initViewContext(context);
        HttpServletResponse response = context.getResponse();
        if (response instanceof NoWriteWrapper)
            context.setResponse(new NoWriteWrapper(response));
    }


    static void initViewContext(ViewContext context)
    {
        initViewContext(context, currentRequest(), currentResponse());
    }


    static void initViewContext(ViewContext context, HttpServletRequest request, HttpServletResponse response)
    {
        ViewContext top = topViewContext();
        if (null != top)
        {
            // container is usually derived from URL helper, might be different for webparts?
            if (null == context.getContainer())
                context.setContainer(top.getContainer());
            if (null == context.getUser())
                context.setUser(top.getUser());
            if (null == context.getActionURL())
            {
                ActionURL url = top.getActionURL();
                if (null != url && !url.isReadOnly())
                {
                    url = url.clone();
                    url.setReadOnly();
                }
                context.setActionURL(url);
            }
        }
        context.setRequest(request);
        context.setResponse(response);
    }
    

    // find most top-most view context on stack
    public static ViewContext topViewContext()
    {
        Stack<ViewStackEntry> stack = _viewContexts.get();
        for (int i=stack.size()-1 ; i>= 0 ;i--)
        {
            ModelAndView mv = stack.get(i).mv;
            if (mv instanceof HttpView<?> hv)
            {
                ViewContext context = hv.getViewContext();
                assert null != context;
                return context;
            }
        }
        return null;
    }


    /**
     * Subclasses usually implement renderInternal(Map, PrintWriter), or they may
     * implement renderInternal(Map, HttpServletRequest, HttpServletResponse)
     */
    protected void renderInternal(ModelBean model, HttpServletRequest request, HttpServletResponse response) throws Exception
    {
        renderInternal(model, response.getWriter());
    }


    protected void renderInternal(ModelBean model, PrintWriter out) throws Exception
    {
        throw new IllegalStateException("override renderInternal");
    }
    

    protected HttpView()
    {
        this(new ViewContext());
        initViewContextNoWriter(getViewContext());
    }

    
    protected HttpView(ViewContext context)
    {
        _viewContext = context;
        setView(this);
        _creationStackTrace = MiniProfiler.getTroubleshootingStackTrace();
        MemTracker.getInstance().put(this);
    }


    protected HttpView(ModelBean model)
    {
        this();
        setModelBean(model);
    }


    public void setBody(ModelAndView body)
    {
        setView(BODY, body);
    }


    public ModelAndView getBody()
    {
        return getView(BODY, true);
    }


    public void setView(String name, ModelAndView view)
    {
        if (null == _views)
            _views = new HashMap<>();
        _views.put(name, view);
    }

    // only used to satisfy HasViewContext
    @Override
    public void setViewContext(ViewContext context)
    {
        _viewContext = context;
    }

    @Override
    public ViewContext getViewContext()
    {
        return _viewContext;
    }


    public ModelAndView getView(String name)
    {
        return getView(name, false);
    }


    public ModelAndView getView(String name, boolean fLocalScope)
    {
        return null == _views ? null : _views.get(name);
    }

    public Set<String> getViewNames()
    {
        return _views.keySet();
    }


    /**
     * CAREFUL: You probably should be using currentContext() instead of this.  This context won't represent the
     * "current" action if, for example, the original action has forwarded somewhere else
     */
    @Nullable
    public static ViewContext getRootContext()
    {
		ViewStack stack = _viewContexts.get();
		return stack.isEmpty() ? null : ((HttpView<?>) stack.elementAt(0).mv).getViewContext();
    }


    //
    // static methods
    //

    public static <K extends HttpView<?>> K currentView()
    {
        Stack<ViewStackEntry> s = _viewContexts.get();
        return (K)s.peek().mv;
    }

    public static boolean hasCurrentView()
    {
        Stack<ViewStackEntry> s = _viewContexts.get();
        return !s.isEmpty();
    }


    /**
     * This may seem odd, who needs currentModel except the view which can call getModel()?
     * However this is useful for Jsp's which actually subclass JspBase not HttpView
     */
    public static Object currentModel()
    {
        return currentView().getModelBean();
    }


    @Nullable
    public static HttpServletRequest currentRequest()
    {
        Stack<ViewStackEntry> s = _viewContexts.get();
        return s.isEmpty() ? null : s.peek().request;
    }

    @Nullable
    public static HttpServletResponse currentResponse()
    {
        Stack<ViewStackEntry> s = _viewContexts.get();
        return s.isEmpty() ? null : s.peek().response;
    }

    @NotNull
    public static PageConfig currentPageConfig()
    {
        Stack<ViewStackEntry> s = _viewContexts.get();
        var ret = s.isEmpty() ? null : s.peek().pageConfig;
        if (null == ret)
            throw UnexpectedException.wrap(new ServletException("Request is not configured for rendering"));
        return ret;
    }


    // using real subclass to avoid the throws Exception
    public static class InitAutoCloseable implements AutoCloseable
    {
        private final int _resetSize;

        public InitAutoCloseable(int resetSize)
        {
            _resetSize = resetSize;
        }

        @Override
        public void close()
        {
            HttpView.resetStackSize(_resetSize);
        }
    }


    public static InitAutoCloseable initForRequest(final ViewContext context, HttpServletRequest request, HttpServletResponse response)
    {
        // placeholder view
        var resetSize = _viewContexts.get().size();
        pushView(new ServletView(null, context), request, response, new PageConfig(request));
        return new InitAutoCloseable(resetSize);
    }


    public static class PushAutoCloseable implements AutoCloseable
    {
        private final ModelAndView _mv;

        public PushAutoCloseable(ModelAndView mv)
        {
            _mv = mv;
        }

        @Override
        public void close()
        {
            Stack<ViewStackEntry> s1 = _viewContexts.get();
            assert _mv == s1.peek().mv;
            popView();
        }
    }


    protected static PushAutoCloseable pushView(ModelAndView mv, HttpServletRequest request, HttpServletResponse response, @Nullable PageConfig pageConfig)
    {
        Stack<ViewStackEntry> s = _viewContexts.get();
        if (null == pageConfig && !s.isEmpty())
            pageConfig = s.peek().pageConfig;
        ViewStackEntry vse = new ViewStackEntry(mv, request,response, pageConfig);
        MemTracker.getInstance().put(vse);
        s.push(vse);
        return new PushAutoCloseable(mv);
    }


    protected static void popView()
    {
        Stack<ViewStackEntry> s = _viewContexts.get();
        s.pop();
    }


    public static int getStackSize()
    {
        Stack<ViewStackEntry> s = _viewContexts.get();
        return null == s ? 0 : s.size();
    }


    public static void resetStackSize(int reset)
    {
        Stack<ViewStackEntry> s = _viewContexts.get();
        if (null == s)
            return;
        while (s.size() > reset)
            s.pop();
        if (s.isEmpty())
            _viewContexts.remove();
    }


    public static HttpView<?> viewFromString(String viewName)
    {
        if (null == viewName)
            return null;

        try
        {
            if (viewName.endsWith(".jsp"))
                return new JspView<>(viewName);

            WebPartFactory f = Portal.getPortalPart(viewName);
            if (null != f)
            {
                HttpView<?> v = f.getWebPartView(HttpView.getRootContext(), new Portal.WebPart());
                if (null != v)
                    return v;
            }

            Class<?> clss = Class.forName(viewName);
            return (HttpView<?>) clss.getDeclaredConstructor().newInstance();
        }
        catch (Exception x)
        {
            LogManager.getLogger(HttpView.class).error(x);
        }
        return null;
    }

    public ModelAndView getView(String name, String defaultView)
    {
        ModelAndView v = getView(name);
        if (null == v || v.getClass() == DebugView.class)
        {
            HttpView<?> w = viewFromString(defaultView);
            if (null != w)
                v = w;
        }
        if (null != v)
            return v;

        if (_debug >= Debug.DEBUG)
            v = new DebugView("Could not find view '" + name + "'");

        return v;
    }


    /**
     * Only for use in special cases (Global.app, ViewServlet, test harness, etc)
     */
    public static void include(ModelAndView mv, HttpServletRequest request, HttpServletResponse response) throws Exception
    {
        mv.getView().render(mv.getModel(), request, response);
    }


    public static String renderToString(ModelAndView mv, HttpServletRequest request) throws Exception
    {
        MockHttpServletResponse response = new MockHttpServletResponse(){
            @Override
            public void setStatus(int status)
            {
                if (status != HttpServletResponse.SC_OK)
                    throw new RuntimeException("Unexpected Status: " + status);
            }
        };
        include(mv, request, response);
        return response.getContentAsString();
    }


    /**
     * This method can be used by HttpView subclasses to include another ModelAndView.
     */
    public void include(ModelAndView mv) throws Exception
    {
        include(mv, null);
    }


    public void include(ModelAndView mv, Writer writer) throws Exception
    {
        HttpServletRequest request = HttpView.currentRequest();
        HttpServletResponse response = HttpView.currentResponse();

        if (null == response)
            response = new MockHttpServletResponse();

        include(mv, writer, request, response);
    }


    protected void include(ModelAndView mv, Writer writer, HttpServletRequest request, HttpServletResponse response) throws Exception
    {
        if (null != writer)
        {
            final PrintWriter out = writer instanceof PrintWriter ? (PrintWriter)writer : new PrintWriter(writer);

            // UNDONE: this getWriter() call prevents getOutputStream() from being called later...
            if (out != response.getWriter())
            {
                response = new HttpServletResponseWrapper(response)
                {
                    @Override
                    public PrintWriter getWriter()
                    {
                        return out;
                    }
                };
            }
        }

        mv.getView().render(mv.getModel(), request, response);
    }


    /**
     * allows some views to previewed out-of-context
     */
    protected static class DebugView extends HttpView<Object>
    {
        String _name = "";

        DebugView()
        {
        }


        DebugView(String name)
        {
            _name = name;
        }


        @Override
        public ModelAndView getView(String name)
        {
            ModelAndView v = super.getView(name);
            if (null == v)
                v = new DebugView(name);
            return v;
        }


        @Override
        protected void renderInternal(Object model, PrintWriter out) throws IOException
        {
            out.print("<div class=\"labkey-bordered\">" + _name + "</div>");
        }
    }

    public static HttpView<?> redirect(URLHelper url, boolean allowAbsoluteUrl)
    {
        throw new RedirectException(url, allowAbsoluteUrl);
    }

    public static HttpView<?> redirect(URLHelper url)
    {
        throw new RedirectException(url);
    }

    public static HttpView<?> redirect(ActionURL url)
    {
        throw new RedirectException(url);
    }

    /**
     * Pulls out the context's URL for redirecting. This fetches
     * the original URL before any redirects, in case internally
     * we've done that.
     */
    public static String getContextURL()
    {
        ViewContext context = getRootContext();
        HttpServletRequest request = context.getRequest();
        String url = (String)request.getAttribute(ViewServlet.ORIGINAL_URL_STRING);
        if (url == null)
        {
            url = context.getActionURL().getLocalURIString();
        }
        
        return url;
    }


    public static URLHelper getContextURLHelper()
    {
        ViewContext context = getRootContext();
        HttpServletRequest request = context.getRequest();
        URLHelper url = (URLHelper)request.getAttribute(ViewServlet.ORIGINAL_URL_URLHELPER);

        if (url == null)
            url = context.getActionURL();
        
        return url;
    }


    public static Container getContextContainer()
    {
        return getRootContext().getContainer();
    }


    /**
     * Current view context. Dangerous because some views do not use a ViewContext
     * object for their model and can cause a class cast exception.
     * @return the current context, or null if the current thread isn't rendering a view (like a background
     * pipeline thread)
     */
    @Nullable
    public static ViewContext currentContext()
    {
        // CONSIDER: if we ever have something besides HttpView on stack
        // we may need to iterate til we find the top most HttpView
        return hasCurrentView() ? currentView().getViewContext() : null;
    }


    public @NotNull String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append(this.getClass().getName()).append(" {");
        ViewContext ctx = getViewContext();

        for (Map.Entry<?, ?> entry : ctx.entrySet())
        {
            sb.append(entry.getKey()).append("=");

            Object v;

            try
            {
                v = entry.getValue();
            }
            catch(Throwable t)
            {
                v = t.getMessage();
            }

            String value;
            if (v == this)
            {
                value = "<this>";
            }
            else if (v instanceof HttpView)
            {
                value = ObjectUtils.identityToString(v);
            }
            else
            {
                value = String.valueOf(v);
            }
            sb.append(value, 0, Math.min(100, value.length()));
            sb.append("; ");
        }

        sb.append("} ");
        sb.append(getModelBean());
        return sb.toString();
    }


    private boolean _isVisible = true;

    public boolean isVisible()
    {
        return _isVisible;
    }

    protected void setVisible(boolean v)
    {
        _isVisible = v;
    }

    /**
     * for compatibility with old late-bound views
     */
    @Override
    @Deprecated
    public ModelAndView addObject(String key, Object value)
    {
        getViewContext().put(key, value);
        return this;
    }


    private static class NoWriteWrapper extends HttpServletResponseWrapper
    {
        public NoWriteWrapper(HttpServletResponse response)
        {
            super(response);
        }

        @Override
        public PrintWriter getWriter()
        {
            throw new IllegalStateException("Should not call getWriter() before render() is called.");
        }

        @Override
        public ServletOutputStream getOutputStream()
        {
            throw new IllegalStateException("Should not call getOutputStream() before render() is called.");
        }
    }

    public static PropertyValues getBindPropertyValues()
    {
        ViewStack s = _viewContexts.get();
        for (int i = s.size()-1 ; i>= 0 ; i--)
        {
            ViewStackEntry vse = s.get(i);
            ModelAndView mv = vse.mv;
            if (mv instanceof HasViewContext)
            {
                ViewContext context = ((HttpView<?>)mv).getViewContext();
                if (context._pvsBind != null)
                    return context._pvsBind;
            }
        }
        return null;
    }

    @NotNull
    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>(_clientDependencies);

        //include resources of nested views
        if (_views != null)
        {
            for (ModelAndView v : _views.values())
            {
                if(v instanceof HttpView<?> hv)
                    resources.addAll(hv.getClientDependencies());
            }
        }

        // necessary for hbox, vbox
        if (_view != null && _view instanceof HttpView<?> hv)
        {
            List<ModelAndView> views = hv.getViews();
            if (views != null)
            {
                for (ModelAndView v : views)
                {
                    if (v instanceof HttpView<?> hv2)
                        resources.addAll(hv2.getClientDependencies());
                }
            }
        }
        return resources;
    }

    public void setClientDependencies(Set<ClientDependency> scripts)
    {
        _clientDependencies = new LinkedHashSet<>(scripts);
    }

    public void addClientDependency(ClientDependency resource)
    {
        _clientDependencies.add(resource);
    }

    public void addClientDependencies(Set<ClientDependency> resources)
    {
        _clientDependencies.addAll(resources);
    }

    public void addClientDependencies(List<Supplier<ClientDependency>> resources)
    {
        _clientDependencies.addAll(ClientDependency.getClientDependencySet(resources));
    }

    public List<ModelAndView> getViews()
    {
        if (_views == null)
            return null;

        return new ArrayList<>(_views.values());
    }
}
