/*
 * Copyright (c) 2004-2014 Fred Hutchinson Cancer Research Center
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
import org.jetbrains.annotations.Nullable;
import org.labkey.api.jsp.JspBase;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.CSRFUtil;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.MemTracker;
import org.labkey.api.jsp.JspLoader;
import org.labkey.api.action.HasViewContext;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.jsp.HttpJspPage;
import java.util.Map;
import java.util.Set;


public class JspView<ModelClass> extends WebPartView<ModelClass>
{
    protected String _path;
    protected HttpJspPage _page;
    protected Errors _errors;

    // HttpJspPage constructors

    public JspView(HttpJspPage page)
    {
        _page = page;
        MemTracker.getInstance().put(this);
    }

    public JspView(HttpJspPage page, ModelClass model)
    {
        super(model);
        _page = page;
    }

    public JspView(HttpJspPage page, ModelClass model, Errors errors)
    {
        super(model);
        _page = page;
        _errors = errors;
    }

    // String constructors 

    public JspView(String page)
    {
        this(page, null);
    }

    public JspView(String page, @Nullable ModelClass model)
    {
        super(model);
        MemTracker.getInstance().put(this);
        _path = page;
        _page = JspLoader.createPage((String)null, page);

        if (_page instanceof JspBase)
            addClientDependencies(((JspBase)_page).getClientDependencies());
    }

    public JspView(String page, @Nullable ModelClass model, BindException errors)
    {
        this(page, model);
        _errors = errors;
    }

    public JspView(Class packageClass, String jspName, ModelClass model)
    {
        super(model);
        MemTracker.getInstance().put(this);
        _path = jspName;
        _page = JspLoader.createPage(packageClass, jspName);

        if(_page instanceof JspBase)
            addClientDependencies(((JspBase)_page).getClientDependencies());
    }


    public JspView(Class packageClass, String jspName, ModelClass model, Errors errors)
    {
        this(packageClass, jspName, model);
        _errors = errors;
    }


    public HttpJspPage getPage()
    {
        return _page;
    }


    public Errors getErrors()
    {
        return _errors;
    }
    

    @Override
    protected void renderView(ModelClass model, HttpServletRequest request, HttpServletResponse response) throws Exception
    {
        if (_page instanceof HasViewContext)
            ((HasViewContext)_page).setViewContext(getViewContext());

        if (null != _errors && _errors instanceof BindException)
            exposeModelAsRequestAttributes(((BindException)_errors).getModel(), request, response);
        exposeModelAsRequestAttributes(_renderMap, request, response);

        boolean devMode = AppProps.getInstance().isDevMode();
        boolean isDebugHtml = devMode && this.getFrame() != FrameType.NOT_HTML && StringUtils.startsWith(response.getContentType(), "text/html");
        if (isDebugHtml)
            response.getWriter().print("<!--" + _page.getClass() + "-->");

        try
        {
            _page._jspService(request, response);
        }
        catch (IllegalStateException x)
        {
            StringBuilder sb = new StringBuilder();
            try
            {
                sb.append("request: " + request.getClass().getName() + "\n");
                HttpSession s = request.getSession(false);
                sb.append("session: " + (null == s ? "null" : s.getClass().getName()) + "\n");
                sb.append("user: " + (null == request.getUserPrincipal() ? "null" : request.getUserPrincipal().getName()));
            }
            catch (Exception extra)
            {/* */}

            ExceptionUtil.decorateException(x, ExceptionUtil.ExceptionInfo.ExtraMessage, sb.toString(), true);
            throw x;
        }

        if (isDebugHtml)
            response.getWriter().print("<!--/" + _page.getClass() + "-->");
    }


    public void include(ModelAndView mv) throws Exception
    {
        assert false : "jsp must use include(ModelAndView, Writer): " + _page.getClass().getName();
    }


    //
    // UNDONE: see package org.springframework.web.servlet.view.AbstractView.exposeModelAsRequestAttributes()
    // CONSIDER: extends AbstractView
    //

    protected void exposeModelAsRequestAttributes(Map model, HttpServletRequest request, HttpServletResponse response) throws Exception
    {
        try
        {
            // init CSRF attribute as side-effect
            CSRFUtil.getExpectedToken(request, response);
        }
        catch (IllegalStateException x)
        {
            // Cannot create a session after the response has been committed
        }
        
        // a) merge properties that were dropped into the spring ModelMap
        // b) copy parameters into request attributes where JSP framework/taglibs might expect them
        ViewContext context = getViewContext();
        
        if (model != null && model != context)
            context.getExtendedProperties().putAll(model);
        
        for (Map.Entry e : (Set<Map.Entry>)context.getExtendedProperties().entrySet())
        {
            if (e.getKey() instanceof String && e.getValue() != null)
                request.setAttribute((String)e.getKey(), e.getValue());
        }
    }
}