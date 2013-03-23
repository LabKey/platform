package org.labkey.api.jsp.taglib;

import org.labkey.api.jsp.JspBase;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.template.ClientDependency;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.JspWriter;
import javax.servlet.jsp.PageContext;
import java.io.IOException;
import java.util.LinkedHashSet;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: 3/22/13
 */
public class ScriptDependenciesTag extends SimpleTagBase
{
    private boolean _ajaxOnly;

    @Override
    public void doTag() throws JspException, IOException
    {
        Object requestObj = this.getJspContext().getAttribute(PageContext.REQUEST);

        if (requestObj instanceof HttpServletRequest)
        {
            HttpServletRequest request = (HttpServletRequest)requestObj;
            boolean getDependencies = isAjaxOnly() ? ("XMLHttpRequest".equals(request.getHeader("x-requested-with"))) : true;

            if (getDependencies)
            {
                Object page = this.getJspContext().getAttribute(PageContext.PAGE);
                if (page instanceof JspBase)
                {
                    JspBase jspView = (JspBase)page;
                    LinkedHashSet<ClientDependency> dependencies = jspView.getClientDependencies();

                    ViewContext context = jspView.getViewContext();

                    LinkedHashSet<String> includes = new LinkedHashSet<String>();
                    LinkedHashSet<String> implicitIncludes = new LinkedHashSet<String>();
                    PageFlowUtil.getJavaScriptFiles(context.getContainer(), context.getUser(), dependencies, includes, implicitIncludes);

                    LinkedHashSet<String> cssScripts = new LinkedHashSet<String>();
                    for (ClientDependency d : dependencies)
                    {
                        cssScripts.addAll(d.getCssPaths(context.getContainer(), context.getUser(), AppProps.getInstance().isDevMode()));
                    }

                    if (!includes.isEmpty() || !cssScripts.isEmpty())
                    {
                        StringBuilder sb = new StringBuilder();

                        sb.append("<script type=\"text/javascript\">");

                        for (String script : includes)
                        {
                            sb.append("\tLABKEY.requiresScript('").append(script).append("');\n");
                        }

                        for (String script : cssScripts)
                        {
                            sb.append("\tLABKEY.requiresCss('").append(script).append("');\n");
                        }
                        sb.append("</script>\n");

                        JspWriter out = getOut();
                        out.write(sb.toString());
                    }
                }
            }
        }
    }

    public boolean isAjaxOnly()
    {
        return _ajaxOnly;
    }

    public void setAjaxOnly(boolean ajaxOnly)
    {
        _ajaxOnly = ajaxOnly;
    }
}
