package org.labkey.api.view;

import org.labkey.api.util.PageFlowUtil;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Dec 20, 2007
 * Time: 3:02:54 PM
 */
public class ActionWebPart extends WebPartView
{
    ActionURL _url = null;

    public ActionWebPart(ActionURL url)
    {
        _url = url;
        _url.setContextPath(null);
    }

    @Override
    protected void renderView(Object model, PrintWriter out) throws Exception
    {
        HttpServletRequest request = getViewContext().getRequest();
        HttpServletResponse response = getViewContext().getResponse();

        // catch and ignore close
        out.flush();
        final PrintWriter outWrapper = new PrintWriter(out)
        {
            public void close()
            {
            }
        };
        MockHttpServletResponse r = new MockHttpServletResponse()
        {
            @Override
            public PrintWriter getWriter()
            {
                return outWrapper;
            }

            public void setContentType(String s)
            {
                if (!s.startsWith("text/html"))
                {
                    throw new IllegalStateException("can only include html");
                }
            }
        };

        ViewServlet.forwardActionURL(request, r, _url);
        String redirect = (String)r.getHeader("Location");
        if (redirect != null)
        {
            out.write("<a href='");
            out.write(PageFlowUtil.filter(redirect));
            out.write("'>");
            out.write(PageFlowUtil.filter(redirect));
            out.write("</a>");
        }

        outWrapper.flush();
    }
}
