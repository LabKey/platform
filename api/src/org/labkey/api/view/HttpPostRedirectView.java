package org.labkey.api.view;

import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.springframework.web.servlet.View;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Map;

/**
 * User: kevink
 * Date: 11/8/12
 *
 * Renders an HTML form that will POST inputs to a URL.
 * Set the PageConfig template to Template.None before rendering the view.
 */
public class HttpPostRedirectView extends HttpView
{
    final String _url;
    final Collection<? extends Map.Entry<String, String>> _hiddenInputs;

    public HttpPostRedirectView(String url, Map<String, String> hiddenInputs)
    {
        _url = url;
        _hiddenInputs = hiddenInputs.entrySet();
    }

    public HttpPostRedirectView(String url, Collection<Pair<String, String>> hiddenInputs)
    {
        _url = url;
        _hiddenInputs = hiddenInputs;
    }

    @Override
    protected void renderInternal(Object model, PrintWriter out) throws Exception
    {
        out.println("<html>");
        out.println("<body onload='document.forms[\"form\"].submit()'>");
        out.println("<form name='form' method='POST' action='" + PageFlowUtil.filter(_url) + "'>");
        for (Map.Entry<String, String> pair : _hiddenInputs)
        {
            out.println("<input type='hidden' name='" + PageFlowUtil.filter(pair.getKey()) + "' value='" + PageFlowUtil.filter(pair.getValue()) + "'>");
        }
        out.println("</form>");
        out.println("</body>");
        out.println("</html>");
    }
}
