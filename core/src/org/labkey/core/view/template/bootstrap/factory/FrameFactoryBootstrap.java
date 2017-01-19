package org.labkey.core.view.template.bootstrap.factory;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.util.PageFlowUtil;
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

    protected class FramePortalBootstrap implements WebPartFrame
    {
        final ViewContext context;
        final FrameConfig config;

        FramePortalBootstrap(ViewContext context, FrameConfig config)
        {
            this.context = context;
            this.config = config;
        }

        @Override
        public void doStartTag(PrintWriter out)
        {
            out.println("<!--FrameType.PORTAL-->");
            out.write("<div class=\"panel panel-default\"");
            if (config._webpart != null)
            {
                out.write(" id=\"" + config._webpart.getRowId() + "\"");
            }
            out.write(">");

            String title = config._title;
            if (!StringUtils.isEmpty(title))
            {
                out.println("<div class=\"panel-heading clearfix\">");
                out.println("<h3 class=\"panel-title pull-left\">" + PageFlowUtil.filter(title) + "</h3>");
                out.println("</div>");
            }

            out.write("<div class=\"panel-body\">");
        }

        @Override
        public void doEndTag(PrintWriter out)
        {
            out.write("</div></div><!--/FrameType.PORTAL-->");
        }
    }
}
