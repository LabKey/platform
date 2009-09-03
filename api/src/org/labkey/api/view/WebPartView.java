/*
 * Copyright (c) 2004-2009 Fred Hutchinson Cancer Research Center
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

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.security.ACL;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.HString;
import org.labkey.api.util.ErrorRenderer;
import static org.labkey.api.util.PageFlowUtil.filter;
import org.labkey.api.portal.ProjectUrls;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Map;


public abstract class WebPartView<ModelBean> extends HttpView<ModelBean> implements HttpView.ViewWrapper
{
    private Throwable _prepareException = null;
    private boolean _isPrepared = false;
    private boolean _isEmbedded = false;
    private String _helpPopup;

    public static enum FrameType
    {
        PORTAL,     // with portal widgets
        TITLE,      // title w/hr
        DIALOG,
        DIV,        // just <div class="">
        LEFT_NAVIGATION,
        NONE        // clean
    }

    public WebPartView()
    {
        super();
        setFrame(FrameType.PORTAL);
    }

    public WebPartView(String title)
    {
        this();
        addObject("title", title);
    }

    public WebPartView(ModelBean model)
    {
        super(model);
        setFrame(FrameType.PORTAL);
    }

    public WebPartView(String title, HttpView contents)
    {
        this();
        addObject("title", title);
        setBody(contents);
    }

    
    public void setFrame(FrameType type)
    {
        addObject("WebPartView.frame", type);
    }

    public FrameType getFrame()
    {
        return (FrameType)getViewContext().get("WebPartView.frame");
    }

    public void setBodyClass(String className)
    {
        addObject("className", className);
    }

    public String getBodyClass()
    {
        return StringUtils.defaultString((String) getViewContext().get("className"), "");
    }

    public void setTitle(CharSequence title)
    {
        if (title instanceof HString)
            title = ((HString)title).getSource();
        addObject("title", title == null ? "" : title.toString());
    }

    public String getTitle()
    {
        Object ret = getViewContext().get("title");
        if (ret != null && !(ret instanceof String))
        {
            return ret.toString();
        }
        return (String) ret;
    }

    // Use ActionURL version instead
    @Deprecated
    public void setTitleHref(String href)
    {
        addObject("href", href);
    }

    public void setTitlePopupHelp(String title, String body)
    {
        _helpPopup = PageFlowUtil.helpPopup(title, body);
    }

    public void setTitleHref(ActionURL href)
    {
        addObject("href", href.getLocalURIString());
    }


    public String getTitleHref()
    {
        return (String) getViewContext().get("href");
    }


    public NavTree getCustomizeLinks()
    {
        NavTree navTree = (NavTree) getViewContext().get("customizeLinks");
        if (null == navTree)
        {
            navTree = new NavTree();
            addObject("customizeLinks", navTree);
        }

        return navTree;
    }

    public void setCustomizeLinks(NavTree navTree)
    {
        addObject("customizeLinks", navTree);
    }

    public void enableExpandCollapse(String rootId, boolean collapsed)
    {
        addObject("collapsed", Boolean.valueOf(collapsed));
        addObject("rootId", rootId);
    }

    @Override
    protected final void renderInternal(ModelBean model, HttpServletRequest request, HttpServletResponse response) throws Exception
    {
        prepare(model);
        if (!isVisible())
            return;

        Throwable exceptionToRender = _prepareException;
        String errorMessage = null;

        this.doStartTag(getViewContext(), response.getWriter());

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
                if (ExceptionUtil.isClientAbortException(exceptionToRender))
                {
                    exceptionToRender = null;
                }
                else
                {
                    Logger.getLogger(WebPartView.class).error("renderView() exception in " + getClass().getName() + " while responding to " + getViewContext().getActionURL().getLocalURIString(), exceptionToRender);
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

        this.doEndTag(getViewContext(), response.getWriter());
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


    public void doStartTag(Map m, PrintWriter out)
    {
        ViewContext context = getViewContext();
        String contextPath = context.getContextPath();
        FrameType frameType = (FrameType) context.get("WebPartView.frame");

        String title = (String) context.get("title");
        String href = (String) context.get("href");

        // HACK for backwards compatibility
        if (null == title && frameType==FrameType.PORTAL)
        {
            frameType = FrameType.DIV;
            context.put("WebPartView.frame", frameType);
        }

        String className = getBodyClass();

        switch (frameType)
        {
        case NONE:
            break;

        case DIV:
            out.printf("<div class=\"%s\">", className);
            break;

        case TITLE:
            startTitleFrame(out, title, href, "100%", className);
            break;

        case DIALOG:
        {
            out.print("<table class=\"labkey-bordered\"><tr>");
            out.print("<th>");
            out.print(  PageFlowUtil.filter(title));
            out.print(  "<br><img src='" + getViewContext().getContextPath() + "/_.gif' height=1 width=600>");
            out.print("</th>");
            if (getViewContext().get("closeURL") != null)
            {
                Object o = getViewContext().get("closeURL");
                Object closeUrl = o instanceof ActionURL ? ((ActionURL)o).getLocalURIString() : String.valueOf(o);
                out.print("<th>");
                out.print("<a href=\"" + PageFlowUtil.filter(closeUrl) + "\">");
                out.print("<img src=\"" + contextPath + "/_images/delete.gif\"></a>");
                out.print("</th>");
            }

            out.print("</tr><tr><td colspan=2 class=\"" + className + "\">");
            break;
        }

        case PORTAL:
        {
            out.print(
                    "<!--webpart--><table class=\"labkey-wp\">" +
                            "<tr class=\"labkey-wp-header\">" +
                            "<th class=\"labkey-wp-title-left\" title=\"");
            out.print(PageFlowUtil.filter(title));
            out.print("\">");

            Boolean collapsed = (Boolean) context.get("collapsed");
            
            if (collapsed != null)
            {
                String rootId = (String) context.get("rootId");
                ActionURL expandCollapseUrl = null;
                String expandCollapseGifId = "expandCollapse-" + rootId;
                if (collapsed != null)
                {
                    if (rootId == null)
                        throw new IllegalArgumentException("pathToHere or rootId not provided");
                    expandCollapseUrl = PageFlowUtil.urlProvider(ProjectUrls.class). getExpandCollapseURL(getViewContext().getContainer(), "", rootId);
                }

                out.printf("<a href=\"%s\" onclick=\"return toggleLink(this, %s);\" id=\"%s\">",
                        filter(expandCollapseUrl.getLocalURIString()), "true", expandCollapseGifId);
                String image = collapsed ? "plus.gif" : "minus.gif";
                out.printf("<img src=\"%s/_images/%s\"></a>", context.getContextPath(), image);
                
                out.printf(" <a href=\"%s\" onclick=\"return toggleLink(document.getElementById(%s), %s);\">",
                        filter(expandCollapseUrl.getLocalURIString()), PageFlowUtil.jsString(expandCollapseGifId), "true");
                out.print(PageFlowUtil.filter(title));
                out.print("</a>");
            }
            else
            {
                if (null != href)
                    out.print("<a href=\"" + PageFlowUtil.filter(href) + "\">");
                out.print(PageFlowUtil.filter(title));
                if (null != href)
                    out.print("</a>");
            }
            if (_helpPopup != null)
            {
                out.print(_helpPopup);
            }
            
            out.print("</th>\n<th class=\"labkey-wp-title-right\">");
            NavTree[] links = getCustomizeLinks().getChildren();
            if (links == null || links.length == 0)
                out.print("&nbsp;");
            else
            {
                String sep = "";
                for (NavTree link : links)
                {
                    out.print(sep);
                    String linkHref = link.second;
                    String linkText = link.first;

                    if (null != linkHref && 0 < linkHref.length())
                        out.print("<a href=\"" + PageFlowUtil.filter(linkHref) + "\">");
                    if (null != link.getImageSrc())
                        out.print("<img src=\"" + link.getImageSrc() +
                                "\" title=\"" + PageFlowUtil.filter(linkText) + "\">");
                    else
                        out.print(PageFlowUtil.filter(linkText));
                    if (null != linkHref && 0 < linkHref.length())
                        out.print("</a>");
                    sep = "&nbsp;";
                }
            }
            out.print("</th></tr>\n<tr id=\"" + getContentId() + "\" ");
            if (collapsed != null && collapsed.booleanValue())
            {
                out.print("style=\"display: none\"");
            }
            out.print("><td colspan=2 class=\"" + className + " labkey-wp-body\">");
            break;
        }
        case LEFT_NAVIGATION:
            {
                out.print("<!--leftnav-webpart--><table class=\"labkey-expandable-nav\">");

                Boolean collapsed = (Boolean) context.get("collapsed");
                if (title != null)
                {
                    out.print("<tr>" +
                            "<th class=\"labkey-header\" style=\"padding-left: 4px\" title=\"");
                    out.print(PageFlowUtil.filter(title));
                    out.print("\">");

                    String rootId = (String) context.get("rootId");
                    ActionURL expandCollapseUrl = null;
                    String expandCollapseGifId = "expandCollapse-" + rootId;
                    if (collapsed != null)
                    {
                        if (rootId == null)
                            throw new IllegalArgumentException("pathToHere or rootId not provided");
                        expandCollapseUrl = PageFlowUtil.urlProvider(ProjectUrls.class). getExpandCollapseURL(getViewContext().getContainer(), "", rootId);
                    }

                    if (null != href)
                    {
                        // print title with user-specified link:
                        out.print("<a href=\"" + PageFlowUtil.filter(href) + "\">");
                        out.print(PageFlowUtil.filter(title));
                        out.print("</a>");
                    }
                    else if (collapsed != null)
                    {
                        // print title with expand/collapse link:
                        out.printf("<a href=\"%s\" onclick=\"return toggleLink(document.getElementById(%s), %s);\">",
                                filter(expandCollapseUrl.getLocalURIString()), PageFlowUtil.jsString(expandCollapseGifId), "true");
                        out.print(PageFlowUtil.filter(title));
                        out.print("</a>");
                    }
                    else
                    {
                        // print the title alone:
                        out.print(PageFlowUtil.filter(title));
                    }

                    out.print("</th>\n<th class=\"labkey-expand-collapse-area\">");

                    if (collapsed != null)
                    {
                        out.printf("<a href=\"%s\" onclick=\"return toggleLink(this, %s);\" id=\"%s\">",
                                filter(expandCollapseUrl.getLocalURIString()), "true", expandCollapseGifId);
                        String image = collapsed ? "plus.gif" : "minus.gif";
                        out.printf("<img src=\"%s/_images/%s\"></a>", context.getContextPath(), image);
                    }
                    out.print("</th></tr>\n");
                }
                out.print("<tr" + (collapsed != null && collapsed ? " style=\"display:none\"" : "") + " class=\"" + className + "\">" +
                        "<td colspan=\"2\" class=\"labkey-expandable-nav-body\">");
                break;
            }
        }
    }

    private String getContentId()
    {
        return "WebPartView" + System.identityHashCode(this);
    }
    
    public static void startTitleFrame(Writer out, String title, String href, String width, String className)
    {
        try
        {
            out.write(
                    "<table " + (null!=width?"width=\"" + width + "\"" : "") + ">" +
                            "<tr>" +
                            "<td class=\"labkey-announcement-title\" align=left><span>");
            if (null != href)
                out.write("<a href=\"" + PageFlowUtil.filter(href) + "\">");
            out.write(PageFlowUtil.filter(title));
            if (null != href)
                out.write("</a>");
            out.write("</span></td></tr>");
            out.write("<tr><td class=\"labkey-title-area-line\"><img height=1 width=1 src=\"" + AppProps.getInstance().getContextPath() + "/_.gif\"></td></tr>");
            out.write("<tr><td colspan=3 class=\"" + className + "\">");
        }
        catch (IOException x)
        {
            throw new RuntimeException(x);
        }
    }

    public static void startTitleFrame(Writer out, String title)
    {
        startTitleFrame(out, title, null, null, null);
    }


    public static void endTitleFrame(Writer out)
    {
        try
        {
            out.write("</td></tr></table>");
        }
        catch (IOException x)
        {
            throw new RuntimeException(x);
        }
    }


    public void doEndTag(Map context, PrintWriter out)
    {
        FrameType frameType = (FrameType) context.get("WebPartView.frame");

        switch (frameType)
        {
        case NONE:
            break;

        case DIV:
            out.print("</div>");
            break;

        case TITLE:
            endTitleFrame(out);
            break;

        case LEFT_NAVIGATION:
        case PORTAL:
            out.print("</td></tr></table><!--/webpart-->");
            break;

        case DIALOG:
            out.print("</td></tr></table>");
        }
    }

    protected Container hasAccess(ViewContext model, String name) throws Exception
    {
        try
        {
            return model.getContainer(ACL.PERM_READ);
        }
        catch (UnauthorizedException e)
        {
            String message;

            if (model.getUser().isGuest())
                message = "Please log in to see this data.";
            else
                message = "You do not have permission to see this data.";

            HtmlView view = new HtmlView("<table class=\"DataRegion\"><tr><td>" + message + "</td></tr></table>");
            view.setTitle(name);
            include(view);
            return null;
        }
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
        return _isEmbedded;
    }

    public void setEmbedded(boolean embedded)
    {
        _isEmbedded = embedded;
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

        public Collapsible[] getChildren()
        {
            return new Collapsible[0];
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
}
