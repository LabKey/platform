package org.labkey.api.view;

import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: May 20, 2009
 * Time: 2:13:22 PM
 *
 * like VBox or HBox, but with tabs!
 */
public class TabBoxView extends VBox
{
    FrameType _frameType = FrameType.NONE;

    public void setFrameType(FrameType type)
    {

    }

    @Override
    public void renderView(Object model, HttpServletRequest request, HttpServletResponse response) throws Exception
    {
        PrintWriter out = response.getWriter();

        JspView tv = new JspView<TabBoxView>(TabBoxView.class, "tabbox.jsp", this);
        include(tv, out);

        out.println("<div style='display:none;'>");
        int index=0;
        for (ModelAndView view : _views)
        {
            if (null == view)
                continue;
            if (null != _frameType && view instanceof WebPartView)
                ((WebPartView)view).setFrame(_frameType);
            out.print("<div id='tabWebPart" + index + "'>");
            include(view);
            out.println("</div>");
            index++;
        }
        out.println("'div'");
    }
}
