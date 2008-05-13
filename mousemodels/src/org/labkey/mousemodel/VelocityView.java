/*
 * Copyright (c) 2004-2008 Fred Hutchinson Cancer Research Center
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
package org.labkey.mousemodel;

import org.apache.log4j.Logger;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.apache.velocity.app.tools.VelocityFormatter;
import org.labkey.api.security.ACL;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ButtonServlet;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Set;


public class VelocityView extends WebPartView
{
    public String _templateName = null;
    private VelocityContext _requestContext = null;
    private static VelocityTools _tools = new VelocityTools();
    private static Logger _log = Logger.getLogger(VelocityView.class);


    public VelocityView(String templateName)
    {
        WebAppVelocity.initVelocity();
        _templateName = templateName;
    }


    public VelocityView(String templateName, String title)
    {
        this(templateName);
        setTitle(title);
    }


    /**
     * Gets the default context for this view. Default context includes
     * the following elements;
     * request: HttpServletRequest that invoked this view
     * container: Note that if container for read could not be retrieved,
     * no error is thrown. Should not rely on this to do security checking
     * tools: instance of VelocityTools
     * formatter: instance of VelocityFormatter (DateFormats etc)
     */
    public VelocityContext getRequestContext(ViewContext context)
    {
        if (null == _requestContext)
        {
            _requestContext = new VelocityContext(context);
            _requestContext.put("request", context.getRequest());
            try
            {
                _requestContext.put("container", getViewContext().getContainer(ACL.PERM_READ));
            }
            catch (Exception e)
            {
                //Just don't put a container in...
            }
            _requestContext.put("tools", _tools);
            _requestContext.put("formatter", new VelocityFormatter(_requestContext));
        }
        return _requestContext;
    }


    @Override
    public void renderView(Object model, HttpServletRequest request, HttpServletResponse response) throws Exception
    {
        if (!_templateName.startsWith("/"))
        {
            String dirPath = request.getServletPath();
            int lastSlash = dirPath.lastIndexOf('/');
            if (lastSlash != -1)
                dirPath = dirPath.substring(0, lastSlash + 1);
            _templateName = dirPath + _templateName;
        }

        if (!Velocity.resourceExists(_templateName))
            throw new ServletException("Template " + _templateName + " not found");
        VelocityContext ctx = getRequestContext(getViewContext());
        ctx.put("response", response);
        try
        {
            Velocity.mergeTemplate(_templateName, "UTF-8", ctx, response.getWriter());
        }
        catch (Exception e)
        {
            _log.error("Exception in mergeTemplate", e);
            response.getWriter().write(e.getMessage());
            throw new ServletException(e);
        }
    }


    public static class VelocityTools
    {
        private static PageFlowUtil _pageFlowUtil = new PageFlowUtil();

        public PageFlowUtil getPageFlowUtil()
        {
            return _pageFlowUtil;
        }

        public String includeView(HttpServletRequest request, HttpServletResponse response, String viewName) throws Exception
        {
            final StringWriter writer = new StringWriter();
            final PrintWriter printWriter = new PrintWriter(writer);

            HttpView me = (HttpView)HttpView.currentView();
            me.include(me.getView(viewName, null), printWriter);

            return writer.toString();
        }

        public String encode(String str)
        {
            return PageFlowUtil.encode(str);
        }

        public String filter(String str)
        {
            return PageFlowUtil.filter(str);
        }


        /**
         * Returns a map where get always returns a string that is suitable
         * for HTML output. Does not encode spaces to &nbsp; however
         */
        public Map filteredMap(Map orig)
        {
            return new FilteredMap(orig);
        }


        public int getLength(Object c)
        {
            if (null == c)
                return 0;
            if (c instanceof Collection)
                return ((Collection) c).size();
            else
                return ((Object[]) c).length;
        }


        public String buttonSrc(String text)
        {
            return ButtonServlet.buttonSrc(text);
        }

        public String buttonSrc(String text, String style)
        {
            return ButtonServlet.buttonSrc(text, style);
        }
    }


    public static class FilteredMap implements Map
    {
        private Map _map = null;


        public FilteredMap(Map orig)
        {
            _map = orig;
        }


        public void clear()
        {
            _map.clear();
        }


        public boolean containsKey(Object arg0)
        {
            return _map.containsKey(arg0);
        }


        public boolean containsValue(Object arg0)
        {
            throw new UnsupportedOperationException();
        }


        public Set entrySet()
        {
            return _map.entrySet();
        }


        public Object get(Object arg0)
        {
            Object o = _map.get(arg0);
            return PageFlowUtil.filter(o.toString());
        }


        public boolean isEmpty()
        {
            return _map.isEmpty();
        }


        public Set keySet()
        {
            return _map.keySet();
        }


        public Object put(Object arg0, Object arg1)
        {
            return _map.put(arg0, arg1);
        }


        public void putAll(Map arg0)
        {
            _map.putAll(arg0);
        }


        public Object remove(Object arg0)
        {
            return _map.remove(arg0);
        }


        public int size()
        {
            return _map.size();
        }


        public Collection values()
        {
            Collection oldValues = _map.values();
            ArrayList filteredValues = new ArrayList(oldValues.size());

            for (Object oldValue : oldValues)
                filteredValues.add(PageFlowUtil.filter(oldValue.toString()));

            return filteredValues;
        }
    }
}
