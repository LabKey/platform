/*
 * Copyright (c) 2015-2017 LabKey Corporation
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
package org.labkey.core.view.template.bootstrap;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.PopupMenu;
import org.labkey.api.view.PopupMenuView;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ViewService;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartFrame;
import org.labkey.api.view.WebPartView.FrameType;
import org.labkey.api.view.template.PageConfig;
import org.labkey.api.view.template.PageConfig.Template;
import org.springframework.web.servlet.ModelAndView;

import java.io.PrintWriter;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static org.labkey.api.util.PageFlowUtil.filter;
import static org.labkey.api.util.PageFlowUtil.jsString;

/**
 * This service/impl pair allows templates and frame types to be implemented in the core module rather than API, where
 * the corresponding enums live.
 *
 * Created by matthew on 12/14/15.
 */
public class ViewServiceImpl implements ViewService
{
    private static final ViewService INSTANCE = new ViewServiceImpl();

    public static ViewService getInstance()
    {
        return INSTANCE;
    }

    private ViewServiceImpl()
    {
    }

    @Override
    public HttpView<PageConfig> getTemplate(Template t, ViewContext context, ModelAndView body, PageConfig page)
    {
        switch (t)
        {
            case None:
            {
                return null;
            }
            case Framed:
            case Print:
            {
                return new PrintTemplate(context, body, page);
            }
            case Dialog:
            {
                return new DialogTemplate(context, body, page);
            }
            case Wizard:
            {
                return new WizardTemplate(context, body, page);
            }
            case Body:
            case App:
            {
                return new AppTemplate(context, body, page, t.equals(Template.App));
            }
            case Home:
            {
                return new PageTemplate(context, body, page);
            }
        }

        throw new IllegalStateException("Unknown Template");
    }

    @Override
    public WebPartFrame getFrame(FrameType frameType, ViewContext context, WebPartFrame.FrameConfig config)
    {
        switch (frameType)
        {
            case DIV:
                return new FrameDiv(context, config);
            case LEFT_NAVIGATION:
                return new FrameNavigation(context, config);
            case NONE:
            case NOT_HTML:
                return new FrameNone(context, config);
            case TITLE:
                return new FrameTitle(context, config);
            case PORTAL:
                if (config._webpart == null || config._webpart.hasFrame())
                    return new FramePortal(context, config);
                else
                    return new FramelessPortal(context, config);
            case DIALOG:
                return new FrameDialog(context, config);
        }

        throw new IllegalStateException("Unknown FrameType");
    }

    private abstract class AbstractFrame implements WebPartFrame
    {
        protected final boolean _devMode = AppProps.getInstance().isDevMode();
        protected final ViewContext context;
        protected final FrameConfig config;

        AbstractFrame(ViewContext context, FrameConfig config)
        {
            this.context = context;
            this.config = config;
        }

        protected ViewContext getContext()
        {
            return context;
        }

        protected FrameConfig getConfig()
        {
            return config;
        }
    }

    private class FrameDiv extends AbstractFrame
    {
        FrameDiv(ViewContext context, FrameConfig config)
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

    @Deprecated // Please, do not add any more usages of this
    private class FrameNavigation extends AbstractFrame
    {
        FrameNavigation(ViewContext context, FrameConfig config)
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
                        expandCollapseUrl = PageFlowUtil.urlProvider(ProjectUrls.class).getExpandCollapseURL(context.getContainer(), "", rootId);
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

    private class FrameNone extends AbstractFrame
    {
        FrameNone(ViewContext context, FrameConfig config)
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

    private class FrameTitle extends AbstractFrame
    {
        FrameTitle(ViewContext context, FrameConfig config)
        {
            super(context, config);
        }

        @Override
        public void doStartTag(PrintWriter out)
        {
            String title = getConfig()._title;
            String href = getConfig()._titleHref;
            String className = getConfig()._className;
            if (_devMode)
                out.print("<!--FrameType.TITLE-->");

            out.write("<table width=\"100%\"><tr><td class=\"labkey-announcement-title\" align=\"left\"><span>");
            if (null != href)
                out.write("<a href=\"" + PageFlowUtil.filter(href) + "\">");
            out.write(PageFlowUtil.filter(title));
            if (null != href)
                out.write("</a>");
            out.write("</span></td></tr>");
            out.write("<tr><td style=\"padding: 10px 0\"><span class=\"labkey-title-area-line\" style=\"width: 100%;float: left;\"></span></td></tr>");
            out.write("<tr><td colspan=3 class=\"" + className + "\">");
        }

        @Override
        public void doEndTag(PrintWriter out)
        {
            out.write("</td></tr></table>");
        }
    }

    private class FrameDialog extends AbstractFrame
    {
        FrameDialog(ViewContext context, FrameConfig config)
        {
            super(context, config);
        }

        @Override
        public void doStartTag(PrintWriter out)
        {
            if (_devMode)
                out.print("<!--FrameType.DIALOG-->");
            out.println("<div class=\"panel panel-default labkey-dialog-frame\">");
            out.println("<div class=\"panel-heading\">");
            out.print("<span class=\"panel-title pull-left\">");
            out.print(PageFlowUtil.filter(getConfig()._title));
            out.print("</span>");

            if (null != getConfig()._closeURL)
            {
                String closeUrl = getConfig()._closeURL.getLocalURIString();
                out.print("<a class=\"pull-right\" href=\"" + PageFlowUtil.filter(closeUrl) + "\">");
                out.print("<i class=\"fa fa-times\"></i>");
                out.print("</a>");
            }

            out.print("</div><div class=\"" + StringUtils.defaultString(getConfig()._className,"") + " panel-body\">");
        }

        @Override
        public void doEndTag(PrintWriter out)
        {
            out.print("</div></div>");
        }
    }

    private class FramePortal extends AbstractFrame
    {
        FramePortal(ViewContext context, FrameConfig config)
        {
            super(context, config);
        }

        @Override
        public void doStartTag(PrintWriter out)
        {
            String title = StringUtils.trimToEmpty(config._title);

            renderWebpartStart(out);

            // Don't render webpart header if title is null or blank
            if (!StringUtils.isEmpty(title))
            {
                renderWebpartHeaderStart(out, title);

                if (config._helpPopup != null)
                {
                    out.print(config._helpPopup);
                }

                renderPortalMenu(out, title);

                renderWebpartHeaderEnd(out);
            }
            renderPortalBody(out);
        }

        public void renderCustomButton(NavTree current, PrintWriter out)
        {
            renderCustomButton(current, out, false);
        }

        public void renderCustomButton(NavTree current, PrintWriter out, boolean isFramelessFloatingBtn)
        {
            String linkHref = current.getHref();
            String linkText = current.getText();
            String script = current.getScript();

            if (StringUtils.isEmpty(linkHref) && StringUtils.isEmpty(script))
            {
                out.print("<span class=\"labkey-wp-icon-button-inactive");
                if (isFramelessFloatingBtn)
                    out.print(" labkey-frameless-wp-icon");
                out.print("\">");
            }
            else
            {
                out.print("<span class=\"");
                if (isFramelessFloatingBtn)
                    out.print("labkey-frameless-wp-icon");
                out.print("\">");

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

        protected void renderFloatingCustomButton(PrintWriter out)
        {
            // Render custom buttons (e.g. wiki edit)
            if (config._floatingBtns != null)
            {
                Iterator itr = config._floatingBtns.iterator();
                NavTree current;
                while (itr.hasNext())
                {
                    current = (NavTree) itr.next();

                    if (!current.hasChildren()) // floating dropdown not yet supported
                        renderCustomButton(current, out, true);
                }
            }
        }

        private void renderNonCollapsiblePortalTitle(PrintWriter out)
        {
            if (null != config._titleHref)
                out.print("<a href=\"" + PageFlowUtil.filter(config._titleHref) + "\">");
            if (config._showTitle)
            {
                out.print("<span class=\"labkey-wp-title-text\">");
                out.print(PageFlowUtil.filter(StringUtils.trimToEmpty(config._title)));
                out.print("</span>");
            }
            if (null !=  config._titleHref)
                out.print("</a>");
        }

        public void renderPortalMenu(PrintWriter out, String title)
        {
            List<NavTree> links = null == config._portalLinks ? Collections.emptyList() : config._portalLinks.getChildren();
            String sep = "";

            // Add Custom link
            NavTree nMenu = config._navMenu;
            if (nMenu == null)
                nMenu = new NavTree("More");  // 11730 : Customize link missing on many webparts

            if (config._customize != null)
            {
                config._customize.setText("Customize");
                nMenu.addChild(config._customize);
            }

            if (config._webpart != null && context.getUser().isInSiteAdminGroup())
            {
                Portal.WebPart webPart = config._webpart;
                String permissionString = null;
                String containerPathString = null;

                if (webPart.getPermission() != null)
                    permissionString = "'" + webPart.getPermission() + "'";

                if (webPart.getPermissionContainer() != null)
                    containerPathString = PageFlowUtil.qh(webPart.getPermissionContainer().getPath());

                // Wrapped in immediately invoke function expression because of Issue 16953
                if (PageFlowUtil.isPageAdminMode(getContext()))
                {
                    NavTree permissionsNav = new NavTree("Permissions",
                            "javascript:LABKEY.Portal._showPermissions(" +
                                    config._webpart.getRowId() + "," +
                                    permissionString + "," +
                                    containerPathString + ");"
                    );

                    permissionsNav.setId("permissions_" + webPart.getRowId());
                    nMenu.addChild(permissionsNav);
                }
            }

            if (config._location != null && config._location.equals(WebPartFactory.LOCATION_RIGHT))
            {
                // Portal
                if (!links.isEmpty())
                {
                    for (NavTree link : links)
                        nMenu.addChild(link);
                }

                if (nMenu.hasChildren())
                {
                    out.print("&nbsp;");
                    renderPortalMenuIcon(title, nMenu, out, "fa fa-caret-down");
                }
            }
            else if (!config._isWebpart)
            {
                // Purposely don't render custom links to avoid duplicates

                // Render Navigation Links
                if (nMenu.hasChildren())
                {
                    out.print("<div class=\"labkey-wp-text-buttons pull-right\">");
                    for (NavTree link : nMenu.getChildren())
                    {
                        if (link.hasChildren())
                        {
                            try
                            {
                                link.setText(link.getText());
                                PopupMenu more = new PopupMenu(link, PopupMenu.Align.RIGHT, PopupMenu.ButtonStyle.TEXT);
                                more.setOffset("-7");
                                more.render(out);
                            }
                            catch (Exception e)
                            {
                                throw new RuntimeException(e);
                            }
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
                if (!links.isEmpty())
                {
                    for (NavTree link : links)
                        nMenu.addChild(link);
                }

                out.print("&nbsp;");

                if (nMenu.hasChildren())
                {
                    renderPortalMenuIcon(title, nMenu, out, "fa fa-caret-down");
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
                            renderCustomDropDown(title, current, out);
                        }
                        else
                        {
                            renderCustomButton(current, out);
                        }
                    }
                }
            }
        }

        protected void renderWebpartStart(PrintWriter out)
        {
            out.print("<!--FrameType.PORTAL-->");
            out.println("<div name=\"webpart\"");
            if (null != getConfig()._webpart)
                out.println(" id=\"webpart_" + getConfig()._webpart.getRowId() + "\"");
            out.write(">");
            out.println("<div class=\"panel panel-portal\">");
        }

        protected void renderWebpartHeaderStart(PrintWriter out, String title)
        {
            out.print("<div class=\"panel-heading\">");
            out.print("<h3 class=\"panel-title pull-left\"");
            if (getConfig()._showTitle)
            {
                out.print(" title=\"");
                out.print(PageFlowUtil.filter(title));
                out.print("\"");
            }
            out.print("><a name=\"" + PageFlowUtil.filter(title) + "\" class=\"labkey-anchor-disabled\">");
            if (getConfig()._isCollapsible)
                renderCollapsiblePortalTitle(out);
            else
                renderNonCollapsiblePortalTitle(out);
            out.print("</a></h3>");
        }

        public void renderWebpartHeaderEnd(PrintWriter out)
        {
            out.println("<div class=\"clearfix\"></div></div>");
        }

        @Override
        public void doEndTag(PrintWriter out)
        {
            out.write("</div></div></div><!--/FrameType.PORTAL-->");
        }

        protected void renderPortalBody(PrintWriter out)
        {
            out.print("<div id=\"WebPartView" + System.identityHashCode(this) + "\" ");
            if (getConfig()._collapsed && getConfig()._isCollapsible)
                out.print("style=\"display: none\"");
            out.print(" class=\"" + (null==getConfig()._className?"":getConfig()._className) + " panel-body\">");
        }

        public void renderPortalMenuIcon(String title, NavTree menu, PrintWriter out, String imageCls)
        {
            renderMenuWithFontImage(title, menu, out, imageCls, true);
        }

        public void renderCollapsiblePortalTitle(PrintWriter out)
        {
            ActionURL expandCollapseUrl;
            String expandCollapseGifId = "expandCollapse-" + getConfig()._rootId;

            if (getConfig()._rootId == null)
                throw new IllegalArgumentException("pathToHere or rootId not provided");
            expandCollapseUrl = PageFlowUtil.urlProvider(ProjectUrls.class).getExpandCollapseURL(getContext().getContainer(), "", getConfig()._rootId);


            out.printf("<a href=\"%s\" onclick=\"return LABKEY.Utils.toggleLink(this, %s, %s);\" id=\"%s\">",
                    filter(expandCollapseUrl.getLocalURIString()), "true", jsString("DIV"), expandCollapseGifId);
            String image = getConfig()._collapsed ? "plus.gif" : "minus.gif";
            out.printf("<img width=9 height=9 style=\"margin-bottom: 0\" src=\"%s/_images/%s\"></a>", getContext().getContextPath(), image);

            out.printf(" <a href=\"%s\" onclick=\"return LABKEY.Utils.toggleLink(document.getElementById(%s), %s, %s);\">",
                    filter(expandCollapseUrl.getLocalURIString()), PageFlowUtil.jsString(expandCollapseGifId), "true", jsString("DIV"));
            if (getConfig()._showTitle)
            {
                out.print("<span class=\"labkey-wp-title-text\">");
                out.print(PageFlowUtil.filter(StringUtils.trimToEmpty(getConfig()._title)));
                out.print("</span>");
            }
            out.print("</a>");

        }

        public void renderCustomDropDown(String title, NavTree current, PrintWriter out)
        {
            renderMenuWithFontImage(null, current, out, null, false);
        }
    }

    private class FramelessPortal extends FramePortal
    {
        FramelessPortal(ViewContext context, FrameConfig config)
        {
            super(context, config);
        }

        @Override
        protected void renderWebpartStart(PrintWriter out)
        {
            out.print("<!--FrameType.PORTAL_FRAMELESS-->");
            out.println("<div name=\"webpart\"");
            if (null != getConfig()._webpart)
                out.println(" id=\"webpart_" + getConfig()._webpart.getRowId() + "\"");
            out.write(">");
            out.println("<div class=\"panel-frameless\">");
        }

        @Override
        protected void renderWebpartHeaderStart(PrintWriter out, String title)
        {
            if (PageFlowUtil.isPageAdminMode(getContext()))
            {
                super.renderWebpartHeaderStart(out, title);
            }
        }

        @Override
        public void renderWebpartHeaderEnd(PrintWriter out)
        {
            if (PageFlowUtil.isPageAdminMode(getContext()))
            {
                super.renderWebpartHeaderEnd(out);
            }
        }

        @Override
        protected void renderPortalBody(PrintWriter out)
        {
            out.print("<div id=\"WebPartView" + System.identityHashCode(this) + "\"");
            out.print(" style=\"");
            if (getConfig()._collapsed && getConfig()._isCollapsible)
                out.print("display: none;");
            if (getConfig()._showfloatingCustomBtn && !PageFlowUtil.isPageAdminMode(getContext()))
                out.print(" position: relative;");
            out.print("\"");
            if (!StringUtils.isEmpty(getConfig()._className))
                out.print(" class=\"" + getConfig()._className + "\"");
            out.print(">");

            if (getConfig()._showfloatingCustomBtn && !PageFlowUtil.isPageAdminMode(getContext()))
            {
                renderFloatingCustomButton(out);
            }
        }

        @Override
        public void renderPortalMenu(PrintWriter out, String title)
        {
            if (PageFlowUtil.isPageAdminMode(getContext()))
            {
                super.renderPortalMenu(out, title);
            }
        }

        @Override
        public void doEndTag(PrintWriter out)
        {
            out.write("</div></div></div><!--/FrameType.PORTAL_FRAMELESS-->");
        }
    }

    private void renderMenuWithFontImage(String title, NavTree menu, PrintWriter out, String imageCls, boolean rightAlign)
    {
        try
        {
            out.print("<span class=\"dropdown dropdown-rollup");
            if (rightAlign)
                out.print(" pull-right");
            out.print("\">");
            out.print("<a href=\"#\" data-toggle=\"dropdown\" class=\"dropdown-toggle ");
            out.print(!StringUtils.isEmpty(imageCls) ? imageCls : !StringUtils.isEmpty(menu.getImageCls()) ? menu.getImageCls() : "");
            out.print("\">");
            out.print("</a>");
            out.print("<ul class=\"dropdown-menu dropdown-menu-right\">");

            PopupMenuView.renderTree(menu, out);

            out.print("</ul>");
            out.print("</span>");
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }
}
