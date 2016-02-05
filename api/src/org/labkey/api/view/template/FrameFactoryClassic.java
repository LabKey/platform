/*
 * Copyright (c) 2015-2016 LabKey Corporation
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
package org.labkey.api.view.template;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.PopupMenu;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ViewService;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartFrame;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Iterator;

import static org.labkey.api.util.PageFlowUtil.filter;
import static org.labkey.api.view.WebPartView.FrameType;


public class FrameFactoryClassic implements ViewService.FrameFactory
{

    public void registerFrames()
    {
        for (FrameType f : FrameType.values())
        {
            ServiceRegistry.get(ViewService.class).registerFrameFactory(f,this);
        }
    }


    @Override
    public WebPartFrame createFrame(Enum e, ViewContext context, WebPartFrame.FrameConfig config)
    {
        if (!(e instanceof FrameType))
            throw new IllegalStateException();

        switch ((FrameType)e)
        {
            case NONE:
            case NOT_HTML:
                return new _FrameNone(context, config);
            case DIV:
                return new _FrameDiv(context, config);
            case TITLE:
                return new _FrameTitle(context, config);
            case PORTAL:
                return new _FramePortal(context, config);
            case DIALOG:
                return new _FrameDialog(context, config);
            case LEFT_NAVIGATION:
                return new _FrameNavigation(context, config);

        }

        throw new IllegalStateException();
    }


    protected abstract class AbstractFrame implements WebPartFrame
    {
        protected final boolean _devMode = AppProps.getInstance().isDevMode();
        final ViewContext context;
        final FrameConfig config;

        AbstractFrame(ViewContext context, FrameConfig config)
        {
            this.context = context;
            this.config = config;
        }
    }


    protected class _FrameNone extends AbstractFrame
    {
        _FrameNone(ViewContext context, FrameConfig config)
        {
            super(context, config);
        }

        @Override
        public void doStartTag(PrintWriter out)
        {

        }

        @Override
        public void doEndTag(PrintWriter out)
        {

        }
    }



    protected class _FrameDiv extends AbstractFrame
    {
        _FrameDiv(ViewContext context, FrameConfig config)
        {
            super(context, config);
        }

        @Override
        public void doStartTag(PrintWriter out)
        {
            if (_devMode)
                out.print("<!--FrameType.DIV-->");
            if (!StringUtils.isEmpty(config._className))
                out.printf("<div class=\"%s\">", config._className);
            else
                out.printf("<div>");
        }

        @Override
        public void doEndTag(PrintWriter out)
        {
            out.print("</div>");
        }
    }



    protected class _FrameTitle extends AbstractFrame
    {
        _FrameTitle(ViewContext context, FrameConfig config)
        {
            super(context, config);
        }

        @Override
        public void doStartTag(PrintWriter out)
        {
            String title = config._title;
            String href = config._titleHref;
            String className = config._className;
            if (_devMode)
                out.print("<!--FrameType.TITLE-->");
            startTitleFrame(out, title, href, "100%", className);
        }

        @Override
        public void doEndTag(PrintWriter out)
        {
            endTitleFrame(out);
        }
    }


    protected class _FrameDialog extends AbstractFrame
    {
        _FrameDialog(ViewContext context, FrameConfig config)
        {
            super(context, config);
        }

        @Override
        public void doStartTag(PrintWriter out)
        {
            String contextPath = AppProps.getInstance().getContextPath();
            if (_devMode)
                out.print("<!--FrameType.DIALOG-->");
            out.print("<table class=\"labkey-bordered\"><tr>");
            out.print("<th>");
            out.print(PageFlowUtil.filter(config._title));
            out.print("<br><img src='" + contextPath + "/_.gif' height=1 width=600>");
            out.print("</th>");

            if (null != config._closeURL)
            {
                String closeUrl = config._closeURL.getLocalURIString();
                out.print("<th valign=\"top\" align=\"right\">");
                out.print("<a href=\"" + PageFlowUtil.filter(closeUrl) + "\">");
                out.print("<img src=\"" + contextPath + "/_images/partdelete.gif\"></a>");
                out.print("</th>");
            }

            out.print("</tr><tr><td colspan=2 class=\"" + StringUtils.defaultString(config._className,"") + "\">");
        }

        @Override
        public void doEndTag(PrintWriter out)
        {
            out.print("</td></tr></table>");
        }
    }


    protected class _FramePortal extends AbstractFrame
    {
        _FramePortal(ViewContext context, FrameConfig config)
        {
            super(context, config);
        }

        @Override
        public void doStartTag(PrintWriter out)
        {
            String title = config._title;

            if (StringUtils.isEmpty(title) && config._showTitle)
            {
                if (_devMode)
                    throw new IllegalStateException("Call WebPartView.setTitle() or WebPartView.setFrame(FrameType.DIV) or WebPartView.setShowTitle(false)");
                title = " ";
            }

            out.print("<!--FrameType.PORTAL-->");
            out.println("<table name=\"webpart\" id=\"webpart_" + config._webPartRowId + "\" class=\"labkey-wp\">");

            Boolean collapseXd = false;

            // Don't render webpart header if title is null or blank
            if (!StringUtils.isEmpty(title))
            {
                out.println("<tr class=\"labkey-wp-header\">");
                out.print("<th class=\"labkey-wp-title-left\" title=\"");
                if (config._showTitle)
                    out.print(PageFlowUtil.filter(title));
                out.print("\">");

                if (config._collapsed && config._isCollapsible)
                {
                    ActionURL expandCollapseUrl = null;
                    String expandCollapseGifId = "expandCollapse-" + config._rootId;
                    if (config._collapsed)
                    {
                        if (config._rootId == null)
                            throw new IllegalArgumentException("pathToHere or rootId not provided");
                        expandCollapseUrl = PageFlowUtil.urlProvider(ProjectUrls.class).getExpandCollapseURL(context.getContainer(), "", config._rootId);
                    }

                    out.printf("<a href=\"%s\" onclick=\"return LABKEY.Utils.toggleLink(this, %s);\" id=\"%s\">",
                            filter(expandCollapseUrl.getLocalURIString()), "true", expandCollapseGifId);
                    String image = config._collapsed ? "plus.gif" : "minus.gif";
                    out.printf("<img width=9 height=9 style=\"margin-bottom: 0\" src=\"%s/_images/%s\"></a>", context.getContextPath(), image);

                    out.printf(" <a href=\"%s\" onclick=\"return LABKEY.Utils.toggleLink(document.getElementById(%s), %s);\">",
                            filter(expandCollapseUrl.getLocalURIString()), PageFlowUtil.jsString(expandCollapseGifId), "true");
                    if (config._showTitle)
                    {
                        out.print("<span class=\"labkey-wp-title-text\">");
                        out.print(PageFlowUtil.filter(title));
                        out.print("</span>");
                    }
                    out.print("</a>");
                }
                else
                {
                    if (null != config._titleHref)
                        out.print("<a href=\"" + PageFlowUtil.filter(config._titleHref) + "\">");
                    if (config._showTitle)
                    {
                        out.print("<span class=\"labkey-wp-title-text\">");
                        out.print(PageFlowUtil.filter(title));
                        out.print("</span>");
                    }
                    if (null !=  config._titleHref)
                        out.print("</a>");
                }

                if (config._helpPopup != null)
                {
                    out.print(config._helpPopup);
                }

                NavTree[] links = null==config._portalLinks ? new NavTree[0] : config._portalLinks.getChildren();
                String sep = "";

                // Add Custom link
                NavTree nMenu = config._navMenu;
                if (nMenu == null)
                    nMenu = new NavTree("More");  // // 11730 : Customize link missing on many webparts

                if (config._customize != null)
                {
                    config._customize.setText("Customize");
                    nMenu.addChild(config._customize);
                }

                if (context.getUser().isSiteAdmin())
                {
                    Portal.WebPart webPart = Portal.getPart(context.getContainer(), config._webPartRowId);

                    if (webPart != null)
                    {
                        String permissionString = null;
                        String containerPathString = null;

                        if (webPart.getPermission() != null)
                            permissionString = "'" + webPart.getPermission() + "'";

                        if (webPart.getPermissionContainer() != null)
                            containerPathString = PageFlowUtil.qh(webPart.getPermissionContainer().getPath());

                        // Wrapped in immediately invoke function expression because of Issue 16953
                        NavTree permissionsNav = new NavTree("Permissions",
                                "javascript:LABKEY.Portal._showPermissions(" +
                                        config._webPartRowId + "," +
                                        permissionString + "," +
                                        containerPathString + ");"
                        );

                        permissionsNav.setId("permissions_" + webPart.getRowId());
                        nMenu.addChild(permissionsNav);
                    }
                }

                if (config._location != null && config._location.equals(WebPartFactory.LOCATION_RIGHT))
                {

                    // Collapse all items into one drop-down
                    // Render the navigation menu
                    if (nMenu == null)
                        nMenu = new NavTree("More");

                    // Portal
                    if (links.length > 0)
                    {
//                            NavTree portal = new NavTree("Layout");
                        for (NavTree link : links)
                            nMenu.addChild(link);
//                            nMenu.addChild(portal);
                    }

                    if (nMenu.hasChildren())
                    {
                        out.print("&nbsp;");
                        renderMenuWithFontImage(title, nMenu, out, "fa fa-caret-down");
                    }
                    out.print("</th>\n<th class=\"labkey-wp-title-right\">");
                }
                else if (!config._isWebpart)
                {
                    out.print("</th>\n<th class=\"labkey-wp-title-right\">");
                    // Purposely don't render custom links to avoid duplicates

                    // Render Navigation Links
                    if (nMenu.hasChildren())
                    {
                        out.print("<div class=\"labkey-wp-text-buttons\">");
                        for (NavTree link : nMenu.getChildren())
                        {
                            if (link.hasChildren())
                            {
                                renderMenu(link, out);
                            }
                            out.print(sep);
                            String linkHref = link.getHref();
                            String linkText = link.getText();

                            if (null != linkHref && 0 < linkHref.length())
                            {
                                out.print("<a href=\"" + linkHref + "\"");
                                if (link.isNoFollow())
                                    out.print(" rel=\"nofollow\"");
                                out.print(">" + linkText + "</a>");
                            }
                            else if (link.getScript() != null)
                            {
                                out.print("<a onClick=" + PageFlowUtil.jsString(link.getScript()) + ">" + linkText + "</a>");
                            }
                        }
                        out.print("</div>");
                    }
                }
                else
                {
                    // show the move up move down and delete web part menu options
                    if (links.length > 0)
                    {
                        for (NavTree link : links)
                            nMenu.addChild(link);
                    }

                    out.print("&nbsp;");

                    if (nMenu != null && nMenu.hasChildren())
                    {
                        renderMenuWithFontImage(title, nMenu, out, "fa fa-caret-down");
                    }

                    // Render specific parts first (e.g. wiki edit)
                    if (config._customMenus != null)
                    {
                        Iterator itr = config._customMenus.iterator();
                        NavTree current;
                        while (itr.hasNext())
                        {
                            current = (NavTree) itr.next();
                            out.print(sep);
                            if (current.hasChildren())
                            {
                                renderMenu(title, current, out, current.getImageSrc());
                            }
                            else
                            {
                                String linkHref = current.getHref();
                                String linkText = current.getText();
                                String script = current.getScript();

                                if (StringUtils.isEmpty(linkHref) && StringUtils.isEmpty(script))
                                {
                                    out.print("<span class=\"labkey-wp-icon-button-inactive\">");
                                }
                                else
                                {
                                    out.print("<span class=\"labkey-wp-icon-button-active\">");

                                    if (StringUtils.isEmpty(linkHref))
                                        linkHref = "javascript:void(0);";

                                    out.print("<a href=\"" + PageFlowUtil.filter(linkHref) + "\"");
                                    if (current.isNoFollow())
                                        out.print(" rel=\"nofollow\"");
                                    if (StringUtils.isNotEmpty(script))
                                        out.print(" onclick=\"" + PageFlowUtil.filter(script) + "\"");
                                    out.print(">");
                                }

                                if (null != current.getImageCls())
                                {
                                    out.print("<span class=\"" + current.getImageCls() + "\" title=\"" + PageFlowUtil.filter(linkText) + "\"></span>");
                                }
                                else if (null != current.getImageSrc())
                                {
                                    if (current.getImageWidth() != null && current.getImageHeight() != null)
                                        out.print("<img height=" + current.getImageHeight() + " width=" + current.getImageWidth() + " src=\"" + current.getImageSrc() + "\" title=\"" + PageFlowUtil.filter(linkText) + "\">");
                                    else
                                        out.print("<img src=\"" + current.getImageSrc() + "\" title=\"" + PageFlowUtil.filter(linkText) + "\">");
                                }
                                else
                                {
                                    out.print(PageFlowUtil.filter(linkText));
                                }

                                if (StringUtils.isNotEmpty(linkHref) || StringUtils.isNotEmpty(script))
                                    out.print("</a>");
                                out.print("</span>");
                            }
                        }
                    }

                    out.print("</th>\n<th class=\"labkey-wp-title-right\">");

                }

                out.println("</th>");
                out.println("</tr>");

                out.print("<tr id=\"WebPartView" + System.identityHashCode(this) + "\" ");
                if (config._collapsed && config._isCollapsible)
                    out.print("style=\"display: none\"");
                out.println(">");

                out.print("<td colspan=2 class=\"" + (null==config._className?"":config._className) + " labkey-wp-body\">");
            }
        }


        @Override
        public void doEndTag(PrintWriter out)
        {
            out.print("</td></tr></table><!--/FrameType.PORTAL-->");
        }
    }



        // dumb method because labkey-announcement-title has huge padding which we need to avoid sometimes
    public static void startTitleFrame(Writer out, String title, String href, String width, String className, int paddingTop)
    {
        try
        {
            out.write(
                    "<table " + (null != width ? "width=\"" + width + "\"" : "") + ">" +
                    "<tr>" +
                    "<td class=\"labkey-announcement-title\" style=\"padding-top:" + paddingTop + ";\" align=left><span>");
            if (null != href)
                out.write("<a href=\"" + PageFlowUtil.filter(href) + "\">");
            out.write(PageFlowUtil.filter(title));
            if (null != href)
                out.write("</a>");
            out.write("</span></td></tr>");
            out.write("<tr><td class=\"labkey-title-area-line\"></td></tr>");
            out.write("<tr><td colspan=3 class=\"" + className + "\">");
        }
        catch (IOException x)
        {
            throw new RuntimeException(x);
        }
    }

    public static void startTitleFrame(Writer out, String title, String href, String width, String className)
    {
        try
        {
            out.write(
                    "<table " + (null != width ? "width=\"" + width + "\"" : "") + ">" +
                            "<tr>" +
                            "<td class=\"labkey-announcement-title\" align=left><span>");
            if (null != href)
                out.write("<a href=\"" + PageFlowUtil.filter(href) + "\">");
            out.write(PageFlowUtil.filter(title));
            if (null != href)
                out.write("</a>");
            out.write("</span></td></tr>");
            out.write("<tr><td class=\"labkey-title-area-line\"></td></tr>");
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


    private void renderMenu(NavTree menu, PrintWriter out)
    {
        try
        {
            menu.setText(menu.getText());
            PopupMenu more = new PopupMenu(menu, PopupMenu.Align.RIGHT, PopupMenu.ButtonStyle.TEXT);
            more.setOffset("-7");
            more.render(out);
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    private void renderMenu(String title, NavTree menu, PrintWriter out, String imageSrc)
    {
        try
        {
            menu.setText("More");
            PopupMenu more = new PopupMenu(menu, PopupMenu.Align.RIGHT, PopupMenu.ButtonStyle.IMAGE);
            menu.setImage(imageSrc, 24, 24);
            more.setImageId("more-" + PageFlowUtil.filter(title.toLowerCase()));
            out.print("<span class=\"labkey-wp-icon-button-active\">");
            more.render(out);
            out.print("</span>");
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    private void renderMenuWithFontImage(String title, NavTree menu, PrintWriter out, String imageCls)
    {
        try
        {
            menu.setText("More");
            PopupMenu more = new PopupMenu(menu, PopupMenu.Align.RIGHT, PopupMenu.ButtonStyle.IMAGE);
            menu.setImageCls(imageCls);
            more.setImageId("more-" + PageFlowUtil.filter(title.toLowerCase()));
            out.print("<span class=\"labkey-wp-icon-button-active\">");
            more.render(out);
            out.print("</span>");
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }



    // WOULD LOVE TO KILL THIS ONE

    class _FrameNavigation extends AbstractFrame
    {
        _FrameNavigation(ViewContext context, FrameConfig config)
        {
            super(context, config);
        }

        @Override
        public void doStartTag(PrintWriter out)
        {
            String title = config._title;
            String href = config._titleHref;
            String className = config._className;

            out.print("<!--leftnav-webpart--><table class=\"labkey-expandable-nav\">");

            if (title != null)
            {
                out.print("<tr>");

                String rootId = config._rootId;
                ActionURL expandCollapseUrl = null;
                String expandCollapseGifId = "expandCollapse-" + rootId;

                boolean isCollapsible = config._isCollapsible;
                if (isCollapsible)
                {
                    if (rootId == null)
                        isCollapsible = false;
                    else
                        expandCollapseUrl = PageFlowUtil.urlProvider(ProjectUrls.class). getExpandCollapseURL(context.getContainer(), "", rootId);
                }

                out.print("<td class=\"labkey-expand-collapse-area\"><div>");

                if (isCollapsible)
                {
                    out.printf("<a class=\"labkey-header\" href=\"%s\" onclick=\"return LABKEY.Utils.toggleLink(this, %s);\" id=\"%s\">",
                            filter(expandCollapseUrl.getLocalURIString()), "true", expandCollapseGifId);
                    String image = config._collapsed ? "plus.gif" : "minus.gif";
                    out.printf("<img src=\"%s/_images/%s\" width=9 height=9></a>", context.getContextPath(), image);
                }
                if (null != href)
                {
                    // print title with user-specified link:
                    out.print("<a class=\"labkey-header\" href=\"" + PageFlowUtil.filter(href) + "\">");
                    out.print(PageFlowUtil.filter(title));
                    out.print("</a>");
                }
                else if (isCollapsible)
                {
                    // print title with expand/collapse link:
                    out.printf("<a class=\"labkey-header\" href=\"%s\" onclick=\"return LABKEY.Utils.toggleLink(document.getElementById(%s), %s);\">",
                            filter(expandCollapseUrl.getLocalURIString()), PageFlowUtil.jsString(expandCollapseGifId), "true");
                    out.print(PageFlowUtil.filter(title));
                    out.print("</a>");
                }
                else
                {
                    // print the title alone:
                    out.print(PageFlowUtil.filter(title));
                }

                out.print("</div></td></tr>\n"); // end of second <th>
            }
            out.print("<tr" + (config._isCollapsible && config._collapsed ? " style=\"display:none\"" : "") + " class=\"" + className + "\">" +
                    "<td colspan=\"2\" class=\"labkey-expandable-nav-body\">");
        }

        @Override
        public void doEndTag(PrintWriter out)
        {
            out.print("</td></tr></table><!--/webpart-->");
        }
    }
}
