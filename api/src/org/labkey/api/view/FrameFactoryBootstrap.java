package org.labkey.api.view;

import java.io.PrintWriter;

public class FrameFactoryBootstrap extends FrameFactoryClassic
{
    @Override
    public WebPartFrame createFrame(Enum e, ViewContext context, WebPartFrame.FrameConfig config)
    {
        final WebPartFrame wpf = super.createFrame(e,context,config);
        if (e != WebPartView.FrameType.PORTAL)
            return wpf;
        return new WebPartFrame()
        {
            @Override
            public void doStartTag(PrintWriter out)
            {
                out.write("<div style=\"border:solid 1px purple;\">");
                wpf.doStartTag(out);
            }

            @Override
            public void doEndTag(PrintWriter out)
            {
                wpf.doEndTag(out);
                out.write("</div>");
            }
        };
    }
}
