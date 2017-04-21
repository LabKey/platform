package org.labkey.core.view.template.bootstrap.factory;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFrame;
import org.labkey.api.view.WebPartView;
import org.labkey.api.view.template.FrameFactoryClassic;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;

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
                return super.createFrame(e, context, config);
            case TITLE:
                return new FrameTitleBootstrap(context, config);
            case PORTAL:
                return new FramePortalBootstrap(context, config);
            case DIALOG:
                return new FrameDialogBootstrap(context, config);
            case LEFT_NAVIGATION:
            case NONE:
            case NOT_HTML:
                return super.createFrame(e, context, config);
        }

        return super.createFrame(e, context, config);
    }

    public static void startBootstrapTitleFrame(Writer out, String title, String href, String className)
    {
        try
        {
            out.write(
                    "<table width=\"100%\">" +
                            "<tr>" +
                            "<td class=\"labkey-announcement-title\" align=left><span>");
            if (null != href)
                out.write("<a href=\"" + PageFlowUtil.filter(href) + "\">");
            out.write(PageFlowUtil.filter(title));
            if (null != href)
                out.write("</a>");
            out.write("</span></td></tr>");
            out.write("<tr><td style=\"padding: 10px 0\"><span class=\"labkey-title-area-line\" style=\"width: 100%;float: left;\"></span></td></tr>");
            out.write("<tr><td colspan=3 class=\"" + className + "\">");
        }
        catch (IOException x)
        {
            throw new RuntimeException(x);
        }
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

            startBootstrapTitleFrame(out,title, href, className);
        }

        @Override
        public void doEndTag(PrintWriter out)
        {
            endTitleFrame(out);
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

            out.println("<div class=\"panel-heading clearfix\">");
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
            out.println("<div name=\"webpart\" class=\"panel panel-default labkey-portal-wp\"");
            if (null != getConfig()._webpart)
                out.println(" id=\"webpart_" + getConfig()._webpart.getRowId() + "\"");
            out.write(">");
        }

        @Override
        public void renderWebpartHeaderStart(PrintWriter out, String title)
        {
            out.println("<div class=\"panel-heading clearfix\">");
            out.print("<h3 class=\"panel-title pull-left\" title=\"");
            if (getConfig()._showTitle)
                out.print(PageFlowUtil.filter(title));
            out.print("\">");
            out.print("<a name=\"" + PageFlowUtil.filter(title) + "\" class=\"labkey-wp-title-anchor\">");
            if (getConfig()._isCollapsible)
            {
                renderCollapsiblePortalTitle(out);
            }
            else
            {
                renderNonCollapsiblePortalTitle(out);
            }

            out.print("</a>");
            out.print("</h3>");
        }

        @Override
        public void renderWebpartHeaderEnd(PrintWriter out)
        {
            out.println("</div>");
        }

        @Override
        public void doEndTag(PrintWriter out)
        {
            out.write("</div></div><!--/FrameType.PORTAL-->");
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

    }

    @Override
    protected String getWebpartIconBtnActiveCls()
    {
        return "";
    }

}
