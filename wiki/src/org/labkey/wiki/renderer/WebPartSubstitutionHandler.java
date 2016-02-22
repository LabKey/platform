/*
 * Copyright (c) 2007-2016 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.UniqueID;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.NotFoundException;
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
    private static final Logger LOG = Logger.getLogger(WebPartSubstitutionHandler.class);
    private static final ThreadLocal<Stack<Map>> _paramsStack = new ThreadLocal<Stack<Map>>()
    {
        @Override
        protected Stack<Map> initialValue()
        {
            return new Stack<>();
        }
    };


    @NotNull
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
            WebPartFactory factory = Portal.getPortalPartCaseInsensitive(partName);

            if (null == factory)
                return new FormattedHtml("<br><font class='error' color='red'>Error: Could not find webpart \"" + partName + "\"</font>");

            String partLocation = params.get("location");

            Portal.WebPart part = new Portal.WebPart();
            part.setName(partName);
            if (partLocation != null)
                part.setLocation(partLocation);
            part.getPropertyMap().putAll(params);

            StringWriter sw = new StringWriter();
            LinkedHashSet<ClientDependency> dependencies = new LinkedHashSet<>();

            try
            {
                ViewContext ctx = HttpView.currentContext();

                if (null == ctx)
                    throw new IllegalStateException("ViewContext should be set");

                // Try to ensure that each webpart gets a unique default DataRegionName
                part.setIndex(UniqueID.getRequestScopedUID(ctx.getRequest()));

                WebPartView<?> view = Portal.getWebPartViewSafe(factory, ctx, part);

                if (null == view)
                    throw new NotFoundException("Webpart factory \"" + factory.getName() + "\" did not return a view");

                view.setEmbedded(true);  // Let the webpart know it's being embedded in another page

                String showFrame = params.get("showFrame");

                if (null != showFrame && !Boolean.parseBoolean(showFrame))
                    view.setFrame(WebPartView.FrameType.NONE);

                view.addAllObjects(params);

                //Issue 15609: we need to include client dependencies for embedded webparts
                dependencies.addAll(view.getClientDependencies());

                view.include(view, sw);
            }
            catch (Throwable e)
            {
                // Let's at least log these exceptions in dev mode
                if (AppProps.getInstance().isDevMode())
                    LOG.error("Error substituting " + partName, e);

                // Return HTML with error
                return new FormattedHtml("<br><font class='error' color='red'>Error substituting " + partName + ": " + e.getMessage() + "</font>");
            }

            return new FormattedHtml(sw.toString(), true, dependencies);  // All webparts are considered volatile... CONSIDER: Be more selective (e.g., query & messages, but not search)
        }
        finally
        {
            Map m = stack.pop();
            assert m == params : "Stack problem while checking for recursive webpart rendering: popped params didn't match the params that were pushed";
        }
    }
}
