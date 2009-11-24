/*
 * Copyright (c) 2007-2009 LabKey Corporation
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

package org.labkey.api.view.menu;

import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.util.FolderDisplayMode;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.VBox;
import org.labkey.api.view.WebPartView;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * User: brittp
 * Date: Apr 9, 2007
 * Time: 9:36:56 AM
 */
public class MenuView extends VBox
{
    public MenuView(List<? extends WebPartView> views)
    {
        _views = new ArrayList<ModelAndView>(views);
        setFrame(FrameType.NONE);
    }

    @Override
    public void renderView(Object model, HttpServletRequest request, HttpServletResponse response) throws Exception
    {
        boolean showFolders = getViewContext().isShowFolders();
        FolderDisplayMode displayMode = LookAndFeelProperties.getInstance(getViewContext().getContainer()).getFolderDisplayMode();
        boolean renderFolderExpander = false;

        PrintWriter out = response.getWriter();

        out.println("<table class=\"labkey-expandable-nav-panel\">");
        if (showFolders)
        {
            List<HttpView> nonNullViews = new ArrayList<HttpView>();
            for (ModelAndView possibleView : _views)
            {
                if (possibleView instanceof HttpView && ((HttpView)possibleView).isVisible())
                    nonNullViews.add((HttpView)possibleView);
            }
            boolean expanderRendered = false;
            for (HttpView view : nonNullViews)
            {
                if (renderFolderExpander && ! expanderRendered)
                {
                    out.println("<tr><td><!-- menuview element -->");
                    include(view);
                    out.println("<!--/ menuview element --></td>");
                    renderExpandCollapseTD(request, out, showFolders);
                    out.println("</tr>");
                    expanderRendered = true;
                }
                else
                {
                    out.println("<tr><td style=\"padding: 0px;\" colspan=" + (renderFolderExpander ? "2" : "1") + "><!-- menuview element -->");
                    include(view);
                    out.println("<!--/ menuview element --></td></tr>");
                }
            }
        }
        else if(renderFolderExpander) 
        {
            out.println("<tr>");
            renderExpandCollapseTD(request, out, showFolders);
            out.println("</tr>");
        }
        out.print("</table>");
    }

    private void renderExpandCollapseTD(HttpServletRequest request, PrintWriter out, boolean showFolders)
    {
        ActionURL hideShowLink = HttpView.currentContext().cloneActionURL();
        hideShowLink.deleteParameters();
        hideShowLink.setPageFlow("admin");
        hideShowLink.setAction("setShowFolders.view");
        hideShowLink.addParameter("showFolders", Boolean.toString(!showFolders));
        hideShowLink.addParameter("redir", HttpView.currentContext().getActionURL().toString());

        out.print("<td id=\"expandCollapseFolders\" style=\"padding-top:5px;\">\n");
        out.print("<a href=\"" + hideShowLink.getEncodedLocalURIString() + "\">");
        String img = (showFolders ? "collapse_" : "expand_") + "folders.gif";
        String title = "Click to " + (showFolders ? "hide" : "show") + " folders.";
        out.print("<img src=\"" + request.getContextPath() + "/_images/" + img + "\" title=\"");
        out.print(PageFlowUtil.filter(title));
        out.print("\">");
        out.print("</a></td>");
    }
}
