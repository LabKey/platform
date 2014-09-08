/*
 * Copyright (c) 2013 LabKey Corporation
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
package org.labkey.api.jsp.taglib;

import org.labkey.api.jsp.JspBase;
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

                    LinkedHashSet<String> includes = new LinkedHashSet<>();
                    LinkedHashSet<String> implicitIncludes = new LinkedHashSet<>();
                    PageFlowUtil.getJavaScriptFiles(context.getContainer(), dependencies, includes, implicitIncludes);

                    LinkedHashSet<String> cssScripts = new LinkedHashSet<>();
                    for (ClientDependency d : dependencies)
                    {
                        cssScripts.addAll(d.getCssPaths(context.getContainer()));
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
