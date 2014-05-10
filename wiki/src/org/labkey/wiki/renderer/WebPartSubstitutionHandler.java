/*
 * Copyright (c) 2007-2014 LabKey Corporation
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

package org.labkey.wiki.renderer;

import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.UniqueID;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.api.wiki.FormattedHtml;

import java.io.StringWriter;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Stack;

/**
 * User: adam
 * Date: Jun 27, 2007
 * Time: 2:53:12 AM
 */
public class WebPartSubstitutionHandler implements HtmlRenderer.SubstitutionHandler
{
    private static ThreadLocal<Stack<Map>> _paramsStack = new ThreadLocal<Stack<Map>>()
    {
        @Override
        protected Stack<Map> initialValue()
        {
            return new Stack<>();
        }
    };


    public FormattedHtml getSubstitution(Map<String, String> params)
    {
        params = new CaseInsensitiveHashMap<>(params);
        Stack<Map> stack = _paramsStack.get();

        if (stack.contains(params))
            return new FormattedHtml("<br><font class='error' color='red'>Error: recursive rendering</font>");

        stack.push(params);

        try
        {
            String partName = params.get("partName");
            WebPartFactory desc = Portal.getPortalPartCaseInsensitive(partName);

            if (null == desc)
                return new FormattedHtml("<br><font class='error' color='red'>Error: Could not find webpart \"" + partName + "\"</font>");

            String partLocation = params.get("location");

            Portal.WebPart part = new Portal.WebPart();
            part.setName(partName);
            if (partLocation != null)
                part.setLocation(partLocation);
            part.getPropertyMap().putAll(params);

            WebPartView view = null;
            try
            {
                ViewContext ctx = HttpView.currentContext();

                // Try to ensure that each webpart gets a unique default DataRegionName
                if (null != ctx)
                    part.setIndex(UniqueID.getRequestScopedUID(ctx.getRequest()));

                view = Portal.getWebPartViewSafe(desc, ctx, part);
                view.setEmbedded(true);  // Let the webpart know it's being embedded in another page

                String showFrame = params.get("showFrame");

                if (null != showFrame && !Boolean.parseBoolean(showFrame))
                    view.setFrame(WebPartView.FrameType.NONE);
            }
            catch (Exception e)
            {
                //
            }

            if (null == view)
                return null;

            view.addAllObjects(params);
            StringWriter sw = new StringWriter();

            try
            {
                //Issue 15609: we need to include client dependencies for embedded webparts
                if (view.getClientDependencies().size() > 0)
                {
                    Container c;
                    User u;
                    ViewContext ctx = HttpView.currentContext();
                    assert ctx != null;

                    if (ctx == null)
                    {
                        c = ContainerManager.getRoot();
                        u = User.guest;
                    }
                    else
                    {
                        c = ctx.getContainer();
                        u = ctx.getUser();
                    }

                    LinkedHashSet<ClientDependency> dependencies = view.getClientDependencies();
                    LinkedHashSet<String> includes = new LinkedHashSet<>();
                    PageFlowUtil.getJavaScriptFiles(c, u, dependencies, includes, new LinkedHashSet<String>());

                    LinkedHashSet<String> cssScripts = new LinkedHashSet<>();
                    for (ClientDependency d : dependencies)
                    {
                        cssScripts.addAll(d.getCssPaths(c, u, AppProps.getInstance().isDevMode()));
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

                        sw.write(sb.toString());
                    }
                }
                view.include(view, sw);
            }
            catch (Throwable e)
            {
                return null;
            }

            return new FormattedHtml(sw.toString(), true);  // All webparts are considered volatile... CONSIDER: Be more selective (e.g., query & messages, but not search) 
        }
        finally
        {
            Map m = stack.pop();
            assert m == params : "Stack problem while checking for recursive webpart rendering: popped params didn't match the params that were pushed";
        }
    }
}
