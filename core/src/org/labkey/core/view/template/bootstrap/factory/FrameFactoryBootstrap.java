/*
 * Copyright (c) 2017 LabKey Corporation
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
package org.labkey.core.view.template.bootstrap.factory;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.PopupMenuView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFrame;
import org.labkey.api.view.WebPartView;
import org.labkey.api.view.template.FrameFactoryClassic;

import java.io.PrintWriter;

import static org.labkey.api.util.PageFlowUtil.filter;
import static org.labkey.api.util.PageFlowUtil.jsString;

public class FrameFactoryBootstrap extends FrameFactoryClassic
{
    @Override
    public WebPartFrame createFrame(Enum e, ViewContext context, WebPartFrame.FrameConfig config)
    {
        if (!(e instanceof WebPartView.FrameType))
            throw new IllegalStateException();

        switch ((WebPartView.FrameType) e)
        {
            case DIV:
            case LEFT_NAVIGATION:
            case NONE:
            case NOT_HTML:
                break;
            case TITLE:
                return new FrameTitleBootstrap(context, config);
            case PORTAL:
                if (config._webpart == null || config._webpart.hasFrame())
                    return new FramePortalBootstrap(context, config);
                else
                    return new FramelessPortalBootstrap(context, config);
            case DIALOG:
                return new FrameDialogBootstrap(context, config);
        }

        return super.createFrame(e, context, config);
    }

    protected class FrameTitleBootstrap extends _FrameTitle
    {
        public FrameTitleBootstrap(ViewContext context, FrameConfig config)
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

    protected class FrameDialogBootstrap extends _FrameDialog
    {
        public FrameDialogBootstrap(ViewContext context, FrameConfig config)
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

    protected class FramePortalBootstrap extends _FramePortal
    {
        public FramePortalBootstrap(ViewContext context, FrameConfig config)
        {
            super(context, config);
        }

        @Override
        public void renderWebpartStart(PrintWriter out)
        {
            out.print("<!--FrameType.PORTAL-->");
            out.println("<div name=\"webpart\"");
            if (null != getConfig()._webpart)
                out.println(" id=\"webpart_" + getConfig()._webpart.getRowId() + "\"");
            out.write(">");
            out.println("<div class=\"panel panel-portal\">");
        }

        @Override
        public void renderWebpartHeaderStart(PrintWriter out, String title)
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

        @Override
        public void renderWebpartHeaderEnd(PrintWriter out)
        {
            out.println("<div class=\"clearfix\"></div></div>");
        }

        @Override
        public void doEndTag(PrintWriter out)
        {
            out.write("</div></div></div><!--/FrameType.PORTAL-->");
        }

        @Override
        public void renderPortalBody(PrintWriter out)
        {
            out.print("<div id=\"WebPartView" + System.identityHashCode(this) + "\" ");
            if (getConfig()._collapsed && getConfig()._isCollapsible)
                out.print("style=\"display: none\"");
            out.print(" class=\"" + (null==getConfig()._className?"":getConfig()._className) + " panel-body\">");
        }

        @Override
        public void renderPortalMenuIcon(String title, NavTree menu, PrintWriter out, String imageCls)
        {
            renderMenuWithFontImage(title, menu, out, imageCls, true);
        }

        @Override
        public void renderPortalTitleRight(PrintWriter out)
        {

        }

        @Override
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

        @Override
        public void renderCustomDropDown(String title, NavTree current, PrintWriter out)
        {
            renderMenuWithFontImage(null, current, out, null, false);
        }
    }

    public class FramelessPortalBootstrap extends FramePortalBootstrap
    {
        public FramelessPortalBootstrap(ViewContext context, FrameConfig config)
        {
            super(context, config);
        }

        @Override
        public void renderWebpartStart(PrintWriter out)
        {
            out.print("<!--FrameType.PORTAL_FRAMELESS-->");
            out.println("<div name=\"webpart\"");
            if (null != getConfig()._webpart)
                out.println(" id=\"webpart_" + getConfig()._webpart.getRowId() + "\"");
            out.write(">");
            out.println("<div class=\"panel-frameless\">");
        }

        @Override
        public void renderWebpartHeaderStart(PrintWriter out, String title)
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
        public void renderPortalBody(PrintWriter out)
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
                super.renderFloatingCustomButton(out);
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

    @Override
    protected String getWebpartIconBtnActiveCls()
    {
        return "";
    }

    @Override
    protected void renderMenuWithFontImage(String title, NavTree menu, PrintWriter out, String imageCls, boolean rightAlign)
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
            out.print("<ul class=\"dropdown-menu dropdown-menu-right");
            out.print(getWebpartIconBtnActiveCls());
            out.print("\">");

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
