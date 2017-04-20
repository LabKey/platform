package org.labkey.core.view.template.bootstrap.factory;

import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFrame;
import org.labkey.api.view.WebPartView;
import org.labkey.api.view.template.FrameFactoryClassic;

import java.io.PrintWriter;

public class FrameFactoryBootstrap extends FrameFactoryClassic
{
    @Override
    public WebPartFrame createFrame(Enum e, ViewContext context, WebPartFrame.FrameConfig config)
    {
        if (!(e instanceof WebPartView.FrameType))
            throw new IllegalStateException();

        WebPartView.FrameType type = (WebPartView.FrameType) e;
        if (type.equals(WebPartView.FrameType.PORTAL))
        {
            return new FramePortalBootstrap(context, config);
        }

        return super.createFrame(e, context, config);
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
            out.print("<h3 class=\"panel-title pull-left\" title=\">");
            if (getConfig()._showTitle)
                out.print(PageFlowUtil.filter(title));
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

    }
}
