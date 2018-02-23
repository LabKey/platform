/*
 * Copyright (c) 2013-2016 LabKey Corporation
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
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.template.ClientDependency;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.jsp.JspWriter;
import javax.servlet.jsp.PageContext;
import java.io.IOException;
import java.util.LinkedHashSet;

/**
 * User: klum
 * Date: 3/22/13
 */
@Deprecated // Configure your view to include dependencies at initialization prior to render time
public class ScriptDependenciesTag extends SimpleTagBase
{
    private boolean _ajaxOnly;
    private String _callback;
    private String _scope;

    @Override
    public void doTag() throws IOException
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

                    if (dependencies.size() > 0)
                    {
                        LinkedHashSet<String> includes = new LinkedHashSet<>();
                        LinkedHashSet<String> implicitIncludes = new LinkedHashSet<>();
                        LinkedHashSet<String> cssScripts = new LinkedHashSet<>();
                        ViewContext context = jspView.getViewContext();

                        PageFlowUtil.getJavaScriptFiles(context.getContainer(), dependencies, includes, implicitIncludes);
                        cssScripts.addAll(PageFlowUtil.getExtJSStylesheets(context.getContainer(), dependencies));

                        for (ClientDependency d : dependencies)
                        {
                            cssScripts.addAll(d.getCssPaths(context.getContainer()));
                        }

                        if (!includes.isEmpty() || !cssScripts.isEmpty())
                        {
                            StringBuilder sb = new StringBuilder();
                            sb.append("<script type=\"text/javascript\">");

                            if (_callback != null && _scope != null)
                            {
                                StringBuilder files = new StringBuilder("[");
                                String delim = "";
                                for (String script : includes)
                                {
                                    files.append(delim);
                                    files.append("'").append(script).append("'");

                                    delim = ",";
                                }
                                files.append(']');
                                sb.append("\tLABKEY.requiresScript(").append(files).append(",").append(_callback).append(",").
                                        append(_scope).append(", true);\n");
                            }
                            else
                            {
                                for (String script : includes)
                                {
                                    sb.append("\tLABKEY.requiresScript('").append(script).append("');\n");
                                }
                            }

                            for (String script : cssScripts)
                            {
                                sb.append("\tLABKEY.requiresCss('").append(script).append("');\n");
                            }

                            if (AppProps.getInstance().isDevMode())
                                sb.append("console.warn('<labkey:scriptDependency/> was deprecated in 18.1. If you find that it is required for your usage please investigate why view dependencies are not loaded before render time.');");
                            sb.append("</script>\n");

                            JspWriter out = getOut();
                            out.write(sb.toString());
                        }
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

    public String getCallback()
    {
        return _callback;
    }

    public void setCallback(String callback)
    {
        _callback = callback;
    }

    public String getScope()
    {
        return _scope;
    }

    public void setScope(String scope)
    {
        _scope = scope;
    }
}
